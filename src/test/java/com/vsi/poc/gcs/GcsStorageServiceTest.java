package com.vsi.poc.gcs;

import com.google.cloud.storage.Storage;
import com.vsi.poc.gcs.exception.GcsException;
import com.vsi.poc.gcs.exception.ResourceNotFoundException;
import com.vsi.poc.gcs.model.UploadSession;
import com.vsi.poc.gcs.service.GcsStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link GcsStorageService} covering the chunked upload session
 * lifecycle (initiate → chunk → complete / error paths).
 */
class GcsStorageServiceTest {

    @TempDir
    Path tempDir;

    private GcsStorageService service;

    @BeforeEach
    void setUp() throws IOException {
        Storage mockStorage = mock(Storage.class);
        service = new GcsStorageService(mockStorage);
        ReflectionTestUtils.setField(service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(service, "downloadChunkSizeBytes", 8388608L);
        ReflectionTestUtils.setField(service, "tempDirPath", tempDir.toString());
    }

    // -------------------------------------------------------------------------
    // initiateUpload
    // -------------------------------------------------------------------------

    @Test
    void initiateUpload_returnsSessionWithCorrectFields() {
        UploadSession session = service.initiateUpload("uploads/test.txt", "text/plain", 3);

        assertThat(session.getSessionId()).isNotBlank();
        assertThat(session.getObjectName()).isEqualTo("uploads/test.txt");
        assertThat(session.getContentType()).isEqualTo("text/plain");
        assertThat(session.getTotalChunks()).isEqualTo(3);
        assertThat(session.getReceivedChunks()).isZero();
        assertThat(session.isCompleted()).isFalse();
    }

    @Test
    void initiateUpload_throwsForZeroTotalChunks() {
        assertThatThrownBy(() -> service.initiateUpload("file.txt", "text/plain", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalChunks must be greater than 0");
    }

    @Test
    void initiateUpload_throwsForNegativeTotalChunks() {
        assertThatThrownBy(() -> service.initiateUpload("file.txt", "text/plain", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------
    // uploadChunk
    // -------------------------------------------------------------------------

    @Test
    void uploadChunk_storesChunkAndIncrementsReceivedCount() {
        UploadSession session = service.initiateUpload("uploads/test.txt", "text/plain", 2);
        MockMultipartFile chunk = new MockMultipartFile("chunk", "hello world".getBytes());

        UploadSession updated = service.uploadChunk(session.getSessionId(), 0, chunk);

        assertThat(updated.getReceivedChunks()).isEqualTo(1);
    }

    @Test
    void uploadChunk_throwsForInvalidChunkIndex() {
        UploadSession session = service.initiateUpload("uploads/test.txt", "text/plain", 2);
        MockMultipartFile chunk = new MockMultipartFile("chunk", "data".getBytes());

        assertThatThrownBy(() -> service.uploadChunk(session.getSessionId(), 5, chunk))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    @Test
    void uploadChunk_throwsForUnknownSession() {
        MockMultipartFile chunk = new MockMultipartFile("chunk", "data".getBytes());

        assertThatThrownBy(() -> service.uploadChunk("non-existent-id", 0, chunk))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // completeUpload – error paths (no real GCS connection needed)
    // -------------------------------------------------------------------------

    @Test
    void completeUpload_throwsWhenNotAllChunksReceived() {
        UploadSession session = service.initiateUpload("uploads/test.txt", "text/plain", 3);
        MockMultipartFile chunk = new MockMultipartFile("chunk", "part0".getBytes());
        service.uploadChunk(session.getSessionId(), 0, chunk);

        assertThatThrownBy(() -> service.completeUpload(session.getSessionId()))
                .isInstanceOf(GcsException.class)
                .hasMessageContaining("only 1 received");
    }

    @Test
    void completeUpload_throwsForUnknownSession() {
        assertThatThrownBy(() -> service.completeUpload("unknown-session"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // getSessionStatus
    // -------------------------------------------------------------------------

    @Test
    void getSessionStatus_returnsCorrectSession() {
        UploadSession session = service.initiateUpload("uploads/file.pdf", "application/pdf", 5);
        UploadSession fetched = service.getSessionStatus(session.getSessionId());

        assertThat(fetched.getSessionId()).isEqualTo(session.getSessionId());
        assertThat(fetched.getObjectName()).isEqualTo("uploads/file.pdf");
    }

    @Test
    void getSessionStatus_throwsForUnknownSession() {
        assertThatThrownBy(() -> service.getSessionStatus("does-not-exist"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
