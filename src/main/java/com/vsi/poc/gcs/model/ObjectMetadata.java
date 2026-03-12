package com.vsi.poc.gcs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata returned when a client queries information about a GCS object before
 * initiating a chunked download.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObjectMetadata {

    /** GCS object name (path). */
    private String objectName;

    /** Total size of the object in bytes. */
    private long sizeBytes;

    /** MIME content-type stored in GCS. */
    private String contentType;

    /** Server-recommended chunk size in bytes for downloads. */
    private long recommendedChunkSizeBytes;

    /** Total number of chunks at the recommended chunk size. */
    private int totalChunks;
}
