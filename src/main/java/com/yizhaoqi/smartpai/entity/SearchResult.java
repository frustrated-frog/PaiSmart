package com.yizhaoqi.smartpai.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SearchResult {
    private String fileMd5;    // 文件指纹
    private Integer chunkId;   // 文本分块序号
    private Long parentChunkId; // 父上下文块 ID
    private Integer parentChunkIndex;
    private String textContent; // 文本内容
    private Double score;      // 搜索得分
    private String fileName;   // 原始文件名
    private String userId;     // 上传用户ID
    private String orgTag;     // 组织标签
    private Boolean isPublic;  // 是否公开
    private Integer pageNumber; // PDF 页码
    private String anchorText; // 页内定位锚点
    private String retrievalMode; // 召回方式
    private String matchedChunkText; // 命中的 chunk 原文
    private Double rawScore; // 当前阶段原始分数（BM25/向量/reranker）
    private Double rrfScore; // RRF 融合分数
    private Double rerankScore; // 重排分数
    private Integer finalRank; // 最终排名（从 1 开始）
    private List<RetrievalHit> retrievalHits = new ArrayList<>(); // 多查询、多通道命中轨迹

    public SearchResult(String fileMd5, Integer chunkId, String textContent, Double score) {
        this(fileMd5, chunkId, textContent, score, null, null, false, null, null, null, null, null);
    }

    public SearchResult(String fileMd5, Integer chunkId, String textContent, Double score, String fileName) {
        this(fileMd5, chunkId, textContent, score, null, null, false, fileName, null, null, null, null);
    }

    public SearchResult(String fileMd5, Integer chunkId, String textContent, Double score, String userId, String orgTag, boolean isPublic) {
        this(fileMd5, chunkId, textContent, score, userId, orgTag, isPublic, null, null, null, null, null);
    }

    public SearchResult(String fileMd5, Integer chunkId, String textContent, Double score, String userId, String orgTag, boolean isPublic, String fileName) {
        this(fileMd5, chunkId, textContent, score, userId, orgTag, isPublic, fileName, null, null, null, null);
    }

    public SearchResult(String fileMd5, Integer chunkId, String textContent, Double score, String userId, String orgTag,
                        boolean isPublic, String fileName, Integer pageNumber, String anchorText) {
        this(fileMd5, chunkId, textContent, score, userId, orgTag, isPublic, fileName, pageNumber, anchorText, null, textContent);
    }

    public SearchResult(String fileMd5, Integer chunkId, String textContent, Double score, String userId, String orgTag,
                        boolean isPublic, String fileName, Integer pageNumber, String anchorText,
                        String retrievalMode, String matchedChunkText) {
        this.fileMd5 = fileMd5;
        this.chunkId = chunkId;
        this.textContent = textContent;
        this.score = score;
        this.userId = userId;
        this.orgTag = orgTag;
        this.isPublic = isPublic;
        this.fileName = fileName;
        this.pageNumber = pageNumber;
        this.anchorText = anchorText;
        this.retrievalMode = retrievalMode;
        this.matchedChunkText = matchedChunkText != null ? matchedChunkText : textContent;
        this.rawScore = score;
    }

    @Data
    public static class RetrievalHit {
        private String channel;
        private String queryType;
        private String query;
        private Integer rank;
        private Double score;
        private Double rrfContribution;

        public RetrievalHit(String channel,
                            String queryType,
                            String query,
                            Integer rank,
                            Double score,
                            Double rrfContribution) {
            this.channel = channel;
            this.queryType = queryType;
            this.query = query;
            this.rank = rank;
            this.score = score;
            this.rrfContribution = rrfContribution;
        }
    }
}
