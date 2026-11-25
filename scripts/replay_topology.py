#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
拓扑修订证据链 / 版本化旧图复演 / 开放工单人工重审 —— 端到端回放（纯标准库）。

核对题目两条路径：
  A. 只确认 S7-S8、S8 末端未测：
     - 证据五要素（草图/两端标识/测距/0.2m 误差/复核人）+ 双人核验 + 资产工程师闸门；
     - 发布 PARTIAL 版本后，[V3,V4] 候选隔离 12 户（A06-A21 中已知地址），
       未知 S9 上的 A22/A23 标为未知、不得纳入；
     - 两张引用旧图 TOPO-V1 的开放工单生成 PENDING 重审单：旧 V3=6 / V4=6、
       新阀门组合、地址 diff、已通知对象快照；调度确认后才出新计划版本，
       旧通知原样保留，新增地址须显式 notify-added；
     - 已结案旧事故固定 TOPO-V1，按旧图精确复演 6 户；同一 V3 在新图下已漂移为 4 户；
     - 新版本生效后新建事故固定 V2 并使用 12 户新计算。
  B. 整段测完后 FULL 发布：补测 S9，[V3,V4]=14 户，未知消除；旧图仍可复演。

用法：
  python3 scripts/replay_topology.py
  BASE_URL=http://127.0.0.1:8080 python3 scripts/replay_topology.py
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = os.environ.get("BASE_URL", "http://127.0.0.1:8080").rstrip("/")

PASS_COUNT = 0
FAIL_COUNT = 0
FAIL_LINES = []

SIX = ["A06", "A07", "A08", "A09", "A14", "A15"]
S8_SIX = ["A16", "A17", "A18", "A19", "A20", "A21"]
TWELVE = ["A06", "A07", "A08", "A09", "A14", "A15",
          "A16", "A17", "A18", "A19", "A20", "A21"]
FOURTEEN = TWELVE + ["A22", "A23"]


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
        raise AssertionError(f"关键断言失败，拓扑回放中止: {name} {detail}")


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
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(BASE + "/api/health", timeout=5) as r:
                if r.status == 200:
                    return True
        except Exception:
            time.sleep(2)
    return False


def login(u, p):
    _, d = http("POST", "/api/auth/login", {"username": u, "password": p}, expect=200)
    return d["token"]


def evidence_body(code, reviewer=None, error=0.2):
    body = {
        "code": code,
        "title": f"{code} 现场开挖探测证据",
        "sketchRef": f"evidence/{code}.png",
        "sketchDescription": "手绘草图：S7 东端原竣工图盲端工作坑 NE7，"
                             "开挖后见一条支管向东南接入 S8 的 NE8 接点；尺量两坑中心距。",
        "endANodeCode": "NE7",
        "endAIdentifier": "S7 末端工作坑 J7（竣工图盲端处）",
        "endAMarker": "J7-红漆桩",
        "endALng": 120.00105, "endALat": 29.99915,
        "endBNodeCode": "NE8",
        "endBIdentifier": "S8 西侧探测接点 J8（V4 阀后约 46m）",
        "endBMarker": "J8-探测仪标记",
        "endBLng": 120.00320, "endBLat": 29.99910,
        "measuredDistanceM": 12.8,
        "measurementErrorM": error,
        "measurementMethod": "钢尺复量 + 管线探测仪 double-check",
        "surveyorName": "王测量",
        "surveyedAt": "2026-11-25T21:40:00+08:00",
    }
    if reviewer:
        body["reviewerName"] = reviewer
        body["reviewedAt"] = "2026-11-25T21:55:00+08:00"
        body["reviewNote"] = "草图、两端标识与现场一致，测距在误差范围内"
    return body


