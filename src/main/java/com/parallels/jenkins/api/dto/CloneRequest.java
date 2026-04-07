package com.parallels.jenkins.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request body sent to {@code PUT /api/v1/machines/{id}/clone}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CloneRequest {

    @JsonProperty("clone_name")
    private String cloneName;

    @JsonProperty("destination_path")
    private String destinationPath;

    public CloneRequest() {}

    public CloneRequest(String cloneName, String destinationPath) {
        this.cloneName = cloneName;
        this.destinationPath = destinationPath;
    }

    public String getCloneName() {
        return cloneName;
    }

    public void setCloneName(String cloneName) {
        this.cloneName = cloneName;
    }

    public String getDestinationPath() {
        return destinationPath;
    }

    public void setDestinationPath(String destinationPath) {
        this.destinationPath = destinationPath;
    }
}
