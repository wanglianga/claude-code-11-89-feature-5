package com.waterrepair.incident;

import com.waterrepair.auth.AuthService;
import com.waterrepair.common.ApiException;
import com.waterrepair.network.AddressPoint;
import com.waterrepair.network.AddressPointRepository;
import com.waterrepair.network.ImpactService;
import com.waterrepair.report.AttributionJudgment;
import com.waterrepair.report.AttributionJudgmentRepository;
import com.waterrepair.report.RepairReport;
import com.waterrepair.report.RepairReportRepository;
import com.waterrepair.topology.TopologyQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepo;
    private final IncidentSourceRepository sourceRepo;
    private final IsolationPlanRepository planRepo;
    private final NotificationRecordRepository notificationRepo;
    private final RepairReportRepository reportRepo;
    private final AttributionJudgmentRepository judgmentRepo;
    private final AddressPointRepository addressRepo;
    private final ImpactService impactService;
    private final TopologyQueryService topologyQuery;

    /** 兼容既有直接装配 8 参构造器的调用/单测；版本能力退化为在线现图计算。 */
    public IncidentService(IncidentRepository incidentRepo,
                           IncidentSourceRepository sourceRepo,
                           IsolationPlanRepository planRepo,
                           NotificationRecordRepository notificationRepo,
                           RepairReportRepository reportRepo,
                           AttributionJudgmentRepository judgmentRepo,
                           AddressPointRepository addressRepo,
                           ImpactService impactService) {
        this(incidentRepo, sourceRepo, planRepo, notificationRepo, reportRepo,
                judgmentRepo, addressRepo, impactService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public IncidentService(IncidentRepository incidentRepo,
                           IncidentSourceRepository sourceRepo,
                           IsolationPlanRepository planRepo,
                           NotificationRecordRepository notificationRepo,
                           RepairReportRepository reportRepo,
                           AttributionJudgmentRepository judgmentRepo,
                           AddressPointRepository addressRepo,
                           ImpactService impactService,
                           TopologyQueryService topologyQuery) {
        this.incidentRepo = incidentRepo;
        this.sourceRepo = sourceRepo;
        this.planRepo = planRepo;
        this.notificationRepo = notificationRepo;
        this.reportRepo = reportRepo;
        this.judgmentRepo = judgmentRepo;
        this.addressRepo = addressRepo;
        this.impactService = impactService;
        this.topologyQuery = topologyQuery;
    }

    @Transactional
    public Incident create(IncidentDtos.CreateIncidentRequest req, String operator) {
        List<RepairReport> reports = new ArrayList<>();
        for (Long rid : req.reportIds()) {
            RepairReport r = reportRepo.findById(rid)
                    .orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND", "报修线索不存在: " + rid));
            if (sourceRepo.existsByReportId(rid)) {
                List<IncidentSource> links = sourceRepo.findByReportId(rid);
                throw ApiException.conflict("REPORT_ALREADY_IN_INCIDENT",
                        "线索 " + r.getCode() + " 已归属事故 " + links.get(0).getIncidentId()
                                + "；重拨/追加来源请使用 sources 接口",
                        Map.of("reportId", rid, "incidentId", links.get(0).getIncidentId()));
            }
            reports.add(r);
        }

        List<String> boundary = req.boundarySegmentCodes();
        if (boundary == null || boundary.isEmpty()) {
            Set<String> selected = new HashSet<>();
            Set<String> candidates = new HashSet<>();
            boolean ambiguous = false;
            for (RepairReport r : reports) {
                List<AttributionJudgment> js =
                        judgmentRepo.findByReportIdOrderByVersionNoAscIdAsc(r.getId());
                if (js.isEmpty()) {
                    ambiguous = true;
                    continue;
                }
                AttributionJudgment current = js.get(js.size() - 1);
                if (!current.getSelectedSegmentCodes().isEmpty()) {
                    selected.addAll(current.getSelectedSegmentCodes());
                } else {
                    ambiguous = true;
                    candidates.addAll(current.getCandidateSegmentCodes());
                }
            }
            if (selected.isEmpty()) {
                throw ApiException.unprocessable("BOUNDARY_REQUIRED",
                        "线索归属存在歧义或未匹配，调度确认事故时必须显式指定 boundarySegmentCodes",
                        Map.of("candidateSegmentCodes", candidates));
            }
            boundary = selected.stream().sorted().toList();
            if (ambiguous) {
                // 有部分线索仍歧义：允许调度用显式边界确认，但这里因为没传 boundary 不应到达
                boundary = selected.stream().sorted().toList();
            }
        }

        Incident incident = new Incident();
        incident.setTitle(req.title() != null && !req.title().isBlank()
                ? req.title()
                : "供水抢修事故（线索 " + reports.stream().map(RepairReport::getCode).collect(Collectors.joining(",")) + "）");
        incident.setBoundarySegmentCodes(boundary);
        incident.setStatus("CONFIRMED");
        // 新事故固定当前 ACTIVE 拓扑版本；此后拓扑发布不擅自改变本事故的计算基准
        if (topologyQuery != null) {
            incident.setTopologyVersionCode(topologyQuery.currentActiveVersionCode());
        }
        incident.setConfirmedBy(operator);
        incident.setConfirmationNote(req.note());
        incident.setConfirmedAt(OffsetDateTime.now());
        incident.setCreatedAt(OffsetDateTime.now());
        incident = incidentRepo.saveAndFlush(incident);
        incident.setCode(String.format("INC-%04d", incident.getId()));
        incident = incidentRepo.save(incident);

        for (RepairReport r : reports) {
            String type = r.getRedialOfId() != null ? "REDIAL" : "INITIAL";
            sourceRepo.save(new IncidentSource(incident.getId(), r.getId(), type, OffsetDateTime.now()));
        }
        return incident;
    }

    @Transactional
    public IncidentSource addSource(Long incidentId, Long reportId, String sourceType) {
        Incident incident = mustGet(incidentId);
        RepairReport report = reportRepo.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("REPORT_NOT_FOUND", "报修线索不存在: " + reportId));
        List<IncidentSource> existing = sourceRepo.findByReportId(reportId);
        if (!existing.isEmpty()) {
            IncidentSource s = existing.get(0);
            if (!s.getIncidentId().equals(incidentId)) {
                throw ApiException.conflict("REPORT_IN_OTHER_INCIDENT",
                        "线索 " + report.getCode() + " 已属于其他事故 " + s.getIncidentId(),
                        Map.of("reportId", reportId, "incidentId", s.getIncidentId()));
            }
            return s; // 幂等
        }
        if (sourceRepo.existsByReportId(reportId)) {
            throw ApiException.conflict("REPORT_ALREADY_IN_INCIDENT", "线索已归属事故", null);
        }
        return sourceRepo.save(new IncidentSource(incident.getId(), reportId,
                sourceType == null ? "MANUAL" : sourceType, OffsetDateTime.now()));
    }

    @Transactional(readOnly = true)
    public List<Incident> list() {
        return incidentRepo.findAll();
    }

    @Transactional(readOnly = true)
    public Incident mustGet(Long id) {
        return incidentRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("INCIDENT_NOT_FOUND", "事故不存在: " + id));
    }

    @Transactional
    public IsolationPlan createPlan(Long incidentId, List<String> valveCodes, String operator) {
        Incident incident = mustGet(incidentId);
        if ("CLOSED".equals(incident.getStatus())) {
            throw ApiException.conflict("INCIDENT_CLOSED", "事故已结案，不能再变更隔离方案", null);
        }
        // 按事故固定的拓扑版本校验/快照，保证新旧拓扑边界并存期间口径一致
        ImpactService.ImpactResult impact;
        if (topologyQuery == null) {
            impact = impactService.assertIsolatesBoundary(
                    valveCodes, incident.getBoundarySegmentCodes());
        } else {
            String versionCode = incident.getTopologyVersionCode() == null
                    ? TopologyQueryService.BASELINE_VERSION : incident.getTopologyVersionCode();
            impact = topologyQuery.assertIsolates(versionCode, valveCodes,
                    incident.getBoundarySegmentCodes());
        }

        List<IsolationPlan> plans = planRepo.findByIncidentIdOrderByVersionNoAscIdAsc(incidentId);
        int nextVersion = plans.size() + 1;
        for (IsolationPlan p : plans) {
            if ("ACTIVE".equals(p.getStatus())) {
                p.setStatus("SUPERSEDED");
                planRepo.save(p);
            }
        }

        IsolationPlan plan = new IsolationPlan();
        plan.setIncidentId(incidentId);
        plan.setVersionNo(nextVersion);
        plan.setStatus("ACTIVE");
        plan.setValveCodes(valveCodes.stream().sorted().distinct().toList());
        plan.setOutageSegmentCodes(impact.outageSegmentCodes());
        plan.setAffectedAddressCodes(impact.affectedAddressCodes());
        plan.setCreatedBy(operator);
        plan.setCreatedAt(OffsetDateTime.now());
        return planRepo.save(plan);
    }

    @Transactional(readOnly = true)
    public List<IsolationPlan> plans(Long incidentId) {
        mustGet(incidentId);
        return planRepo.findByIncidentIdOrderByVersionNoAscIdAsc(incidentId);
    }

    @Transactional
    public IncidentDtos.NotifyResult notifyActivePlan(Long incidentId) {
        Incident incident = mustGet(incidentId);
        IsolationPlan plan = planRepo
                .findFirstByIncidentIdAndStatusOrderByVersionNoDesc(incidentId, "ACTIVE")
                .orElseThrow(() -> ApiException.conflict("NO_ACTIVE_PLAN",
                        "事故尚未选择隔离方案，不能发送停水通知", null));

        List<NotificationRecord> existing = notificationRepo.findByIncidentIdOrderByIdAsc(incidentId).stream()
                .filter(n -> "INITIAL".equals(n.getKind()) && plan.getId().equals(n.getPlanId()))
                .toList();
        if (!existing.isEmpty()) {
            return new IncidentDtos.NotifyResult(existing.size(), existing, true);
        }

        Map<String, AddressPoint> addressMap = addressRepo.findAll().stream()
                .collect(Collectors.toMap(AddressPoint::getCode, a -> a));
        List<NotificationRecord> sent = new ArrayList<>();
        for (String code : plan.getAffectedAddressCodes()) {
            AddressPoint a = addressMap.get(code);
            String addr = a == null ? code : a.getDisplayAddress();
            NotificationRecord n = new NotificationRecord();
            n.setIncidentId(incidentId);
            n.setPlanId(plan.getId());
            n.setAddressCode(code);
            n.setKind("INITIAL");
            n.setStatus("SENT");
            n.setChannel("SIMULATED_SMS");
            n.setContent("【供水抢修停水通知】" + incident.getCode()
                    + " 需关闭阀门 " + plan.getValveCodes() + " 抢修，您的地址 " + code + "（" + addr
                    + "）位于停水范围，请提前储水并留意后续恢复/更正通知。");
            n.setSentAt(OffsetDateTime.now());
            sent.add(notificationRepo.save(n));
        }
        return new IncidentDtos.NotifyResult(sent.size(), sent, false);
    }

    @Transactional(readOnly = true)
    public List<IncidentSource> sources(Long incidentId) {
        mustGet(incidentId);
        return sourceRepo.findByIncidentId(incidentId);
    }

    @Transactional(readOnly = true)
    public List<NotificationRecord> notifications(Long incidentId) {
        mustGet(incidentId);
        return notificationRepo.findByIncidentIdOrderByIdAsc(incidentId);
    }
}