def run_path_a():
    section("路径 A：只确认 S7-S8，S8 末端未测 → PARTIAL 局部修订")

    disp = login("dispatcher", "Disp@12345")
    asset = login("asset", "Asset@12345")
    viewer = login("viewer", "View@12345")
    check("资产工程师账号 asset 可登录且角色为 ASSET_ENGINEER", bool(asset))

    http("POST", "/api/admin/reset", {}, token=disp, expect=200)

    _, doc = http("GET", "/v3/api-docs")
    check("OpenAPI 含拓扑修订发布接口", "/api/topology/revisions" in json.dumps(doc))

    # ---- 基线 TOPO-V1 ----
    _, versions = http("GET", "/api/topology/versions", token=disp, expect=200)
    require("播种唯一基线版本 TOPO-V1/ACTIVE/BASELINE",
            len(versions) == 1 and versions[0]["versionCode"] == "TOPO-V1"
            and versions[0]["status"] == "ACTIVE"
            and versions[0]["revisionMode"] == "BASELINE", versions)

    _, v3old = http("POST", "/api/network/impact", {"valveCodes": ["V3"]}, token=disp)
    check("旧图：单关 V3 恰影响 6 户（A06-A09 + A14/A15）",
          v3old["affectedAddressCodes"] == SIX, v3old["affectedAddressCodes"])
    _, v4old = http("POST", "/api/network/impact", {"valveCodes": ["V4"]}, token=disp)
    check("旧图：单关 V4 恰影响 S8 的 6 户 A16-A21",
          v4old["affectedAddressCodes"] == S8_SIX, v4old["affectedAddressCodes"])

    _, segs = http("GET", "/api/network/segments", token=disp)
    s9 = next(s for s in segs if s["code"] == "S9")
    check("相邻未测支管 S9 基线即标记为 UNKNOWN（不进权威计算）",
          s9.get("topologyStatus") == "UNKNOWN", s9.get("topologyStatus"))

    _, incs = http("GET", "/api/incidents", token=disp)
    by_code = {i["code"]: i for i in incs}
    require("播种两张引用旧图的开放工单 WO-2026-11/WO-2026-12 与已结案 INC-2026-088",
            all(c in by_code for c in ("WO-2026-11", "WO-2026-12", "INC-2026-088")),
            list(by_code))
    wo_s7, wo_s8, closed = by_code["WO-2026-11"], by_code["WO-2026-12"], by_code["INC-2026-088"]
    check("两张工单为开放状态，旧案为 CLOSED，且都固定 TOPO-V1",
          wo_s7["status"] != "CLOSED" and wo_s8["status"] != "CLOSED"
          and closed["status"] == "CLOSED"
          and wo_s7["topologyVersionCode"] == "TOPO-V1"
          and closed["topologyVersionCode"] == "TOPO-V1")

    _, d7 = http("GET", f"/api/incidents/{wo_s7['id']}", token=disp)
    check("WO-S7 旧计划 [V3] 快照 6 户且已发 6 条通知",
          d7["plans"][-1]["valveCodes"] == ["V3"]
          and len(d7["plans"][-1]["affectedAddressCodes"]) == 6
          and len(d7["notifications"]) == 6
          and all(n["status"] == "SENT" for n in d7["notifications"]))
    _, d8 = http("GET", f"/api/incidents/{wo_s8['id']}", token=disp)
    check("WO-S8 旧计划 [V4] 快照 A16-A21 且已发 6 条通知",
          d8["plans"][-1]["valveCodes"] == ["V4"]
          and d8["plans"][-1]["affectedAddressCodes"] == S8_SIX
          and len(d8["notifications"]) == 6)

    # ---- 角色闸门 ----
    st, _ = http("POST", "/api/topology/evidence", evidence_body("TEV-NOPE"), token=viewer)
    check("VIEWER 不能登记现场证据（403）", st == 403, st)
    st, body = http("POST", "/api/topology/revisions", {"mode": "PARTIAL"}, token=disp)
    check("调度员不能越过资产工程师直接发布拓扑版本（403 ROLE_FORBIDDEN）",
          st == 403 and body.get("code") == "ROLE_FORBIDDEN", (st, body))

    # ---- 双人核验：同一人被拒 ----
    _, bad = http("POST", "/api/topology/evidence",
                  evidence_body("TEV-BAD"), token=disp, expect=200)
    st, same = http("POST", f"/api/topology/evidence/{bad['id']}/dual-check",
                    {"reviewerName": "王测量"}, token=disp)
    check("复核人与测量人为同一人时 422 SAME_PERSON_REVIEWER（双人核验）",
          st == 422 and same.get("code") == "SAME_PERSON_REVIEWER", (st, same))

    # ---- 正式证据：先登记再双人复核 ----
    _, ev = http("POST", "/api/topology/evidence",
                 evidence_body("TEV-S7S8"), token=disp, expect=200)
    check("证据初始状态 REGISTERED，且自动生成编号/保留五要素",
          ev["status"] == "REGISTERED" and ev["code"] == "TEV-S7S8"
          and ev["measurementErrorM"] == 0.2 and ev["measuredDistanceM"] == 12.8
          and ev["sketchRef"] and ev["endAIdentifier"] and ev["endBIdentifier"], ev)
    _, evc = http("POST", f"/api/topology/evidence/{ev['id']}/dual-check",
                  {"reviewerName": "李复核", "reviewNote": "双人均到坑边核对"},
                  token=disp, expect=200)
    check("第二名复核人核验后 DUAL_CHECKED，复核人留痕",
          evc["status"] == "DUAL_CHECKED" and evc["reviewerName"] == "李复核"
          and evc["surveyorName"] == "王测量")
    _, evget = http("GET", "/api/topology/evidence/TEV-S7S8", token=disp, expect=200)
    check("证据可按编号审查（草图/端标识/测距/误差/复核人齐全）",
          evget["measurementErrorM"] == 0.2 and evget["reviewerName"] == "李复核"
          and "NE7" in evget["endANodeCode"] and evget["endBNodeCode"] == "NE8")

    # 未核验证据不能发布
    _, raw_ev = http("POST", "/api/topology/evidence",
                     evidence_body("TEV-RAW"), token=disp, expect=200)
    st, unchk = http("POST", "/api/topology/revisions", {
        "mode": "PARTIAL", "evidenceCodes": ["TEV-RAW"],
        "addConnections": [{"segmentCode": "CX-X", "fromNodeCode": "NE7", "toNodeCode": "NE8"}]
    }, token=asset)
    check("使用未双人核验证据发布被 422 EVIDENCE_NOT_DUAL_CHECKED 拒绝",
          st == 422 and unchk.get("code") == "EVIDENCE_NOT_DUAL_CHECKED", (st, unchk))

    # ---- 资产工程师发布 PARTIAL ----
    publish_body = {
        "mode": "PARTIAL",
        "effectiveAt": "2026-11-25T22:30:00+08:00",
        "confirmerName": "赵资产",
        "changeSummary": "双人核验 S7 另接 S8；S8 末端方向未复核，相邻 S9 标未知，先发布局部修订",
        "evidenceCodes": ["TEV-S7S8"],
        "addConnections": [{
            "segmentCode": "C78",
            "name": "S7-S8 现场确认连通支管",
            "fromNodeCode": "NE7", "toNodeCode": "NE8"
        }],
        "unknownBranches": [{
            "code": "UB-S8-FAR", "attachedNodeCode": "NE8", "direction": "东南",
            "suspectedSegmentCode": "S9", "status": "UNKNOWN",
            "note": "S8 末端方向尚未复核，相邻未测支管，禁止自动纳入住户",
            "candidateAddressCodes": ["A22", "A23"]
        }]
    }
    _, pub = http("POST", "/api/topology/revisions", publish_body, token=asset, expect=200)
    ver = pub["version"]
    require("发布 TOPO-V2-PARTIAL/ACTIVE，父版本 TOPO-V1，记录确认人与证据",
            ver["versionCode"] == "TOPO-V2-PARTIAL" and ver["status"] == "ACTIVE"
            and ver["revisionMode"] == "PARTIAL"
            and ver["parentVersionCode"] == "TOPO-V1"
            and ver["confirmerName"] == "赵资产"
            and ver["evidenceCodes"] == ["TEV-S7S8"], ver)
    check("新版本生效时刻为请求指定的 2026-11-25 22:30（新版本从何时生效可核对）",
          str(ver["effectiveAt"]).startswith("2026-11-25T22:30"), ver["effectiveAt"])
    check("发布结果恰好给出 2 张开放工单的拓扑重审单",
          len(pub["changeReviews"]) == 2,
          [(r["incidentCode"], r["status"]) for r in pub["changeReviews"]])

    # ---- 新拓扑影响：12 户，未知 2 户不纳入 ----
    _, imp12 = http("POST", "/api/network/impact",
                    {"valveCodes": ["V3", "V4"]}, token=disp)
    check("新连接后关 [V3,V4] 候选隔离范围恰为 12 户",
          imp12["affectedAddressCodes"] == TWELVE, imp12["affectedAddressCodes"])
    check("未知 S9 的 A22/A23 不进入权威影响集合",
          "A22" not in imp12["affectedAddressCodes"]
          and "A23" not in imp12["affectedAddressCodes"])

    _, impv3new = http("POST", "/api/network/impact", {"valveCodes": ["V3"]}, token=disp)
    check("新图下只关 V3 已不能隔离 S7（经 C78-S8-V4 反供），只剩 S3 四户",
          impv3new["affectedAddressCodes"] == ["A06", "A07", "A08", "A09"],
          impv3new["affectedAddressCodes"])

    _, cand7 = http("POST", "/api/network/candidate-isolations",
                    {"boundarySegmentCodes": ["S7"]}, token=disp)
    require("新图 S7 首选候选为 [V3,V4] 且影响 12 户",
            cand7 and cand7[0]["valveCodes"] == ["V3", "V4"]
            and cand7[0]["affectedAddressCount"] == 12, cand7)

    # 版本化接口：旧版本仍是 6，新版本是 12
    _, v1impact = http("POST", "/api/topology/versions/TOPO-V1/impact",
                       {"valveCodes": ["V3"]}, token=disp)
    check("不可变旧版本 TOPO-V1 复算 V3 仍为 6 户（发布不覆盖历史快照）",
          v1impact["affectedAddressCodes"] == SIX, v1impact["affectedAddressCodes"])
    _, v2impact = http("POST", "/api/topology/versions/TOPO-V2-PARTIAL/impact",
                       {"valveCodes": ["V3", "V4"]}, token=disp)
    check("TOPO-V2-PARTIAL 快照复算 [V3,V4] 为 12 户",
          v2impact["affectedAddressCodes"] == TWELVE)
    _, v2view = http("GET", "/api/topology/versions/TOPO-V2-PARTIAL", token=disp)
    check("PARTIAL 版本快照保留 1 个未知支管 UB-S8-FAR",
          [u["code"] for u in v2view["snapshot"]["unknownBranches"]] == ["UB-S8-FAR"])

    # ---- 重审单内容 ----
    reviews = {r["incidentCode"]: r for r in pub["changeReviews"]}
    r7 = reviews["WO-2026-11"]
    r8 = reviews["WO-2026-12"]
    check("WO-S7 重审：旧 [V3]/6 户，推荐 [V3,V4]，新增 A16-A21",
          r7["oldValveCodes"] == ["V3"] and r7["oldAddressCodes"] == SIX
          and r7["recommendedValveCodes"] == ["V3", "V4"]
          and r7["addedAddressCodes"] == S8_SIX
          and r7["removedAddressCodes"] == [], r7)
    check("WO-S7 重审：未知地址 A22/A23 仅列入 uncertain，不进新增集合",
          r7["uncertainAddressCodes"] == ["A22", "A23"]
          and "A22" not in r7["addedAddressCodes"])
    check("WO-S7 重审：新版本候选组合明细可审查（首选 12 户）",
          r7["candidateCombos"][0]["valveCodes"] == ["V3", "V4"]
          and r7["candidateCombos"][0]["affectedAddressCount"] == 12,
          r7["candidateCombos"])
    check("WO-S7 重审：快照了 6 个已通知对象（地址/状态/渠道齐全）",
          len(r7["notifiedObjects"]) == 6
          and {o["addressCode"] for o in r7["notifiedObjects"]} == set(SIX)
          and all(o["status"] == "SENT" and o["channel"] == "SIMULATED_SMS"
                  for o in r7["notifiedObjects"]), r7["notifiedObjects"])

    check("WO-S8 重审：旧 [V4]/A16-A21，推荐 [V3,V4]，新增 A06-A09+A14/A15",
          r8["oldValveCodes"] == ["V4"] and r8["oldAddressCodes"] == S8_SIX
          and r8["recommendedValveCodes"] == ["V3", "V4"]
          and r8["addedAddressCodes"] == SIX, r8)
    check("两张重审单初始均 PENDING_CONFIRMATION",
          r7["status"] == "PENDING_CONFIRMATION" and r8["status"] == "PENDING_CONFIRMATION")

    # 事故维度也能取到重审单
    _, irs = http("GET", f"/api/incidents/{wo_s7['id']}/topology-reviews", token=disp)
    check("GET /incidents/{id}/topology-reviews 能取到 WO-S7 的重审单", len(irs) >= 1)

    # ---- 新旧边界并存：未确认前，事故仍按 V1 出旧候选/旧计划 ----
    _, oldcand = http("GET", f"/api/incidents/{wo_s7['id']}/candidate-isolations", token=disp)
    check("调度确认前 WO-S7 的事故候选仍按固定旧图 V1 给 [V3]/6 户（新旧边界并存）",
          [c["valveCodes"] for c in oldcand] == [["V3"]]
          and oldcand[0]["affectedAddressCodes"] == SIX, oldcand)

    # 资产工程师不能代调度确认计划（职责分离）
    st, forb = http("POST", f"/api/topology/change-reviews/{r7['id']}/confirm",
                    {}, token=asset)
    check("ASSET_ENGINEER 不能代调度确认隔离计划（403）",
          st == 403 and forb.get("code") == "ROLE_FORBIDDEN", (st, forb))

    # ---- 调度确认 WO-S7：新计划版本，但通知不动 ----
    _, conf = http("POST", f"/api/topology/change-reviews/{r7['id']}/confirm",
                   {"note": "现场核对后同意扩为 V3+V4"}, token=disp, expect=200)
    check("WO-S7 重审单 CONFIRMED，确认阀门 [V3,V4] 并生成 newPlanId",
          conf["status"] == "CONFIRMED" and conf["confirmedValveCodes"] == ["V3", "V4"]
          and conf["newPlanId"] is not None and conf["confirmedBy"] == "dispatcher", conf)

    _, d7b = http("GET", f"/api/incidents/{wo_s7['id']}", token=disp)
    active_plans = [p for p in d7b["plans"] if p["status"] == "ACTIVE"]
    check("确认后事故固定版本升级 V2、revision=2，新 ACTIVE 计划快照 12 户，旧计划 SUPERSEDED",
          d7b["incident"]["topologyVersionCode"] == "TOPO-V2-PARTIAL"
          and d7b["incident"]["revision"] == 2
          and len(active_plans) == 1
          and active_plans[0]["valveCodes"] == ["V3", "V4"]
          and active_plans[0]["affectedAddressCodes"] == TWELVE
          and any(p["status"] == "SUPERSEDED" for p in d7b["plans"]),
          [(p["versionNo"], p["status"], len(p["affectedAddressCodes"])) for p in d7b["plans"]])
    check("确认动作不自动改写/新增通知：WO-S7 仍是原 6 条 SENT",
          len(d7b["notifications"]) == 6
          and all(n["status"] == "SENT" and n["kind"] == "INITIAL"
                  for n in d7b["notifications"]),
          [(n["kind"], n["status"]) for n in d7b["notifications"]])

    # 显式给新增 6 户发扩大范围通知（幂等）
    _, na = http("POST", f"/api/topology/change-reviews/{r7['id']}/notify-added",
                 {}, token=disp, expect=200)
    check("显式 notify-added 仅向新增 A16-A21 发 6 条 TOPOLOGY_EXPANSION",
          na["sentCount"] == 6
          and {n["addressCode"] for n in na["notifications"] if n["kind"] == "TOPOLOGY_EXPANSION"}
          == set(S8_SIX), na)
    _, na2 = http("POST", f"/api/topology/change-reviews/{r7['id']}/notify-added",
                  {}, token=disp, expect=200)
    check("notify-added 幂等：重复调用 sentCount=0 且 alreadyNotified=true",
          na2["sentCount"] == 0 and na2["alreadyNotified"] is True, na2)
    _, d7c = http("GET", f"/api/incidents/{wo_s7['id']}", token=disp)
    check("此时 WO-S7 共 12 条通知：6 原始 INITIAL + 6 扩大 TOPOLOGY_EXPANSION，旧通知未被改状态",
          len(d7c["notifications"]) == 12
          and len([n for n in d7c["notifications"] if n["kind"] == "INITIAL" and n["status"] == "SENT"]) == 6
          and len([n for n in d7c["notifications"] if n["kind"] == "TOPOLOGY_EXPANSION"]) == 6)

    # 已处理重审单不能重复确认
    st, twice = http("POST", f"/api/topology/change-reviews/{r7['id']}/confirm",
                     {}, token=disp)
    check("重复确认同一重审单被 409 TOPOLOGY_REVIEW_ALREADY_DECIDED 拒绝",
          st == 409 and twice.get("code") == "TOPOLOGY_REVIEW_ALREADY_DECIDED", (st, twice))

    # WO-S8 仍待人工重审（没有被自动确认/改范围）
    _, pending = http("GET", "/api/topology/change-reviews?status=PENDING_CONFIRMATION", token=disp)
    check("WO-S7 确认后，WO-S8 仍为 PENDING（系统不自动把旧通知改成新范围）",
          [r["incidentCode"] for r in pending] == ["WO-2026-12"],
          [r["incidentCode"] for r in pending])
    _, d8b = http("GET", f"/api/incidents/{wo_s8['id']}", token=disp)
    check("WO-S8 在人工确认前保持 V1/[V4]/6 户与原 6 条通知不变",
          d8b["incident"]["topologyVersionCode"] == "TOPO-V1"
          and d8b["plans"][-1]["valveCodes"] == ["V4"]
          and len(d8b["notifications"]) == 6)

    # 调度随后人工确认 WO-S8
    _, conf8 = http("POST", f"/api/topology/change-reviews/{r8['id']}/confirm",
                   {"valveCodes": ["V3", "V4"], "note": "合并关阀"}, token=disp, expect=200)
    check("WO-S8 人工确认后 CONFIRMED 并挂新计划", conf8["status"] == "CONFIRMED")
    _, d8c = http("GET", f"/api/incidents/{wo_s8['id']}", token=disp)
    check("WO-S8 确认后计划为 12 户，但旧 6 条通知一条未改",
          d8c["plans"][-1]["affectedAddressCodes"] == TWELVE
          and len(d8c["notifications"]) == 6
          and all(n["status"] == "SENT" for n in d8c["notifications"]))

    # ---- 已结案旧事故按旧图复演 ----
    _, rep = http("GET", f"/api/incidents/{closed['id']}/topology-replay", token=disp, expect=200)
    check("旧案复演：固定 TOPO-V1，按旧图计算与存储快照一致 reproducesStoredPlan=true",
          rep["adoptedTopologyVersion"] == "TOPO-V1"
          and rep["reproducesStoredPlan"] is True
          and rep["adoptedVersionEvaluation"]["affectedAddressCodes"] == SIX
          and rep["storedAddressCodes"] == SIX, rep)
    check("同一旧阀门 [V3] 放到最新 V2-PARTIAL 图已不能重现旧结论（latestReproduces=false，"
          "证明必须版本固定而非用现图重算）",
          rep["latestTopologyVersion"] == "TOPO-V2-PARTIAL"
          and rep["latestReproducesStoredPlan"] is False
          and rep["latestVersionEvaluation"]["affectedAddressCodes"]
          == ["A06", "A07", "A08", "A09"], rep)
    _, dc = http("GET", f"/api/incidents/{closed['id']}", token=disp)
    check("已结案旧事故仍 CLOSED，旧计划与 6 条历史通知原样保留",
          dc["incident"]["status"] == "CLOSED" and len(dc["notifications"]) == 6)
    st, closed_plan = http("POST", f"/api/incidents/{closed['id']}/isolation-plans",
                           {"valveCodes": ["V3", "V4"]}, token=disp)
    check("已结案事故不能再变更隔离方案（409 INCIDENT_CLOSED）",
          st == 409 and closed_plan.get("code") == "INCIDENT_CLOSED", (st, closed_plan))

    # ---- 新版本生效后的新事故使用新拓扑 ----
    _, rep0 = http("POST", "/api/reports", {
        "callerPhone": "13900000077", "rawAddress": "文昌巷 S7 新增报修",
        "description": "开挖点邻管漏水", "receivedAt": "2026-11-25T22:40:00+08:00"},
        token=disp, expect=200)
    rid = rep0["report"]["id"]
    _, new_inc = http("POST", "/api/incidents", {
        "title": "V2 生效后新派工事故", "reportIds": [rid],
        "boundarySegmentCodes": ["S7"]}, token=disp, expect=200)
    check("V2 生效后新建事故自动固定 TOPO-V2-PARTIAL",
          new_inc["topologyVersionCode"] == "TOPO-V2-PARTIAL", new_inc)
    _, nc = http("GET", f"/api/incidents/{new_inc['id']}/candidate-isolations", token=disp)
    check("新事故候选直接给 [V3,V4]/12 户（新版本供新派工与影响计算使用）",
          nc[0]["valveCodes"] == ["V3", "V4"] and nc[0]["affectedAddressCount"] == 12, nc)


