package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "document_parent_chunks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_parent_chunk_file_index",
                columnNames = {"file_md5", "parent_index"}
        ),
        indexes = {
                @Index(name = "idx_parent_chunk_file", columnList = "file_md5"),
                @Index(name = "idx_parent_chunk_scope", columnList = "user_id,org_tag,is_public")
        }
)
public class DocumentParentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_md5", nullable = false, length = 32)
    private String fileMd5;

    @Column(name = "parent_index", nullable = false)
    private Integer parentIndex;

    @Lob
    @Column(name = "text_content", nullable = false)
    private String textContent;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "anchor_text", length = 512)
    private String anchorText;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "org_tag", length = 50)
    private String orgTag;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
