package com.waterrepair.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waterrepair.auth.AuthService;
import com.waterrepair.auth.UserAccount;
import com.waterrepair.auth.UserAccountRepository;
import com.waterrepair.crew.Crew;
import com.waterrepair.crew.CrewRepository;
import com.waterrepair.crew.OperationPermit;
import com.waterrepair.crew.OperationPermitRepository;
import com.waterrepair.crew.ToolItem;
import com.waterrepair.crew.ToolItemRepository;
import com.waterrepair.incident.Incident;
import com.waterrepair.incident.IncidentRepository;
import com.waterrepair.incident.IsolationPlan;
import com.waterrepair.incident.IsolationPlanRepository;
import com.waterrepair.incident.NotificationRecord;
import com.waterrepair.incident.NotificationRecordRepository;
import com.waterrepair.crew.Vehicle;
import com.waterrepair.crew.VehicleRepository;
import com.waterrepair.network.AddressPoint;
import com.waterrepair.network.AddressPointRepository;
import com.waterrepair.network.GeoLandmark;
import com.waterrepair.network.GeoLandmarkRepository;
import com.waterrepair.network.NetworkNode;
import com.waterrepair.network.NetworkNodeRepository;
import com.waterrepair.network.PipeSegment;
import com.waterrepair.network.PipeSegmentRepository;
import com.waterrepair.network.PressureDevice;
import com.waterrepair.network.PressureDeviceRepository;
import com.waterrepair.network.PressureReading;
import com.waterrepair.network.PressureReadingRepository;
import com.waterrepair.network.ValveSpec;
import com.waterrepair.network.ValveSpecRepository;
import com.waterrepair.network.ImpactService;
import com.waterrepair.topology.TopologyQueryService;
import com.waterrepair.topology.TopologyVersion;
import com.waterrepair.topology.TopologyVersionRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 载入随项目提供的模拟管网/班组/账号样本；reset 时清空 public 模式全部业务表后重播。
 */
@Service
public class SeedService {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;
    private final UserAccountRepository userRepo;
    private final NetworkNodeRepository nodeRepo;
    private final PipeSegmentRepository segmentRepo;
    private final ValveSpecRepository valveRepo;
    private final AddressPointRepository addressRepo;
    private final GeoLandmarkRepository landmarkRepo;
    private final PressureDeviceRepository deviceRepo;
    private final PressureReadingRepository readingRepo;
    private final CrewRepository crewRepo;
    private final VehicleRepository vehicleRepo;
    private final ToolItemRepository toolRepo;
    private final OperationPermitRepository permitRepo;
    private final TopologyVersionRepository topologyVersionRepo;
    private final TopologyQueryService topologyQuery;
    private final ImpactService impactService;
    private final IncidentRepository incidentRepo;
    private final IsolationPlanRepository planRepo;
    private final NotificationRecordRepository notificationRepo;

