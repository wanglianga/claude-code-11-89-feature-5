package com.waterrepair.network;

import com.waterrepair.topology.TopologyQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "02-管网图与影响分析")
@RestController
@RequestMapping("/api/network")
public class NetworkController {

    private final NetworkNodeRepository nodeRepo;
    private final PipeSegmentRepository segmentRepo;
    private final ValveSpecRepository valveRepo;
    private final AddressPointRepository addressRepo;
    private final GeoLandmarkRepository landmarkRepo;
    private final PressureDeviceRepository deviceRepo;
    private final PressureReadingRepository readingRepo;
    private final ImpactService impactService;
    private final TopologyQueryService topologyQuery;

    /** 兼容既有 8 参构造器；topologyVersion 参数将不生效。 */
    public NetworkController(NetworkNodeRepository nodeRepo,
                             PipeSegmentRepository segmentRepo,
                             ValveSpecRepository valveRepo,
                             AddressPointRepository addressRepo,
                             GeoLandmarkRepository landmarkRepo,
                             PressureDeviceRepository deviceRepo,
                             PressureReadingRepository readingRepo,
                             ImpactService impactService) {
        this(nodeRepo, segmentRepo, valveRepo, addressRepo, landmarkRepo, deviceRepo,
                readingRepo, impactService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NetworkController(NetworkNodeRepository nodeRepo,
                             PipeSegmentRepository segmentRepo,
                             ValveSpecRepository valveRepo,
                             AddressPointRepository addressRepo,
                             GeoLandmarkRepository landmarkRepo,
                             PressureDeviceRepository deviceRepo,
                             PressureReadingRepository readingRepo,
                             ImpactService impactService,
                             TopologyQueryService topologyQuery) {
        this.nodeRepo = nodeRepo;
        this.segmentRepo = segmentRepo;
        this.valveRepo = valveRepo;
        this.addressRepo = addressRepo;
        this.landmarkRepo = landmarkRepo;
        this.deviceRepo = deviceRepo;
        this.readingRepo = readingRepo;
        this.impactService = impactService;
        this.topologyQuery = topologyQuery;
    }

    @Operation(summary = "全部管网节点（水源/三通/阀门/末端）")
    @GetMapping("/nodes")
    public List<NetworkNode> nodes() {
        return nodeRepo.findAll();
    }

    @Operation(summary = "全部管段及复压正常区间")
    @GetMapping("/segments")
    public List<PipeSegment> segments() {
        return segmentRepo.findAll();
    }

    @Operation(summary = "全部阀门台账与所需许可")
    @GetMapping("/valves")
    public List<ValveSpec> valves() {
        return valveRepo.findAll();
    }

    @Operation(summary = "全部地址点及其管段映射")
    @GetMapping("/addresses")
    public List<AddressPoint> addresses() {
        return addressRepo.findAll();
    }

    @Operation(summary = "相邻管段之间的地标/路口（多候选段）")
    @GetMapping("/landmarks")
    public List<GeoLandmark> landmarks() {
        return landmarkRepo.findAll();
    }

    @Operation(summary = "压力设备台账")
    @GetMapping("/pressure-devices")
    public List<PressureDevice> devices() {
        return deviceRepo.findAll();
    }

    @Operation(summary = "全部压力读数（含设备号、采样时刻、校时偏差）")
    @GetMapping("/pressure-readings")
    public List<PressureReading> readings() {
        return readingRepo.findAll();
    }

    public record ImpactRequest(List<String> valveCodes, String topologyVersion) {
    }

    @Operation(summary = "给定关闭阀门集合，计算失压管段与精确受影响地址；可传 topologyVersion 按历史版本复算")
    @PostMapping("/impact")
    public ImpactService.ImpactResult impact(@RequestBody ImpactRequest request) {
        if (topologyQuery != null
                && request.topologyVersion() != null && !request.topologyVersion().isBlank()) {
            return topologyQuery.evaluate(request.topologyVersion(), request.valveCodes());
        }
        return impactService.evaluate(request.valveCodes());
    }

    public record CandidateRequest(List<String> boundarySegmentCodes, String topologyVersion) {
    }

    @Operation(summary = "枚举隔离给定边界管段的最小候选关阀组合及各自影响集合；可传 topologyVersion")
    @PostMapping("/candidate-isolations")
    public List<ImpactService.CandidateCombo> candidates(@RequestBody CandidateRequest request) {
        if (topologyQuery != null
                && request.topologyVersion() != null && !request.topologyVersion().isBlank()) {
            return topologyQuery.candidates(request.topologyVersion(), request.boundarySegmentCodes());
        }
        return impactService.candidateCombos(request.boundarySegmentCodes());
    }

    @Operation(summary = "管网基础数据总览")
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return Map.of(
                "nodeCount", nodeRepo.count(),
                "segmentCount", segmentRepo.count(),
                "valveCount", valveRepo.count(),
                "addressCount", addressRepo.count(),
                "landmarkCount", landmarkRepo.count(),
                "deviceCount", deviceRepo.count(),
                "readingCount", readingRepo.count()
        );
    }
}
