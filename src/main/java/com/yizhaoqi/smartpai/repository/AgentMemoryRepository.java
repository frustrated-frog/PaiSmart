package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.AgentMemory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgentMemoryRepository extends JpaRepository<AgentMemory, Long> {

    Optional<AgentMemory> findByOwnerUserIdAndMemoryKey(String ownerUserId, String memoryKey);

    @Query("""
            select memory from AgentMemory memory
            where memory.ownerUserId = :userId
              and memory.status = 'ACTIVE'
              and (memory.expiresAt is null or memory.expiresAt > :now)
            order by memory.updatedAt desc
            """)
    List<AgentMemory> findActiveForUser(@Param("userId") String userId,
                                        @Param("now") LocalDateTime now,
                                        Pageable pageable);

    List<AgentMemory> findByOwnerUserIdOrderByUpdatedAtDesc(String ownerUserId, Pageable pageable);
}