    public SeedService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate, AuthService authService,
                       UserAccountRepository userRepo, NetworkNodeRepository nodeRepo,
                       PipeSegmentRepository segmentRepo, ValveSpecRepository valveRepo,
                       AddressPointRepository addressRepo, GeoLandmarkRepository landmarkRepo,
                       PressureDeviceRepository deviceRepo, PressureReadingRepository readingRepo,
                       CrewRepository crewRepo, VehicleRepository vehicleRepo,
                       ToolItemRepository toolRepo, OperationPermitRepository permitRepo,
                       TopologyVersionRepository topologyVersionRepo,
                       TopologyQueryService topologyQuery,
                       ImpactService impactService,
                       IncidentRepository incidentRepo,
                       IsolationPlanRepository planRepo,
                       NotificationRecordRepository notificationRepo) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
        this.userRepo = userRepo;
        this.nodeRepo = nodeRepo;
        this.segmentRepo = segmentRepo;
        this.valveRepo = valveRepo;
        this.addressRepo = addressRepo;
        this.landmarkRepo = landmarkRepo;
        this.deviceRepo = deviceRepo;
        this.readingRepo = readingRepo;
        this.crewRepo = crewRepo;
        this.vehicleRepo = vehicleRepo;
        this.toolRepo = toolRepo;
        this.permitRepo = permitRepo;
        this.topologyVersionRepo = topologyVersionRepo;
        this.topologyQuery = topologyQuery;
        this.impactService = impactService;
        this.incidentRepo = incidentRepo;
        this.planRepo = planRepo;
        this.notificationRepo = notificationRepo;
    }

    @Transactional
    public void seedIfEmpty() {
        if (userRepo.count() > 0) {
            return;
        }
        doSeed();
    }

    @Transactional
    public Map<String, Object> reset() {
        List<String> tables = jdbcTemplate.queryForList(
                "select tablename from pg_tables where schemaname = 'public'", String.class);
        if (!tables.isEmpty()) {
            String joined = tables.stream().map(t -> "public." + t)
                    .reduce((a, b) -> a + "," + b).orElseThrow();
            jdbcTemplate.execute("truncate table " + joined + " restart identity cascade");
        }
        doSeed();
        return counts();
    }

    private void doSeed() {
        SeedDtos.NetworkSeed network = readJson("samples/network.json", SeedDtos.NetworkSeed.class);
        for (SeedDtos.NodeDto n : network.nodes()) {
            nodeRepo.save(new NetworkNode(n.code(), n.name(), n.type(), n.lng(), n.lat()));
        }
        for (SeedDtos.SegmentDto s : network.segments()) {
            PipeSegment segment = new PipeSegment(s.code(), s.name(), s.from(), s.to(),
                    s.pMin(), s.pMax(), s.tolSec());
            if (s.topologyStatus() != null && !s.topologyStatus().isBlank()) {
                segment.setTopologyStatus(s.topologyStatus());
            }
            segmentRepo.save(segment);
        }
        for (SeedDtos.ValveDto v : network.valves()) {
            valveRepo.save(new ValveSpec(v.code(), v.name(), v.nodeCode(),
                    v.qualification(), v.permit()));
        }
        for (SeedDtos.AddressDto a : network.addresses()) {
            addressRepo.save(new AddressPoint(a.code(), a.address(), a.segment(),
                    a.building(), a.lng(), a.lat(), a.aliases()));
        }
        for (SeedDtos.LandmarkDto l : network.landmarks()) {
            landmarkRepo.save(new GeoLandmark(l.code(), l.name(), l.lng(), l.lat(),
                    l.candidateSegments()));
        }
        for (SeedDtos.DeviceDto d : network.devices()) {
            deviceRepo.save(new PressureDevice(d.deviceNo(), d.name(), d.segment()));
        }
        for (SeedDtos.ReadingDto r : network.readings()) {
            readingRepo.save(new PressureReading(r.code(), r.deviceNo(), r.segment(),
                    r.pressureMpa(), r.sampledAt(),
                    r.clockOffsetSeconds() == null ? 0 : r.clockOffsetSeconds(), r.note()));
        }

        SeedDtos.CrewsSeed crews = readJson("samples/crews.json", SeedDtos.CrewsSeed.class);
        for (SeedDtos.VehicleDto v : crews.vehicles()) {
            vehicleRepo.save(new Vehicle(v.code(), v.name(), v.plateNo(),
                    !Boolean.FALSE.equals(v.available()), v.unavailableReason()));
        }
        for (SeedDtos.ToolDto t : crews.tools()) {
            toolRepo.save(new ToolItem(t.code(), t.name(),
                    !Boolean.FALSE.equals(t.available()), t.unavailableReason()));
        }
        for (SeedDtos.PermitDto p : crews.permits()) {
            permitRepo.save(new OperationPermit(p.code(), p.name(), p.valveCode()));
        }
        for (SeedDtos.CrewDto c : crews.crews()) {
            crewRepo.save(new Crew(c.code(), c.name(),
                    LocalTime.parse(c.shiftStart()), LocalTime.parse(c.shiftEnd()),
                    c.contactPhone(), !Boolean.FALSE.equals(c.active()),
                    c.primaryVehicleCode(), c.qualificationCodes(), c.standardToolCodes()));
        }

        SeedDtos.AccountsSeed accounts = readJson("samples/accounts.json", SeedDtos.AccountsSeed.class);
        for (SeedDtos.AccountDto a : accounts.accounts()) {
            userRepo.save(new UserAccount(a.username(), authService.encode(a.password()),
                    a.displayName(), a.role(), a.crewCode()));
        }

        seedTopologyBaselineAndCases();
    }

    /**
     * 固化竣工图基线拓扑版本 TOPO-V1，并播种 S7/S8 拓扑修订题目的
     * 两张引用旧图的开放工单与一个已结案旧事故（含旧计划快照与已通知对象）。
     */
    private void seedTopologyBaselineAndCases() {
        TopologyVersion baseline = new TopologyVersion();
        baseline.setVersionCode(TopologyQueryService.BASELINE_VERSION);
        baseline.setVersionNo(1);
        baseline.setStatus("ACTIVE");
        baseline.setRevisionMode("BASELINE");
        baseline.setChangeSummary("随题竣工图基线：S7 为 V3 后的盲端，S8 由 V4 单独供水，S9 为未测状态");
        baseline.setPublishedBy("system");
        baseline.setConfirmerName("竣工图库");
        OffsetDateTime baselineTime = OffsetDateTime.parse("2026-11-20T09:00:00+08:00");
        baseline.setPublishedAt(baselineTime);
        baseline.setEffectiveAt(baselineTime);
        baseline.setCreatedAt(baselineTime);
        baseline.setSnapshotJson(topologyQuery.writeSnapshot(topologyQuery.buildLiveSnapshot()));
        topologyVersionRepo.save(baseline);

        SeedDtos.TopologyCaseSeed cases =
                readJson("samples/topology-case.json", SeedDtos.TopologyCaseSeed.class);
        for (SeedDtos.CaseIncidentDto c : cases.cases()) {
            OffsetDateTime created = c.createdAt() == null ? OffsetDateTime.now() : c.createdAt();
            Incident inc = new Incident();
            inc.setCode(c.code());
            inc.setTitle(c.title());
            inc.setStatus(c.status());
            inc.setBoundarySegmentCodes(c.boundarySegmentCodes());
            inc.setCurrentCycle(1);
            inc.setRevision(1);
            inc.setTopologyVersionCode(TopologyQueryService.BASELINE_VERSION);
            inc.setConfirmedBy("dispatcher");
            inc.setConfirmationNote(c.note());
            inc.setConfirmedAt(c.confirmedAt() == null ? created.plusMinutes(8) : c.confirmedAt());
            inc.setCreatedAt(created);
            if ("CLOSED".equals(c.status())) {
                inc.setClosedAt(c.closedAt() == null ? created.plusHours(6) : c.closedAt());
            }
            inc = incidentRepo.saveAndFlush(inc);

            ImpactService.ImpactResult impact = impactService.evaluate(c.valveCodes());
            IsolationPlan plan = new IsolationPlan();
            plan.setIncidentId(inc.getId());
            plan.setVersionNo(1);
            plan.setStatus("ACTIVE");
            plan.setValveCodes(c.valveCodes().stream().sorted().distinct().toList());
            plan.setOutageSegmentCodes(impact.outageSegmentCodes());
            plan.setAffectedAddressCodes(impact.affectedAddressCodes());
            plan.setCreatedBy("dispatcher");
            plan.setCreatedAt(created.plusMinutes(10));
            plan = planRepo.saveAndFlush(plan);

            if (Boolean.TRUE.equals(c.sendNotify())) {
                int k = 0;
                for (String addressCode : impact.affectedAddressCodes()) {
                    NotificationRecord n = new NotificationRecord();
                    n.setIncidentId(inc.getId());
                    n.setPlanId(plan.getId());
                    n.setAddressCode(addressCode);
                    n.setKind("INITIAL");
                    n.setStatus("SENT");
                    n.setChannel("SIMULATED_SMS");
                    n.setContent("【供水抢修停水通知】" + inc.getCode()
                            + " 需关闭阀门 " + plan.getValveCodes() + " 抢修，您的地址 "
                            + addressCode + " 位于停水范围，请提前储水。");
                    n.setSentAt(created.plusMinutes(15 + k));
                    notificationRepo.save(n);
                    k++;
                }
            }
        }
    }

    private <T> T readJson(String path, Class<T> type) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (Exception ex) {
            throw new IllegalStateException("无法读取样本文件 " + path + ": " + ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> counts() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accounts", userRepo.count());
        m.put("nodes", nodeRepo.count());
        m.put("segments", segmentRepo.count());
        m.put("valves", valveRepo.count());
        m.put("addresses", addressRepo.count());
        m.put("landmarks", landmarkRepo.count());
        m.put("devices", deviceRepo.count());
        m.put("readings", readingRepo.count());
        m.put("crews", crewRepo.count());
        m.put("vehicles", vehicleRepo.count());
        m.put("tools", toolRepo.count());
        m.put("permits", permitRepo.count());
        return m;
    }
}
