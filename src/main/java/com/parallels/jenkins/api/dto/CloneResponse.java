package com.parallels.jenkins.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response body from {@code PUT /api/v1/machines/{id}/clone}.
 *
 * <pre>
 * {
 *   "id":     "&lt;string&gt;",
 *   "status": "&lt;string&gt;",
 *   "error":  "&lt;string&gt;"
 * }
 * </pre>
 *
 * {@code id} is the new clone's VM ID. {@code error} is non-empty when
 * the service reports a partial failure.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloneResponse {

    private String id;
    private String status;
    private String error;

    public CloneResponse() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
