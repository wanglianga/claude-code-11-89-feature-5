package com.waterrepair.network;

import com.waterrepair.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管网隔离分析。
 *
 * <p>把阀门视为图上不可穿越的节点：关闭阀门后从所有水源节点做可达性遍历；
 * 若一条管段的两个端点都不可达，则该管段失压（停水），其上地址全部受影响。</p>
 */
@Service
public class ImpactService {

    /** 候选组合最多枚举的阀门数，样例管网只有 3 个阀，留足扩展余量。 */
    private static final int MAX_COMBO_SIZE = 4;

    private final NetworkNodeRepository nodeRepo;
    private final PipeSegmentRepository segmentRepo;
    private final ValveSpecRepository valveRepo;
    private final AddressPointRepository addressRepo;

    public ImpactService(NetworkNodeRepository nodeRepo,
                          PipeSegmentRepository segmentRepo,
                          ValveSpecRepository valveRepo,
                          AddressPointRepository addressRepo) {
        this.nodeRepo = nodeRepo;
        this.segmentRepo = segmentRepo;
        this.valveRepo = valveRepo;
        this.addressRepo = addressRepo;
    }

    public record ImpactResult(List<String> closedValveCodes,
                               List<String> unreachableNodeCodes,
                               List<String> outageSegmentCodes,
                               List<String> affectedAddressCodes) {
        public int affectedAddressCount() {
            return affectedAddressCodes.size();
        }
    }

    public record CandidateCombo(List<String> valveCodes,
                                 List<String> outageSegmentCodes,
                                 List<String> affectedAddressCodes,
                                 int affectedAddressCount) {
    }

    @Transactional(readOnly = true)
    public ImpactResult evaluate(Collection<String> closedValveCodes) {
        List<NetworkNode> nodes = nodeRepo.findAll();
        // UNKNOWN 管段（相邻未测支管）不进入权威连通计算，避免把未确认住户纳入错误区域
        List<PipeSegment> segments = segmentRepo.findAll().stream()
                .filter(s -> !"UNKNOWN".equals(s.getTopologyStatus()))
                .toList();
        List<AddressPoint> addresses = addressRepo.findAll();

        Set<String> closed = new HashSet<>();
        if (closedValveCodes != null) {
            closed.addAll(closedValveCodes);
        }
        Map<String, NetworkNode> nodeByCode = new HashMap<>();
        for (NetworkNode n : nodes) {
            nodeByCode.put(n.getCode(), n);
        }
        for (String v : closed) {
            NetworkNode n = nodeByCode.get(v);
            if (n == null) {
                // 也允许直接传 ValveSpec 编码（样例中阀门编码与节点编码分别为 V1 / NV1）
                ValveSpec spec = valveRepo.findById(v).orElse(null);
                if (spec == null || !nodeByCode.containsKey(spec.getNodeCode())) {
                    throw ApiException.badRequest("UNKNOWN_VALVE_OR_NODE", "管网中不存在阀门/节点: " + v);
                }
            }
        }
        // ValveSpec 编码 -> 节点编码，遍历时关闭的是节点
        Set<String> closedNodeCodes = new HashSet<>();
        for (String v : closed) {
            NetworkNode n = nodeByCode.get(v);
            if (n != null && "VALVE".equals(n.getType())) {
                closedNodeCodes.add(v);
            } else {
                ValveSpec spec = valveRepo.findById(v).orElseThrow();
                closedNodeCodes.add(spec.getNodeCode());
            }
        }

        Map<String, List<PipeSegment>> incident = new LinkedHashMap<>();
        for (PipeSegment s : segments) {
            incident.computeIfAbsent(s.getFromNodeCode(), k -> new ArrayList<>()).add(s);
            incident.computeIfAbsent(s.getToNodeCode(), k -> new ArrayList<>()).add(s);
        }

        Set<String> reachable = new HashSet<>();
        Deque<String> stack = new java.util.ArrayDeque<>();
        for (NetworkNode n : nodes) {
            if ("SOURCE".equals(n.getType())) {
                reachable.add(n.getCode());
                stack.push(n.getCode());
            }
        }
        while (!stack.isEmpty()) {
            String cur = stack.pop();
            for (PipeSegment s : incident.getOrDefault(cur, List.of())) {
                String other = s.getFromNodeCode().equals(cur) ? s.getToNodeCode() : s.getFromNodeCode();
                if (closedNodeCodes.contains(other) || closedNodeCodes.contains(cur)) {
                    continue; // 关闭的阀门不可进入/穿越
                }
                if (reachable.add(other)) {
                    stack.push(other);
                }
            }
        }

        List<String> outageSegments = segments.stream()
                .filter(s -> !reachable.contains(s.getFromNodeCode())
                        && !reachable.contains(s.getToNodeCode()))
                .map(PipeSegment::getCode)
                .sorted()
                .toList();
        Set<String> outageSet = new HashSet<>(outageSegments);

        List<String> affectedAddresses = addresses.stream()
                .filter(a -> outageSet.contains(a.getSegmentCode()))
                .map(AddressPoint::getCode)
                .sorted()
                .toList();

        List<String> unreachableNodes = nodeByCode.keySet().stream()
                .filter(c -> !reachable.contains(c))
                .sorted()
                .toList();

        return new ImpactResult(closed.stream().sorted().toList(), unreachableNodes,
                outageSegments, affectedAddresses);
    }

