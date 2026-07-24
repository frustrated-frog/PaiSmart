package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.AgentRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;

public interface AgentRunRepository extends JpaRepository<AgentRun, String> {
    List<AgentRun> findTop20ByUserIdOrderByCreatedAtDesc(String userId);
    List<AgentRun> findTop50ByUserIdAndConversationIdOrderByCreatedAtAsc(String userId, String conversationId);
    List<AgentRun> findByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(String userId, LocalDateTime createdAt);
    List<AgentRun> findByStatusIn(List<String> statuses);
}
