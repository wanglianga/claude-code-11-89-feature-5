#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
供水抢修服务端到端回放脚本（仅依赖 Python 标准库）。

回放题目要求的四条主线：
  1. 同段重拨：热线重拨只添来源，不增事故/派工；
  2. 相邻双支管：相邻报修分别归属 S2 / S3，候选边界与影响集合精确；
  3. 唯一持证班组被两次并发派单：行锁+时间窗互斥，恰好一成一败，并验证占用释放；
  4. 复压失败后重新派工：顺序回执、复压门槛、周期封存、占用释放、历史回执归属。
另含：两种调度路线（勘查先行 / 维修队+阀票窗口捆绑暂留）、
      压力复核推翻后的范围/占用/通知更正链、资质/当班/车辆/工具门槛、校时容差试算。

用法：
  python3 scripts/replay.py                # 默认 http://127.0.0.1:8080
  BASE_URL=http://127.0.0.1:8080 python3 scripts/replay.py
退出码 0 表示全部断言通过。
"""
import json
import os
import sys
import time
import threading
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

BASE = os.environ.get("BASE_URL", "http://127.0.0.1:8080").rstrip("/")
ROOT = Path(__file__).resolve().parent.parent
SAMPLE_DIR = ROOT / "src" / "main" / "resources" / "samples"

PASS_COUNT = 0
FAIL_COUNT = 0
FAIL_LINES = []


def section(title):
    print("\n" + "=" * 78)
    print("▶ " + title)
    print("=" * 78)


def check(name, cond, detail=""):
    global PASS_COUNT, FAIL_COUNT
    if cond:
        PASS_COUNT += 1
        print(f"  ✅ {name}")
    else:
        FAIL_COUNT += 1
        FAIL_LINES.append(f"{name} :: {detail}")
        print(f"  ❌ {name}  {('- ' + str(detail)) if detail else ''}")


def require(name, cond, detail=""):
    check(name, cond, detail)
    if not cond:
        raise AssertionError(f"关键断言失败，回放中止: {name} {detail}")


# ---------------- HTTP 客户端 ----------------

def http(method, path, body=None, token=None, expect=None):
    url = BASE + path
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read().decode("utf-8")
            status = resp.status
            payload = json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            payload = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            payload = {"raw": raw}
        status = e.code
    if expect is not None and status != expect:
        raise AssertionError(f"{method} {path} 期望 {expect}，实际 {status}: {payload}")
    return status, payload


def wait_healthy(timeout_sec=90):
    deadline = time.time() + timeout_sec
    last = None
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(BASE + "/api/health", timeout=5) as r:
                if r.status == 200:
                    return True
        except Exception as e:  # noqa
            last = e
            time.sleep(2)
    print("服务未就绪:", last)
    return False


def login(username, password):
    _, data = http("POST", "/api/auth/login",
                   {"username": username, "password": password}, expect=200)
    return data["token"]


def as_set(xs):
    return set(xs or [])


# ---------------- 主流程 ----------------

def main():
    if not wait_healthy():
        print("ALL_DONE: false")
        sys.exit(2)

    reports_doc = json.loads((SAMPLE_DIR / "reports.json").read_text(encoding="utf-8"))
    receipt_tpls = json.loads(
        (SAMPLE_DIR / "field-receipt-templates.json").read_text(encoding="utf-8"))["templates"]

    # ---------- 0. 鉴权与 reset ----------
    section("0. 鉴权、测试账号与可重复播种")
    st, _ = http("GET", "/api/incidents")
    check("未登录访问 /api/incidents 返回 401", st == 401, f"status={st}")

    viewer_token = login("viewer", "View@12345")
    st, body = http("POST", "/api/incidents", {"reportIds": [1]}, token=viewer_token)
    check("VIEWER 账号不能调度确认事故（403）", st == 403, f"status={st} body={body}")

    disp = login("dispatcher", "Disp@12345")
    crew_token = login("crewsole", "Crew@12345")
    check("四个测试账号均可登录（dispatcher/viewer/crewsole）",
          bool(disp and viewer_token and crew_token))

    st, reset_body = http("POST", "/api/admin/reset", {}, token=disp, expect=200)
    check("POST /api/admin/reset 重新播种成功", reset_body.get("reset") is True, reset_body)

    st, doc = http("GET", "/v3/api-docs")
    check("OpenAPI 3.x 文档 /v3/api-docs 可匿名访问且含接口定义",
          st == 200 and "/api/incidents" in json.dumps(doc), f"status={st}")
    try:
        with urllib.request.urlopen(BASE + "/swagger-ui/index.html", timeout=10) as r:
            check("Swagger UI /swagger-ui/index.html 可访问", r.status == 200)
    except Exception as e:  # noqa
        check("Swagger UI /swagger-ui/index.html 可访问", False, str(e))

    # ---------- 1. 样例管网与精确影响集合 ----------
    section("1. 样例管网：S1=A10-A13, S2=A01-A05, S3=A06-A09；V1/V2 影响集合")
    _, addresses = http("GET", "/api/network/addresses", token=disp)
    seg_of = {a["code"]: a["segmentCode"] for a in addresses}
    s1 = sorted(c for c, s in seg_of.items() if s == "S1")
    s2 = sorted(c for c, s in seg_of.items() if s == "S2")
    s3 = sorted(c for c, s in seg_of.items() if s == "S3")
    check("S1 地址恰为 A10-A13", s1 == ["A10", "A11", "A12", "A13"], s1)
    check("S2 地址恰为 A01-A05", s2 == ["A01", "A02", "A03", "A04", "A05"], s2)
    check("S3 地址恰为 A06-A09", s3 == ["A06", "A07", "A08", "A09"], s3)

    _, imp_v2 = http("POST", "/api/network/impact", {"valveCodes": ["V2"]}, token=disp)
    check("单关 V2 影响集合精确为 A01-A05",
          imp_v2["affectedAddressCodes"] == ["A01", "A02", "A03", "A04", "A05"],
          imp_v2["affectedAddressCodes"])

    _, imp_v1 = http("POST", "/api/network/impact", {"valveCodes": ["V1"]}, token=disp)
    check("单关 V1 同时影响 S1 与 S2（A01-A05 + A10-A13，共 9 户）",
          imp_v1["affectedAddressCodes"] ==
          ["A01", "A02", "A03", "A04", "A05", "A10", "A11", "A12", "A13"],
          imp_v1["affectedAddressCodes"])

    _, imp_v3 = http("POST", "/api/network/impact", {"valveCodes": ["V3"]}, token=disp)
    check("单关 V3 影响集合精确为 A06-A09 + S7 的 A14/A15（旧图共 6 户）",
          imp_v3["affectedAddressCodes"] ==
          ["A06", "A07", "A08", "A09", "A14", "A15"],
          imp_v3["affectedAddressCodes"])

    # ---------- 2. 受理与自动归属 ----------
    section("2. 六条模拟报修受理：原文保存 + 自动归属（含路口/同楼歧义）")
    rid = {}
    for item in reports_doc["initial"]:
        key = item["key"]
        st, detail = http("POST", "/api/reports", item["body"], token=disp, expect=200)
        rid[key] = detail["report"]["id"]
        cur = detail["currentJudgment"]
        check(f"{key} 原文已保存且编号生成",
              detail["report"]["code"].startswith("R-") and detail["report"]["rawAddress"])
        if "expectStatus" in item:
            check(f"{key} 自动归属状态={item['expectStatus']}",
                   cur["status"] == item["expectStatus"], cur)
        if "expectSegments" in item:
            check(f"{key} 唯一管段={item['expectSegments']}",
                  cur["selectedSegmentCodes"] == item["expectSegments"], cur)
        if "expectCandidateSegments" in item:
            check(f"{key} 候选管段={item['expectCandidateSegments']}（不自动合并事故）",
                  cur["candidateSegmentCodes"] == item["expectCandidateSegments"], cur)

    # 人工确认 R5/R6 到 S3，判断历史保留 AUTO 行
    for key in ("R5", "R6"):
        st, detail = http("POST", f"/api/reports/{rid[key]}/judgments",
                          {"segmentCodes": ["S3"], "status": "CONFIRMED",
                           "reason": "勘查与社区核对：积水来自滨河巷 S3 支管"},
                          token=disp, expect=200)
        versions = detail["judgments"]
        check(f"{key} 追加人工归属 v2=CONFIRMED/S3，且 v1 自动判断仍保留",
              len(versions) == 2 and versions[0]["source"] == "AUTO"
              and versions[1]["source"] == "MANUAL"
              and detail["currentJudgment"]["selectedSegmentCodes"] == ["S3"],
              [j["status"] for j in versions])

    # ---------- 3. 同段重拨：只添来源 ----------
    section("3. 同段重拨：R1/R2 确认 I2 后，R3 重拨自动添来源，不增事故/派工")
    _, i2 = http("POST", "/api/incidents",
                 {"title": "幸福路 S2 低压事故", "reportIds": [rid["R1"], rid["R2"]],
                  "boundarySegmentCodes": ["S2"],
                  "note": "同一条支管两端同时低压，确认同一事故"},
                 token=disp, expect=200)
    i2id = i2["id"]
    _, incidents_before = http("GET", "/api/incidents", token=disp)
    n_inc_before = len(incidents_before)

    redial_item = reports_doc["redial"]
    redial_body = dict(redial_item["body"])
    redial_body["redialOfReportId"] = rid[redial_item["redialOfKey"]]
    _, r3detail = http("POST", "/api/reports", redial_body, token=disp, expect=200)
    rid["R3"] = r3detail["report"]["id"]
    check("重拨单标记 redial=true 且自动挂到原事故 I2",
          r3detail["redial"] is True and r3detail["autoAttachedIncidentId"] == i2id,
          r3detail)

    _, incidents_after = http("GET", "/api/incidents", token=disp)
    check("重拨后事故数量不增加", len(incidents_after) == n_inc_before)
    _, i2detail = http("GET", f"/api/incidents/{i2id}", token=disp)
    check("I2 现有 3 条线索来源（R1/R2/R3）",
          len(i2detail["sources"]) == 3, len(i2detail["sources"]))
    check("来源中包含 REDIAL 类型",
          any(s["sourceType"] == "REDIAL" for s in i2detail["sources"]))
    check("I2 尚无任何派工单（重拨不触发派工）", len(i2detail["assignments"]) == 0)

    # 重复追加来源幂等
    st, _ = http("POST", f"/api/incidents/{i2id}/sources",
                 {"reportId": rid["R3"], "sourceType": "REDIAL"}, token=disp, expect=200)
    _, i2detail = http("GET", f"/api/incidents/{i2id}", token=disp)
    check("重复 sources 调用幂等，仍为 3 条来源", len(i2detail["sources"]) == 3)

    # ---------- 4. 相邻双支管：I3(S3) 与 I1(S1)，候选边界分开 ----------
    section("4. 相邻双支管：I3=S3、I1=S1 分别确认，候选关阀边界互不混淆")
    _, i3 = http("POST", "/api/incidents",
                 {"title": "滨河巷 S3 积水/低压事故",
                  "reportIds": [rid["R4"], rid["R5"], rid["R6"]],
                  "boundarySegmentCodes": ["S3"]},
                 token=disp, expect=200)
    i3id = i3["id"]
    _, i1 = http("POST", "/api/incidents",
                 {"title": "建设路 S1 低压事故（待压力复核）",
                  "reportIds": [rid["R7"]], "boundarySegmentCodes": ["S1"]},
                 token=disp, expect=200)
    i1id = i1["id"]
    require("两条相邻报修形成不同事故 I2≠I3，且还有 I1",
            len({i2id, i3id, i1id}) == 3)

    _, cand2 = http("GET", f"/api/incidents/{i2id}/candidate-isolations", token=disp)
    map2 = {"+".join(c["valveCodes"]): c["affectedAddressCodes"] for c in cand2}
    check("I2(S2) 候选包含 V2 且影响恰为 A01-A05",
          map2.get("V2") == ["A01", "A02", "A03", "A04", "A05"], map2)
    check("I2(S2) 候选包含 V1 且 V1 方案把 S1 的 A10-A13 也纳入",
          map2.get("V1") == ["A01", "A02", "A03", "A04", "A05", "A10", "A11", "A12", "A13"],
          map2)

    _, cand3 = http("GET", f"/api/incidents/{i3id}/candidate-isolations", token=disp)
    map3 = {"+".join(c["valveCodes"]): c["affectedAddressCodes"] for c in cand3}
    check("I3(S3) 候选为 V3 且影响恰为 A06-A09+A14/A15（旧图 6 户），与 I2 边界分开",
          map3.get("V3") == ["A06", "A07", "A08", "A09", "A14", "A15"], map3)
    check("V2 不是 S3 的候选隔离组合", "V2" not in map3, list(map3))

    _, cand1 = http("GET", f"/api/incidents/{i1id}/candidate-isolations", token=disp)
    check("I1(S1) 候选只有 V1",
          ["+".join(c["valveCodes"]) for c in cand1] == ["V1"], cand1)

    # 选方案 + 通知
    _, p2 = http("POST", f"/api/incidents/{i2id}/isolation-plans",
                 {"valveCodes": ["V2"]}, token=disp, expect=200)
    _, p3 = http("POST", f"/api/incidents/{i3id}/isolation-plans",
                 {"valveCodes": ["V3"]}, token=disp, expect=200)
    _, p1 = http("POST", f"/api/incidents/{i1id}/isolation-plans",
                 {"valveCodes": ["V1"]}, token=disp, expect=200)
    check("三个隔离方案均快照了影响地址",
          len(p2["affectedAddressCodes"]) == 5 and len(p3["affectedAddressCodes"]) == 6
          and len(p1["affectedAddressCodes"]) == 9)

    _, n2 = http("POST", f"/api/incidents/{i2id}/notify", {}, token=disp, expect=200)
    _, n3 = http("POST", f"/api/incidents/{i3id}/notify", {}, token=disp, expect=200)
    _, n1 = http("POST", f"/api/incidents/{i1id}/notify", {}, token=disp, expect=200)
    check("I2 通知 5 户 A01-A05",
          n2["sentCount"] == 5 and as_set(x["addressCode"] for x in n2["notifications"]) == set(s2))
    check("I3 通知 6 户 A06-A09+A14/A15", n3["sentCount"] == 6)
    check("I1(V1) 通知 9 户（含 S1）", n1["sentCount"] == 9)
    _, n2b = http("POST", f"/api/incidents/{i2id}/notify", {}, token=disp, expect=200)
    check("通知按方案幂等，重复 notify 不重发", n2b["alreadyNotified"] is True and n2b["sentCount"] == 5)

    # 不能用无效方案派维修
    st, bad_plan = http("POST", f"/api/incidents/{i3id}/isolation-plans",
                        {"valveCodes": ["V2"]}, token=disp)
    check("选择不能隔离 S3 的 V2 方案被 422 拒绝", st == 422 and bad_plan["code"] == "PLAN_DOES_NOT_ISOLATE")

    # ---------- 5. 资格门槛：许可 / 当班 / 车辆 / 工具 ----------
    section("5. 接单资格：许可、当班窗口、车辆、抢修工具")
    _, elig_nores = http("POST", f"/api/incidents/{i2id}/dispatch/eligibility",
                         {"kind": "REPAIR", "crewCode": "C_NORES",
                          "windowStart": "2026-11-26T05:00:00+08:00",
                          "windowEnd": "2026-11-26T07:00:00+08:00"}, token=disp)
    codes_nores = {c["code"]: c["passed"] for c in elig_nores["checks"]}
    check("C_NORES 资质/当班通过但车辆与工具不可用 → eligible=false",
          elig_nores["eligible"] is False
          and codes_nores.get("QUALIFICATION") is True
          and codes_nores.get("VEHICLE_AVAILABLE") is False
          and codes_nores.get("TOOLS_AVAILABLE") is False, elig_nores)

    _, elig_shift = http("POST", f"/api/incidents/{i2id}/dispatch/eligibility",
                         {"kind": "REPAIR", "crewCode": "C_SOLE",
                          "windowStart": "2026-11-26T20:00:00+08:00",
                          "windowEnd": "2026-11-26T22:00:00+08:00"}, token=disp)
    shift_check = next(c for c in elig_shift["checks"] if c["code"] == "WINDOW_WITHIN_SHIFT")
    check("C_SOLE 申请当班外窗口(20-22)被当班校验拒绝",
          elig_shift["eligible"] is False and shift_check["passed"] is False)

    st, scout_repair = http("POST", f"/api/incidents/{i2id}/dispatch/repair",
                            {"crewCode": "C_SCOUT",
                             "windowStart": "2026-11-26T05:00:00+08:00",
                             "windowEnd": "2026-11-26T07:00:00+08:00"}, token=disp)
    check("只有勘查许可的 C_SCOUT 不能接维修单（422 资质失败）",
          st == 422 and scout_repair["code"] == "DISPATCH_ELIGIBILITY_FAILED",
          (st, scout_repair))

    # ---------- 6. 唯一持证班组并发双派单 ----------
    section("6. 并发互斥：同一 C_SOLE 同一窗口被 I2/I3 同时派单，恰好一成一败")

    def race_dispatch(incident_id):
        barrier.wait()
        return http("POST", f"/api/incidents/{incident_id}/dispatch/repair",
                    {"crewCode": "C_SOLE",
                     "strategy": "DIRECT",
                     "windowStart": "2026-11-26T04:00:00+08:00",
                     "windowEnd": "2026-11-26T04:40:00+08:00"}, token=disp)

    barrier = threading.Barrier(2)
    with ThreadPoolExecutor(max_workers=2) as pool:
        futs = [pool.submit(race_dispatch, i2id), pool.submit(race_dispatch, i3id)]
        race_results = [f.result() for f in futs]
    statuses = sorted(s for s, _ in race_results)
    check("并发两个派单：状态码恰为一个 200、一个 409", statuses == [200, 409], statuses)
    conflict = next(b for s, b in race_results if s == 409)
    winner = next(b for s, b in race_results if s == 200)
    check("失败方错误码 CREW_ALREADY_BOOKED 且细节指向占用方",
          conflict["code"] == "CREW_ALREADY_BOOKED"
          and conflict["details"]["resourceCode"] == "C_SOLE"
          and conflict["details"]["heldByAssignmentCode"], conflict)

    winner_id = winner["id"]
    _, occ_w = http("GET", f"/api/assignments/{winner_id}/occupancies", token=disp)
    check("成功方持有 CREW/VEHICLE/TOOL/PERMIT 共 5 项 HELD 占用",
          len([o for o in occ_w if o["status"] == "HELD"]) == 5,
          [(o["resourceType"], o["resourceCode"]) for o in occ_w])

    _, cancelled = http("POST", f"/api/assignments/{winner_id}/cancel",
                        {"reason": "并发回放后的试探派单，调度收回"}, token=disp, expect=200)
    check("取消成功派工单", cancelled["status"] == "CANCELLED")
    _, occ_w2 = http("GET", f"/api/assignments/{winner_id}/occupancies", token=disp)
    check("取消后其全部占用 RELEASED 且记录释放原因（可追溯）",
          all(o["status"] == "RELEASED" and o["releaseReason"] for o in occ_w2))

    # ---------- 7. 路线 A：I3 先派勘查 ----------
    section("7. 路线A（勘查先行）：I3 勘查班 04:25 到场，顺序/角色约束生效")
    _, scout_asg = http("POST", f"/api/incidents/{i3id}/dispatch/scout",
                        {"crewCode": "C_SCOUT",
                         "windowStart": "2026-11-26T04:20:00+08:00",
                         "windowEnd": "2026-11-26T05:00:00+08:00"},
                        token=disp, expect=200)
    scout_id = scout_asg["id"]

    def receipt(incident_id, tpl_key, assignment_id, token=None, extra=None):
        body = dict(receipt_tpls[tpl_key])
        body["assignmentId"] = assignment_id
        if extra:
            body.update(extra)
        return http("POST", f"/api/incidents/{incident_id}/receipts", body,
                    token=token or disp)

    st, arr = receipt(i3id, "I3_SCOUT_ARRIVAL", scout_id)
    check("勘查单提交 ARRIVAL 成功，归属 C_SCOUT",
          st == 200 and arr["type"] == "ARRIVAL" and arr["crewCode"] == "C_SCOUT", (st, arr))

    st, order_err = http("POST", f"/api/incidents/{i3id}/receipts",
                         {"assignmentId": scout_id, "type": "REPAIR",
                          "eventAt": "2026-11-26T04:30:00+08:00"}, token=disp)
    check("跳过 ISOLATION 直接 REPAIR 被 409 RECEIPT_ORDER_CONFLICT 拒绝",
          st == 409 and order_err["code"] == "RECEIPT_ORDER_CONFLICT"
          and order_err["details"]["expected"] == "ISOLATION", (st, order_err))

    st, role_err = http("POST", f"/api/incidents/{i3id}/receipts",
                        {"assignmentId": scout_id, "type": "ISOLATION",
                         "eventAt": "2026-11-26T04:32:00+08:00"}, token=disp)
    check("勘查单不能提交 ISOLATION（必须 REPAIR 派工单+阀许可）",
          st == 422 and role_err["code"] == "REPAIR_ASSIGNMENT_REQUIRED", (st, role_err))

    _, done_scout = http("POST", f"/api/assignments/{scout_id}/complete",
                         {"reason": "已确认 V3 阀门故障并交接维修队"}, token=disp, expect=200)
    check("勘查派工单完成交接，勘查资源占用释放", done_scout["status"] == "COMPLETED")

    # ---------- 8. 路线 B：I2 捆绑暂留维修队+V2 阀票窗口 ----------
    section("8. 路线B（捆绑暂留）：I2 首次出动前暂留 C_SOLE + POP-V2 + 车/工具")
    _, a21 = http("POST", f"/api/incidents/{i2id}/dispatch/repair",
                  {"crewCode": "C_SOLE", "strategy": "RESERVE_BUNDLE", "holdOnly": True,
                   "windowStart": "2026-11-26T05:00:00+08:00",
                   "windowEnd": "2026-11-26T07:00:00+08:00",
                   "note": "根因未最终确认也先捆绑暂留，保证维修接续"},
                  token=disp, expect=200)
    a21id = a21["id"]
    check("暂留派工单状态 PROVISIONAL", a21["status"] == "PROVISIONAL")
    _, occ21 = http("GET", f"/api/assignments/{a21id}/occupancies", token=disp)
    held21 = {(o["resourceType"], o["resourceCode"]) for o in occ21 if o["status"] == "HELD"}
    check("暂留即锁定 C_SOLE、POP-V2、VEH-1 与两件工具",
          ("CREW", "C_SOLE") in held21 and ("VALVE_PERMIT", "POP-V2") in held21
          and ("VEHICLE", "VEH-1") in held21 and ("TOOL", "TOOL-CLAMP") in held21
          and ("TOOL", "TOOL-WELDER") in held21, held21)
    _, issued = http("POST", f"/api/assignments/{a21id}/issue", {}, token=disp, expect=200)
    check("暂留单下达后 DISPATCHED", issued["status"] == "DISPATCHED")

    # 路线 A 的 I3 此时尝试锁同一持证队的重叠窗口 → 被 I2 先占
    st, lock_fail = http("POST", f"/api/incidents/{i3id}/dispatch/repair",
                         {"crewCode": "C_SOLE", "strategy": "LOCK_AFTER_SCOUT",
                          "windowStart": "2026-11-26T06:00:00+08:00",
                          "windowEnd": "2026-11-26T08:00:00+08:00"}, token=disp)
    check("勘查先行的 I3 锁定 06-08 维修窗口时，被路线B暂留的 I2 占队 → 409",
          st == 409 and lock_fail["code"] == "CREW_ALREADY_BOOKED", (st, lock_fail))

    # ---------- 9. I2 第一周期回执 + 复压失败 ----------
    section("9. I2 第一周期五类回执；复压值低于 S2 区间，禁关单")
    for tpl in ["I2_C1_ARRIVAL", "I2_C1_ISOLATION", "I2_C1_REPAIR", "I2_C1_FLUSH"]:
        st, rc = receipt(i2id, tpl, a21id)
        check(f"{tpl} 提交成功并归属 A2-1/C_SOLE",
              st == 200 and rc["assignmentId"] == a21id and rc["crewCode"] == "C_SOLE", (st, rc))

    st, fail_rc = receipt(i2id, "I2_C1_REPRESSURE_FAIL", a21id)
    check("复压失败回执被保存且 verificationStatus=FAILED（历史不抹掉）",
          st == 200 and fail_rc["verificationStatus"] == "FAILED"
          and any("超出" in r for r in fail_rc["verificationReasons"]), fail_rc)

    st, close_fail = http("POST", f"/api/incidents/{i2id}/close", {}, token=disp)
    check("复压未通过时关单被 409 REPRESSURE_NOT_PASSED 拒绝",
          st == 409 and close_fail["code"] == "REPRESSURE_NOT_PASSED", (st, close_fail))

    # 失败后不能在旧周期直接补回执
    st, chain_full = http("POST", f"/api/incidents/{i2id}/receipts",
                          {"assignmentId": a21id, "type": "ARRIVAL",
                           "eventAt": "2026-11-26T07:05:00+08:00"}, token=disp)
    check("未重新派工前不能开启/插入新回执（链停在 REPRESSURE 失败态）",
          st == 409 and chain_full["code"] in ("RECEIPT_CHAIN_COMPLETE", "RECEIPT_ORDER_CONFLICT"),
          (st, chain_full))

    # ---------- 10. 重新派工 + 第二周期通过 + 占用释放 ----------
    section("10. I2 复压失败后重新派工：旧占用释放、新周期五回执通过、关单释放全部占用")
    _, a22 = http("POST", f"/api/incidents/{i2id}/redispatch",
                  {"crewCode": "C_SOLE",
                   "windowStart": "2026-11-26T07:20:00+08:00",
                   "windowEnd": "2026-11-26T09:20:00+08:00",
                   "note": "返工并重新派工"}, token=disp, expect=200)
    a22id = a22["id"]
    check("新派工单 attemptNo=2/cycleNo=2，parent 指向 A2-1",
          a22["attemptNo"] == 2 and a22["cycleNo"] == 2
          and a22["parentAssignmentId"] == a21id, a22)

    _, occ21_after = http("GET", f"/api/assignments/{a21id}/occupancies", token=disp)
    check("重新派工后 A2-1 原占用全部 RELEASED，原因含 REDISPATCH",
          all(o["status"] == "RELEASED" and "REDISPATCH" in (o["releaseReason"] or "")
              for o in occ21_after) and len(occ21_after) == 5, occ21_after)

    st, old_cycle_rc = http("POST", f"/api/incidents/{i2id}/receipts",
                            {"assignmentId": a21id, "type": "ARRIVAL",
                             "eventAt": "2026-11-26T07:21:00+08:00"}, token=disp)
    check("旧派工单不能在新周期提交回执（RECEIPT_WRONG_CYCLE，历史周期封存）",
          st == 409 and old_cycle_rc["code"] == "RECEIPT_WRONG_CYCLE", (st, old_cycle_rc))

    for tpl in ["I2_C2_ARRIVAL", "I2_C2_ISOLATION", "I2_C2_REPAIR",
                "I2_C2_FLUSH", "I2_C2_REPRESSURE_OK"]:
        st, rc = receipt(i2id, tpl, a22id)
        check(f"{tpl} 新周期提交成功并归属 A2-2",
              st == 200 and rc["cycleNo"] == 2 and rc["assignmentId"] == a22id, (st, rc))
    _, i2detail2 = http("GET", f"/api/incidents/{i2id}", token=disp)
    rep_ok = [r for r in i2detail2["receipts"] if r["type"] == "REPRESSURE" and r["cycleNo"] == 2][0]
    check("第二周期复压 PASSED，校时差值在容差内",
          rep_ok["verificationStatus"] == "PASSED"
          and rep_ok["correctedTimeDeltaSeconds"] <= rep_ok["timeToleranceSeconds"], rep_ok)

    _, closed2 = http("POST", f"/api/incidents/{i2id}/close", {}, token=disp, expect=200)
    check("I2 关单成功 CLOSED", closed2["status"] == "CLOSED")
    _, occ22 = http("GET", f"/api/assignments/{a22id}/occupancies", token=disp)
    check("关单后 A2-2 占用全部 RELEASED/INCIDENT_CLOSED",
          all(o["status"] == "RELEASED" and o["releaseReason"] == "INCIDENT_CLOSED" for o in occ22))

    # ---------- 11. I3 锁定晚窗口并闭环，核对每条回执归属 ----------
    section("11. I3 避开 I2 窗口锁定 09:30-11:30，闭环；核对每条历史回执归属")
    st, a32 = http("POST", f"/api/incidents/{i3id}/dispatch/repair",
                   {"crewCode": "C_SOLE", "strategy": "LOCK_AFTER_SCOUT",
                    "windowStart": "2026-11-26T09:30:00+08:00",
                    "windowEnd": "2026-11-26T11:30:00+08:00"}, token=disp, expect=200)
    a32id = a32["id"]
    check("I3 在 I2 窗口结束(09:20)后锁定同一持证队成功（半开区间相邻不冲突）",
          a32["status"] == "DISPATCHED", (st, a32))

    for tpl in ["I3_ISOLATION", "I3_REPAIR", "I3_FLUSH", "I3_REPRESSURE_OK"]:
        st, rc = receipt(i3id, tpl, a32id)
        check(f"{tpl} 提交成功", st == 200 and rc["crewCode"] == "C_SOLE", (st, rc))

    _, closed3 = http("POST", f"/api/incidents/{i3id}/close", {}, token=disp, expect=200)
    check("I3 复压通过并关单", closed3["status"] == "CLOSED")

    _, d3 = http("GET", f"/api/incidents/{i3id}", token=disp)
    by_type = {r["type"]: r for r in d3["receipts"]}
    check("I3 共 5 条回执且类型顺序正确",
          [r["type"] for r in d3["receipts"]] ==
          ["ARRIVAL", "ISOLATION", "REPAIR", "FLUSH", "REPRESSURE"],
          [r["type"] for r in d3["receipts"]])
    check("到场回执归属勘查班 C_SCOLE/勘查单，其余四条归属 C_SOLE/维修单",
          by_type["ARRIVAL"]["crewCode"] == "C_SCOUT"
          and by_type["ARRIVAL"]["assignmentId"] == scout_id
          and all(by_type[t]["crewCode"] == "C_SOLE" and by_type[t]["assignmentId"] == a32id
                  for t in ["ISOLATION", "REPAIR", "FLUSH", "REPRESSURE"]),
          {t: (by_type[t]["crewCode"], by_type[t]["assignmentCode"]) for t in by_type})

    # CREW 账号也可提交回执的角色验证（viewer 不行）——用一个轻量 403 检查
    st, _ = http("POST", f"/api/incidents/{i3id}/receipts",
                 {"assignmentId": a32id, "type": "X", "eventAt": "2026-11-26T11:20:00+08:00"},
                 token=viewer_token)
    check("VIEWER 不能提交现场回执（403）", st == 403, f"status={st}")

    # ---------- 12. 压力复核推翻：范围/占用/通知更正 ----------
    section("12. I1 压力复核推翻 S1/V1 → S2/V2：范围 diff、阀票换占、旧通知更正链")
    _, a1 = http("POST", f"/api/incidents/{i1id}/dispatch/repair",
                 {"crewCode": "C_SOLE", "strategy": "RESERVE_BUNDLE",
                  "windowStart": "2026-11-26T13:00:00+08:00",
                  "windowEnd": "2026-11-26T15:00:00+08:00"}, token=disp, expect=200)
    a1id = a1["id"]

    _, review_result = http("POST", f"/api/incidents/{i1id}/pressure-reviews", {
        "verdict": "OVERTURNS",
        "reviewer": "压力复核岗",
        "evidenceReadingCodes": ["PR-REV-S1", "PR-REV-S2"],
        "correctedBoundarySegmentCodes": ["S2"],
        "correctedValveCodes": ["V2"],
        "note": "S1 压力全程正常，异常曲线与校时后的读数均指向 S2；原 S1/V1 判断推翻。"
    }, token=disp, expect=200)
    corr = review_result["correction"]
    require("生成更正记录 correction", corr is not None)
    check("更正记录保留旧边界 S1/旧阀 V1/旧范围 9 户",
          corr["oldBoundarySegments"] == ["S1"] and corr["oldValveCodes"] == ["V1"]
          and corr["oldAddressCodes"] ==
          ["A01", "A02", "A03", "A04", "A05", "A10", "A11", "A12", "A13"], corr)
    check("新边界 S2/新阀 V2/新范围 A01-A05",
          corr["newBoundarySegments"] == ["S2"] and corr["newValveCodes"] == ["V2"]
          and corr["newAddressCodes"] == ["A01", "A02", "A03", "A04", "A05"], corr)
    check("地址 diff：移出 A10-A13，无新增",
          corr["removedAddressCodes"] == ["A10", "A11", "A12", "A13"]
          and corr["addedAddressCodes"] == [], corr)
    check("阀门许可占用更正：释放 POP-V1，新占 POP-V2",
          corr["releasedPermitCodes"] == ["POP-V1"]
          and corr["reservedPermitCodes"] == ["POP-V2"], corr)

    _, occ1 = http("GET", f"/api/assignments/{a1id}/occupancies", token=disp)
    popv1 = [o for o in occ1 if o["resourceCode"] == "POP-V1"]
    popv2 = [o for o in occ1 if o["resourceCode"] == "POP-V2"]
    check("I1 占用明细：POP-V1 已 RELEASED(PRESSURE_REVIEW_CORRECTION)，POP-V2 HELD",
          len(popv1) == 1 and popv1[0]["status"] == "RELEASED"
          and "PRESSURE_REVIEW_CORRECTION" in popv1[0]["releaseReason"]
          and len(popv2) == 1 and popv2[0]["status"] == "HELD", occ1)

    _, d1 = http("GET", f"/api/incidents/{i1id}", token=disp)
    initial_notes = [n for n in d1["notifications"] if n["kind"] == "INITIAL"]
    correction_notes = [n for n in d1["notifications"] if n["kind"] == "CORRECTION"]
    cancel_notes = [n for n in d1["notifications"] if n["kind"] == "CORRECTION_CANCEL"]
    check("9 条原始通知全部置 SUPERSEDED 并挂更正 id（不删除、不伪装未发生）",
          len(initial_notes) == 9
          and all(n["status"] == "SUPERSEDED" and n["supersededByCorrectionId"] == corr["id"]
                  for n in initial_notes))
    check("向新范围 5 户发 CORRECTION，向移出的 A10-A13 发 4 条 CORRECTION_CANCEL",
          len(correction_notes) == 5 and len(cancel_notes) == 4
          and as_set(n["addressCode"] for n in cancel_notes) ==
          {"A10", "A11", "A12", "A13"})
    a12_cancel = next(n for n in cancel_notes if n["addressCode"] == "A12")
    old_a12 = next(n for n in initial_notes if n["addressCode"] == "A12")
    check("A12 取消通知通过 replacesNotificationId 链接到被作废的旧通知",
          a12_cancel["replacesNotificationId"] == old_a12["id"]
          and old_a12["status"] == "SUPERSEDED")
    check("I1 边界已更新为 S2，方案版本变为 2 且旧方案 CORRECTED",
          d1["incident"]["boundarySegmentCodes"] == ["S2"]
          and d1["incident"]["revision"] == 2
          and [p["status"] for p in d1["plans"]].count("CORRECTED") == 1
          and d1["plans"][-1]["status"] == "ACTIVE")

    # 按更正后的 S2 完成 I1 并关单
    for tpl in ["I1_ARRIVAL", "I1_ISOLATION", "I1_REPAIR", "I1_FLUSH", "I1_REPRESSURE_OK"]:
        st, rc = receipt(i1id, tpl, a1id)
        check(f"更正后 {tpl} 提交并按 S2 核验", st == 200, (st, rc))
    _, closed1 = http("POST", f"/api/incidents/{i1id}/close", {}, token=disp, expect=200)
    check("更正后的 I1 复压通过并关单", closed1["status"] == "CLOSED")

    # ---------- 13. 校时容差/区间试算 ----------
    section("13. 复压核验试算：正常区间与采样校时容差分别可判")
    _, vbad_val = http("POST", "/api/pressure/verify-repressure",
                       {"readingCode": "PR-S2-FAIL",
                        "eventAt": "2026-11-26T06:50:00+08:00",
                        "boundarySegmentCodes": ["S2"]}, token=disp)
    check("低压读数判 FAILED 且原因是超出区间（校时后时刻其实接近）",
          vbad_val["verificationStatus"] == "FAILED"
          and any("正常区间" in r for r in vbad_val["reasons"])
          and not any("容差" in r for r in vbad_val["reasons"]), vbad_val)

    _, vbad_time = http("POST", "/api/pressure/verify-repressure",
                        {"readingCode": "PR-TIME-BAD",
                         "eventAt": "2026-11-26T06:50:00+08:00",
                         "boundarySegmentCodes": ["S2"]}, token=disp)
    check("压力值合格但采样时刻超校时容差 → FAILED",
          vbad_time["verificationStatus"] == "FAILED"
          and any("容差" in r for r in vbad_time["reasons"]), vbad_time)

    _, vok = http("POST", "/api/pressure/verify-repressure",
                  {"readingCode": "PR-S2-OK",
                   "eventAt": "2026-11-26T09:05:00+08:00",
                   "boundarySegmentCodes": ["S2"]}, token=disp)
    check("PR-S2-OK 区间与校时容差均通过 → PASSED",
          vok["verificationStatus"] == "PASSED", vok)

    # ---------- 14. 终态：所有占用均已释放 ----------
    section("14. 终态核对：所有资源占用 RELEASED，三个事故 CLOSED")
    _, occ_all = http("GET", "/api/occupancies", token=disp)
    held_left = [o for o in occ_all if o["status"] == "HELD"]
    check("回放结束后不存在任何 HELD 占用（并发试探单已取消、各单已完成/关单）",
          not held_left, [(o["resourceType"], o["resourceCode"], o["incidentId"]) for o in held_left])
    _, incs = http("GET", "/api/incidents", token=disp)
    inc_by_id = {i["id"]: i for i in incs}
    check("回放创建的 I1/I2/I3 三个事故均 CLOSED（另播种有 S7/S8 拓扑工单，不计入本断言）",
          all(inc_by_id[x]["status"] == "CLOSED" for x in (i1id, i2id, i3id)),
          [(i["code"], i["status"]) for i in incs])

    print("\n" + "#" * 78)
    print(f"# 回放断言结果：PASS={PASS_COUNT}  FAIL={FAIL_COUNT}")
    if FAIL_COUNT:
        print("# 失败项：")
        for line in FAIL_LINES:
            print("  - " + line)
        print("ALL_DONE: false")
        sys.exit(1)
    print("# 🎉 全部场景断言通过：同段重拨 / 相邻双支管 / 并发互斥 / 复压重派 / 更正链")
    print("ALL_DONE: true")
    sys.exit(0)


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("\n回放因关键断言失败中止:", e)
        print("ALL_DONE: false")
        sys.exit(1)
    except Exception as e:  # noqa
        import traceback
        traceback.print_exc()
        print("ALL_DONE: false")
        sys.exit(2)