    /**
     * 枚举能够隔离全部边界管段的“包含意义上最小”关阀组合。
     */
    @Transactional(readOnly = true)
    public List<CandidateCombo> candidateCombos(List<String> boundarySegmentCodes) {
        if (boundarySegmentCodes == null || boundarySegmentCodes.isEmpty()) {
            throw ApiException.badRequest("BOUNDARY_REQUIRED", "计算候选关阀组合前必须先确定事故边界管段");
        }
        Set<String> boundary = new HashSet<>(boundarySegmentCodes);
        // 校验管段存在
        for (String code : boundary) {
            if (segmentRepo.findById(code).isEmpty()) {
                throw ApiException.badRequest("UNKNOWN_SEGMENT", "管段不存在: " + code);
            }
        }

        List<String> valves = valveRepo.findAll().stream()
                .map(ValveSpec::getCode).sorted().toList();

        List<CandidateCombo> matches = new ArrayList<>();
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
            ImpactResult result = evaluate(combo);
            if (!new HashSet<>(result.outageSegmentCodes()).containsAll(boundary)) {
                continue;
            }
            boolean minimal = true;
            for (String v : combo) {
                List<String> reduced = combo.stream().filter(x -> !x.equals(v)).toList();
                // 单阀组合删去后为空集：空集不可能隔离边界，故单阀天然最小
                ImpactResult reducedResult = evaluate(reduced);
                if (new HashSet<>(reducedResult.outageSegmentCodes()).containsAll(boundary)) {
                    minimal = false;
                    break;
                }
            }
            if (minimal) {
                matches.add(new CandidateCombo(List.copyOf(combo), result.outageSegmentCodes(),
                        result.affectedAddressCodes(), result.affectedAddressCodes().size()));
            }
        }
        matches.sort(Comparator.comparingInt(CandidateCombo::affectedAddressCount)
                .thenComparing(c -> String.join("+", c.valveCodes())));
        return matches;
    }

    /** 校验给定关阀组合确实能隔离边界，否则 422。 */
    public ImpactResult assertIsolatesBoundary(List<String> valveCodes, List<String> boundarySegmentCodes) {
        ImpactResult impact = evaluate(valveCodes);
        Set<String> outage = new HashSet<>(impact.outageSegmentCodes());
        List<String> missing = boundarySegmentCodes.stream().filter(s -> !outage.contains(s)).toList();
        if (!missing.isEmpty()) {
            throw ApiException.unprocessable("PLAN_DOES_NOT_ISOLATE",
                    "所选关阀组合不能隔离事故边界管段: " + missing,
                    Map.of("missingSegments", missing, "outageSegments", impact.outageSegmentCodes()));
        }
        return impact;
    }
}
