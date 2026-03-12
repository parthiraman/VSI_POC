package com.vsi.poc.gcs.controller;

import com.vsi.poc.gcs.model.ObjectMetadata;
import com.vsi.poc.gcs.service.GcsStorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * REST controller for chunked file downloads from Google Cloud Storage.
 *
 * <h2>Chunked download protocol</h2>
 * <pre>
 * 1. GET /api/files/download/metadata?objectName=uploads/report.pdf
 *    ← 200 { objectName, sizeBytes, contentType, recommendedChunkSizeBytes, totalChunks }
 *
 * 2. GET /api/files/download/chunk
 *         ?objectName=uploads/report.pdf&amp;chunkIndex=0&amp;chunkSize=8388608
 *    ← 206 Partial Content with binary body and headers:
 *         Content-Range: bytes 0-8388607/&lt;totalSize&gt;
 *         X-Chunk-Index: 0
 *         X-Total-Chunks: &lt;n&gt;
 *    (repeat for chunkIndex 1, 2, … until all chunks received)
 * </pre>
 *
 * <p>The default chunk size is 8&nbsp;MB – safely below the Apigee 10&nbsp;MB limit.
 * Callers may override it with the {@code chunkSize} query parameter, but should
 * not exceed 10&nbsp;MB.
 */
@Slf4j
@RestController
@RequestMapping("/api/files/download")
@RequiredArgsConstructor
public class GcsDownloadController {

    private final GcsStorageService gcsStorageService;

    @Value("${gcs.download-chunk-size-bytes:8388608}")
    private long defaultChunkSizeBytes;

    /**
     * Returns metadata for a GCS object so the client can calculate how many
     * chunks to request.
     *
     * @param objectName GCS object path
     * @return {@link ObjectMetadata}
     */
    @GetMapping("/metadata")
    public ResponseEntity<ObjectMetadata> getMetadata(@RequestParam String objectName) {
        log.info("Metadata request for objectName={}", objectName);
        ObjectMetadata metadata = gcsStorageService.getObjectMetadata(objectName);
        return ResponseEntity.ok(metadata);
    }

    /**
     * Downloads a single chunk (byte range) of a GCS object.
     *
     * <p>Returns HTTP&nbsp;206 Partial Content with the chunk bytes in the body.
     * The response includes a {@code Content-Range} header that mirrors the
     * HTTP range specification, plus convenience headers {@code X-Chunk-Index}
     * and {@code X-Total-Chunks}.
     *
     * @param objectName  GCS object path
     * @param chunkIndex  zero-based chunk index
     * @param chunkSize   chunk size in bytes (default 8&nbsp;MB; max 10&nbsp;MB)
     * @param response    HTTP response used to stream the chunk bytes
     * @throws IOException if a stream error occurs
     */
    @GetMapping("/chunk")
    public void downloadChunk(
            @RequestParam String objectName,
            @RequestParam int chunkIndex,
            @RequestParam(required = false) Long chunkSize,
            HttpServletResponse response) throws IOException {

        long effectiveChunkSize = (chunkSize != null && chunkSize > 0) ? chunkSize : defaultChunkSizeBytes;

        ObjectMetadata metadata = gcsStorageService.getObjectMetadata(objectName);
        long totalSize = metadata.getSizeBytes();
        long totalChunksLong = (long) Math.ceil((double) totalSize / effectiveChunkSize);
        int totalChunks = totalChunksLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalChunksLong;

        long startByte = (long) chunkIndex * effectiveChunkSize;
        long endByte = Math.min(startByte + effectiveChunkSize, totalSize) - 1;

        log.info("Chunk download: objectName={}, chunkIndex={}/{}, bytes [{}-{}]",
                objectName, chunkIndex, totalChunks - 1, startByte, endByte);

        String fileName = objectName.contains("/")
                ? objectName.substring(objectName.lastIndexOf('/') + 1)
                : objectName;

        response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
        response.setContentType(metadata.getContentType() != null
                ? metadata.getContentType()
                : "application/octet-stream");
        response.setHeader(HttpHeaders.CONTENT_RANGE,
                "bytes " + startByte + "-" + endByte + "/" + totalSize);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
        response.setHeader("X-Chunk-Index", String.valueOf(chunkIndex));
        response.setHeader("X-Total-Chunks", String.valueOf(totalChunks));
        response.setContentLengthLong(endByte - startByte + 1);

        gcsStorageService.downloadChunk(objectName, chunkIndex, effectiveChunkSize, response.getOutputStream());
    }
}
