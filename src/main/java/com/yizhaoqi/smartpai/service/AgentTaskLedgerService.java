package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.model.AgentTaskLedger;
import com.yizhaoqi.smartpai.rag.model.EvidenceAssessment;
import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 复杂任务的 Goal/Todo/Blocker 账本，保证压缩或恢复后仍知道下一步。 */
@Service
public class AgentTaskLedgerService {

    private final ObjectMapper objectMapper;
    private final Map<String, AgentTaskLedger> ledgers = new ConcurrentHashMap<>();

    public AgentTaskLedgerService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<AgentTaskLedger> initialize(String generationId, QueryPlan plan) {
        if (!requiresLedger(plan)) {
            return Optional.empty();
        }
        List<AgentTaskLedger.TaskItem> todos = new ArrayList<>();
        List<String> retrievalAspects = plan.variants().stream()
                .filter(variant -> variant.type() == QueryPlan.VariantType.DECOMPOSED)
                .map(QueryPlan.QueryVariant::query)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
        if (retrievalAspects.isEmpty()) {
            retrievalAspects = plan.entities().stream().limit(4).toList();
        }
        if (retrievalAspects.isEmpty()) {
            retrievalAspects = List.of(plan.originalQuery());
        }
        for (int index = 0; index < retrievalAspects.size(); index++) {
            todos.add(new AgentTaskLedger.TaskItem(
                    "retrieve-" + (index + 1),
                    "检索并覆盖：" + retrievalAspects.get(index),
                    index == 0 ? AgentTaskLedger.Status.IN_PROGRESS : AgentTaskLedger.Status.PENDING,
                    List.of(),
                    List.of()
            ));
        }
        List<String> retrievalIds = todos.stream().map(AgentTaskLedger.TaskItem::id).toList();
        todos.add(new AgentTaskLedger.TaskItem(
                "verify-evidence",
                "验证证据是否充分、冲突或缺失",
                AgentTaskLedger.Status.PENDING,
                retrievalIds,
                List.of()
        ));
        todos.add(new AgentTaskLedger.TaskItem(
                "answer",
                "生成带引用的最终答案",
                AgentTaskLedger.Status.PENDING,
                List.of("verify-evidence"),
                List.of()
        ));
        AgentTaskLedger ledger = new AgentTaskLedger(
                plan.originalQuery(),
                plan.constraints(),
                List.of("覆盖问题中的主要实体或子问题", "结论由检索证据支持", "保留可点击引用"),
                todos,
                List.of(),
                todos.get(0).title(),
                1
        );
        ledgers.put(generationId, ledger);
        return Optional.of(ledger);
    }

