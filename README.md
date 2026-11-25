# 供水抢修调度服务（water-repair-service）

暴雨后的凌晨，值守人员从原始报修形成事故、核实隔离范围、派出合格班组并确认恢复。
本项目用 **Java 21 / Spring Boot 3.5 / PostgreSQL** 实现了一个真实可调用的后端服务，
覆盖：报修原文与归属判断、事故确认、图算法候选关阀组合与精确地址影响集合、
班组许可/当班窗口/车辆/工具校验、队伍与阀门操作许可的并发互斥、两种稀缺队伍调度路线、
五类现场回执的顺序约束、复压区间+校时容差核验、复压失败重新派工，以及压力复核推翻后的
范围/资源占用/通知全链路更正。

**拓扑修订（S7/S8 题）**：现场草图、管段两端标识、测距结果、测量误差、测量/复核双人留痕
作为可审查证据；**资产工程师**确认后才生成不可变的新拓扑版本（`PARTIAL` 局部修订可把相邻
未测支管标 `UNKNOWN`，或 `FULL` 整段测完后整块替换）。新版本供新派工/影响计算使用，事故
固定成案时的版本，已结案旧案始终按旧图复演；拓扑变化只对引用旧版本的开放工单生成
“阀门组合 / 地址集合 / 已通知对象”差异重审单，**调度人工确认后**才更新计划，绝不自动改写
旧通知。随题样例：旧图 V3 影响 6 户 → 确认 S7-S8 连通后候选隔离 `[V3,V4]` 涉及 12 户
（未知末端 S9 上 2 户只提示不纳入）→ 整段测完确认 S9 后为 14 户；另有 2 张引用旧图的开放
工单与 1 个已结案旧事故用于重审/复演核对。

> 项目类型：**后端 API 服务**。自带 OpenAPI 3 文档与 Swagger UI，无独立前端页面。

---

## 1. 一分钟启动

### 方式 A：Docker Compose（一键，推荐验收）

```bash
docker compose up --build
```

- 自动启动 PostgreSQL 18 与应用，应用监听 <http://localhost:8080>
- 宿主机 PostgreSQL 端口冲突时：`DB_HOST_PORT=55432 APP_PORT=18080 docker compose up --build`
- 容器内已配置阿里云 Maven 镜像；样例数据在首次启动时自动播种

打开：

- Swagger UI：<http://localhost:8080/swagger-ui/index.html>
- OpenAPI JSON：<http://localhost:8080/v3/api-docs>
- 健康检查：<http://localhost:8080/api/health>

### 方式 B：本地开发启动（本工作区已验证）

环境：JDK 25（`/opt/jdk-25`，编译目标 21）、Maven 3.9（`/opt/maven`）、
PostgreSQL 18（本机 `127.0.0.1:5432`，库/用户/密码均为 `ccdev`）。

```bash
# 1) 确保 PostgreSQL 可连（容器自带实例）
PGPASSWORD=ccdev /opt/postgresql-18/bin/psql -h 127.0.0.1 -U ccdev -d ccdev -c 'select 1;'

# 2) 开发模式运行（首次会自动建表+播种）
JAVA_HOME=/opt/jdk-25 /opt/maven/bin/mvn -s settings.xml spring-boot:run

# 或打包后运行
JAVA_HOME=/opt/jdk-25 /opt/maven/bin/mvn -s settings.xml -DskipTests package
/opt/jdk-25/bin/java -jar target/water-repair-service.jar
```

如需使用自己的数据库，用环境变量覆盖：

```bash
DB_URL='jdbc:postgresql://HOST:5432/DBNAME' DB_USER=u DB_PASSWORD=p \
  /opt/jdk-25/bin/java -jar target/water-repair-service.jar
```

---

## 2. 测试账号

| 用户名 | 密码 | 角色 | 用途 |
| --- | --- | --- | --- |
| `dispatcher` | `Disp@12345` | DISPATCHER | 值班调度员，全部调度写操作（受理/确认/派工/复核/拓扑工单确认/关单/reset） |
| `asset` | `Asset@12345` | ASSET_ENGINEER | 资产工程师，唯一可确认证据并发布拓扑新版本的角色 |
| `crewsole` | `Crew@12345` | CREW（绑定 `C_SOLE`） | 唯一资质装备齐备的持证维修班班长，可提交回执 |
| `scout` | `Scout@12345` | CREW（绑定 `C_SCOUT`） | 勘查一班 |
| `viewer` | `View@12345` | VIEWER | 只读角色，写接口返回 403 |

