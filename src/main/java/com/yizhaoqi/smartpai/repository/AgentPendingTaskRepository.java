package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.AgentPendingTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentPendingTaskRepository extends JpaRepository<AgentPendingTask, Long> {

    Optional<AgentPendingTask> findTopByUserIdAndConversationIdAndStatusOrderByIdDesc(
            String userId,
            String conversationId,
            String status
    );

    List<AgentPendingTask> findByUserIdAndConversationIdAndStatus(
            String userId,
            String conversationId,
            String status
    );
}
