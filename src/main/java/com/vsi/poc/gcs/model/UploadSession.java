package com.vsi.poc.gcs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks the server-side state of an in-progress chunked upload session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadSession {

    /** Unique session identifier returned to the client on initiation. */
    private String sessionId;

    /** Final GCS object name (path) after assembly. */
    private String objectName;

    /** MIME content-type of the file being uploaded. */
    private String contentType;

    /** Total number of chunks expected from the client. */
    private int totalChunks;

    /** Number of chunks successfully received so far. */
    private int receivedChunks;

    /** Whether all chunks have been received and the file uploaded to GCS. */
    private boolean completed;
}