登录换取 token：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"dispatcher","password":"Disp@12345"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
echo "$TOKEN"
```

之后所有 `/api/**`（除 `/api/auth/login`、`/api/health`）都要带：

```
Authorization: Bearer <token>
```

在 Swagger UI 中点右上角 **Authorize**，粘贴 token 原文（不要带 `Bearer ` 前缀）即可调试。

---

## 3. 自动回放验证（推荐，纯 Python 标准库）

`scripts/verify.sh` 会等待服务就绪后**依次**执行两套回放；两套脚本开头都会登录调度员并调用
`POST /api/admin/reset` 清库重播，因此都可独立、重复执行。

```bash
# 终端 1：启动服务（见上）

# 终端 2：一条命令跑两套回放（基线 114 断言 + 拓扑专题 63 断言）
bash scripts/verify.sh

# 也可分别执行：
python3 scripts/replay.py             # 基线全链路 114 断言
python3 scripts/replay_topology.py    # 本题拓扑修订双路径 63 断言
# 或
bash scripts/verify.sh
# 远端/自定义地址：
BASE_URL=http://localhost:8080 bash scripts/verify.sh
```

`scripts/replay.py` 预期结尾：

```
##############################################################################
# 回放断言结果：PASS=114  FAIL=0
# 🎉 全部场景断言通过：同段重拨 / 相邻双支管 / 并发互斥 / 复压重派 / 更正链
ALL_DONE: true
```

`scripts/replay_topology.py` 预期结尾：

```
##############################################################################
# 拓扑修订回放断言结果：PASS=63  FAIL=0
# 🎉 两条路径全部通过：部分修订(12户/未知2户) / 整段发布(14户) / 工单人工重审 / 旧图复演
ALL_DONE: true
```

`scripts/replay.py` 覆盖并核对：

1. **同段重拨**：R1/R2 形成 I2 后，R3 热线重拨自动作为 `REDIAL` 来源挂入同一事故；
   事故数、派工数均不增加；重复追加来源幂等。
2. **相邻双支管**：相隔 <100m 的幸福路 8 号（A05→S2）与滨河巷 1 号（A06→S3）形成不同事故；
   路口地标与“幸福里 6 栋”同楼双地址都只给 `AMBIGUOUS [S2,S3]`，不自动合并；
   人工确认保留 v1 AUTO / v2 MANUAL 两级判断历史。
3. **精确影响集合**：单关 `V2 = A01..A05`；单关 `V1 = A01..A05 + A10..A13`（S1+S2，9 户）；
   旧图单关 `V3 = A06..A09 + A14/A15`（S3+S7，6 户）；单关 `V4 = A16..A21`（S8，6 户）。
   I2 候选组合为 `[V2(5户), V1(9户)]`，I3 只有 `V3`，I1 只有 `V1`。
4. **资格门槛**：许可缺失（勘查班不能接维修单，422）、当班窗口外、
   车辆不可用、工具不可用都有逐项 `checks`。
5. **唯一持证班组两次并发派单**：两线程用 Barrier 同时请求 I2/I3 的维修派单，
   数据库行锁 + 时间窗重叠校验保证**恰好一个 200、一个 409 `CREW_ALREADY_BOOKED`**，
   409 细节给出占用方派工单/事故/窗口；取消后占用全部 `RELEASED` 且原因可查。
6. **两条调度路线**：
   - 路线 A（勘查先行，I3）：先派 `C_SCOUT`，04:25 到场确认 V3；
     跳序 `REPAIR` 被 `RECEIPT_ORDER_CONFLICT` 拒，勘查单提交 `ISOLATION` 被 422 拒；
     随后锁 `C_SOLE` 的 06:00-08:00 窗口被路线 B 的 I2 先占（409），
     最终改锁 09:30-11:30（与 I2 窗口 09:20 结束半开相接，不冲突）成功。
   - 路线 B（捆绑暂留，I2）：`holdOnly=true` 一次锁定
     `CREW C_SOLE + VALVE_PERMIT POP-V2 + VEHICLE VEH-1 + TOOL-*` 共 5 项 HELD 占用，
     派工单 `PROVISIONAL`，`issue` 后才 `DISPATCHED`。
7. **复压失败→重新派工**：I2 第一周期五回执齐全，`PR-S2-FAIL`（0.14MPa < S2 下限 0.22）
   核验 `FAILED`，关单 409 `REPRESSURE_NOT_PASSED`；`redispatch` 后
   旧派工单 5 项占用以 `REDISPATCH_AFTER_REPRESSURE_FAILURE` 释放，
   `cycleNo=2 / attemptNo=2 / parentAssignmentId` 成链；旧单再交回执报 `RECEIPT_WRONG_CYCLE`；
   新周期用 `PR-S2-OK` 通过并关单，新占用以 `INCIDENT_CLOSED` 释放。
8. **回执归属**：I3 的 `ARRIVAL` 永久归属勘查单/`C_SCOUT`，
   其余四条归属维修单/`C_SOLE`；每条回执都带 `assignmentId/assignmentCode/cycleNo`。
9. **压力复核更正链（I1）**：复核 `OVERTURNS` 把边界 S1/V1 改为 S2/V2：
   更正记录保存新旧边界/阀门/地址全集，`removed=[A10..A13]`、`added=[]`；
   占用侧释放 `POP-V1`（原因含 `PRESSURE_REVIEW_CORRECTION`）并新占 `POP-V2`；
   9 条原始通知全部 `SUPERSEDED` 且挂 `supersededByCorrectionId`，
   新发 5 条 `CORRECTION` + 4 条 `CORRECTION_CANCEL`（A10-A13），
   取消通知用 `replacesNotificationId` 链回旧通知——旧通知不删除、不伪装未发生。
10. **校时容差**：`POST /api/pressure/verify-repressure` 可分别判定
    “压力值超出支管区间”和“校时后采样时刻与复压时刻偏差超过容差”。
11. 终态：所有 `resource_occupancy` 均为 `RELEASED`，3 个事故均 `CLOSED`。

`scripts/replay_topology.py` 覆盖并核对（每次先 reset，两条路径各自独立重播）：

**路径 A：只确认 S7-S8、S8 末端未测（PARTIAL）**

1. 基线 `TOPO-V1/BASELINE/ACTIVE`：`V3=6 户`、`V4=6 户`，未测 `S9` 为 `UNKNOWN`；
   播种 `WO-2026-11`(S7)、`WO-2026-12`(S8) 两张引用旧图的开放工单（各带 ACTIVE 旧计划与
   6 条 SENT 通知），以及已结案旧事故 `INC-2026-088`。
2. 职责与证据闸门：VIEWER 不能登记证据；DISPATCHER 不能发布版本（403）；
   复核人=测量人返回 `SAME_PERSON_REVIEWER`；证据五要素（`sketchRef/sketchDescription`、
   两端 `endA/endB` 标识、`measuredDistanceM=12.8`、`measurementErrorM=0.2`、`reviewerName`）
   齐全且 `DUAL_CHECKED` 后才能发布；使用未核验证据发布返回 `EVIDENCE_NOT_DUAL_CHECKED`。
3. 资产工程师发布 `TOPO-V2-PARTIAL`（显式 `effectiveAt=2026-11-25T22:30+08:00`，
   记录确认人/证据/父版本）：新增连通管 `C78 NE7-NE8`，把 `UB-S8-FAR/S9` 标为未知。
4. 新影响结论：`[V3,V4]` 为 S7 的唯一最小候选，恰 **12 户** `A06..A21`；未知 `A22/A23`
   只进 `uncertainAddressCodes`，**不纳入**权威集合/通知；新图下单关 V3 因 S7 被
   `C78→S8→V4` 反供而只剩 S3 四户——直观说明旧组合为何必须重审。
5. 两张工单各生成一张 `PENDING_CONFIRMATION` 重审单：旧阀门/旧 6 户集合、新候选组合明细、
   地址 added/removed diff、未知地址、以及发布时点的 6 个已通知对象快照。
6. **新旧边界并存**：调度确认前，事故候选接口仍按固定的 V1 给 `[V3]/6 户`；
   ASSET_ENGINEER 不能代调度确认计划（403）。
7. 调度确认 WO-S7 后：事故固定版本升级 V2、`revision=2`、新 ACTIVE 计划 12 户、旧计划
   SUPERSEDED；**通知仍是原 6 条 SENT，一条未改**；随后显式 `notify-added` 才给 A16-A21
   发 6 条 `TOPOLOGY_EXPANSION`（重复调用幂等）。另一张 WO-S8 此时仍 PENDING，不会被自动确认。
8. **旧图复演**：`INC-2026-088` 固定 V1，`topology-replay` 的
   `reproducesStoredPlan=true`（6 户与存储快照一致）；同一 `[V3]` 放到最新 V2 图仅 4 户、
   `latestReproducesStoredPlan=false`，证明不能用现图重算历史；已结案事故改计划返回 409。
9. V2 生效后新建事故自动固定 `TOPO-V2-PARTIAL`，候选直接给 `[V3,V4]/12 户`。

**路径 B：整段测完后发布（FULL）**

- 两份双人核验证据（S7-S8 + S8 末端/S9 全部接头）；仍携带未知支管时 FULL 发布被
  `FULL_REPLACEMENT_NEEDS_ALL_MEASURED` 拒绝；补测 `confirmSegmentCodes=["S9"]`、
  `unknownBranches=[]` 后发布 `TOPO-V2-FULL`，S9 在线转为 CONFIRMED，`[V3,V4]` 达 **14 户**
  （含 A22/A23），重审单 unknown 清空；旧案仍按 V1 复演 6 户；
  `/api/network/impact` 的 `topologyVersion` 参数可分别复算 V1=6 / V2-FULL=14。

---

## 4. 手工 API 走查（核心端点）

所有写接口都需要调度员 token。完整 schema 见 Swagger UI。

```bash
B=http://localhost:8080
H="Authorization: Bearer $TOKEN"; J='Content-Type: application/json'

# 复位样例（回放脚本开头也会调用）
curl -s -X POST $B/api/admin/reset -H "$H" -H "$J" -d '{}'

# 管网与精确影响
curl -s $B/api/network/addresses -H "$H" | python3 -m json.tool
curl -s -X POST $B/api/network/impact -H "$H" -H "$J" -d '{"valveCodes":["V2"]}'
curl -s -X POST $B/api/network/candidate-isolations -H "$H" -H "$J" \
  -d '{"boundarySegmentCodes":["S2"]}'

# 受理报修（自动归属判断）
curl -s -X POST $B/api/reports -H "$H" -H "$J" -d '{
  "callerPhone":"13900000009","rawAddress":"幸福路3号",
  "description":"低压","receivedAt":"2026-11-26T03:05:00+08:00"}'

# 调度确认事故
curl -s -X POST $B/api/incidents -H "$H" -H "$J" -d '{
  "title":"S2 低压","reportIds":[1],"boundarySegmentCodes":["S2"]}'

# 候选方案 → 选 V2 → 通知 A01-A05
curl -s $B/api/incidents/1/candidate-isolations -H "$H"
curl -s -X POST $B/api/incidents/1/isolation-plans -H "$H" -H "$J" -d '{"valveCodes":["V2"]}'
curl -s -X POST $B/api/incidents/1/notify -H "$H"

# 资格预检 → 捆绑暂留派工 → 下达
curl -s -X POST $B/api/incidents/1/dispatch/eligibility -H "$H" -H "$J" -d '{
  "kind":"REPAIR","crewCode":"C_SOLE",
  "windowStart":"2026-11-26T05:00:00+08:00","windowEnd":"2026-11-26T07:00:00+08:00"}'
curl -s -X POST $B/api/incidents/1/dispatch/repair -H "$H" -H "$J" -d '{
  "crewCode":"C_SOLE","strategy":"RESERVE_BUNDLE","holdOnly":true,
  "windowStart":"2026-11-26T05:00:00+08:00","windowEnd":"2026-11-26T07:00:00+08:00"}'

# 按顺序提交回执（assignmentId 从上一步响应取）
curl -s -X POST $B/api/incidents/1/receipts -H "$H" -H "$J" -d '{
  "assignmentId":1,"type":"ARRIVAL","eventAt":"2026-11-26T05:05:00+08:00","note":"到场"}'

# 事故全状态聚合（核对来源/判断/方案/通知/更正/派工/占用/回执）
curl -s $B/api/incidents/1 -H "$H" | python3 -m json.tool
```

### 拓扑修订专题走查（S7/S8）

```bash
# 资产工程师 token（发布版本专用）
AT=$(curl -s -X POST $B/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"asset","password":"Asset@12345"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
HA="Authorization: Bearer $AT"

# 旧图：V3=6 户；基线版本 TOPO-V1
curl -s -X POST $B/api/network/impact -H "$H" -H "$J" -d '{"valveCodes":["V3"]}'
curl -s $B/api/topology/versions -H "$H" | python3 -m json.tool

# 1) 登记现场证据（草图引用/两端标识/测距 12.8m/误差 0.2m/测量人）
curl -s -X POST $B/api/topology/evidence -H "$H" -H "$J" -d '{
  "code":"TEV-S7S8","sketchRef":"evidence/TEV-S7S8.png",
  "sketchDescription":"S7 旧盲端工作坑 NE7 见支管接向 S8 的 NE8",
  "endANodeCode":"NE7","endAIdentifier":"S7 末端 J7 红漆桩",
  "endBNodeCode":"NE8","endBIdentifier":"S8 接点 J8 探测标记",
  "measuredDistanceM":12.8,"measurementErrorM":0.2,
  "surveyorName":"王测量","reviewerName":"李复核"}'

# 2) 资产工程师发布 PARTIAL：加 C78 连通管，S9 标未知
curl -s -X POST $B/api/topology/revisions -H "$HA" -H "$J" -d '{
  "mode":"PARTIAL","effectiveAt":"2026-11-25T22:30:00+08:00",
  "confirmerName":"赵资产","evidenceCodes":["TEV-S7S8"],
  "addConnections":[{"segmentCode":"C78","fromNodeCode":"NE7","toNodeCode":"NE8"}],
  "unknownBranches":[{"code":"UB-S8-FAR","attachedNodeCode":"NE8",
    "suspectedSegmentCode":"S9","status":"UNKNOWN",
    "candidateAddressCodes":["A22","A23"]}]}' | python3 -m json.tool

# 3) 12 户新范围 / 两张开放工单重审单
curl -s -X POST $B/api/network/impact -H "$H" -H "$J" -d '{"valveCodes":["V3","V4"]}'
curl -s "$B/api/topology/change-reviews?status=PENDING_CONFIRMATION" -H "$H"

# 4) 调度人工确认 WO-2026-11 对应重审单（id 从上一步取），旧通知不会被改
curl -s -X POST $B/api/topology/change-reviews/1/confirm -H "$H" -H "$J" -d '{}'
# 仅显式动作才给新增 6 户发扩大通知（幂等）
curl -s -X POST $B/api/topology/change-reviews/1/notify-added -H "$H" -H "$J"

# 5) 已结案旧事故按 TOPO-V1 复演
curl -s $B/api/incidents/3/topology-replay -H "$H" | python3 -m json.tool
```

主要拓扑端点（同时挂载别名前缀 `/api/network/topology`）：

| 方法与路径 | 角色 | 说明 |
| --- | --- | --- |
| `POST /api/topology/evidence` | DISPATCHER/CREW/ASSET_ENGINEER | 登记现场证据，可直接带不同 `reviewerName` 完成双人核验 |
| `POST /api/topology/evidence/{id}/dual-check` | 同上 | 第二名复核人核验（不得与测量人同名） |
| `GET /api/topology/evidence[/{idOrCode}]` | 登录即可 | 证据列表/详情，供审查 |
| `POST /api/topology/revisions` | **ASSET_ENGINEER** | 凭双人核验证据发布 `PARTIAL/FULL` 不可变版本 |
| `GET /api/topology/versions[/{code}]` | 登录即可 | 版本列表/详情（含完整快照与未知支管） |
| `POST /api/topology/versions/{code}/impact`、`.../candidate-isolations` | 登录即可 | 按任意历史版本复算 |
| `GET /api/topology/change-reviews[?status=]`、`GET /change-reviews/{id}` | 登录即可 | 开放工单差异重审单（候选组合+已通知对象） |
| `POST /api/topology/change-reviews/{id}/confirm` | DISPATCHER | 人工确认阀门组合→新计划版本，不改旧通知 |
| `POST /api/topology/change-reviews/{id}/dismiss` | DISPATCHER | 人工排除/维持旧图（必填理由） |
| `POST /api/topology/change-reviews/{id}/notify-added` | DISPATCHER | 显式给新增地址发扩大范围通知（幂等） |
| `GET /api/incidents/{id}/topology-replay` | 登录即可 | 按事故固定版本复演，并与最新版本试算对比 |

主要资源分组（Swagger Tag 与之一致）：

| 分组 | 代表端点 |
| --- | --- |
| 01 鉴权 | `POST /api/auth/login` |
| 02 管网图与影响 | `/api/network/*`、`POST /api/network/impact`、`/api/network/candidate-isolations` |
| 03 报修与归属 | `POST /api/reports`、`POST /api/reports/{id}/judgments` |
| 04 事故/方案/通知/复核 | `POST /api/incidents`、`/isolation-plans`、`/notify`、`/pressure-reviews`、`GET /{id}` |
| 05 派工与互斥 | `/dispatch/scout`、`/dispatch/repair`、`/redispatch`、`/api/assignments/{id}/cancel|complete|issue` |
| 06 回执与复压 | `POST /api/incidents/{id}/receipts`、`/close`、`POST /api/pressure/verify-repressure` |
| 07 拓扑证据/版本/重审 | `/api/topology/evidence`、`/evidence/{id}/dual-check`、`/revisions`、`/versions*`、`/change-reviews*`、`/api/incidents/{id}/topology-replay` |
| 00 回放支持 | `POST /api/admin/reset` |

---

## 5. 领域模型与关键实现

### 5.1 随题样例管网（`src/main/resources/samples/network.json`）

基线竣工图 `TOPO-V1`（注意 S7 被标成 V3 后的盲端，S9 为 `UNKNOWN` 未测状态）：

```
NSRC ─ M0 ─ NT0 ─ F1 ─ NV1(V1) ─ F1D ─ NT1 ─ S1 ─ NE1   地址 A10-A13
                │                    └ F2 ─ NV2(V2) ─ S2 ─ NE2  地址 A01-A05
                ├ F3 ─ NV3(V3) ─┬ S3 ─ NE3                     地址 A06-A09
                │               └ S7 ─ NE7(旧图盲端)           地址 A14-A15
                └ F4 ─ NV4(V4) ─ S8 ─ NE8 ┄ S9(UNKNOWN) ┄ NE9  S8=A16-A21 / S9=A22-A23(未纳入)

现场修订后新增 C78：NE7(S7) ──────── NE8(S8)，S7 与 S8 连通：
  PARTIAL：C78=CONFIRMED、S9 仍 UNKNOWN  → 隔离 S7 需 [V3,V4]，12 户（A06-A21），A22/A23 仅提示
  FULL   ：S9 补测转 CONFIRMED           → [V3,V4] 14 户（再含 A22/A23）
```

- 节点：`SOURCE / TEE 三通 / VALVE 阀门 / END 末端`；管段连接两节点，带 `topologyStatus`
  （`CONFIRMED` 默认 / `UNKNOWN` 不进权威连通计算）。
- 地址点唯一映射管段；`幸福里6栋`同时是 A04(S2)/A07(S3) 的别名，
  路口 `LM-XF-BH` 候选 `[S2,S3]`——用于证明“同楼/最近坐标 ≠ 同一事故”。
- 每段配置复压正常区间（如 S2 `[0.22, 0.45] MPa`）与校时容差（600s）。
- 压力读数带设备号、采样时刻 `sampledAt`、校时偏差 `clockOffsetSeconds`；
  约定 **校时后采样时刻 = sampledAt + clockOffsetSeconds**。

影响算法（`network/ImpactService.java`）：把关闭的阀门视为不可穿越节点，
从所有 SOURCE 做可达性遍历；一条管段两端都不可达即判为失压，其上地址全部受影响。
候选方案枚举全部阀门子集（≤4 阀组合），返回能隔离边界管段的“包含意义最小”组合。

### 5.2 归属与事故边界

- 每条报修永久保存原文（地址、描述、照片引用、接听时刻）。
- 自动归属按“最长别名命中 → 路口地标 → 坐标半径”的优先级产出
  `MATCHED / AMBIGUOUS / UNMATCHED` 判断；人工判断只追加新版本，不覆盖历史。
- 报修受理**不会**自动建事故；只有调度调用 `POST /api/incidents` 显式确认边界后才成案。
- 热线重拨带 `redialOfReportId/hotlineCallId`：若原线索已在事故中，
  新线索自动以 `REDIAL` 加入同一事故，不产生新事故或派工。

### 5.3 互斥与资格

- 资格逐项检查：班组 active、申请窗口落在当班时间（按 Asia/Shanghai）、
  所需资质（`Q_INSPECT/Q_REPAIR/Q_VALVE_STD/Q_VALVE_LARGE`，V1 要大阀许可）、
  绑定车辆 `available`、标配工具全部 `available`。
- 占用资源类型：`CREW / VALVE_PERMIT / VEHICLE / TOOL`。
- 预留时先对资源主行 `SELECT ... FOR UPDATE`，再用半开区间条件
  `existing.start < :end AND existing.end > :start AND status='HELD'` 查重叠。
  因此同一队伍（或同一阀门操作票）的两个并发派单在数据库层串行化，恰好一成一败；
  端点相接的窗口（如 09:20 结束 / 09:30 开始）不冲突。
- 释放不删行：占用行置 `RELEASED` 并写 `releaseReason/releasedAt`。

### 5.4 回执周期与更正链

- 顺序固定：`ARRIVAL → ISOLATION → REPAIR → FLUSH → REPRESSURE`，按 `(incident, cycleNo)` 成链。
- `ISOLATION` 及之后必须由 `REPAIR` 派工单提交；勘查单只能交 `ARRIVAL`。
- 复压必须引用压力读数，核验：压力值在该支管区间内、校时后采样时刻与回执时刻偏差 ≤ 容差、
  读数设备管段与当前边界一致；任一不过即 `FAILED`，`/close` 返回 409。
- `redispatch` 释放上一维修单占用、`cycleNo+1`、建立新 `Assignment(attemptNo,parent)`；
  旧周期回执与失败结论原样保留，旧单不能再写新周期。
- 压力复核 `OVERTURNS` 在一个事务内完成：旧方案 `CORRECTED` + 新方案版本、
  事故边界更新、地址集合 diff、阀门操作票“释放旧票/新占新票”（新票冲突则整体回滚）、
  旧通知 `SUPERSEDED` + 更正/取消通知链、`CorrectionRecord` 留全量快照。

### 5.5 拓扑证据、不可变版本与旧图复演（S7/S8 题）

- **证据 `TopologyEvidence`**：现场草图（`sketchRef` 附件号 + `sketchDescription` 文字/示意）、
  管段两端标识（节点码 + 现场标识 + 标记桩 + 坐标）、`measuredDistanceM` 测距结果、
  `measurementErrorM` 测量误差（题样例 0.2m）、测量人 `surveyorName` 与复核人 `reviewerName`。
  复核人与测量人同名直接 `SAME_PERSON_REVIEWER`；`DUAL_CHECKED` 且五要素齐全才允许送发布。
- **版本 `TopologyVersion`**：`TOPO-V1`（播种基线/BASELINE）→ `TOPO-V2-PARTIAL` 或
  `TOPO-V2-FULL`。每版保存整图 `snapshotJson`（节点/管段/阀门/地址/未知支管），发布后旧版
  置 `SUPERSEDED` 但快照不删不改；`effectiveAt` 是新版本可供新派工/计算采用的生效时刻。
  发布在一个事务内：校验证据 → 由父快照打补丁生成新版本 → 同步在线管网表（新增 C78、
  把 S9 转 CONFIRMED）→ 扫描开放事故生成 `TopologyChangeReview`。
- **事故固定版本**：`incident.topologyVersionCode` 在成案时取当前 ACTIVE；之后拓扑发布不回写。
  事故维度的候选/方案校验都走固定版本快照（纯函数 `TopologyGraphEvaluator`，算法与在线
  `ImpactService` 一致），因此“新旧边界并存”时未确认工单仍按旧图出 `[V3]/6 户`。
- **重审单 `TopologyChangeReview`**：只对“旧组合在新图不再隔离边界，或推荐集合变化”的开放
  事故生成，内含旧阀门/旧失压段/旧地址、新版本全部候选组合 JSON、added/removed/uncertain
  地址，以及发布时点“已通知对象”快照 JSON。调度 `confirm` 后才新建 ACTIVE 计划版本并把事故
  迁到新版本；**确认与发布都不修改 NotificationRecord**。需要扩大通知时由调度显式调用
  `notify-added`（新种类 `TOPOLOGY_EXPANSION`，按事故+种类幂等）。
- **旧案复演**：`GET /api/incidents/{id}/topology-replay` 同时给“固定版本复算 vs 存储计划
  快照”的一致性（`reproducesStoredPlan`）与“最新版本试算”的漂移对比
  （`latestReproducesStoredPlan`）。已结案事故不产生重审单、不能再改计划。
- **未知支管原则**：`topologyStatus=UNKNOWN` 的管段不进入可达性遍历，其候选地址只出现在
  `uncertainAddressCodes`，从机制上避免把未确认住户接进错误隔离/通知范围。

---

## 6. 本地开发与验证方式（实际执行记录）

以下验证均在本工作区容器内**实际执行通过**（日期 2026-11-25）：

| 验证 | 命令 | 实际结果 |
| --- | --- | --- |
| 编译 | `JAVA_HOME=/opt/jdk-25 /opt/maven/bin/mvn -s settings.xml compile` | BUILD SUCCESS |
| 单元测试 | `... mvn -s settings.xml test` | `ImpactServiceTest` 4 个 + `TopologyGraphEvaluatorTest` 4 个，Tests run: 8, Failures: 0, Errors: 0 |
| 打包 | `... mvn -s settings.xml -DskipTests package` | 生成 `target/water-repair-service.jar`（约 57 MB fat jar） |
| 真实运行 | `/opt/jdk-25/bin/java -jar target/water-repair-service.jar` | 8080 启动成功，Hibernate 连 PostgreSQL 18.1 建表/播种（含 TOPO-V1 与 3 个拓扑样例工单），日志无 ERROR |
| 基线端到端回放 | `python3 scripts/replay.py` | `PASS=114 FAIL=0`，并连续重复多次结果一致（含真实双线程并发派单） |
| 拓扑双路径回放 | `python3 scripts/replay_topology.py` | `PASS=63 FAIL=0`（PARTIAL 12 户/未知 2 户 + FULL 14 户 + 工单重审 + 旧图复演） |
| 一键校验脚本 | `bash scripts/verify.sh` | 顺序执行两套回放，114+63 全部通过，退出码 0 |
| 接口文档 | `GET /v3/api-docs`、`/swagger-ui/index.html` | 均 200，OpenAPI 含全部 `/api/**` 路径（含 `/api/topology/revisions`） |

说明与限制（如实记录）：

- 当前容器**未安装 Docker**，因此 `docker compose up --build` 未能在本机实际执行；
  `Dockerfile`（多阶段 Maven 21 构建 + JRE 21 运行）与 `docker-compose.yml`
  （`postgres:18-alpine` + 健康检查 + 依赖编排）已按标准方式提供，需在有 Docker 的环境验收。
- 回放使用显式 ISO 时间（样例事故日 `2026-11-26`，时区 `+08:00`），不依赖系统时钟，可重复。
- 短信通道为 `SIMULATED_SMS`：通知落库并在接口/聚合详情中可查，不接真实短信网关。

---

## 7. 目录速览

```
pom.xml / settings.xml / Dockerfile / docker-compose.yml
scripts/replay.py                 # 基线 114 断言端到端回放（纯标准库）
scripts/replay_topology.py        # 拓扑修订双路径 63 断言回放（本题）
scripts/verify.sh                 # 等健康检查后顺序执行两套回放
src/main/resources/application.yml
src/main/resources/samples/       # 管网/班组/账号/报修/回执/拓扑工单样本
src/main/java/com/waterrepair/
  auth/        账号、登录 token、角色拦截（含 ASSET_ENGINEER）
  common/      统一异常、健康检查、首页跳转
  config/      Web/OpenAPI/播种/reset
  network/     节点/管段(含 topologyStatus)/阀门/地址/压力读数 + 图影响算法
  topology/    现场证据 + 不可变拓扑版本快照 + 版本化图评估 + 开放工单重审/旧图复演
  report/      报修原文 + 归属判断版本
  incident/    事故(固定 topologyVersionCode)/来源/方案/通知/更正/压力复核
  crew/        班组/车辆/工具/阀门操作许可
  dispatch/    派工单 + 资源占用 + 行锁互斥 + 资格校验
  receipt/     五类顺序回执 + 复压核验
src/test/java/com/waterrepair/network/ImpactServiceTest.java
src/test/java/com/waterrepair/topology/TopologyGraphEvaluatorTest.java
```
