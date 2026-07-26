package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.AgentRun;
import com.yizhaoqi.smartpai.model.AgentToolCall;
import com.yizhaoqi.smartpai.repository.AgentRunRepository;
import com.yizhaoqi.smartpai.repository.AgentToolCallRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 高风险或结果未知工具的人工审批服务。 */
@Service
public class AgentToolApprovalService {

    private final AgentRunRepository runRepository;
    private final AgentToolCallRepository toolCallRepository;

    public AgentToolApprovalService(AgentRunRepository runRepository,
                                    AgentToolCallRepository toolCallRepository) {
        this.runRepository = runRepository;
        this.toolCallRepository = toolCallRepository;
    }

    @Transactional
    public ApprovalResult decide(String userId,
                                 String generationId,
                                 long toolLedgerId,
                                 ApprovalDecision decision) {
        if (decision == null) {
            throw new IllegalArgumentException("审批决定不能为空");
        }
        AgentRun run = runRepository.findById(generationId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 运行记录不存在"));
        if (!userId.equals(run.getUserId())) {
            throw new IllegalArgumentException("无权审批该 Agent 工具调用");
        }
        if (!"WAITING_APPROVAL".equals(run.getStatus())) {
            throw new IllegalStateException("Agent 运行当前不处于等待审批状态");
        }

        AgentToolCall toolCall = toolCallRepository.findById(toolLedgerId)
                .filter(item -> generationId.equals(item.getGenerationId()))
                .orElseThrow(() -> new IllegalArgumentException("待审批工具调用不存在"));
        String targetStatus = decision == ApprovalDecision.APPROVE ? "APPROVED" : "REJECTED";
        if (targetStatus.equals(toolCall.getStatus())) {
            return result(toolCall, true);
        }
        if ("APPROVED".equals(toolCall.getStatus()) || "REJECTED".equals(toolCall.getStatus())) {
            throw new IllegalStateException("该工具审批已经完成，不能修改决定");
        }
        if (!"WAITING_APPROVAL".equals(toolCall.getStatus())) {
            throw new IllegalStateException("工具调用当前不允许审批: " + toolCall.getStatus());
        }
        toolCall.setStatus(targetStatus);
        toolCall.setFinishedAt(LocalDateTime.now());
        toolCallRepository.save(toolCall);
        return result(toolCall, false);
    }

    private ApprovalResult result(AgentToolCall toolCall, boolean idempotent) {
        return new ApprovalResult(
                toolCall.getGenerationId(),
                toolCall.getId(),
                toolCall.getToolName(),
                toolCall.getStatus(),
                idempotent
        );
    }

    public enum ApprovalDecision {
        APPROVE,
        REJECT
    }

    public record ApprovalResult(String generationId,
                                 long toolLedgerId,
                                 String toolName,
                                 String status,
                                 boolean idempotent) {
    }
}
