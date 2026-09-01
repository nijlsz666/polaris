package com.polaris.bpm.service.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Server-side graph validation shared by draft validation and publishing. */
@Component
public class ProcessDesignValidator {
    public List<String> validate(Map<String, Object> payload, boolean strict) {
        List<String> errors = new ArrayList<>();
        String code = WorkflowPayload.nullable(payload.get("processCode"));
        String name = WorkflowPayload.text(payload.get("processName"), payload.get("name"), "");
        if (code == null || !code.matches("[A-Za-z_][A-Za-z0-9_]{1,99}")) {
            errors.add("流程编码必须以字母或下划线开头，且只允许字母、数字、下划线");
        }
        if (name.isBlank()) {
            errors.add("流程名称不能为空");
        }

        Object rawNodes = payload.get("nodes");
        Object rawEdges = payload.get("edges");
        if (!(rawNodes instanceof List<?> nodeList) || nodeList.isEmpty()) {
            errors.add("至少配置一个流程节点");
            return errors;
        }

        Set<String> nodeIds = new HashSet<>();
        Map<String, List<String>> graph = new HashMap<>();
        int starts = 0;
        int ends = 0;
        for (Object raw : nodeList) {
            if (!(raw instanceof Map<?, ?> node)) {
                errors.add("节点数据格式无效");
                continue;
            }
            String id = WorkflowPayload.nullable(node.get("id"));
            String type = WorkflowPayload.text(node.get("type"), null, "");
            if (id == null || !nodeIds.add(id)) {
                errors.add("节点 ID 不能为空且不能重复：" + String.valueOf(id));
            }
            graph.putIfAbsent(id, new ArrayList<>());
            if ("start".equals(type)) {
                starts++;
            }
            if ("end".equals(type)) {
                ends++;
            }
            if ("userTask".equals(type)) {
                validateUserTask(node, id, errors);
            }
            if ("subprocess".equals(type)
                    && WorkflowPayload.nullable(node.get("processRef")) == null
                    && WorkflowPayload.nullable(node.get("calledProcess")) == null) {
                errors.add("子流程节点“" + WorkflowPayload.text(node.get("label"), null, id)
                        + "”未配置被调用流程编码");
            }
        }
        if (starts != 1) {
            errors.add("流程必须且只能有一个开始事件");
        }
        if (ends < 1) {
            errors.add("流程至少需要一个结束事件");
        }

        Set<String> edgeKeys = new HashSet<>();
        if (rawEdges instanceof List<?> edgeList) {
            for (Object raw : edgeList) {
                if (!(raw instanceof Map<?, ?> edge)) {
                    errors.add("连线数据格式无效");
                    continue;
                }
                String source = WorkflowPayload.nullable(edge.get("source"));
                String target = WorkflowPayload.nullable(edge.get("target"));
                if (source == null || target == null || !nodeIds.contains(source) || !nodeIds.contains(target)) {
                    errors.add("连线必须连接两个已存在的节点");
                    continue;
                }
                if (source.equals(target)) {
                    errors.add("连线不能连接节点自身");
                }
                if (!edgeKeys.add(source + "->" + target + ":" + WorkflowPayload.nullable(edge.get("condition")))) {
                    errors.add("存在重复的流程连线：" + source + " -> " + target);
                }
                graph.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target);
            }
        } else if (strict) {
            errors.add("流程至少需要一条有效连线");
        }

        if (strict && errors.isEmpty()) {
            validateReachability(nodeList, graph, errors);
        }
        return errors;
    }

    private void validateUserTask(Map<?, ?> node, String id, List<String> errors) {
        String mode = WorkflowPayload.text(node.get("assigneeMode"), null, "group");
        if ("group".equals(mode) && WorkflowPayload.nullable(node.get("candidateGroups")) == null) {
            errors.add("人工节点“" + WorkflowPayload.text(node.get("label"), null, id) + "”未配置候选角色");
        }
        if ("user".equals(mode) && WorkflowPayload.nullable(node.get("assignee")) == null) {
            errors.add("人工节点“" + WorkflowPayload.text(node.get("label"), null, id) + "”未配置指定用户");
        }
        if ("expression".equals(mode) && WorkflowPayload.nullable(node.get("assigneeExpression")) == null) {
            errors.add("人工节点“" + WorkflowPayload.text(node.get("label"), null, id) + "”未配置处理人表达式");
        }
    }

    private void validateReachability(List<?> nodes, Map<String, List<String>> graph, List<String> errors) {
        String start = null;
        Set<String> ends = new HashSet<>();
        for (Object raw : nodes) {
            if (raw instanceof Map<?, ?> node) {
                String id = WorkflowPayload.nullable(node.get("id"));
                String type = WorkflowPayload.text(node.get("type"), null, "");
                if ("start".equals(type)) {
                    start = id;
                }
                if ("end".equals(type)) {
                    ends.add(id);
                }
            }
        }
        if (start == null) {
            return;
        }
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            queue.addAll(graph.getOrDefault(current, List.of()));
        }
        if (!visited.containsAll(ends)) {
            errors.add("存在无法从开始事件到达的结束事件");
        }
        if (visited.size() < graph.size()) {
            errors.add("存在未连接到主流程的孤立节点");
        }
    }
}
