package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.handler.ChatWebSocketHandler;
import com.yizhaoqi.smartpai.service.AgentToolRegistry;
import com.yizhaoqi.smartpai.service.AgentMemoryService;
import com.yizhaoqi.smartpai.service.AgentRunService;
import com.yizhaoqi.smartpai.evaluation.RetrievalEvaluationService;
import com.yizhaoqi.smartpai.service.ChatGenerationStateService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import com.yizhaoqi.smartpai.utils.LogUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final JwtUtils jwtUtils;
    private final ChatGenerationStateService chatGenerationStateService;
    private final AgentToolRegistry agentToolRegistry;
    private final AgentMemoryService agentMemoryService;
    private final AgentRunService agentRunService;
    private final RetrievalEvaluationService retrievalEvaluationService;

    public ChatController(JwtUtils jwtUtils,
                          ChatGenerationStateService chatGenerationStateService,
                          AgentToolRegistry agentToolRegistry,
                          AgentMemoryService agentMemoryService,
                          AgentRunService agentRunService,
                          RetrievalEvaluationService retrievalEvaluationService) {
        this.jwtUtils = jwtUtils;
        this.chatGenerationStateService = chatGenerationStateService;
        this.agentToolRegistry = agentToolRegistry;
        this.agentMemoryService = agentMemoryService;
        this.agentRunService = agentRunService;
        this.retrievalEvaluationService = retrievalEvaluationService;
    }
    
    /**
     * 获取WebSocket停止指令Token
     */
    @GetMapping("/websocket-token")
    public ResponseEntity<?> getWebSocketToken(@RequestHeader("Authorization") String token) {
        try {
            if (token == null || !token.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(responseBody(401, "Invalid token", null));
            }
            String jwtToken = token.replace("Bearer ", "");
            if (!jwtUtils.validateToken(jwtToken)) {
                return ResponseEntity.status(401).body(responseBody(401, "Invalid token", null));
            }

            String cmdToken = ChatWebSocketHandler.getInternalCmdToken();
            
            // 检查token是否有效
            if (cmdToken == null || cmdToken.trim().isEmpty()) {
                return ResponseEntity.status(500).body(responseBody(500, "Token生成失败", null));
            }
            
            return ResponseEntity.ok(responseBody(200, "获取WebSocket停止指令Token成功", Map.of("cmdToken", cmdToken)));
            
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_WEBSOCKET_TOKEN", "system", "获取WebSocket Token失败", e);
            return ResponseEntity.status(500).body(responseBody(500, "服务器内部错误：" + e.getMessage(), null));
        }
    }

    @GetMapping("/generation/{generationId}")
    public ResponseEntity<?> getGeneration(
            @PathVariable String generationId,
            @RequestHeader("Authorization") String token) {
        String userId = extractValidatedUserId(token);
        if (userId == null) {
            return ResponseEntity.status(401).body(responseBody(401, "Invalid token", null));
        }

        return ResponseEntity.ok(responseBody(
                200,
                "获取生成状态成功",
                chatGenerationStateService.getGenerationForUser(generationId, userId).orElse(null)
        ));
    }

    @GetMapping("/agent-runs/{generationId}")
    public ResponseEntity<?> getAgentRun(
            @PathVariable String generationId,
            @RequestHeader("Authorization") String token) {
        String userId = extractValidatedUserId(token);
        if (userId == null) {
            return ResponseEntity.status(401).body(responseBody(401, "Invalid token", null));
        }
        AgentRunService.RunDetail detail = agentRunService.get(generationId)
                .filter(item -> userId.equals(item.run().getUserId()))
                .orElse(null);
        if (detail == null) {
            return ResponseEntity.status(404).body(responseBody(404, "Agent 运行记录不存在", null));
        }
        return ResponseEntity.ok(responseBody(200, "获取 Agent 运行记录成功", detail));
    }

    @GetMapping("/memories")
    public ResponseEntity<?> listMemories(@RequestHeader("Authorization") String token) {
        String userId = extractValidatedUserId(token);
        if (userId == null) {
            return ResponseEntity.status(401).body(responseBody(401, "Invalid token", null));
        }
        return ResponseEntity.ok(responseBody(200, "获取长期记忆成功", agentMemoryService.listForUser(userId, 100)));
    }

    @PostMapping("/evaluations/retrieval")
    public ResponseEntity<?> evaluateRetrieval(@RequestHeader("Authorization") String token,
                                               @RequestBody RetrievalEvaluationRequest request) {
        String userId = extractValidatedUserId(token);
        if (userId == null) {
            return ResponseEntity.status(401).body(responseBody(401, "Invalid token", null));
        }
        try {
            return ResponseEntity.ok(responseBody(
                    200,
                    "检索评测完成",
                    retrievalEvaluationService.evaluate(userId, request == null ? null : request.cases())
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(responseBody(400, exception.getMessage(), null));
        }
    }

    @PutMapping("/memories/{memoryId}/status")
    public ResponseEntity<?> updateMemoryStatus(@PathVariable long memoryId,
                                                @RequestHeader("Authorization") String token,
                                                @RequestBody MemoryStatusRequest request) {
        String userId = extractValidatedUserId(token);
        if (userId == null) {
            return ResponseEntity.status(401).body(responseBody(401, "Invalid token", null));
        }
        try {
            return ResponseEntity.ok(responseBody(
                    200,
                    "更新长期记忆状态成功",
                    agentMemoryService.transition(userId, memoryId, request.status())
            ));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(responseBody(400, exception.getMessage(), null));
        }
    }

    @GetMapping("/active-generation")
    public ResponseEntity<?> getActiveGeneration(@RequestHeader("Authorization") String token) {
        String userId = extractValidatedUserId(token);
        if (userId == null) {
            return ResponseEntity.status(401).body(responseBody(401, "Invalid token", null));
        }

        return ResponseEntity.ok(responseBody(
                200,
                "获取当前活动生成状态成功",
                chatGenerationStateService.getActiveGenerationForUser(userId).orElse(null)
        ));
    }

    @PostMapping("/feedback")
    public ResponseEntity<?> submitFeedback(@RequestHeader("Authorization") String token,
                                            @RequestBody FeedbackRequest request) {
        String userId = extractValidatedUserId(token);
        if (userId == null) {
            return ResponseEntity.status(401).body(responseBody(401, "Invalid token", null));
        }

        if (request == null || request.rating() == null || request.rating().isBlank()) {
            return ResponseEntity.badRequest().body(responseBody(400, "rating 不能为空", null));
        }

        Map<String, Object> arguments = new HashMap<>();
        arguments.put("rating", request.rating());
        String reason = buildFeedbackReason(request);
        if (!reason.isBlank()) {
            arguments.put("reason", reason);
        }
        if (request.query() != null && !request.query().isBlank()) {
            arguments.put("query", request.query().trim());
        }
        if (request.correction() != null && !request.correction().isBlank()) {
            arguments.put("correction", request.correction().trim());
        }
        String sourceReference = request.generationId() != null && !request.generationId().isBlank()
                ? request.generationId().trim()
                : request.conversationId();
        if (sourceReference != null && !sourceReference.isBlank()) {
            arguments.put("sourceReference", sourceReference);
        }

        AgentToolRegistry.ToolExecutionResult result =
                agentToolRegistry.executeTool("submit_feedback", arguments, userId);
        return ResponseEntity.ok(responseBody(200, "反馈已记录", result.data()));
    }

    private String buildFeedbackReason(FeedbackRequest request) {
        StringBuilder reason = new StringBuilder();
        if (request.reason() != null && !request.reason().isBlank()) {
            reason.append(request.reason().trim());
        }
        return reason.toString();
    }

    private void appendReasonPart(StringBuilder reason, String part) {
        if (!reason.isEmpty()) {
            reason.append("; ");
        }
        reason.append(part);
    }

    private String extractValidatedUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }

        String jwtToken = authorization.replace("Bearer ", "");
        if (!jwtUtils.validateToken(jwtToken)) {
            return null;
        }
        return jwtUtils.extractUserIdFromToken(jwtToken);
    }

    private Map<String, Object> responseBody(int code, String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", code);
        response.put("message", message);
        response.put("data", data);
        return response;
    }

    public record FeedbackRequest(
            String rating,
            String reason,
            String conversationId,
            String generationId,
            String query,
            String correction
    ) {
    }

    public record MemoryStatusRequest(String status) {
    }

    public record RetrievalEvaluationRequest(List<RetrievalEvaluationService.EvaluationCase> cases) {
    }
}
