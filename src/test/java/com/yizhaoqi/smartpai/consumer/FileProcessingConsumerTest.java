package com.yizhaoqi.smartpai.consumer;

import com.yizhaoqi.smartpai.model.FileProcessingTask;
import com.yizhaoqi.smartpai.service.DocumentService;
import com.yizhaoqi.smartpai.service.ParseService;
import com.yizhaoqi.smartpai.service.VectorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileProcessingConsumerTest {

    private static final String FILE_MD5 = "04c52be7527b2ef71de2329d4db8b767";

    @Mock
    private ParseService parseService;

    @Mock
    private VectorizationService vectorizationService;

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private FileProcessingConsumer consumer;

    @Test
    void retriesVectorizationWithoutParsingCompleteChunksAgain() throws Exception {
        FileProcessingTask task = new FileProcessingTask(
                FILE_MD5,
                null,
                "dive_into_claude_code原文.pdf",
                "1",
                "admin",
                false,
                FileProcessingTask.TASK_TYPE_UPLOAD_PROCESS,
                "1"
        );
        VectorizationService.VectorizationUsageResult result =
                new VectorizationService.VectorizationUsageResult(74551, 481, "aliyun:qwen3.7-text-embedding:2048");

        when(documentService.prepareUploadParsing(FILE_MD5)).thenReturn(false);
        when(vectorizationService.vectorizeWithUsage(FILE_MD5, "1", "admin", false, "1"))
                .thenReturn(result);

        assertDoesNotThrow(() -> consumer.processTask(task));

        verify(parseService, never()).parseAndSave(
                anyString(), any(InputStream.class), anyString(), anyString(), anyBoolean());
        verify(vectorizationService).vectorizeWithUsage(FILE_MD5, "1", "admin", false, "1");
        verify(documentService).markVectorizationCompleted(eq(FILE_MD5), eq(result));
        verify(documentService, never()).markVectorizationFailed(eq(FILE_MD5), any(Throwable.class));
    }
}
