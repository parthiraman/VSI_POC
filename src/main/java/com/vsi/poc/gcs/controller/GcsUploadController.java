package com.vsi.poc.gcs.controller;

import com.vsi.poc.gcs.model.UploadSession;
import com.vsi.poc.gcs.service.GcsStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * REST controller for chunked file uploads to Google Cloud Storage.
 *
 * <h2>Chunked upload protocol</h2>
 * <pre>
 * 1. POST /api/files/upload/initiate
 *      ?objectName=uploads/report.pdf&amp;contentType=application/pdf&amp;totalChunks=5
 *    ← 200 { sessionId, objectName, totalChunks, receivedChunks, completed }
 *
 * 2. POST /api/files/upload/chunk
 *      ?sessionId=&lt;id&gt;&amp;chunkIndex=0
 *      Body: multipart/form-data, field name "chunk"   (≤ 10 MB per Apigee limit)
 *    ← 200 { sessionId, receivedChunks, totalChunks }
 *    (repeat for each chunk)
 *
 * 3. POST /api/files/upload/complete?sessionId=&lt;id&gt;
 *    ← 200 { objectName }   (assembles all chunks and pushes to GCS)
 *
 * Optional:
 *   GET /api/files/upload/status/{sessionId}  ← current session status
 * </pre>
 */
@Slf4j
@RestController
@RequestMapping("/api/files/upload")
@RequiredArgsConstructor
public class GcsUploadController {

    private final GcsStorageService gcsStorageService;

    /**
     * Step 1 – Initiates a new chunked upload session.
     *
     * @param objectName  target GCS object path (e.g. {@code "uploads/report.pdf"})
     * @param contentType MIME type (defaults to {@code application/octet-stream})
     * @param totalChunks total number of chunks the client will send
     * @return the created {@link UploadSession}
     */
    @PostMapping("/initiate")
    public ResponseEntity<UploadSession> initiateUpload(
            @RequestParam String objectName,
            @RequestParam(defaultValue = "application/octet-stream") String contentType,
            @RequestParam int totalChunks) {

        log.info("Initiate upload: objectName={}, contentType={}, totalChunks={}", objectName, contentType, totalChunks);
        UploadSession session = gcsStorageService.initiateUpload(objectName, contentType, totalChunks);
        return ResponseEntity.ok(session);
    }

    /**
     * Step 2 – Uploads a single chunk.
     *
     * <p>Each chunk must be ≤&nbsp;10&nbsp;MB (Apigee gateway limit). Chunks can be
     * sent in any order; the server stores them locally and assembles them in order
     * during {@link #completeUpload}.
     *
     * @param sessionId  upload session identifier from step 1
     * @param chunkIndex zero-based chunk index
     * @param chunk      the chunk data as a multipart file part named {@code "chunk"}
     * @return a summary with current progress
     */
    @PostMapping(value = "/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadChunk(
            @RequestParam String sessionId,
            @RequestParam int chunkIndex,
            @RequestParam("chunk") MultipartFile chunk) {

        log.info("Received chunk {}, size={} bytes, sessionId={}", chunkIndex, chunk.getSize(), sessionId);
        UploadSession session = gcsStorageService.uploadChunk(sessionId, chunkIndex, chunk);

        Map<String, Object> response = Map.of(
                "sessionId", session.getSessionId(),
                "chunkIndex", chunkIndex,
                "receivedChunks", session.getReceivedChunks(),
                "totalChunks", session.getTotalChunks()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Step 3 – Completes the upload: assembles all chunks and pushes the result to GCS.
     *
     * <p>All chunks must have been received before calling this endpoint.
     *
     * @param sessionId upload session identifier
     * @return the GCS object name of the uploaded file
     */
    @PostMapping("/complete")
    public ResponseEntity<Map<String, String>> completeUpload(@RequestParam String sessionId) {
        log.info("Complete upload: sessionId={}", sessionId);
        String objectName = gcsStorageService.completeUpload(sessionId);
        return ResponseEntity.ok(Map.of("objectName", objectName));
    }

    /**
     * Returns the current status of an upload session.
     *
     * @param sessionId upload session identifier
     * @return the {@link UploadSession}
     */
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<UploadSession> getSessionStatus(@PathVariable String sessionId) {
        UploadSession session = gcsStorageService.getSessionStatus(sessionId);
        return ResponseEntity.ok(session);
    }
}