def run_path_b():
    section("路径 B：连通子图全部接头测完 → FULL 整块替换发布")

    disp = login("dispatcher", "Disp@12345")
    asset = login("asset", "Asset@12345")
    http("POST", "/api/admin/reset", {}, token=disp, expect=200)

    # 证据 1：S7-S8（先登记，再由第二名复核人 dual-check）
    http("POST", "/api/topology/evidence", evidence_body("TEV-S7S8"),
         token=disp, expect=200)
    _, evs = http("GET", "/api/topology/evidence", token=disp, expect=200)
    e1id = next(e["id"] for e in evs if e["code"] == "TEV-S7S8")
    http("POST", f"/api/topology/evidence/{e1id}/dual-check",
         {"reviewerName": "李复核"}, token=disp, expect=200)

    # 证据 2：整段测完 S8 末端接头 NE8-NE9（登记时直接带不同复核人 → DUAL_CHECKED）
    e2body = evidence_body("TEV-S8FAR", reviewer="周终审", error=0.08)
    e2body.update({
        "title": "S8 末端方向与 S9 全部接头测量",
        "endAIdentifier": "NE8 三通接头",
        "endBNodeCode": "NE9",
        "endBIdentifier": "S9 远端末点 NE9（两户出户管位置已开挖核对）",
        "measuredDistanceM": 18.9,
        "sketchDescription": "整段连通子图所有接头均已测量：NE8 向东南延伸 S9 至 NE9，"
                             "沿途 A22/A23 两户出户管接入，无其他分叉。",
    })
    _, e2 = http("POST", "/api/topology/evidence", e2body, token=disp, expect=200)
    check("第二份证据登记时带不同复核人即 DUAL_CHECKED（双人核验留痕）",
          e2["status"] == "DUAL_CHECKED" and e2["reviewerName"] == "周终审", e2)

    # FULL 发布若仍携带未知支管应被拒（必须整段测完）
    st, notfull = http("POST", "/api/topology/revisions", {
        "mode": "FULL",
        "evidenceCodes": ["TEV-S7S8", "TEV-S8FAR"],
        "addConnections": [{"segmentCode": "C78", "fromNodeCode": "NE7", "toNodeCode": "NE8"}],
        "confirmSegmentCodes": ["S9"],
        "unknownBranches": [{
            "code": "UB-S8-FAR", "suspectedSegmentCode": "S9",
            "candidateAddressCodes": ["A22", "A23"]}]
    }, token=asset)
    check("FULL 模式仍保留未知支管时 422 FULL_REPLACEMENT_NEEDS_ALL_MEASURED",
          st == 422 and notfull.get("code") == "FULL_REPLACEMENT_NEEDS_ALL_MEASURED",
          (st, notfull))

    _, pub = http("POST", "/api/topology/revisions", {
        "mode": "FULL",
        "effectiveAt": "2026-11-26T08:00:00+08:00",
        "confirmerName": "赵资产",
        "changeSummary": "S8 末端与 S9 全部接头测完，整块替换 V3 连通子图",
        "evidenceCodes": ["TEV-S7S8", "TEV-S8FAR"],
        "addConnections": [{"segmentCode": "C78", "name": "S7-S8 连通支管",
                            "fromNodeCode": "NE7", "toNodeCode": "NE8"}],
        "confirmSegmentCodes": ["S9"],
        "unknownBranches": []
    }, token=asset, expect=200)
    ver = pub["version"]
    check("发布 TOPO-V2-FULL/FULL，两份证据关联，生效时刻 2026-11-26 08:00",
          ver["versionCode"] == "TOPO-V2-FULL" and ver["revisionMode"] == "FULL"
          and set(ver["evidenceCodes"]) == {"TEV-S7S8", "TEV-S8FAR"}
          and str(ver["effectiveAt"]).startswith("2026-11-26T08:00"), ver)
    check("FULL 版本快照中未知支管列表为空",
          ver["snapshot"]["unknownBranches"] == [])

    _, segs = http("GET", "/api/network/segments", token=disp)
    s9 = next(s for s in segs if s["code"] == "S9")
    check("发布后在线管网 S9 转为 CONFIRMED", s9.get("topologyStatus") == "CONFIRMED", s9)

    _, imp14 = http("POST", "/api/network/impact",
                   {"valveCodes": ["V3", "V4"]}, token=disp)
    check("整段测完后 [V3,V4] 影响恰为 14 户（原 12 + S9 的 A22/A23）",
          imp14["affectedAddressCodes"] == FOURTEEN, imp14["affectedAddressCodes"])
    _, cand = http("POST", "/api/network/candidate-isolations",
                   {"boundarySegmentCodes": ["S7"]}, token=disp)
    check("FULL 后 S7 首选候选 [V3,V4] 为 14 户",
          cand[0]["valveCodes"] == ["V3", "V4"]
          and cand[0]["affectedAddressCount"] == 14, cand)

    reviews = pub["changeReviews"]
    r7 = next(r for r in reviews if r["incidentCode"] == "WO-2026-11")
    check("FULL 重审单推荐仍为 [V3,V4]/14 户口径，且 uncertainAddressCodes 已清空",
          r7["recommendedValveCodes"] == ["V3", "V4"]
          and r7["candidateCombos"][0]["affectedAddressCount"] == 14
          and r7["uncertainAddressCodes"] == [], r7)

    # 旧案仍按 V1 复演 6 户，不受 FULL 发布影响
    _, incs = http("GET", "/api/incidents", token=disp)
    closed_id = next(i["id"] for i in incs if i["code"] == "INC-2026-088")
    _, rep = http("GET", f"/api/incidents/{closed_id}/topology-replay", token=disp)
    check("FULL 发布后旧案仍按 TOPO-V1 复演成功（6 户），最新版本为 TOPO-V2-FULL",
          rep["reproducesStoredPlan"] is True
          and rep["adoptedVersionEvaluation"]["affectedAddressCodes"] == SIX
          and rep["latestTopologyVersion"] == "TOPO-V2-FULL"
          and rep["latestReproducesStoredPlan"] is False, rep)

    # 两个版本并存可分别试算
    _, v1 = http("POST", "/api/network/impact",
                 {"valveCodes": ["V3"], "topologyVersion": "TOPO-V1"}, token=disp)
    _, v2 = http("POST", "/api/network/impact",
                 {"valveCodes": ["V3", "V4"], "topologyVersion": "TOPO-V2-FULL"}, token=disp)
    check("/api/network/impact 支持 topologyVersion 参数：V1=6、V2-FULL=14",
          v1["affectedAddressCodes"] == SIX and v2["affectedAddressCodes"] == FOURTEEN,
          (v1["affectedAddressCodes"], v2["affectedAddressCodes"]))


def main():
    if not wait_healthy():
        print("服务未就绪")
        print("ALL_DONE: false")
        sys.exit(2)
    try:
        run_path_a()
        run_path_b()
    except AssertionError as e:
        print("\n回放因关键断言失败中止:", e)
        print(f"\n# 拓扑回放断言结果：PASS={PASS_COUNT}  FAIL={FAIL_COUNT}")
        print("ALL_DONE: false")
        sys.exit(1)

    print("\n" + "#" * 78)
    print(f"# 拓扑修订回放断言结果：PASS={PASS_COUNT}  FAIL={FAIL_COUNT}")
    if FAIL_COUNT:
        print("# 失败项：")
        for line in FAIL_LINES:
            print("  - " + line)
        print("ALL_DONE: false")
        sys.exit(1)
    print("# 🎉 两条路径全部通过：部分修订(12户/未知2户) / 整段发布(14户) / 工单人工重审 / 旧图复演")
    print("ALL_DONE: true")
    sys.exit(0)


if __name__ == "__main__":
    try:
        main()
    except Exception:  # noqa
        import traceback
        traceback.print_exc()
        print("ALL_DONE: false")
        sys.exit(2)
