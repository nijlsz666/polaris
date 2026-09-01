package com.polaris.bpm.service.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/** Converts the designer's stable JSON contract into executable BPMN XML. */
@Component
public class BpmnDefinitionCompiler {
    public String simple(String code, String name, String group) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:flowable=\"http://flowable.org/bpmn\" targetNamespace=\"Polaris.BPM\">"
                + "<process id=\"" + WorkflowPayload.xml(code) + "\" name=\"" + WorkflowPayload.xml(name)
                + "\" isExecutable=\"true\"><startEvent id=\"start\"/>"
                + "<sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"approval\"/>"
                + "<userTask id=\"approval\" name=\"" + WorkflowPayload.xml(name)
                + "\" flowable:candidateGroups=\"" + WorkflowPayload.xml(group) + "\"/>"
                + "<sequenceFlow id=\"f2\" sourceRef=\"approval\" targetRef=\"end\"/>"
                + "<endEvent id=\"end\"/></process></definitions>";
    }

    public String compile(String code, String name, Map<String, Object> payload) {
        Object rawNodes = payload.get("nodes");
        Object rawEdges = payload.get("edges");
        if (!(rawNodes instanceof List<?> nodes) || nodes.isEmpty()) {
            return simple(code, name, "planner");
        }
        StringBuilder bpmn = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        bpmn.append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" ")
                .append("xmlns:flowable=\"http://flowable.org/bpmn\" ")
                .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
                .append("targetNamespace=\"Polaris.BPM\">");
        for (Object raw : nodes) {
            if (raw instanceof Map<?, ?> node && "messageEvent".equals(WorkflowPayload.text(node.get("type"), null, ""))) {
                String message = WorkflowPayload.text(node.get("messageName"), node.get("code"), String.valueOf(node.get("id")));
                bpmn.append("<message id=\"").append(WorkflowPayload.bpmnId(message)).append("\" name=\"")
                        .append(WorkflowPayload.xml(message)).append("\"/>");
            }
        }
        bpmn.append("<process id=\"").append(WorkflowPayload.xml(code)).append("\" name=\"")
                .append(WorkflowPayload.xml(name)).append("\" isExecutable=\"true\">");

        Map<String, String> defaultEdges = new LinkedHashMap<>();
        if (rawEdges instanceof List<?> edgeList) {
            for (Object raw : edgeList) {
                if (raw instanceof Map<?, ?> edge && Boolean.parseBoolean(String.valueOf(edge.get("isDefault")))) {
                    defaultEdges.put(String.valueOf(edge.get("source")), WorkflowPayload.bpmnId(edge.get("id")));
                }
            }
        }
        for (Object raw : nodes) {
            if (raw instanceof Map<?, ?> node && WorkflowPayload.nullable(node.get("defaultEdge")) != null) {
                defaultEdges.put(String.valueOf(node.get("id")), WorkflowPayload.bpmnId(node.get("defaultEdge")));
            }
        }
        for (Object raw : nodes) {
            if (!(raw instanceof Map<?, ?> node)) {
                continue;
            }
            String id = WorkflowPayload.bpmnId(node.get("id"));
            String type = WorkflowPayload.text(node.get("type"), null, "userTask");
            String label = WorkflowPayload.text(node.get("label"), node.get("name"), type);
            switch (type) {
                case "start" -> bpmn.append("<startEvent id=\"").append(id).append("\" name=\"")
                        .append(WorkflowPayload.xml(label)).append("\"/>");
                case "end" -> bpmn.append("<endEvent id=\"").append(id).append("\" name=\"")
                        .append(WorkflowPayload.xml(label)).append("\"/>");
                case "exclusiveGateway", "parallelGateway", "inclusiveGateway" -> gateway(bpmn, type, id, label,
                        defaultEdges.get(String.valueOf(node.get("id"))));
                case "serviceTask" -> serviceTask(bpmn, node, id, label);
                case "subprocess" -> bpmn.append("<callActivity id=\"").append(id).append("\" name=\"")
                        .append(WorkflowPayload.xml(label)).append("\" flowable:calledElement=\"")
                        .append(WorkflowPayload.xml(WorkflowPayload.text(node.get("processRef"), node.get("calledProcess"), "")))
                        .append("\"/>");
                case "timerEvent" -> timer(bpmn, node, id, label);
                case "messageEvent" -> bpmn.append("<intermediateCatchEvent id=\"").append(id)
                        .append("\" name=\"").append(WorkflowPayload.xml(label))
                        .append("\"><messageEventDefinition messageRef=\"")
                        .append(WorkflowPayload.bpmnId(WorkflowPayload.text(node.get("messageName"), node.get("code"), id)))
                        .append("\"/></intermediateCatchEvent>");
                default -> userTask(bpmn, node, id, label);
            }
        }
        if (rawEdges instanceof List<?> edges) {
            for (Object raw : edges) {
                if (!(raw instanceof Map<?, ?> edge)) {
                    continue;
                }
                String id = WorkflowPayload.bpmnId(edge.get("id"));
                String source = WorkflowPayload.bpmnId(edge.get("source"));
                String target = WorkflowPayload.bpmnId(edge.get("target"));
                String condition = WorkflowPayload.nullable(edge.get("condition"));
                String expression = WorkflowPayload.nullable(edge.get("expression"));
                bpmn.append("<sequenceFlow id=\"").append(id).append("\" sourceRef=\"").append(source)
                        .append("\" targetRef=\"").append(target).append("\"");
                if (condition != null) {
                    bpmn.append(" name=\"").append(WorkflowPayload.xml(condition)).append("\"");
                }
                bpmn.append(">");
                if (expression != null) {
                    bpmn.append("<conditionExpression xsi:type=\"tFormalExpression\">")
                            .append(WorkflowPayload.xml(expression)).append("</conditionExpression>");
                }
                bpmn.append("</sequenceFlow>");
            }
        }
        return bpmn.append("</process></definitions>").toString();
    }

    private void serviceTask(StringBuilder bpmn, Map<?, ?> node, String id, String label) {
        String serviceKey = WorkflowPayload.text(node.get("serviceKey"), node.get("code"), "workflow.noop");
        bpmn.append("<serviceTask id=\"").append(id).append("\" name=\"").append(WorkflowPayload.xml(label))
                .append("\" flowable:delegateExpression=\"${workflowServiceDelegate}\">")
                .append("<extensionElements><flowable:field name=\"serviceKey\"><flowable:string>")
                .append(WorkflowPayload.xml(serviceKey)).append("</flowable:string></flowable:field></extensionElements>")
                .append("</serviceTask>");
    }

    private void timer(StringBuilder bpmn, Map<?, ?> node, String id, String label) {
        String value = WorkflowPayload.text(node.get("timerValue"), null, "PT1H");
        String timerType = WorkflowPayload.text(node.get("timerType"), null, "duration");
        bpmn.append("<intermediateCatchEvent id=\"").append(id).append("\" name=\"")
                .append(WorkflowPayload.xml(label)).append("\"><timerEventDefinition>");
        if ("date".equals(timerType)) {
            bpmn.append("<timeDate>");
        } else if ("cycle".equals(timerType)) {
            bpmn.append("<timeCycle>");
        } else {
            bpmn.append("<timeDuration>");
        }
        String tag = "date".equals(timerType) ? "timeDate" : "cycle".equals(timerType) ? "timeCycle" : "timeDuration";
        bpmn.append(WorkflowPayload.xml(value)).append("</").append(tag).append(">");
        bpmn.append("</timerEventDefinition></intermediateCatchEvent>");
    }

    private void userTask(StringBuilder bpmn, Map<?, ?> node, String id, String label) {
        String mode = WorkflowPayload.text(node.get("assigneeMode"), null, "group");
        bpmn.append("<userTask id=\"").append(id).append("\" name=\"").append(WorkflowPayload.xml(label)).append("\"");
        if ("user".equals(mode)) {
            bpmn.append(" flowable:assignee=\"").append(WorkflowPayload.xml(WorkflowPayload.text(node.get("assignee"), null, "admin"))).append("\"");
        } else if ("expression".equals(mode)) {
            bpmn.append(" flowable:assignee=\"").append(WorkflowPayload.xml(WorkflowPayload.text(node.get("assigneeExpression"), null, "${starter}"))).append("\"");
        } else {
            bpmn.append(" flowable:candidateGroups=\"").append(WorkflowPayload.xml(WorkflowPayload.text(node.get("candidateGroups"), null, "planner"))).append("\"");
        }
        String formCode = WorkflowPayload.nullable(node.get("formCode"));
        if (formCode != null) {
            bpmn.append(" flowable:formKey=\"").append(WorkflowPayload.xml(formCode)).append("\"");
        }
        bpmn.append("/>");
    }

    private void gateway(StringBuilder bpmn, String type, String id, String label, String defaultEdge) {
        bpmn.append("<").append(type).append(" id=\"").append(id).append("\" name=\"")
                .append(WorkflowPayload.xml(label)).append("\"");
        if (defaultEdge != null) {
            bpmn.append(" default=\"").append(defaultEdge).append("\"");
        }
        bpmn.append("/>");
    }
}
