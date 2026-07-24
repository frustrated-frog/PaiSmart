package com.yizhaoqi.smartpai.rag;

import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.model.DocumentParentChunk;
import com.yizhaoqi.smartpai.repository.DocumentParentChunkRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 检索用细粒度 child chunk，送给模型前再扩展为 parent context。
 * 相同 parent 只保留排名最高的命中，避免邻近 child 重复消耗上下文窗口。
 */
@Service
public class ParentContextAssembler {

    private final DocumentParentChunkRepository repository;

    public ParentContextAssembler(DocumentParentChunkRepository repository) {
        this.repository = repository;
    }

    public AssemblyOutcome assemble(List<SearchResult> rankedResults, int topK) {
        if (rankedResults == null || rankedResults.isEmpty()) {
            return new AssemblyOutcome(List.of(), 0, 0);
        }

        List<Long> parentIds = rankedResults.stream()
                .map(SearchResult::getParentChunkId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, DocumentParentChunk> parents = repository.findAllById(parentIds).stream()
                .collect(Collectors.toMap(DocumentParentChunk::getId, Function.identity()));

        Map<String, SearchResult> deduplicated = new LinkedHashMap<>();
        int expanded = 0;
        for (SearchResult result : rankedResults) {
            Long parentId = result.getParentChunkId();
            String key = parentId == null
                    ? result.getFileMd5() + ":child:" + result.getChunkId()
                    : result.getFileMd5() + ":parent:" + parentId;
            if (deduplicated.containsKey(key)) {
                continue;
            }
            DocumentParentChunk parent = parentId == null ? null : parents.get(parentId);
            if (parent != null) {
                result.setTextContent(parent.getTextContent());
                result.setPageNumber(parent.getPageNumber() != null ? parent.getPageNumber() : result.getPageNumber());
                result.setAnchorText(parent.getAnchorText() != null ? parent.getAnchorText() : result.getAnchorText());
                expanded++;
            }
            deduplicated.put(key, result);
            if (deduplicated.size() >= Math.max(1, topK)) {
                break;
            }
        }

        List<SearchResult> assembled = new ArrayList<>(deduplicated.values());
        for (int index = 0; index < assembled.size(); index++) {
            assembled.get(index).setFinalRank(index + 1);
        }
        return new AssemblyOutcome(List.copyOf(assembled), expanded, rankedResults.size() - assembled.size());
    }

    public record AssemblyOutcome(List<SearchResult> results, int expandedCount, int deduplicatedCount) {
    }
}
