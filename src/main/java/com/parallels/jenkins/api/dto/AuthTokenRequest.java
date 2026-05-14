package com.parallels.jenkins.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body for {@code POST /api/v1/auth/token}.
 */
public class AuthTokenRequest {

    @JsonProperty("email")
    private final String email;

    @JsonProperty("password")
    private final String password;

    public AuthTokenRequest(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() { return email; }
    public String getPassword() { return password; }
}