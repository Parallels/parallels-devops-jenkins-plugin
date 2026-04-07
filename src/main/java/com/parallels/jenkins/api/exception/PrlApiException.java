package com.parallels.jenkins.api.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parallels.jenkins.api.dto.ApiErrorResponse;

/**
 * Thrown when prl-devops-service returns a non-2xx HTTP response, or when a
 * network-level error occurs while communicating with it.
 */
public class PrlApiException extends Exception {

    private final int httpStatus;
    private final ApiErrorResponse detail;

    public PrlApiException(String message) {
        super(message);
        this.httpStatus = 0;
        this.detail = null;
    }

    public PrlApiException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.detail = null;
    }

    public PrlApiException(int httpStatus, String message, ApiErrorResponse detail) {
        super(message);
        this.httpStatus = httpStatus;
        this.detail = detail;
    }

    public PrlApiException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
        this.detail = null;
    }

    /**
     * Parses the raw response body as {@link ApiErrorResponse} and constructs an exception.
     * Falls back to a plain message if the body cannot be parsed.
     */
    public static PrlApiException fromResponse(int httpStatus, String rawBody, ObjectMapper mapper) {
        ApiErrorResponse detail = null;
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                detail = mapper.readValue(rawBody, ApiErrorResponse.class);
            } catch (Exception ignored) {
                // body not in expected format — use raw text as message
            }
        }
        String message = detail != null && detail.getMessage() != null
                ? detail.getMessage()
                : "HTTP " + httpStatus + (rawBody != null ? ": " + rawBody : "");
        return new PrlApiException(httpStatus, message, detail);
    }

    /** The HTTP status code returned by the service, or {@code 0} for network errors. */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** Structured error detail parsed from the response body, may be {@code null}. */
    public ApiErrorResponse getDetail() {
        return detail;
    }
}
