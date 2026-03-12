package com.vsi.poc.gcs.service;

import com.google.cloud.ReadChannel;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.vsi.poc.gcs.exception.GcsException;
import com.vsi.poc.gcs.exception.ResourceNotFoundException;
import com.vsi.poc.gcs.model.ObjectMetadata;
import com.vsi.poc.gcs.model.UploadSession;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Service responsible for all interactions with Google Cloud Storage.
 *
 * <h2>Chunked upload flow</h2>
 * <ol>
 *   <li>Client calls {@link #initiateUpload} → receives a {@code sessionId}.</li>
 *   <li>Client uploads each chunk (≤&nbsp;10&nbsp;MB) via {@link #uploadChunk}.</li>
 *   <li>After all chunks arrive client calls {@link #completeUpload}, which
 *       assembles the chunks into a single GCS object and cleans up local temp files.</li>
 * </ol>
 *
 * <h2>Chunked download flow</h2>
 * <ol>
 *   <li>Client calls {@link #getObjectMetadata} to learn the total size and
 *       recommended chunk size.</li>
 *   <li>Client iterates over chunk indices and calls {@link #downloadChunk} for
 *       each one, streaming the bytes directly to the HTTP response.</li>
 * </ol>
 *
 * <p>No signed URLs are used; authentication is handled via Application Default
 * Credentials (ADC).
 */
@Slf4j
@Service
public class GcsStorageService {

    private final Storage storage;

    @Value("${gcs.bucket-name}")
    private String bucketName;

    @Value("${gcs.download-chunk-size-bytes:8388608}")
    private long downloadChunkSizeBytes;

    @Value("${gcs.temp-dir:/tmp/gcs-chunks}")
    private String tempDirPath;

    /** In-memory session registry. For production consider a distributed cache. */
    private final Map<String, UploadSession> sessions = new ConcurrentHashMap<>();

    public GcsStorageService(Storage storage) {
        this.storage = storage;
    }

    @PostConstruct
    public void init() throws IOException {
        Path tempDir = Paths.get(tempDirPath);
        if (!Files.exists(tempDir)) {
            Files.createDirectories(tempDir);
            log.info("Created temp directory: {}", tempDir.toAbsolutePath());
        }
    }

    // -------------------------------------------------------------------------
    // Chunked Upload
    // -------------------------------------------------------------------------

    /**
     * Creates a new upload session.
     *
     * @param objectName  desired GCS object path (e.g. {@code "uploads/report.pdf"})
     * @param contentType MIME type of the file
     * @param totalChunks total number of chunks the client will send
     * @return the new {@link UploadSession}
     */
    public UploadSession initiateUpload(String objectName, String contentType, int totalChunks) {
        if (totalChunks <= 0) {
            throw new IllegalArgumentException("totalChunks must be greater than 0");
        }
        String sessionId = UUID.randomUUID().toString();
        UploadSession session = UploadSession.builder()
                .sessionId(sessionId)
                .objectName(objectName)
                .contentType(contentType)
                .totalChunks(totalChunks)
                .receivedChunks(0)
                .completed(false)
                .build();
        sessions.put(sessionId, session);
        log.info("Upload session initiated: sessionId={}, objectName={}, totalChunks={}", sessionId, objectName, totalChunks);
        return session;
    }

    /**
     * Stores a single chunk on local disk.
     *
     * @param sessionId  upload session identifier
     * @param chunkIndex zero-based index of this chunk
     * @param chunk      the chunk data
     * @return updated {@link UploadSession}
     */
    public UploadSession uploadChunk(String sessionId, int chunkIndex, MultipartFile chunk) {
        UploadSession session = getSession(sessionId);

        if (session.isCompleted()) {
            throw new GcsException("Upload session " + sessionId + " is already completed");
        }
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new IllegalArgumentException(
                    "chunkIndex " + chunkIndex + " is out of range [0, " + (session.getTotalChunks() - 1) + "]");
        }

        Path chunkFile = chunkPath(sessionId, chunkIndex);
        try {
            Files.write(chunkFile, chunk.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Stored chunk {}/{} for session {}", chunkIndex + 1, session.getTotalChunks(), sessionId);
        } catch (IOException e) {
            throw new GcsException("Failed to store chunk " + chunkIndex + " for session " + sessionId, e);
        }

        session.setReceivedChunks((int) countReceivedChunks(sessionId));
        return session;
    }

    /**
     * Assembles all stored chunks and uploads the resulting object to GCS.
     *
     * @param sessionId upload session identifier
     * @return the GCS object name of the uploaded file
     */
    public String completeUpload(String sessionId) {
        UploadSession session = getSession(sessionId);

        if (session.isCompleted()) {
            throw new GcsException("Upload session " + sessionId + " is already completed");
        }

        long received = countReceivedChunks(sessionId);
        if (received != session.getTotalChunks()) {
            throw new GcsException(
                    "Cannot complete upload: expected " + session.getTotalChunks() +
                    " chunks but only " + received + " received for session " + sessionId);
        }

        BlobId blobId = BlobId.of(bucketName, session.getObjectName());
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(session.getContentType())
                .build();

        try (WriteChannel writer = storage.writer(blobInfo)) {
            byte[] buffer = new byte[8192];
            for (int i = 0; i < session.getTotalChunks(); i++) {
                Path chunkFile = chunkPath(sessionId, i);
                try (InputStream in = Files.newInputStream(chunkFile)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        writer.write(ByteBuffer.wrap(buffer, 0, read));
                    }
                }
                log.debug("Written chunk {} to GCS for session {}", i, sessionId);
            }
        } catch (IOException e) {
            throw new GcsException("Failed to upload assembled file to GCS for session " + sessionId, e);
        }

        session.setCompleted(true);
        cleanupSession(sessionId);
        log.info("Upload completed: sessionId={}, objectName={}", sessionId, session.getObjectName());
        return session.getObjectName();
    }

    /**
     * Returns the current state of an upload session.
     *
     * @param sessionId upload session identifier
     * @return the {@link UploadSession}
     */
    public UploadSession getSessionStatus(String sessionId) {
        return getSession(sessionId);
    }

    // -------------------------------------------------------------------------
    // Chunked Download
    // -------------------------------------------------------------------------

    /**
     * Returns metadata for a GCS object, including its size and the recommended
     * chunk size/count for chunked downloads.
     *
     * @param objectName GCS object path
     * @return {@link ObjectMetadata}
     */
    public ObjectMetadata getObjectMetadata(String objectName) {
        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        if (blob == null || !blob.exists()) {
            throw new ResourceNotFoundException("GCS object not found: " + objectName);
        }

        long sizeBytes = blob.getSize();
        long totalChunksLong = (long) Math.ceil((double) sizeBytes / downloadChunkSizeBytes);
        int totalChunks = totalChunksLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalChunksLong;

        return ObjectMetadata.builder()
                .objectName(objectName)
                .sizeBytes(sizeBytes)
                .contentType(blob.getContentType())
                .recommendedChunkSizeBytes(downloadChunkSizeBytes)
                .totalChunks(totalChunks)
                .build();
    }

    /**
     * Streams a byte range of a GCS object to the provided {@link OutputStream}.
     *
     * <p>The byte range is calculated from {@code chunkIndex} and {@code chunkSizeBytes},
     * so the caller does not need to compute byte offsets manually.
     *
     * @param objectName    GCS object path
     * @param chunkIndex    zero-based chunk index
     * @param chunkSizeBytes size of each chunk in bytes (should be ≤&nbsp;10&nbsp;MB)
     * @param outputStream  target stream (e.g. the HTTP response output stream)
     * @throws IOException if a read or write error occurs
     */
    public void downloadChunk(String objectName, int chunkIndex, long chunkSizeBytes, OutputStream outputStream)
            throws IOException {
        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        if (blob == null || !blob.exists()) {
            throw new ResourceNotFoundException("GCS object not found: " + objectName);
        }

        long totalSize = blob.getSize();
        long startByte = (long) chunkIndex * chunkSizeBytes;
        if (startByte >= totalSize) {
            throw new IllegalArgumentException(
                    "chunkIndex " + chunkIndex + " is beyond the end of the object (size=" + totalSize + " bytes)");
        }
        long endByte = Math.min(startByte + chunkSizeBytes, totalSize);
        long bytesToRead = endByte - startByte;

        log.info("Downloading chunk {}: bytes [{}, {}) of '{}' (total {} bytes)",
                chunkIndex, startByte, endByte, objectName, totalSize);

        try (ReadChannel reader = storage.reader(BlobId.of(bucketName, objectName));
             InputStream inputStream = Channels.newInputStream(reader)) {

            reader.seek(startByte);
            byte[] buffer = new byte[8192];
            long remaining = bytesToRead;
            int read;
            while (remaining > 0 &&
                    (read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                outputStream.write(buffer, 0, read);
                remaining -= read;
            }
        }
        outputStream.flush();
    }

    /**
     * Downloads a complete GCS object to the provided {@link OutputStream}.
     * Use this only for small files; for large files prefer {@link #downloadChunk}.
     *
     * @param objectName   GCS object path
     * @param outputStream target stream
     * @throws IOException if a read or write error occurs
     */
    public void downloadObject(String objectName, OutputStream outputStream) throws IOException {
        Blob blob = storage.get(BlobId.of(bucketName, objectName));
        if (blob == null || !blob.exists()) {
            throw new ResourceNotFoundException("GCS object not found: " + objectName);
        }
        blob.downloadTo(outputStream);
        outputStream.flush();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private UploadSession getSession(String sessionId) {
        UploadSession session = sessions.get(sessionId);
        if (session == null) {
            throw new ResourceNotFoundException("Upload session not found: " + sessionId);
        }
        return session;
    }

    private Path chunkPath(String sessionId, int chunkIndex) {
        return Paths.get(tempDirPath, sessionId + "_chunk_" + chunkIndex);
    }

    private long countReceivedChunks(String sessionId) {
        try (Stream<Path> stream = Files.list(Paths.get(tempDirPath))) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(sessionId + "_chunk_"))
                    .count();
        } catch (IOException e) {
            throw new GcsException("Failed to list chunk files for session " + sessionId, e);
        }
    }

    private void cleanupSession(String sessionId) {
        try (Stream<Path> stream = Files.list(Paths.get(tempDirPath))) {
            stream.filter(p -> p.getFileName().toString().startsWith(sessionId + "_chunk_"))
                  .forEach(p -> {
                      try {
                          Files.deleteIfExists(p);
                      } catch (IOException e) {
                          log.warn("Failed to delete chunk file {}: {}", p, e.getMessage());
                      }
                  });
        } catch (IOException e) {
            log.warn("Failed to clean up chunks for session {}: {}", sessionId, e.getMessage());
        }
        sessions.remove(sessionId);
        log.info("Cleaned up session {}", sessionId);
    }
}
