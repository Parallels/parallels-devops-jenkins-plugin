package com.parallels.jenkins.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthTokenRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesCredentialsAsJsonFields() throws Exception {
        String json = objectMapper.writeValueAsString(
                new AuthTokenRequest("user\"name@example.com", "pa\"ss\\word"));

        assertTrue(json.contains("\"email\":\"user\\\"name@example.com\""));
        assertTrue(json.contains("\"password\":\"pa\\\"ss\\\\word\""));
    }
}