package com.vsi.poc.gcs.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Runtime exception thrown when a GCS operation fails or an upload session
 * cannot be found / is in an invalid state.
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class GcsException extends RuntimeException {

    public GcsException(String message) {
        super(message);
    }

    public GcsException(String message, Throwable cause) {
        super(message, cause);
    }
}
