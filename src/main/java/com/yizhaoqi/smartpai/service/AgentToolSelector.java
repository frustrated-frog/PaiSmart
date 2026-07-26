package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** 根据结构化查询计划确定本轮模型可见的工具，避免只依赖 Prompt 自律。 */
@Service
public class AgentToolSelector {

    private static final Set<String> RETRIEVAL_TOOLS = Set.of("search_knowledge", "generate_summary");

    public List<AgentToolRegistry.AgentTool> select(QueryPlan plan,
                                                    List<AgentToolRegistry.AgentTool> tools) {
        List<AgentToolRegistry.AgentTool> safeTools = tools == null ? List.of() : List.copyOf(tools);
        if (plan == null) {
            return safeTools;
        }
        return safeTools.stream()
                .filter(tool -> isVisible(plan, tool.name()))
                .toList();
    }

    private boolean isVisible(QueryPlan plan, String toolName) {
        if (!plan.retrievalRequired()) {
            return !RETRIEVAL_TOOLS.contains(toolName);
        }
        if (plan.intent() == QueryPlan.Intent.SUMMARY) {
            return !"search_knowledge".equals(toolName);
        }
        return true;
    }
}
