package com.waterrepair.topology;

import com.waterrepair.common.ApiException;
import com.waterrepair.network.ImpactService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在不可变 {@link TopologyDtos.Snapshot} 上执行隔离影响分析的纯函数评估器。
 *
 * <p>算法与在线 {@link ImpactService} 完全一致：关闭阀门对应不可穿越节点，
 * 从所有 SOURCE 做可达性遍历；管段两端均不可达才判为失压。
 * 只使用 topologyStatus=CONFIRMED 的管段，UNKNOWN 支管不进入计算。</p>
 */
public final class TopologyGraphEvaluator {

    /** 候选组合最多枚举的阀门数，与在线 ImpactService 保持一致。 */
    public static final int MAX_COMBO_SIZE = 4;

    private TopologyGraphEvaluator() {
    }

    public static ImpactService.ImpactResult evaluate(TopologyDtos.Snapshot snap,
                                                      Collection<String> closedValveCodes) {
        if (snap == null) {
            throw ApiException.notFound("TOPOLOGY_SNAPSHOT_EMPTY", "拓扑版本快照不存在");
        }
        Set<String> closed = new HashSet<>();
        if (closedValveCodes != null) {
            closed.addAll(closedValveCodes);
        }

        Map<String, TopologyDtos.SnapNode> nodeByCode = new HashMap<>();
        for (TopologyDtos.SnapNode n : nz(snap.nodes())) {
            nodeByCode.put(n.code(), n);
        }
        Map<String, TopologyDtos.SnapValve> valveByCode = new HashMap<>();
        for (TopologyDtos.SnapValve v : nz(snap.valves())) {
            valveByCode.put(v.code(), v);
        }
        for (String v : closed) {
            if (!nodeByCode.containsKey(v) && !valveByCode.containsKey(v)) {
                throw ApiException.badRequest("UNKNOWN_VALVE_OR_NODE",
                        "该拓扑版本中不存在阀门/节点: " + v);
            }
        }
        Set<String> closedNodeCodes = new HashSet<>();
        for (String v : closed) {
            TopologyDtos.SnapNode n = nodeByCode.get(v);
            if (n != null && "VALVE".equals(n.type())) {
                closedNodeCodes.add(v);
            } else {
                TopologyDtos.SnapValve spec = valveByCode.get(v);
                if (spec == null) {
                    throw ApiException.badRequest("UNKNOWN_VALVE_OR_NODE",
                            "该拓扑版本中不存在阀门: " + v);
                }
                closedNodeCodes.add(spec.nodeCode());
            }
        }

        List<TopologyDtos.SnapSegment> usableSegments = nz(snap.segments()).stream()
                .filter(s -> !"UNKNOWN".equals(s.topologyStatus()))
                .toList();

        Map<String, List<TopologyDtos.SnapSegment>> incident = new LinkedHashMap<>();
        for (TopologyDtos.SnapSegment s : usableSegments) {
            incident.computeIfAbsent(s.fromNodeCode(), k -> new ArrayList<>()).add(s);
            incident.computeIfAbsent(s.toNodeCode(), k -> new ArrayList<>()).add(s);
        }

        Set<String> reachable = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        for (TopologyDtos.SnapNode n : nz(snap.nodes())) {
            if ("SOURCE".equals(n.type())) {
                reachable.add(n.code());
                stack.push(n.code());
            }
        }
        while (!stack.isEmpty()) {
            String cur = stack.pop();
            for (TopologyDtos.SnapSegment s : incident.getOrDefault(cur, List.of())) {
                String other = s.fromNodeCode().equals(cur) ? s.toNodeCode() : s.fromNodeCode();
                if (closedNodeCodes.contains(other) || closedNodeCodes.contains(cur)) {
                    continue;
                }
                if (reachable.add(other)) {
                    stack.push(other);
                }
            }
        }

        Set<String> outageSet = new HashSet<>();
        List<String> outageSegments = new ArrayList<>();
        for (TopologyDtos.SnapSegment s : usableSegments) {
            if (!reachable.contains(s.fromNodeCode()) && !reachable.contains(s.toNodeCode())) {
                outageSegments.add(s.code());
                outageSet.add(s.code());
            }
        }
        outageSegments.sort(String::compareTo);

        List<String> affectedAddresses = nz(snap.addresses()).stream()
                .filter(a -> outageSet.contains(a.segmentCode()))
                .map(TopologyDtos.SnapAddress::code)
                .sorted()
                .toList();

        List<String> unreachableNodes = nodeByCode.keySet().stream()
                .filter(c -> !reachable.contains(c))
                .sorted()
                .toList();

        return new ImpactService.ImpactResult(
                closed.stream().sorted().toList(),
                unreachableNodes,
                outageSegments,
                affectedAddresses);
    }

