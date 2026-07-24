package com.yizhaoqi.smartpai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.config.AgenticRagProperties;
import com.yizhaoqi.smartpai.rag.model.QueryPlan;
import com.yizhaoqi.smartpai.service.LlmProviderRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QueryPlanningService {

    private static final Logger logger = LoggerFactory.getLogger(QueryPlanningService.class);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);
    private static final Pattern ENTITY_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{1,}|[一-龥]{2,12}");
    private static final Set<String> STOP_WORDS = Set.of(
            "什么", "怎么", "如何", "为什么", "是否", "可以", "一下", "这个", "那个", "相关", "介绍", "请问", "帮我"
    );

    private final LlmProviderRouter llmProviderRouter;
    private final ObjectMapper objectMapper;
    private final AgenticRagProperties properties;

    public QueryPlanningService(LlmProviderRouter llmProviderRouter,
                                ObjectMapper objectMapper,
                                AgenticRagProperties properties) {
        this.llmProviderRouter = llmProviderRouter;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public QueryPlan plan(String query, String requesterId) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            return fallbackPlan(normalized, "EMPTY_QUERY");
        }
        if (!properties.getQueryPlanning().isLlmEnabled()) {
            return fallbackPlan(normalized, "RULE_BASED");
        }

        try {
            List<Map<String, Object>> messages = List.of(
                    message("system", plannerSystemPrompt()),
                    message("user", normalized)
            );
            LlmProviderRouter.ReActTurn turn = llmProviderRouter.completeReActTurn(
                    requesterId,
                    messages,
                    List.of(),
                    properties.getQueryPlanning().getMaxCompletionTokens()
            );
            return parsePlan(normalized, turn.content());
        } catch (Exception exception) {
            logger.warn("查询规划模型失败，回退到规则规划: query={}, reason={}", normalized, exception.getMessage());
            return fallbackPlan(normalized, "RULE_FALLBACK");
        }
    }

    private QueryPlan parsePlan(String originalQuery, String content) throws Exception {
        Matcher matcher = JSON_OBJECT.matcher(content == null ? "" : content);
        if (!matcher.find()) {
            throw new IllegalArgumentException("查询规划未返回 JSON 对象");
        }
        JsonNode root = objectMapper.readTree(matcher.group());
        QueryPlan.Intent intent = parseEnum(root.path("intent").asText(), QueryPlan.Intent.KNOWLEDGE_QA);
        QueryPlan.Complexity complexity = parseEnum(root.path("complexity").asText(), QueryPlan.Complexity.SIMPLE);
        List<String> entities = textArray(root.path("entities"));
        List<String> constraints = textArray(root.path("constraints"));
        Map<String, String> knownSlots = textObject(root.path("knownSlots"));
        List<String> missingSlots = textArray(root.path("missingSlots"));
        boolean clarificationRequired = root.path("clarificationRequired").asBoolean(false);
        String clarificationQuestion = root.path("clarificationQuestion").asText("").trim();
        List<String> clarificationOptions = textArray(root.path("clarificationOptions"));
        List<QueryPlan.QueryVariant> variants = new ArrayList<>();
        variants.add(new QueryPlan.QueryVariant(QueryPlan.VariantType.ORIGINAL, originalQuery, "保留用户原始表达和实体"));

        JsonNode variantNodes = root.path("variants");
        if (variantNodes.isArray()) {
            for (JsonNode node : variantNodes) {
                QueryPlan.VariantType type = parseEnum(node.path("type").asText(), QueryPlan.VariantType.SEMANTIC);
                String variantQuery = node.path("query").asText("").trim();
                String purpose = node.path("purpose").asText("").trim();
                if (!variantQuery.isEmpty()) {
                    variants.add(new QueryPlan.QueryVariant(type, variantQuery, purpose));
                }
            }
        }

        variants = deduplicateAndLimit(variants, properties.getQueryPlanning().getMaxVariants());
        return new QueryPlan(
                originalQuery,
                intent,
                complexity,
                root.path("retrievalRequired").asBoolean(intent != QueryPlan.Intent.CHAT),
                clarificationRequired,
                entities,
                constraints,
                knownSlots,
                missingSlots,
                clarificationRequired ? defaultClarificationQuestion(clarificationQuestion, missingSlots) : null,
                clarificationOptions,
                variants,
                clamp(root.path("confidence").asDouble(0.75d)),
                "LLM_STRUCTURED"
        );
    }

    private QueryPlan fallbackPlan(String query, String planner) {
        QueryPlan.Intent intent = inferIntent(query);
        boolean complex = looksComplex(query, intent);
        List<String> entities = extractEntities(query);
        FallbackClarification clarification = inferFallbackClarification(query, intent, entities);
        List<QueryPlan.QueryVariant> variants = new ArrayList<>();
        variants.add(new QueryPlan.QueryVariant(QueryPlan.VariantType.ORIGINAL, query, "原始查询"));

        String lexical = buildLexicalQuery(query);
        if (!lexical.equals(query) && !lexical.isBlank()) {
            variants.add(new QueryPlan.QueryVariant(QueryPlan.VariantType.LEXICAL, lexical, "保留实体和高信息量关键词"));
        }
        if (complex) {
            for (String part : splitSubQueries(query)) {
                variants.add(new QueryPlan.QueryVariant(QueryPlan.VariantType.DECOMPOSED, part, "复杂问题的原子子问题"));
            }
        }

        return new QueryPlan(
                query,
                intent,
                complex ? QueryPlan.Complexity.COMPLEX : QueryPlan.Complexity.SIMPLE,
                intent != QueryPlan.Intent.CHAT,
                clarification.required(),
                entities,
                List.of(),
                Map.of(),
                clarification.missingSlots(),
                clarification.question(),
                clarification.options(),
                deduplicateAndLimit(variants, properties.getQueryPlanning().getMaxVariants()),
                0.62d,
                planner
        );
    }

    private QueryPlan.Intent inferIntent(String query) {
        String lower = query.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "总结", "归纳", "摘要", "提炼")) {
            return QueryPlan.Intent.SUMMARY;
        }
        if (containsAny(lower, "对比", "比较", "区别", "差异", "优缺点")) {
            return QueryPlan.Intent.COMPARE;
        }
        if (containsAny(lower, "执行", "创建", "删除", "修改", "提交", "发送")) {
            return QueryPlan.Intent.ACTION;
        }
        if (containsAny(lower, "分别", "结合", "关系", "导致", "影响") && query.length() > 18) {
            return QueryPlan.Intent.MULTI_HOP;
        }
        return QueryPlan.Intent.KNOWLEDGE_QA;
    }

    private boolean looksComplex(String query, QueryPlan.Intent intent) {
        return intent == QueryPlan.Intent.COMPARE
                || intent == QueryPlan.Intent.MULTI_HOP
                || query.length() > 42
                || containsAny(query, "以及", "并且", "同时", "分别", "然后");
    }

    private List<String> extractEntities(String query) {
        LinkedHashSet<String> entities = new LinkedHashSet<>();
        Matcher matcher = ENTITY_PATTERN.matcher(query);
        while (matcher.find() && entities.size() < 8) {
            String value = matcher.group();
            if (!STOP_WORDS.contains(value)) {
                entities.add(value);
            }
        }
        return List.copyOf(entities);
    }

    private String buildLexicalQuery(String query) {
        String cleaned = query.replaceAll("[，。！？；：、,.!?;:]", " ");
        List<String> terms = new ArrayList<>();
        Matcher matcher = ENTITY_PATTERN.matcher(cleaned);
        while (matcher.find()) {
            String term = matcher.group();
            if (!STOP_WORDS.contains(term) && !terms.contains(term)) {
                terms.add(term);
            }
        }
        return terms.isEmpty() ? query : String.join(" ", terms);
    }

    private List<String> splitSubQueries(String query) {
        String[] parts = query.split("(?:以及|并且|同时|分别|然后|与|和|、)");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 4 && !trimmed.equals(query)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private List<QueryPlan.QueryVariant> deduplicateAndLimit(List<QueryPlan.QueryVariant> variants, int limit) {
        Map<String, QueryPlan.QueryVariant> unique = new LinkedHashMap<>();
        for (QueryPlan.QueryVariant variant : variants) {
            if (variant == null || variant.query() == null || variant.query().isBlank()) {
                continue;
            }
            unique.putIfAbsent(variant.query().trim().toLowerCase(Locale.ROOT), variant);
        }
        return unique.values().stream().limit(Math.max(1, limit)).toList();
    }

    private List<String> textArray(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String value = item.asText("").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        });
        return values;
    }

    private Map<String, String> textObject(JsonNode node) {
        if (!node.isObject()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            String value = entry.getValue().asText("").trim();
            if (!value.isEmpty()) {
                values.put(entry.getKey(), value);
            }
        });
        return values;
    }

    private FallbackClarification inferFallbackClarification(String query,
                                                              QueryPlan.Intent intent,
                                                              List<String> entities) {
        String normalized = query == null ? "" : query.trim();
        if (intent == QueryPlan.Intent.ACTION && lacksActionTarget(normalized)) {
            return new FallbackClarification(
                    true,
                    List.of("actionTarget"),
                    "你希望我对哪个具体对象执行这个操作？",
                    List.of()
            );
        }
        if (intent == QueryPlan.Intent.COMPARE && entities.size() < 2) {
            return new FallbackClarification(
                    true,
                    List.of("comparisonTargets"),
                    "你希望比较哪两个具体对象？",
                    List.of()
            );
        }
        return new FallbackClarification(false, List.of(), null, List.of());
    }

    private boolean lacksActionTarget(String query) {
        String normalized = query.replaceFirst("^(?:请|请你|麻烦|帮我|帮忙|给我)+", "").trim();
        String tail = normalized.replaceFirst("^(?:执行|创建|删除|修改|提交|发送)", "").trim();
        return tail.isBlank() || Set.of("一下", "这个", "那个", "它", "吧").contains(tail);
    }

    private String defaultClarificationQuestion(String question, List<String> missingSlots) {
        if (question != null && !question.isBlank()) {
            return question;
        }
        if (missingSlots != null && !missingSlots.isEmpty()) {
            return "为了准确继续，请补充这个关键信息：" + missingSlots.get(0);
        }
        return "为了准确继续，你能再补充一下具体目标或范围吗？";
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private double clamp(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private <T extends Enum<T>> T parseEnum(String value, T fallback) {
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), value.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String plannerSystemPrompt() {
        return """
                你是企业知识 Agent 的查询规划器。只输出一个 JSON 对象，不要 Markdown，不要解释。
                Schema:
                {
                  "intent":"CHAT|KNOWLEDGE_QA|SUMMARY|COMPARE|MULTI_HOP|ACTION",
                  "complexity":"SIMPLE|COMPLEX",
                  "retrievalRequired":true,
                  "clarificationRequired":false,
                  "entities":["原始实体"],
                  "constraints":["时间/范围/权限限制"],
                  "knownSlots":{"槽位":"已知值"},
                  "missingSlots":["缺失且会改变执行路径的槽位"],
                  "clarificationQuestion":"只询问一个最关键问题",
                  "clarificationOptions":["可选项1","可选项2"],
                  "confidence":0.0,
                  "variants":[
                    {"type":"LEXICAL|SEMANTIC|DECOMPOSED","query":"检索查询","purpose":"用途"}
                  ]
                }
                规则：原始查询由系统自动保留；最多生成 3 个额外查询；不得丢失专有名词、编号和限制条件；
                只有缺失信息会改变知识域、核心实体、时间结论或执行风险时才设置 clarificationRequired=true；
                一次只询问一个信息增益最高的问题；简单问题不生成同义句；复杂比较或多跳问题优先拆成原子子问题；
                不要编造用户未提供的事实。
                """;
    }

    private record FallbackClarification(
            boolean required,
            List<String> missingSlots,
            String question,
            List<String> options
    ) {
    }
}
