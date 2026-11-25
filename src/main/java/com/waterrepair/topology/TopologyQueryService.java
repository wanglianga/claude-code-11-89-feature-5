package com.waterrepair.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waterrepair.common.ApiException;
import com.waterrepair.network.AddressPointRepository;
import com.waterrepair.network.ImpactService;
import com.waterrepair.network.NetworkNodeRepository;
import com.waterrepair.network.PipeSegmentRepository;
import com.waterrepair.network.ValveSpecRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 拓扑版本读取、快照构建与版本感知的影响计算。
 */
@Service
public class TopologyQueryService {

    /** 随题播种的竣工图基线版本（旧图：S7 为 V3 后的盲端，无 S7-S8 连通管）。 */
    public static final String BASELINE_VERSION = "TOPO-V1";

    private final TopologyVersionRepository versionRepo;
    private final NetworkNodeRepository nodeRepo;
    private final PipeSegmentRepository segmentRepo;
    private final ValveSpecRepository valveRepo;
    private final AddressPointRepository addressRepo;
    private final ObjectMapper objectMapper;

    public TopologyQueryService(TopologyVersionRepository versionRepo,
                               NetworkNodeRepository nodeRepo,
                               PipeSegmentRepository segmentRepo,
                               ValveSpecRepository valveRepo,
                               AddressPointRepository addressRepo,
                               ObjectMapper objectMapper) {
        this.versionRepo = versionRepo;
        this.nodeRepo = nodeRepo;
        this.segmentRepo = segmentRepo;
        this.valveRepo = valveRepo;
        this.addressRepo = addressRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<TopologyVersion> listVersions() {
        return versionRepo.findAllByOrderByVersionNoAscIdAsc();
    }

    @Transactional(readOnly = true)
    public TopologyVersion mustVersion(String versionCode) {
        return versionRepo.findByVersionCode(versionCode)
                .orElseThrow(() -> ApiException.notFound("TOPOLOGY_VERSION_NOT_FOUND",
                        "拓扑版本不存在: " + versionCode));
    }

    @Transactional(readOnly = true)
    public TopologyVersion currentActiveVersion() {
        return versionRepo.findFirstByStatusOrderByVersionNoDesc("ACTIVE")
                .orElseGet(() -> versionRepo.findByVersionCode(BASELINE_VERSION)
                        .orElseThrow(() -> ApiException.notFound(
                                "TOPOLOGY_VERSION_NOT_FOUND", "尚无任何已发布拓扑版本")));
    }

    public String currentActiveVersionCode() {
        try {
            return currentActiveVersion().getVersionCode();
        } catch (Exception ex) {
            return BASELINE_VERSION;
        }
    }

    /** 从当前在线管网表构建完整快照（播种基线时使用）。 */
    @Transactional(readOnly = true)
    public TopologyDtos.Snapshot buildLiveSnapshot() {
        var nodes = nodeRepo.findAll().stream()
                .map(n -> new TopologyDtos.SnapNode(n.getCode(), n.getName(), n.getType(),
                        n.getLng(), n.getLat()))
                .toList();
        var segments = segmentRepo.findAll().stream()
                .map(s -> new TopologyDtos.SnapSegment(s.getCode(), s.getName(),
                        s.getFromNodeCode(), s.getToNodeCode(), s.getTopologyStatus(),
                        s.getNormalPressureMinMpa(), s.getNormalPressureMaxMpa(),
                        s.getPressureTimeToleranceSeconds()))
                .toList();
        var valves = valveRepo.findAll().stream()
                .map(v -> new TopologyDtos.SnapValve(v.getCode(), v.getName(), v.getNodeCode(),
                        v.getRequiredQualification(), v.getExclusivePermitCode()))
                .toList();
        var addresses = addressRepo.findAll().stream()
                .map(a -> new TopologyDtos.SnapAddress(a.getCode(), a.getDisplayAddress(),
                        a.getSegmentCode(), a.getBuilding(), a.getLng(), a.getLat(), a.getAliases()))
                .toList();
        return new TopologyDtos.Snapshot(nodes, segments, valves, addresses, List.of());
    }

    public String writeSnapshot(TopologyDtos.Snapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new IllegalStateException("拓扑快照序列化失败: " + ex.getMessage(), ex);
        }
    }

    public TopologyDtos.Snapshot readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, TopologyDtos.Snapshot.class);
        } catch (Exception ex) {
            throw new IllegalStateException("拓扑快照反序列化失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 取指定版本快照；versionCode 为空时取当前 ACTIVE 版本。
     */
    @Transactional(readOnly = true)
    public TopologyDtos.Snapshot snapshotOf(String versionCode) {
        TopologyVersion version = (versionCode == null || versionCode.isBlank())
                ? currentActiveVersion()
                : mustVersion(versionCode);
        return readSnapshot(version.getSnapshotJson());
    }

    @Transactional(readOnly = true)
    public ImpactService.ImpactResult evaluate(String versionCode, List<String> valveCodes) {
        return TopologyGraphEvaluator.evaluate(snapshotOf(versionCode), valveCodes);
    }

    @Transactional(readOnly = true)
    public List<ImpactService.CandidateCombo> candidates(String versionCode,
                                                         List<String> boundarySegmentCodes) {
        return TopologyGraphEvaluator.candidateCombos(
                snapshotOf(versionCode), boundarySegmentCodes);
    }

    @Transactional(readOnly = true)
    public ImpactService.ImpactResult assertIsolates(String versionCode,
                                                     List<String> valveCodes,
                                                     List<String> boundarySegmentCodes) {
        return TopologyGraphEvaluator.assertIsolatesBoundary(
                snapshotOf(versionCode), valveCodes, boundarySegmentCodes);
    }

    /** 版本列表/详情视图：实体元数据 + 解析后的快照。 */
    public record VersionView(Long id, String versionCode, Integer versionNo, String status,
                              String revisionMode, String parentVersionCode, String changeSummary,
                              List<String> evidenceCodes, String confirmerName, String publishedBy,
                              java.time.OffsetDateTime publishedAt,
                              java.time.OffsetDateTime effectiveAt,
                              TopologyDtos.Snapshot snapshot) {
    }

    @Transactional(readOnly = true)
    public VersionView toView(TopologyVersion v) {
        return new VersionView(v.getId(), v.getVersionCode(), v.getVersionNo(), v.getStatus(),
                v.getRevisionMode(), v.getParentVersionCode(), v.getChangeSummary(),
                List.copyOf(v.getEvidenceCodes()), v.getConfirmerName(), v.getPublishedBy(),
                v.getPublishedAt(), v.getEffectiveAt(), readSnapshot(v.getSnapshotJson()));
    }

    @Transactional(readOnly = true)
    public VersionView versionView(String versionCode) {
        return toView(mustVersion(versionCode));
    }

    @Transactional(readOnly = true)
    public List<VersionView> versionViews() {
        return listVersions().stream().map(this::toView).toList();
    }
}