    public Optional<AgentTaskLedger> restore(String generationId, Object serializedLedger) {
        if (serializedLedger == null) {
            return Optional.empty();
        }
        try {
            AgentTaskLedger ledger = objectMapper.convertValue(serializedLedger, AgentTaskLedger.class);
            validate(ledger);
            ledgers.put(generationId, ledger);
            return Optional.of(ledger);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public Optional<AgentTaskLedger> observeToolResult(String generationId,
                                                       String toolName,
                                                       Map<String, Object> data) {
        AgentTaskLedger current = ledgers.get(generationId);
        if (current == null || !"search_knowledge".equals(toolName)) {
            return Optional.ofNullable(current);
        }
        EvidenceAssessment assessment = data != null && data.get("evidenceAssessment") instanceof EvidenceAssessment value
                ? value
                : null;
        List<String> evidenceIds = evidenceIds(data);
        List<AgentTaskLedger.TaskItem> updated = new ArrayList<>();
        int coveredBudget = assessment == null ? (evidenceIds.isEmpty() ? 0 : 1) : assessment.coveredAspects().size();
        int completedRetrievals = 0;
        for (AgentTaskLedger.TaskItem item : current.todos()) {
            if (item.id().startsWith("retrieve-") && completedRetrievals < coveredBudget) {
                updated.add(copy(item, AgentTaskLedger.Status.COMPLETED, evidenceIds));
                completedRetrievals++;
            } else {
                updated.add(item);
            }
        }
        boolean evidenceTerminal = assessment != null && List.of(
                EvidenceAssessment.Status.SUFFICIENT,
                EvidenceAssessment.Status.CONFLICTED
        ).contains(assessment.status());
        if (evidenceTerminal) {
            updated = updated.stream().map(item -> {
                if (item.id().startsWith("retrieve-") || "verify-evidence".equals(item.id())) {
                    return copy(item, AgentTaskLedger.Status.COMPLETED, evidenceIds);
                }
                if ("answer".equals(item.id())) {
                    return copy(item, AgentTaskLedger.Status.IN_PROGRESS, item.evidenceIds());
                }
                return item;
            }).toList();
        } else {
            updated = activateFirstPending(updated);
        }
        List<String> blockers = assessment != null && !assessment.missingAspects().isEmpty()
                ? List.of("仍缺少证据：" + String.join("、", assessment.missingAspects()))
                : List.of();
        String nextAction = updated.stream()
                .filter(item -> item.status() == AgentTaskLedger.Status.IN_PROGRESS)
                .map(AgentTaskLedger.TaskItem::title)
                .findFirst()
                .orElse("收敛最终答案");
        AgentTaskLedger next = new AgentTaskLedger(
                current.goal(), current.constraints(), current.acceptanceCriteria(), updated,
                blockers, nextAction, current.planVersion() + 1
        );
        validate(next);
        ledgers.put(generationId, next);
        return Optional.of(next);
    }

    public Optional<AgentTaskLedger> completeAnswer(String generationId) {
        AgentTaskLedger current = ledgers.get(generationId);
        if (current == null) {
            return Optional.empty();
        }
        List<AgentTaskLedger.TaskItem> updated = current.todos().stream()
                .map(item -> {
                    if ("answer".equals(item.id())) {
                        return copy(item, AgentTaskLedger.Status.COMPLETED, item.evidenceIds());
                    }
                    if (item.status() == AgentTaskLedger.Status.IN_PROGRESS
                            || item.status() == AgentTaskLedger.Status.PENDING) {
                        return copy(item, AgentTaskLedger.Status.SKIPPED, item.evidenceIds());
                    }
                    return item;
                })
                .toList();
        AgentTaskLedger next = new AgentTaskLedger(
                current.goal(), current.constraints(), current.acceptanceCriteria(), updated,
                current.blockers(), "任务已完成", current.planVersion() + 1
        );
        ledgers.put(generationId, next);
        validate(next);
        return Optional.of(next);
    }

    public Optional<AgentTaskLedger> get(String generationId) {
        return Optional.ofNullable(ledgers.get(generationId));
    }

    public String buildModelGuidance(String generationId) {
        AgentTaskLedger ledger = ledgers.get(generationId);
        if (ledger == null) {
            return "";
        }
        StringBuilder output = new StringBuilder("[TASK_LEDGER]\n目标：")
                .append(ledger.goal())
                .append("\n验收：").append(String.join("；", ledger.acceptanceCriteria()))
                .append("\n当前任务：");
        ledger.todos().stream()
                .filter(item -> item.status() != AgentTaskLedger.Status.COMPLETED)
                .forEach(item -> output.append("\n- [").append(item.status()).append("] ").append(item.title()));
        if (!ledger.blockers().isEmpty()) {
            output.append("\n阻塞：").append(String.join("；", ledger.blockers()));
        }
        output.append("\n下一步：").append(ledger.nextAction())
                .append("\n只更新当前任务所需动作；不要重复已经完成的步骤。\n[/TASK_LEDGER]");
        return output.toString();
    }

    public void clear(String generationId) {
        ledgers.remove(generationId);
    }

    private boolean requiresLedger(QueryPlan plan) {
        return plan != null && (plan.complexity() == QueryPlan.Complexity.COMPLEX
                || plan.intent() == QueryPlan.Intent.COMPARE
                || plan.intent() == QueryPlan.Intent.MULTI_HOP
                || plan.variants().stream().filter(item -> item.type() == QueryPlan.VariantType.DECOMPOSED).count() > 1);
    }

    private List<String> evidenceIds(Map<String, Object> data) {
        if (data == null || !(data.get("results") instanceof List<?> results)) {
            return List.of();
        }
        return results.stream()
                .filter(SearchResult.class::isInstance)
                .map(SearchResult.class::cast)
                .map(result -> result.getFileMd5() + ':' + result.getChunkId())
                .distinct()
                .toList();
    }

    private List<AgentTaskLedger.TaskItem> activateFirstPending(List<AgentTaskLedger.TaskItem> todos) {
        boolean hasInProgress = todos.stream().anyMatch(item -> item.status() == AgentTaskLedger.Status.IN_PROGRESS);
        if (hasInProgress) {
            return todos;
        }
        boolean[] activated = {false};
        return todos.stream().map(item -> {
            if (!activated[0] && item.status() == AgentTaskLedger.Status.PENDING) {
                activated[0] = true;
                return copy(item, AgentTaskLedger.Status.IN_PROGRESS, item.evidenceIds());
            }
            return item;
        }).toList();
    }

    private AgentTaskLedger.TaskItem copy(AgentTaskLedger.TaskItem item,
                                          AgentTaskLedger.Status status,
                                          List<String> evidenceIds) {
        return new AgentTaskLedger.TaskItem(item.id(), item.title(), status, item.dependencies(), evidenceIds);
    }

    private void validate(AgentTaskLedger ledger) {
        long inProgress = ledger.todos().stream()
                .filter(item -> item.status() == AgentTaskLedger.Status.IN_PROGRESS)
                .count();
        if (inProgress > 1) {
            throw new IllegalStateException("Task Ledger 同时只能有一个 IN_PROGRESS 任务");
        }
    }
}
