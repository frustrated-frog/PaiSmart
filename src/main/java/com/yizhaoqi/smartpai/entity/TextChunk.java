package com.yizhaoqi.smartpai.entity;

import lombok.Getter;
import lombok.Setter;

// 文件分块内容实体类
@Setter
@Getter
public class TextChunk {

    // Getters/Setters
    private int chunkId;       // 分块序号
    private String content;    // 分块内容
    private Integer pageNumber; // PDF 页码
    private String anchorText; // 页内定位锚点
    private Long parentChunkId; // 生成上下文父块
    private Integer parentChunkIndex;
    private String contextualText; // 用于 embedding 的增强文本

    // 构造方法
    public TextChunk(int chunkId, String content) {
        this(chunkId, content, null, null);
    }

    public TextChunk(int chunkId, String content, Integer pageNumber, String anchorText) {
        this(chunkId, content, pageNumber, anchorText, null, null, null);
    }

    public TextChunk(int chunkId,
                     String content,
                     Integer pageNumber,
                     String anchorText,
                     Long parentChunkId,
                     Integer parentChunkIndex,
                     String contextualText) {
        this.chunkId = chunkId;
        this.content = content;
        this.pageNumber = pageNumber;
        this.anchorText = anchorText;
        this.parentChunkId = parentChunkId;
        this.parentChunkIndex = parentChunkIndex;
        this.contextualText = contextualText;
    }
}
