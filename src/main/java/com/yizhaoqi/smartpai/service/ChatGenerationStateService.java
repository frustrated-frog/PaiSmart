package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatGenerationStateService {

    private static final Logger logger = LoggerFactory.getLogger(ChatGenerationStateService.class);
    private static final Duration GENERATION_TTL = Duration.ofMinutes(30);
    private static final TypeReference<Map<String, Map<String, Object>>> REFERENCE_MAP_TYPE =
            new TypeReference<Map<String, Map<String, Object>>>() {};

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ChatGenerationStateService(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public GenerationSnapshot createGeneration(String userId, String conversationId, String question) {
        String generationId = UUID.randomUUID().toString();
        String now = LocalDateTime.now().toString();
        GenerationMeta meta = new GenerationMeta(generationId, userId, conversationId, question, GenerationStatus.STREAMING, now, now, null);

        // 写入顺序：先把可读子项准备好，最后再发布 active key，避免读取者拿到 active key 后却查不到 meta/content。
        redisTemplate.delete(referenceKey(generationId));
        redisTemplate.delete(agentEventKey(generationId));
        redisTemplate.opsForValue().set(contentKey(generationId), "", GENERATION_TTL);
        writeMeta(meta);
        redisTemplate.opsForValue().set(activeGenerationKey(userId), generationId, GENERATION_TTL);
        return toSnapshot(meta, "", Collections.emptyMap(), Collections.emptyList());
    }

    /**
     * 追加一条 Agent 执行事件。使用 Redis List 保存事件流，使浏览器断线重连后仍能恢复执行轨迹。
     * 同一个 stepId 可以先写 RUNNING，再写 COMPLETED/FAILED，前端按 stepId 合并状态。
     */
    public void appendAgentEvent(String generationId, AgentEventSnapshot event) {
        if (event == null) {
            return;
        }
        try {
            redisTemplate.opsForList().rightPush(agentEventKey(generationId), objectMapper.writeValueAsString(event));
            redisTemplate.expire(agentEventKey(generationId), GENERATION_TTL);
            touch(generationId);
        } catch (Exception e) {
            logger.warn("保存 Agent 执行事件失败: generationId={}, stepId={}", generationId, event.stepId(), e);
        }
    }

    public void appendChunk(String generationId, String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            touch(generationId);
            return;
        }

        redisTemplate.opsForValue().append(contentKey(generationId), chunk);
        touch(generationId);
    }

    public void updateReferenceMappings(String generationId, Map<String, Map<String, Object>> referenceMappings) {
        try {
            if (referenceMappings == null || referenceMappings.isEmpty()) {
                redisTemplate.delete(referenceKey(generationId));
            } else {
                redisTemplate.opsForValue().set(
                        referenceKey(generationId),
                        objectMapper.writeValueAsString(referenceMappings),
                        GENERATION_TTL
                );
            }
            touch(generationId);
        } catch (Exception e) {
            logger.warn("保存生成态引用映射失败: generationId={}", generationId, e);
        }
    }

    public void markCompleted(String generationId, Map<String, Map<String, Object>> referenceMappings) {
        updateTerminalState(generationId, GenerationStatus.COMPLETED, null, referenceMappings);
    }

    public void markFailed(String generationId, String errorMessage) {
        updateTerminalState(generationId, GenerationStatus.FAILED, errorMessage, null);
    }

    public void markCancelled(String generationId) {
        updateTerminalState(generationId, GenerationStatus.CANCELLED, null, null);
    }

    public Optional<GenerationSnapshot> getGeneration(String generationId) {
        GenerationMeta meta = readMeta(generationId);
        if (meta == null) {
            return Optional.empty();
        }

        String content = Optional.ofNullable(redisTemplate.opsForValue().get(contentKey(generationId))).orElse("");
        Map<String, Map<String, Object>> references = readReferenceMappings(generationId);
        return Optional.of(toSnapshot(meta, content, references, readAgentEvents(generationId)));
    }

    public Optional<GenerationSnapshot> getGenerationForUser(String generationId, String userId) {
        return getGeneration(generationId).filter(snapshot -> snapshot.userId().equals(userId));
    }

    public Optional<GenerationSnapshot> getActiveGenerationForUser(String userId) {
        String generationId = redisTemplate.opsForValue().get(activeGenerationKey(userId));
        if (generationId == null || generationId.isBlank()) {
            return Optional.empty();
        }
        return getGenerationForUser(generationId, userId);
    }

    private void updateTerminalState(String generationId,
                                     GenerationStatus status,
                                     String errorMessage,
                                     Map<String, Map<String, Object>> referenceMappings) {
        GenerationMeta meta = readMeta(generationId);
        if (meta == null) {
            return;
        }

        if (referenceMappings != null) {
            updateReferenceMappings(generationId, referenceMappings);
        } else {
            touch(generationId);
        }

        String now = LocalDateTime.now().toString();
        GenerationMeta updated = new GenerationMeta(
                meta.generationId(),
                meta.userId(),
                meta.conversationId(),
                meta.question(),
                status,
                meta.createdAt(),
                now,
                errorMessage
        );
        writeMeta(updated);
        clearActiveGeneration(meta.userId(), generationId);
    }

    private void touch(String generationId) {
        GenerationMeta meta = readMeta(generationId);
        if (meta == null) {
            return;
        }

        GenerationMeta updated = new GenerationMeta(
                meta.generationId(),
                meta.userId(),
                meta.conversationId(),
                meta.question(),
                meta.status(),
                meta.createdAt(),
                LocalDateTime.now().toString(),
                meta.errorMessage()
        );
        writeMeta(updated);
        redisTemplate.expire(contentKey(generationId), GENERATION_TTL);
        redisTemplate.expire(referenceKey(generationId), GENERATION_TTL);
        redisTemplate.expire(agentEventKey(generationId), GENERATION_TTL);
        redisTemplate.opsForValue().set(activeGenerationKey(meta.userId()), generationId, GENERATION_TTL);
    }

    private void clearActiveGeneration(String userId, String generationId) {
        String current = redisTemplate.opsForValue().get(activeGenerationKey(userId));
        if (generationId.equals(current)) {
            redisTemplate.delete(activeGenerationKey(userId));
        }
    }

    private void writeMeta(GenerationMeta meta) {
        try {
            redisTemplate.opsForValue().set(metaKey(meta.generationId()), objectMapper.writeValueAsString(meta), GENERATION_TTL);
        } catch (Exception e) {
            throw new IllegalStateException("保存生成态元数据失败", e);
        }
    }

    private GenerationMeta readMeta(String generationId) {
        String raw = redisTemplate.opsForValue().get(metaKey(generationId));
        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(raw, GenerationMeta.class);
        } catch (Exception e) {
            logger.warn("解析生成态元数据失败: generationId={}", generationId, e);
            return null;
        }
    }

    private Map<String, Map<String, Object>> readReferenceMappings(String generationId) {
        String raw = redisTemplate.opsForValue().get(referenceKey(generationId));
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.readValue(raw, REFERENCE_MAP_TYPE);
        } catch (Exception e) {
            logger.warn("解析生成态引用映射失败: generationId={}", generationId, e);
            return Collections.emptyMap();
        }
    }

    private List<AgentEventSnapshot> readAgentEvents(String generationId) {
        List<String> rawEvents = redisTemplate.opsForList().range(agentEventKey(generationId), 0, -1);
        if (rawEvents == null || rawEvents.isEmpty()) {
            return Collections.emptyList();
        }

        List<AgentEventSnapshot> events = new ArrayList<>(rawEvents.size());
        for (String raw : rawEvents) {
            try {
                events.add(objectMapper.readValue(raw, AgentEventSnapshot.class));
            } catch (Exception e) {
                logger.warn("解析 Agent 执行事件失败: generationId={}", generationId, e);
            }
        }
        return events;
    }

    private GenerationSnapshot toSnapshot(GenerationMeta meta,
                                          String content,
                                          Map<String, Map<String, Object>> referenceMappings,
                                          List<AgentEventSnapshot> agentEvents) {
        return new GenerationSnapshot(
                meta.generationId(),
                meta.userId(),
                meta.conversationId(),
                meta.question(),
                meta.status(),
                content,
                meta.createdAt(),
                meta.updatedAt(),
                meta.errorMessage(),
                referenceMappings == null ? Collections.emptyMap() : referenceMappings,
                agentEvents == null ? Collections.emptyList() : agentEvents
        );
    }

    private String metaKey(String generationId) {
        return "chat:generation:" + generationId + ":meta";
    }

    private String contentKey(String generationId) {
        return "chat:generation:" + generationId + ":content";
    }

    private String referenceKey(String generationId) {
        return "chat:generation:" + generationId + ":refs";
    }

    private String agentEventKey(String generationId) {
        return "chat:generation:" + generationId + ":agent_events";
    }

    private String activeGenerationKey(String userId) {
        return "chat:user:" + userId + ":active_generation";
    }

    public enum GenerationStatus {
        STREAMING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public record GenerationMeta(
            String generationId,
            String userId,
            String conversationId,
            String question,
            GenerationStatus status,
            String createdAt,
            String updatedAt,
            String errorMessage
    ) {
    }

    public record GenerationSnapshot(
            String generationId,
            String userId,
            String conversationId,
            String question,
            GenerationStatus status,
            String content,
            String createdAt,
            String updatedAt,
            String errorMessage,
            Map<String, Map<String, Object>> referenceMappings,
            List<AgentEventSnapshot> agentEvents
    ) {
    }

    public record AgentEventSnapshot(
            String stepId,
            String stage,
            String status,
            String title,
            String detail,
            String toolName,
            long timestamp,
            Map<String, Object> metadata
    ) {
        public AgentEventSnapshot {
            metadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
        }
    }
}
