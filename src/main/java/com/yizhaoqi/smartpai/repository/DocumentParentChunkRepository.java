package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.DocumentParentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DocumentParentChunkRepository extends JpaRepository<DocumentParentChunk, Long> {

    List<DocumentParentChunk> findByFileMd5OrderByParentIndexAsc(String fileMd5);

    @Transactional
    @Modifying
    @Query(value = "DELETE FROM document_parent_chunks WHERE file_md5 = ?1", nativeQuery = true)
    void deleteByFileMd5(String fileMd5);
}
