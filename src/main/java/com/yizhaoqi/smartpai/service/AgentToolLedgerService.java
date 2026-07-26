package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.model.AgentToolCall;
import com.yizhaoqi.smartpai.rag.model.EvidenceAssessment;
import com.yizhaoqi.smartpai.repository.AgentToolCallRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 工具请求与结果的原子账本：安全工具可回放，非幂等工具在未知状态时必须停下确认。 */
@Service
public class AgentToolLedgerService {

    private final AgentToolCallRepository repository;
    private final ObjectMapper objectMapper;

    public AgentToolLedgerService(AgentToolCallRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public synchronized PreparedToolCall prepare(String generationId,
                                                 String replaySourceGenerationId,
                                                 String toolCallId,
                                                 String toolName,
                                                 String actionFingerprint,
                                                 Map<String, Object> arguments,
                                                 AgentToolRegistry.ToolPolicy policy) {
        Optional<AgentToolCall> current = find(generationId, actionFingerprint);
        if (current.isPresent()) {
            PreparedToolCall decision = fromExisting(
                    current.get(), generationId, toolCallId, toolName, actionFingerprint, arguments, policy);
            if (decision.disposition() != Disposition.EXECUTE) {
                return decision;
            }
            AgentToolCall retry = current.get();
            retry.setStatus("RUNNING");
            retry.setErrorMessage(null);
            retry.setStartedAt(LocalDateTime.now());
            retry.setFinishedAt(null);
            repository.save(retry);
            return new PreparedToolCall(Disposition.EXECUTE, retry, null, "失败的安全工具请求已重新执行");
        }

        if (replaySourceGenerationId != null && !replaySourceGenerationId.isBlank()) {
            Optional<AgentToolCall> source = find(replaySourceGenerationId, actionFingerprint);
            if (source.isPresent()) {
                if ("APPROVED".equals(source.get().getStatus())) {
                    AgentToolCall approved = saveState(
                            generationId, toolCallId, toolName, actionFingerprint,
                            arguments, policy, "RUNNING", source.get().getId());
                    return new PreparedToolCall(
                            Disposition.EXECUTE, approved, null, "人工审批已通过，允许执行工具");
                }
                PreparedToolCall decision = fromExisting(
                        source.get(), generationId, toolCallId, toolName, actionFingerprint, arguments, policy);
                if (decision.disposition() != Disposition.EXECUTE) {
                    return decision;
                }
            }
        }

        if (policy.replayPolicy() == AgentToolRegistry.ReplayPolicy.REQUIRES_APPROVAL) {
            AgentToolCall blocked = saveState(generationId, toolCallId, toolName, actionFingerprint,
                    arguments, policy, "WAITING_APPROVAL", null);
            return new PreparedToolCall(Disposition.BLOCK, blocked, null, "该工具必须经人工确认后执行");
        }

        AgentToolCall running = saveState(generationId, toolCallId, toolName, actionFingerprint,
                arguments, policy, "RUNNING", null);
        return new PreparedToolCall(Disposition.EXECUTE, running, null, "工具请求已持久化");
    }

    @Transactional
    public void complete(AgentToolCall ticket, AgentToolRegistry.ToolExecutionResult result) {
        if (ticket == null || result == null) {
            return;
        }
        ticket.setStatus("SUCCESS");
        ticket.setResultContent(result.content());
        ticket.setResultDataJson(writeJson(result.data()));
        ticket.setFinishedAt(LocalDateTime.now());
        repository.save(ticket);
    }

    @Transactional
    public void fail(AgentToolCall ticket, Throwable throwable) {
        if (ticket == null) {
            return;
        }
        boolean uncertainWrite = AgentToolRegistry.ReplayPolicy.AT_MOST_ONCE.name().equals(ticket.getReplayPolicy());
        ticket.setStatus(uncertainWrite ? "UNKNOWN" : "FAILED");
        ticket.setErrorMessage(throwable == null ? "工具执行失败" : throwable.getMessage());
        ticket.setFinishedAt(LocalDateTime.now());
        repository.save(ticket);
    }

    private PreparedToolCall fromExisting(AgentToolCall existing,
                                          String generationId,
                                          String toolCallId,
                                          String toolName,
                                          String actionFingerprint,
                                          Map<String, Object> arguments,
                                          AgentToolRegistry.ToolPolicy policy) {
        if ("REJECTED".equals(existing.getStatus())) {
            AgentToolRegistry.ToolExecutionResult result = new AgentToolRegistry.ToolExecutionResult(
                    toolName,
                    false,
                    "用户拒绝了该工具操作，请不要执行副作用，并提供安全替代方案。",
                    Map.of("approvalStatus", "REJECTED"),
                    false
            );
            if (generationId.equals(existing.getGenerationId())) {
                return new PreparedToolCall(Disposition.REUSE, existing, result, "用户已拒绝该工具操作");
            }
            AgentToolCall replay = saveState(generationId, toolCallId, toolName, actionFingerprint,
                    arguments, policy, "REUSED", existing.getId());
            replay.setResultContent(result.content());
            replay.setResultDataJson(writeJson(result.data()));
            replay.setFinishedAt(LocalDateTime.now());
            repository.save(replay);
            return new PreparedToolCall(Disposition.REUSE, replay, result, "已回放用户拒绝决定");
        }
        if (List.of("SUCCESS", "REUSED").contains(existing.getStatus())) {
            AgentToolRegistry.ToolExecutionResult result = new AgentToolRegistry.ToolExecutionResult(
                    toolName,
                    true,
                    existing.getResultContent(),
                    readData(toolName, existing.getResultDataJson()),
                    false
            );
            if (generationId.equals(existing.getGenerationId())) {
                return new PreparedToolCall(Disposition.REUSE, existing, result, "本轮相同工具动作已完成，直接复用结果");
            }
            AgentToolCall replay = saveState(generationId, toolCallId, toolName, actionFingerprint,
                    arguments, policy, "REUSED", existing.getId());
            replay.setResultContent(existing.getResultContent());
            replay.setResultDataJson(existing.getResultDataJson());
            replay.setFinishedAt(LocalDateTime.now());
            repository.save(replay);
            return new PreparedToolCall(Disposition.REUSE, replay, result, "已从上一次运行安全回放工具结果");
        }
        if (policy.replayPolicy() == AgentToolRegistry.ReplayPolicy.AT_MOST_ONCE
                && List.of("RUNNING", "UNKNOWN", "WAITING_APPROVAL").contains(existing.getStatus())) {
            if (generationId.equals(existing.getGenerationId())) {
                return new PreparedToolCall(Disposition.BLOCK, existing, null, "非幂等工具结果不确定，需要人工确认后继续");
            }
            AgentToolCall blocked = saveState(generationId, toolCallId, toolName, actionFingerprint,
                    arguments, policy, "WAITING_APPROVAL", existing.getId());
            return new PreparedToolCall(Disposition.BLOCK, blocked, null, "上一次非幂等工具结果不确定，需要人工确认后继续");
        }
        return new PreparedToolCall(Disposition.EXECUTE, null, null, "允许创建新的工具请求");
    }

    private Optional<AgentToolCall> find(String generationId, String fingerprint) {
        return repository.findTopByGenerationIdAndActionFingerprintOrderByIdDesc(generationId, fingerprint);
    }

    private AgentToolCall saveState(String generationId,
                                    String toolCallId,
                                    String toolName,
                                    String fingerprint,
                                    Map<String, Object> arguments,
                                    AgentToolRegistry.ToolPolicy policy,
                                    String status,
                                    Long reusedFromId) {
        AgentToolCall call = new AgentToolCall();
        call.setGenerationId(generationId);
        call.setToolCallId(toolCallId);
        call.setToolName(toolName);
        call.setActionFingerprint(fingerprint);
        call.setIdempotencyKey(hash(generationId + ':' + fingerprint));
        call.setRiskLevel(policy.riskLevel().name());
        call.setToolEffect(policy.effect().name());
        call.setReplayPolicy(policy.replayPolicy().name());
        call.setStatus(status);
        call.setArgumentsJson(writeJson(arguments));
        call.setReusedFromToolCallId(reusedFromId);
        call.setStartedAt(LocalDateTime.now());
        if (!"RUNNING".equals(status)) {
            call.setFinishedAt(LocalDateTime.now());
        }
        return repository.save(call);
    }

    private Map<String, Object> readData(String toolName, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> data = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() { });
            if ("search_knowledge".equals(toolName)) {
                Object rawResults = data.get("results");
                if (rawResults instanceof List<?> list) {
                    List<SearchResult> results = new ArrayList<>();
                    for (Object value : list) {
                        if (value instanceof Map<?, ?> map) {
                            results.add(toSearchResult(map));
                        }
                    }
                    data.put("results", results);
                }
                Object rawAssessment = data.get("evidenceAssessment");
                if (rawAssessment instanceof Map<?, ?>) {
                    data.put("evidenceAssessment", objectMapper.convertValue(rawAssessment, EvidenceAssessment.class));
                }
            }
            return data;
        } catch (Exception exception) {
            return Map.of("replayDeserializationError", true);
        }
    }

    private SearchResult toSearchResult(Map<?, ?> source) {
        SearchResult result = new SearchResult(
                stringValue(source.get("fileMd5")),
                integerValue(source.get("chunkId")),
                stringValue(source.get("textContent")),
                doubleValue(source.get("score"))
        );
        result.setParentChunkId(longValue(source.get("parentChunkId")));
        result.setParentChunkIndex(integerValue(source.get("parentChunkIndex")));
        result.setFileName(stringValue(source.get("fileName")));
        result.setUserId(stringValue(source.get("userId")));
        result.setOrgTag(stringValue(source.get("orgTag")));
        result.setIsPublic(booleanValue(source.get("isPublic")));
        result.setPageNumber(integerValue(source.get("pageNumber")));
        result.setAnchorText(stringValue(source.get("anchorText")));
        result.setRetrievalMode(stringValue(source.get("retrievalMode")));
        result.setMatchedChunkText(stringValue(source.get("matchedChunkText")));
        result.setRawScore(doubleValue(source.get("rawScore")));
        result.setRrfScore(doubleValue(source.get("rrfScore")));
        result.setRerankScore(doubleValue(source.get("rerankScore")));
        result.setFinalRank(integerValue(source.get("finalRank")));
        return result;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            return "{\"serializationError\":true}";
        }
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成工具幂等键", exception);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integerValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private Boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : null;
    }

    public enum Disposition {
        EXECUTE,
        REUSE,
        BLOCK
    }

    public record PreparedToolCall(Disposition disposition,
                                   AgentToolCall ticket,
                                   AgentToolRegistry.ToolExecutionResult replayedResult,
                                   String message) {
    }
}
