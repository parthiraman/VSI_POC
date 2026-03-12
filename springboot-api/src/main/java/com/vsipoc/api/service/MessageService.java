package com.vsipoc.api.service;

import com.vsipoc.api.model.Message;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Message processing service that mirrors the ESQL Compute module logic
 * defined in Test_Build_Compute.esql.
 *
 * <p>Provides two processing modes:
 * <ul>
 *   <li>{@link #copyEntireMessage(Message)} – copies the entire message (headers + body),
 *       equivalent to {@code SET OutputRoot = InputRoot}</li>
 *   <li>{@link #copyMessageHeaders(Message)} – copies only the message headers</li>
 * </ul>
 */
@Service
public class MessageService {

    /**
     * Copies the entire input message (headers + body) to the output.
     * This is the primary processing mode, equivalent to CopyEntireMessage() in ESQL.
     *
     * @param inputMessage the message received from the input queue
     * @return a new Message containing the full copy of the input
     */
    public Message copyEntireMessage(Message inputMessage) {
        Map<String, String> outputHeaders = inputMessage.getHeaders() != null
                ? new HashMap<>(inputMessage.getHeaders())
                : new HashMap<>();
        return new Message(outputHeaders, inputMessage.getBody(), inputMessage.getQueue());
    }

    /**
     * Copies only the message headers from the input, leaving the body empty.
     * Equivalent to CopyMessageHeaders() in ESQL.
     *
     * @param inputMessage the message received from the input queue
     * @return a new Message containing only the headers from the input
     */
    public Message copyMessageHeaders(Message inputMessage) {
        Map<String, String> outputHeaders = inputMessage.getHeaders() != null
                ? new HashMap<>(inputMessage.getHeaders())
                : new HashMap<>();
        return new Message(outputHeaders, null, inputMessage.getQueue());
    }
}
