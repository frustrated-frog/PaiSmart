package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.model.AgentTerminalReason;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行内语义循环守卫：识别重复动作和连续无进展，而不是只依赖固定轮数。
 */
@Service
public class AgentLoopGuard {

    private final ObjectMapper objectMapper;
    private final AgenticRagProperties properties;
    private final Map<String, RunLoopState> states = new ConcurrentHashMap<>();

    public AgentLoopGuard(ObjectMapper objectMapper, AgenticRagProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ActionDecision beforeAction(String generationId,
                                       String toolName,
                                       Map<String, Object> arguments) {
        String fingerprint = actionFingerprint(toolName, arguments);
        RunLoopState state = states.computeIfAbsent(generationId, ignored -> new RunLoopState());
        int count = state.incrementAction(fingerprint);
        AgenticRagProperties.LoopGuard config = properties.getLoopGuard();
        if (count >= config.getRepeatHardLimit()) {
            return new ActionDecision(
                    Disposition.BLOCK,
                    fingerprint,
                    count,
                    AgentTerminalReason.DUPLICATE_ACTION_LIMIT,
                    "同一工具和参数已经重复请求 " + count + " 次，已停止重复执行"
            );
        }
        if (count >= config.getRepeatWarningThreshold()) {
            return new ActionDecision(
                    Disposition.WARN,
                    fingerprint,
                    count,
                    null,
                    "检测到重复动作；本次执行后必须使用新证据收敛答案或改变策略"
            );
        }
        return new ActionDecision(Disposition.ALLOW, fingerprint, count, null, "");
    }

    public ProgressDecision observeProgress(String generationId, String progressSignature) {
        if (progressSignature == null || progressSignature.isBlank()) {
            return new ProgressDecision(true, 0, null, "未提供可比较的进展签名");
        }
        RunLoopState state = states.computeIfAbsent(generationId, ignored -> new RunLoopState());
        int stagnantRounds = state.observeProgress(progressSignature);
        if (stagnantRounds >= properties.getLoopGuard().getNoProgressLimit()) {
            return new ProgressDecision(
                    false,
                    stagnantRounds,
                    AgentTerminalReason.NO_PROGRESS,
                    "连续 " + stagnantRounds + " 轮证据集合和任务状态没有变化"
            );
        }
        return new ProgressDecision(true, stagnantRounds, null,
                stagnantRounds == 0 ? "检测到新进展" : "本轮没有新进展，下一轮必须改变策略");
    }

    public String actionFingerprint(String toolName, Map<String, Object> arguments) {
        JsonNode argumentsNode = objectMapper.valueToTree(arguments == null ? Map.of() : arguments);
        JsonNode canonical = canonicalize(argumentsNode);
        return sha256((toolName == null ? "" : toolName.trim()) + '\n' + canonical);
    }

    public String observationSignature(String toolName, String content) {
        return sha256((toolName == null ? "" : toolName.trim()) + '\n' + normalizeObservation(content));
    }

    public void clear(String generationId) {
        if (generationId != null) {
            states.remove(generationId);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.nullNode();
        }
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            List<String> names = new ArrayList<>();
            Iterator<String> fields = node.fieldNames();
            fields.forEachRemaining(names::add);
            names.stream().sorted(Comparator.naturalOrder()).forEach(name ->
                    result.set(name, canonicalize(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        if (node.isTextual()) {
            return objectMapper.getNodeFactory().textNode(node.asText().trim().replaceAll("\\s+", " "));
        }
        return node;
    }

    private String normalizeObservation(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(64);
            for (byte item : digest) {
                output.append(String.format("%02x", item));
            }
            return output.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成 Agent 循环签名", exception);
        }
    }

    public enum Disposition {
        ALLOW,
        WARN,
        BLOCK
    }

    public record ActionDecision(
            Disposition disposition,
            String fingerprint,
            int occurrence,
            AgentTerminalReason terminalReason,
            String message
    ) {
    }

    public record ProgressDecision(
            boolean progressing,
            int stagnantRounds,
            AgentTerminalReason terminalReason,
            String message
    ) {
    }

    private static final class RunLoopState {
        private final Map<String, Integer> actionCounts = new ConcurrentHashMap<>();
        private String lastProgressSignature;
        private int stagnantRounds;

        private int incrementAction(String fingerprint) {
            return actionCounts.merge(fingerprint, 1, Integer::sum);
        }

        private synchronized int observeProgress(String signature) {
            if (lastProgressSignature == null || !lastProgressSignature.equals(signature)) {
                lastProgressSignature = signature;
                stagnantRounds = 0;
                return 0;
            }
            stagnantRounds++;
            return stagnantRounds;
        }
    }
}
