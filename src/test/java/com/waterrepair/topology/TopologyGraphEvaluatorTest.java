package com.waterrepair.topology;

import com.waterrepair.network.ImpactService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定 S7/S8 拓扑修订题目的关键图结论：
 * 旧图 V3=6 户；只确认 S7-S8 连通且末端未知时 [V3,V4]=12 户、未知 S9 两户不纳入；
 * 整段测完确认 S9 后 [V3,V4]=14 户。
 */
class TopologyGraphEvaluatorTest {

    private static final List<String> S3_ADDR = List.of("A06", "A07", "A08", "A09");
    private static final List<String> S7_ADDR = List.of("A14", "A15");
    private static final List<String> S8_ADDR =
            List.of("A16", "A17", "A18", "A19", "A20", "A21");
    private static final List<String> S9_ADDR = List.of("A22", "A23");

    private TopologyDtos.Snapshot baseline() {
        List<TopologyDtos.SnapNode> nodes = List.of(
                node("NSRC", "SOURCE"), node("NT0", "TEE"),
                node("NV3", "VALVE"), node("NE3", "END"), node("NE7", "END"),
                node("NV4", "VALVE"), node("NE8", "TEE"), node("NE9", "END"));
        List<TopologyDtos.SnapSegment> segs = new ArrayList<>(List.of(
                seg("M0", "NSRC", "NT0", "CONFIRMED"),
                seg("F3", "NT0", "NV3", "CONFIRMED"),
                seg("S3", "NV3", "NE3", "CONFIRMED"),
                seg("S7", "NV3", "NE7", "CONFIRMED"),
                seg("F4", "NT0", "NV4", "CONFIRMED"),
                seg("S8", "NV4", "NE8", "CONFIRMED"),
                seg("S9", "NE8", "NE9", "UNKNOWN")));
        List<TopologyDtos.SnapValve> valves = List.of(
                new TopologyDtos.SnapValve("V3", "v3", "NV3", "Q_STD", "POP-V3"),
                new TopologyDtos.SnapValve("V4", "v4", "NV4", "Q_STD", "POP-V4"));
        List<TopologyDtos.SnapAddress> addresses = new ArrayList<>();
        S3_ADDR.forEach(a -> addresses.add(addr(a, "S3")));
        S7_ADDR.forEach(a -> addresses.add(addr(a, "S7")));
        S8_ADDR.forEach(a -> addresses.add(addr(a, "S8")));
        S9_ADDR.forEach(a -> addresses.add(addr(a, "S9")));
        return new TopologyDtos.Snapshot(nodes, segs, valves, addresses, List.of());
    }

    private TopologyDtos.Snapshot partial() {
        TopologyDtos.Snapshot b = baseline();
        List<TopologyDtos.SnapSegment> segs = new ArrayList<>(b.segments());
        segs.add(seg("C78", "NE7", "NE8", "CONFIRMED"));
        TopologyDtos.UnknownBranch ub = new TopologyDtos.UnknownBranch(
                "UB-S8-FAR", "NE8", "东南", "S9", "UNKNOWN", "末端未测", S9_ADDR);
        return new TopologyDtos.Snapshot(b.nodes(), segs, b.valves(), b.addresses(), List.of(ub));
    }

    private TopologyDtos.Snapshot full() {
        TopologyDtos.Snapshot p = partial();
        List<TopologyDtos.SnapSegment> segs = p.segments().stream()
                .map(s -> "S9".equals(s.code())
                        ? new TopologyDtos.SnapSegment(s.code(), s.name(), s.fromNodeCode(),
                                s.toNodeCode(), "CONFIRMED", s.normalPressureMinMpa(),
                                s.normalPressureMaxMpa(), s.pressureTimeToleranceSeconds())
                        : s)
                .toList();
        return new TopologyDtos.Snapshot(p.nodes(), segs, p.valves(), p.addresses(), List.of());
    }

    @Test
    void baselineV3AffectsExactlySixHouseholds() {
        ImpactService.ImpactResult r =
                TopologyGraphEvaluator.evaluate(baseline(), List.of("V3"));
        List<String> expected = new ArrayList<>();
        expected.addAll(S3_ADDR);
        expected.addAll(S7_ADDR);
        assertThat(r.affectedAddressCodes()).containsExactlyElementsOf(sorted(expected));
        assertThat(r.affectedAddressCount()).isEqualTo(6);
    }

    @Test
    void baselineV4AffectsSixS8HouseholdsOnly() {
        ImpactService.ImpactResult r =
                TopologyGraphEvaluator.evaluate(baseline(), List.of("V4"));
        assertThat(r.affectedAddressCodes()).containsExactlyElementsOf(S8_ADDR);
    }

    @Test
    void partialRevisionRequiresV3AndV4AndReachesTwelve() {
        // 只关 V3：S7 会经 C78-S8-V4 反供，S7 不再失压，只剩 S3 四户
        ImpactService.ImpactResult v3Only =
                TopologyGraphEvaluator.evaluate(partial(), List.of("V3"));
        assertThat(v3Only.affectedAddressCodes()).containsExactlyElementsOf(S3_ADDR);

        ImpactService.ImpactResult both =
                TopologyGraphEvaluator.evaluate(partial(), List.of("V3", "V4"));
        List<String> expected12 = new ArrayList<>();
        expected12.addAll(S3_ADDR);
        expected12.addAll(S7_ADDR);
        expected12.addAll(S8_ADDR);
        assertThat(both.affectedAddressCodes()).containsExactlyElementsOf(sorted(expected12));
        assertThat(both.affectedAddressCount()).isEqualTo(12);
        // 未知 S9 两户绝不纳入
        assertThat(both.affectedAddressCodes()).doesNotContain("A22", "A23");

        List<ImpactService.CandidateCombo> combos =
                TopologyGraphEvaluator.candidateCombos(partial(), List.of("S7"));
        assertThat(combos).isNotEmpty();
        assertThat(combos.get(0).valveCodes()).containsExactly("V3", "V4");
        assertThat(combos.get(0).affectedAddressCount()).isEqualTo(12);
    }

    @Test
    void fullMeasurementReachesFourteenIncludingS9() {
        ImpactService.ImpactResult both =
                TopologyGraphEvaluator.evaluate(full(), List.of("V3", "V4"));
        assertThat(both.affectedAddressCount()).isEqualTo(14);
        assertThat(both.affectedAddressCodes()).contains("A22", "A23");

        List<ImpactService.CandidateCombo> combos =
                TopologyGraphEvaluator.candidateCombos(full(), List.of("S7"));
        assertThat(combos.get(0).affectedAddressCount()).isEqualTo(14);
    }

    private static TopologyDtos.SnapNode node(String code, String type) {
        return new TopologyDtos.SnapNode(code, code, type, 0.0, 0.0);
    }

    private static TopologyDtos.SnapSegment seg(String code, String from, String to, String status) {
        return new TopologyDtos.SnapSegment(code, code, from, to, status, 0.2, 0.4, 600);
    }

    private static TopologyDtos.SnapAddress addr(String code, String segment) {
        return new TopologyDtos.SnapAddress(code, code, segment, null, 0.0, 0.0, List.of());
    }

    private static List<String> sorted(List<String> in) {
        return in.stream().sorted().toList();
    }
}
