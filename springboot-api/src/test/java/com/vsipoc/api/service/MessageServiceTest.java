package com.vsipoc.api.service;

import com.vsipoc.api.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageServiceTest {

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService();
    }

    @Test
    void copyEntireMessage_copiesHeadersAndBody() {
        Message input = new Message(
                Map.of("Content-Type", "application/xml", "MessageId", "MSG-001"),
                "Hello from InputQ",
                "InputQ");

        Message output = messageService.copyEntireMessage(input);

        assertThat(output.getBody()).isEqualTo("Hello from InputQ");
        assertThat(output.getHeaders()).containsAllEntriesOf(input.getHeaders());
        assertThat(output.getQueue()).isEqualTo("InputQ");
    }

    @Test
    void copyEntireMessage_withNullHeaders_returnsEmptyHeaders() {
        Message input = new Message(null, "body text", "InputQ");

        Message output = messageService.copyEntireMessage(input);

        assertThat(output.getBody()).isEqualTo("body text");
        assertThat(output.getHeaders()).isEmpty();
    }

    @Test
    void copyEntireMessage_returnsIndependentCopy() {
        Map<String, String> originalHeaders = new java.util.HashMap<>();
        originalHeaders.put("MessageId", "MSG-001");
        Message input = new Message(originalHeaders, "body", "InputQ");

        Message output = messageService.copyEntireMessage(input);
        output.getHeaders().put("Extra", "value");

        assertThat(input.getHeaders()).doesNotContainKey("Extra");
    }

    @Test
    void copyMessageHeaders_copiesHeadersOnly() {
        Message input = new Message(
                Map.of("Content-Type", "text/plain", "MessageId", "MSG-002"),
                "Body that should not be copied",
                "InputQ");

        Message output = messageService.copyMessageHeaders(input);

        assertThat(output.getBody()).isNull();
        assertThat(output.getHeaders()).containsAllEntriesOf(input.getHeaders());
        assertThat(output.getQueue()).isEqualTo("InputQ");
    }

    @Test
    void copyMessageHeaders_withNullHeaders_returnsEmptyHeaders() {
        Message input = new Message(null, "some body", "InputQ");

        Message output = messageService.copyMessageHeaders(input);

        assertThat(output.getBody()).isNull();
        assertThat(output.getHeaders()).isEmpty();
    }
}
