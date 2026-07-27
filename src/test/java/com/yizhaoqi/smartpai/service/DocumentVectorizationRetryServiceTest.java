package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.DocumentParentChunkRepository;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentVectorizationRetryServiceTest {

    private static final String FILE_MD5 = "04c52be7527b2ef71de2329d4db8b767";

    @Mock
    private FileUploadRepository fileUploadRepository;

    @Mock
    private DocumentVectorRepository documentVectorRepository;

    @Mock
    private DocumentParentChunkRepository documentParentChunkRepository;

    @Mock
    private ElasticsearchService elasticsearchService;

    @Mock
    private UploadService uploadService;

    @Mock
    private ParseService parseService;

    @Mock
    private VectorizationService vectorizationService;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void reindexReusesCompleteParsedChunks() throws Exception {
        FileUpload upload = new FileUpload();
        upload.setFileMd5(FILE_MD5);
        upload.setUserId("1");
        upload.setOrgTag("admin");
        upload.setPublic(false);
        upload.setEstimatedChunkCount(481);

        VectorizationService.VectorizationUsageResult result =
                new VectorizationService.VectorizationUsageResult(74551, 481, "aliyun:qwen3.7-text-embedding:2048");

        when(fileUploadRepository.findFirstByFileMd5OrderByCreatedAtDesc(FILE_MD5))
                .thenReturn(Optional.of(upload));
        when(documentVectorRepository.countByFileMd5(FILE_MD5)).thenReturn(481L);
        when(documentParentChunkRepository.countByFileMd5(FILE_MD5)).thenReturn(175L);
        when(vectorizationService.vectorizeWithUsage(FILE_MD5, "1", "admin", false, "1"))
                .thenReturn(result);

        assertDoesNotThrow(() -> documentService.reindexDocument(FILE_MD5, "1"));

        verify(uploadService, never()).getMergedFileStream(FILE_MD5);
        verify(parseService, never()).parseAndSave(
                anyString(), any(InputStream.class), anyString(), anyString(), anyBoolean());
        verify(documentVectorRepository, never()).deleteByFileMd5(FILE_MD5);
        verify(documentParentChunkRepository, never()).deleteByFileMd5(FILE_MD5);
        verify(elasticsearchService).deleteByFileMd5(FILE_MD5);
        verify(vectorizationService).vectorizeWithUsage(FILE_MD5, "1", "admin", false, "1");
        verify(fileUploadRepository, times(2)).save(upload);
    }
}
