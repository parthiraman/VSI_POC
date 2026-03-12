package com.vsipoc.api.controller;

import com.vsipoc.api.model.Message;
import com.vsipoc.api.service.MessageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the message processing pipeline as HTTP endpoints.
 *
 * <p>Mirrors the IBM Integration Bus flow:
 * [MQ Input (InputQ)] --> [Compute Node] --> [MQ Output (OutputQ)]
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Processes a message by copying its entire content (headers + body).
     * Equivalent to the active CopyEntireMessage() procedure in Test_Build_Compute.esql.
     *
     * @param inputMessage the input message (received on InputQ)
     * @return the processed output message (sent to OutputQ)
     */
    @PostMapping("/process")
    public ResponseEntity<Message> processMessage(@Valid @RequestBody Message inputMessage) {
        Message outputMessage = messageService.copyEntireMessage(inputMessage);
        return ResponseEntity.ok(outputMessage);
    }

    /**
     * Processes a message by copying only its headers.
     * Equivalent to the CopyMessageHeaders() procedure in Test_Build_Compute.esql.
     *
     * @param inputMessage the input message (received on InputQ)
     * @return the output message containing only the headers
     */
    @PostMapping("/process/headers")
    public ResponseEntity<Message> processMessageHeaders(@RequestBody Message inputMessage) {
        Message outputMessage = messageService.copyMessageHeaders(inputMessage);
        return ResponseEntity.ok(outputMessage);
    }

    /**
     * Health check endpoint.
     *
     * @return a status message indicating the API is running
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("VSI POC Message Processing API is running");
    }
}
