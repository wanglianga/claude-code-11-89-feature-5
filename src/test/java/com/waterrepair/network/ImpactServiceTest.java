package com.waterrepair.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 不依赖 Spring 容器/数据库的图算法测试，固定样例拓扑的关键结论：
 * V2 只停 S2(A01-A05)，V1 停 S1+S2(A01-A05,A10-A13)，V3 只停 S3(A06-A09)。
 */
class ImpactServiceTest {

    private final NetworkNodeRepository nodeRepo = mock(NetworkNodeRepository.class);
    private final PipeSegmentRepository segmentRepo = mock(PipeSegmentRepository.class);
    private final ValveSpecRepository valveRepo = mock(ValveSpecRepository.class);
    private final AddressPointRepository addressRepo = mock(AddressPointRepository.class);

    private final ImpactService impactService =
            new ImpactService(nodeRepo, segmentRepo, valveRepo, addressRepo);

    private void stubSampleGraph() {
        when(nodeRepo.findAll()).thenReturn(List.of(
                new NetworkNode("NSRC", "src", "SOURCE", 0.0, 0.0),
                new NetworkNode("NT0", "t0", "TEE", 0.0, 0.0),
                new NetworkNode("NV1", "v1", "VALVE", 0.0, 0.0),
                new NetworkNode("NT1", "t1", "TEE", 0.0, 0.0),
                new NetworkNode("NE1", "e1", "END", 0.0, 0.0),
                new NetworkNode("NV2", "v2", "VALVE", 0.0, 0.0),
                new NetworkNode("NE2", "e2", "END", 0.0, 0.0),
                new NetworkNode("NV3", "v3", "VALVE", 0.0, 0.0),
                new NetworkNode("NE3", "e3", "END", 0.0, 0.0)));
        List<PipeSegment> segments = List.of(
                new PipeSegment("M0", "m", "NSRC", "NT0", 0.2, 0.46, 600),
                new PipeSegment("F1", "f1", "NT0", "NV1", 0.2, 0.45, 600),
                new PipeSegment("F1D", "f1d", "NV1", "NT1", 0.2, 0.45, 600),
                new PipeSegment("S1", "s1", "NT1", "NE1", 0.2, 0.42, 600),
                new PipeSegment("F2", "f2", "NT1", "NV2", 0.22, 0.45, 600),
                new PipeSegment("S2", "s2", "NV2", "NE2", 0.22, 0.45, 600),
                new PipeSegment("F3", "f3", "NT0", "NV3", 0.18, 0.4, 600),
                new PipeSegment("S3", "s3", "NV3", "NE3", 0.18, 0.4, 600));
        when(segmentRepo.findAll()).thenReturn(segments);
        segments.forEach(s -> when(segmentRepo.findById(s.getCode())).thenReturn(java.util.Optional.of(s)));
        List<ValveSpec> valves = List.of(
                new ValveSpec("V1", "v1", "NV1", "Q_V1", "POP-V1"),
                new ValveSpec("V2", "v2", "NV2", "Q_STD", "POP-V2"),
                new ValveSpec("V3", "v3", "NV3", "Q_STD", "POP-V3"));
        when(valveRepo.findAll()).thenReturn(valves);
        valves.forEach(v -> when(valveRepo.findById(v.getCode())).thenReturn(java.util.Optional.of(v)));
        when(addressRepo.findAll()).thenReturn(List.of(
                addr("A01", "S2"), addr("A02", "S2"), addr("A03", "S2"),
                addr("A04", "S2"), addr("A05", "S2"),
                addr("A06", "S3"), addr("A07", "S3"), addr("A08", "S3"), addr("A09", "S3"),
                addr("A10", "S1"), addr("A11", "S1"), addr("A12", "S1"), addr("A13", "S1")));
    }

    private AddressPoint addr(String code, String seg) {
        return new AddressPoint(code, code, seg, null, 0.0, 0.0, List.of());
    }

    @Test
    void v2IsolatesExactlyS2Addresses() {
        stubSampleGraph();
        ImpactService.ImpactResult r = impactService.evaluate(List.of("V2"));
        assertThat(r.affectedAddressCodes()).containsExactly("A01", "A02", "A03", "A04", "A05");
        assertThat(r.outageSegmentCodes()).contains("S2");
    }

    @Test
    void v1IsolatesS1AndS2Addresses() {
        stubSampleGraph();
        ImpactService.ImpactResult r = impactService.evaluate(List.of("V1"));
        assertThat(r.affectedAddressCodes()).containsExactly(
                "A01", "A02", "A03", "A04", "A05", "A10", "A11", "A12", "A13");
        assertThat(r.outageSegmentCodes()).contains("S1", "S2");
        assertThat(r.outageSegmentCodes()).doesNotContain("S3");
    }

    @Test
    void candidateCombosForS2AreV2AndV1() {
        stubSampleGraph();
        List<ImpactService.CandidateCombo> combos = impactService.candidateCombos(List.of("S2"));
        List<String> keys = combos.stream().map(c -> String.join("+", c.valveCodes())).toList();
        assertThat(keys).containsExactly("V2", "V1"); // 按影响户数排序：V2(5) 在 V1(9) 前
    }

    @Test
    void candidateCombosForS3OnlyV3() {
        stubSampleGraph();
        List<ImpactService.CandidateCombo> combos = impactService.candidateCombos(List.of("S3"));
        assertThat(combos).hasSize(1);
        assertThat(combos.get(0).valveCodes()).containsExactly("V3");
        assertThat(combos.get(0).affectedAddressCodes())
                .containsExactly("A06", "A07", "A08", "A09");
    }
}