    /** 枚举能够隔离全部边界管段的“包含意义上最小”关阀组合。 */
    public static List<ImpactService.CandidateCombo> candidateCombos(
            TopologyDtos.Snapshot snap, List<String> boundarySegmentCodes) {
        if (boundarySegmentCodes == null || boundarySegmentCodes.isEmpty()) {
            throw ApiException.badRequest("BOUNDARY_REQUIRED",
                    "计算候选关阀组合前必须先确定事故边界管段");
        }
        Set<String> segmentCodes = nz(snap.segments()).stream()
                .map(TopologyDtos.SnapSegment::code).collect(java.util.stream.Collectors.toSet());
        for (String code : boundarySegmentCodes) {
            if (!segmentCodes.contains(code)) {
                throw ApiException.badRequest("UNKNOWN_SEGMENT", "该拓扑版本中管段不存在: " + code);
            }
        }
        Set<String> boundary = new HashSet<>(boundarySegmentCodes);
        List<String> valves = nz(snap.valves()).stream()
                .map(TopologyDtos.SnapValve::code).sorted().toList();

        List<ImpactService.CandidateCombo> matches = new ArrayList<>();
        int n = valves.size();
        for (int mask = 1; mask < (1 << n); mask++) {
            List<String> combo = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    combo.add(valves.get(i));
                }
            }
            if (combo.size() > MAX_COMBO_SIZE) {
                continue;
            }
            ImpactService.ImpactResult result = evaluate(snap, combo);
            if (!new HashSet<>(result.outageSegmentCodes()).containsAll(boundary)) {
                continue;
            }
            boolean minimal = true;
            for (String v : combo) {
                List<String> reduced = combo.stream().filter(x -> !x.equals(v)).toList();
                ImpactService.ImpactResult reducedResult = evaluate(snap, reduced);
                if (new HashSet<>(reducedResult.outageSegmentCodes()).containsAll(boundary)) {
                    minimal = false;
                    break;
                }
            }
            if (minimal) {
                matches.add(new ImpactService.CandidateCombo(List.copyOf(combo),
                        result.outageSegmentCodes(), result.affectedAddressCodes(),
                        result.affectedAddressCodes().size()));
            }
        }
        matches.sort(java.util.Comparator
                .comparingInt(ImpactService.CandidateCombo::affectedAddressCount)
                .thenComparing(c -> String.join("+", c.valveCodes())));
        return matches;
    }

    /** 校验给定关阀组合确实能隔离边界，否则 422。 */
    public static ImpactService.ImpactResult assertIsolatesBoundary(
            TopologyDtos.Snapshot snap, List<String> valveCodes,
            List<String> boundarySegmentCodes) {
        ImpactService.ImpactResult impact = evaluate(snap, valveCodes);
        Set<String> outage = new HashSet<>(impact.outageSegmentCodes());
        List<String> missing = boundarySegmentCodes.stream()
                .filter(s -> !outage.contains(s)).toList();
        if (!missing.isEmpty()) {
            throw ApiException.unprocessable("PLAN_DOES_NOT_ISOLATE",
                    "所选关阀组合在指定拓扑版本下不能隔离事故边界管段: " + missing,
                    Map.of("missingSegments", missing,
                            "outageSegments", impact.outageSegmentCodes()));
        }
        return impact;
    }

    private static <T> List<T> nz(List<T> list) {
        return list == null ? List.of() : list;
    }
}
