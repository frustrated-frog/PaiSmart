package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.repository.DocumentParentChunkRepository;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentParsingStateServiceTest {

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
    private StringRedisTemplate stringRedisTemplate;

    @InjectMocks
    private DocumentService documentService;

    private FileUpload upload;

    @BeforeEach
    void setUp() {
        upload = new FileUpload();
        upload.setFileMd5(FILE_MD5);
        upload.setEstimatedChunkCount(481);
        when(fileUploadRepository.findFirstByFileMd5OrderByCreatedAtDesc(FILE_MD5))
                .thenReturn(Optional.of(upload));
    }

    @Test
    void reusesCompleteParsedChunks() {
        when(documentVectorRepository.countByFileMd5(FILE_MD5)).thenReturn(481L);
        when(documentParentChunkRepository.countByFileMd5(FILE_MD5)).thenReturn(175L);

        assertFalse(documentService.prepareUploadParsing(FILE_MD5));

        verify(documentVectorRepository, never()).deleteByFileMd5(FILE_MD5);
        verify(documentParentChunkRepository, never()).deleteByFileMd5(FILE_MD5);
        verify(elasticsearchService, never()).deleteByFileMd5(FILE_MD5);
    }

    @Test
    void clearsIncompleteParsedChunksBeforeParsingAgain() {
        when(documentVectorRepository.countByFileMd5(FILE_MD5)).thenReturn(120L);
        when(documentParentChunkRepository.countByFileMd5(FILE_MD5)).thenReturn(40L);

        assertTrue(documentService.prepareUploadParsing(FILE_MD5));

        verify(elasticsearchService).deleteByFileMd5(FILE_MD5);
        verify(documentVectorRepository).deleteByFileMd5(FILE_MD5);
        verify(documentParentChunkRepository).deleteByFileMd5(FILE_MD5);
    }

    @Test
    void parsesNormallyWhenNoPersistedChunksExist() {
        when(documentVectorRepository.countByFileMd5(FILE_MD5)).thenReturn(0L);
        when(documentParentChunkRepository.countByFileMd5(FILE_MD5)).thenReturn(0L);

        assertTrue(documentService.prepareUploadParsing(FILE_MD5));

        verify(documentVectorRepository, never()).deleteByFileMd5(FILE_MD5);
        verify(documentParentChunkRepository, never()).deleteByFileMd5(FILE_MD5);
        verify(elasticsearchService, never()).deleteByFileMd5(FILE_MD5);
    }
}
