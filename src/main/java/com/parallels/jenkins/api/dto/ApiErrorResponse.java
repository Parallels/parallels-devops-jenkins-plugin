package com.parallels.jenkins.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Deserialised body of a non-2xx response from prl-devops-service.
 *
 * <pre>
 * {
 *   "code":    123,
 *   "message": "&lt;string&gt;",
 *   "stack": [
 *     { "code": 0, "description": "&lt;string&gt;", "error": "&lt;string&gt;", "path": "&lt;string&gt;" }
 *   ]
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiErrorResponse {

    private int code;
    private String message;
    private List<StackEntry> stack;

    public ApiErrorResponse() {}

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<StackEntry> getStack() {
        return stack;
    }

    public void setStack(List<StackEntry> stack) {
        this.stack = stack;
    }

    @Override
    public String toString() {
        return "ApiErrorResponse{code=" + code + ", message='" + message + "'}";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StackEntry {
        private int code;
        private String description;
        private String error;
        private String path;

        public StackEntry() {}

        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }
}
