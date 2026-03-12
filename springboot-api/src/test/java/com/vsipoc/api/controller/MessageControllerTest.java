package com.vsipoc.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vsipoc.api.model.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/api/messages/health"))
                .andExpect(status().isOk());
    }

    @Test
    void processMessage_copiesEntireMessage() throws Exception {
        Message input = new Message(
                Map.of("MessageId", "MSG-001"),
                "Hello from InputQ",
                "InputQ");

        mockMvc.perform(post("/api/messages/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("Hello from InputQ"))
                .andExpect(jsonPath("$.headers.MessageId").value("MSG-001"))
                .andExpect(jsonPath("$.queue").value("InputQ"));
    }

    @Test
    void processMessage_withMissingBody_returnsBadRequest() throws Exception {
        Message input = new Message(Map.of("MessageId", "MSG-002"), null, "InputQ");

        mockMvc.perform(post("/api/messages/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processMessageHeaders_copiesHeadersOnly() throws Exception {
        Message input = new Message(
                Map.of("Content-Type", "text/plain"),
                "Body should not appear in output",
                "InputQ");

        mockMvc.perform(post("/api/messages/process/headers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").doesNotExist())
                .andExpect(jsonPath("$.headers['Content-Type']").value("text/plain"));
    }

    @Test
    void processMessageHeaders_withoutBody_isAccepted() throws Exception {
        Message input = new Message(Map.of("MessageId", "MSG-003"), null, "InputQ");

        mockMvc.perform(post("/api/messages/process/headers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headers.MessageId").value("MSG-003"))
                .andExpect(jsonPath("$.body").doesNotExist());
    }
}
