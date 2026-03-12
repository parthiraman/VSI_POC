package com.vsipoc.api.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Represents an IBM MQ message with headers and body,
 * mirroring the message structure processed by the Test_Build_Compute ESQL module.
 */
public class Message {

    private Map<String, String> headers;

    @NotBlank(message = "Message body must not be blank")
    private String body;

    private String queue;

    public Message() {
    }

    public Message(Map<String, String> headers, String body, String queue) {
        this.headers = headers;
        this.body = body;
        this.queue = queue;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }
}
