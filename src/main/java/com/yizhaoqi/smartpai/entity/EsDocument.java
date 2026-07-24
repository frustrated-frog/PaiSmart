package com.yizhaoqi.smartpai.entity;


import lombok.Data;

/**
 * Elasticsearch存储的文档实体类
 * 包含文档内容和权限信息
 */
@Data
public class EsDocument {

    private String id;             // 文档唯一标识
    private String fileMd5;        // 文件指纹
    private Integer chunkId;       // 文本分块序号
    private Long parentChunkId;    // 父上下文块 ID
    private Integer parentChunkIndex;
    private String textContent;    // 文本内容
    private String contextualText; // embedding 使用的上下文化文本
    private Integer pageNumber;    // PDF 页码
    private String anchorText;     // 页内定位锚点
    private float[] vector;        // 向量数据（768维）
    private String modelVersion;   // 向量生成模型版本
    private String userId;         // 上传用户ID
    private String orgTag;         // 组织标签
    private boolean isPublic;      // 是否公开

    /**
     * 默认构造函数，用于Jackson反序列化
     */
    public EsDocument() {
    }

    /**
     * 完整构造函数，包含权限字段
     */
    public EsDocument(String id, String fileMd5, int chunkId,
                     Long parentChunkId, Integer parentChunkIndex,
                     String content, String contextualText,
                     Integer pageNumber, String anchorText,
                     float[] vector, String modelVersion, 
                     String userId, String orgTag, boolean isPublic) {
        this.id = id;
        this.fileMd5 = fileMd5;
        this.chunkId = chunkId;
        this.parentChunkId = parentChunkId;
        this.parentChunkIndex = parentChunkIndex;
        this.textContent = content;
        this.contextualText = contextualText;
        this.pageNumber = pageNumber;
        this.anchorText = anchorText;
        this.vector = vector;
        this.modelVersion = modelVersion;
        this.userId = userId;
        this.orgTag = orgTag;
        this.isPublic = isPublic;
    }
    

}
