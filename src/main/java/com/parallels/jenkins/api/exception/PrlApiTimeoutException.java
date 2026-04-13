package com.parallels.jenkins.api.exception;

import java.time.Duration;

/**
 * Thrown by {@link com.parallels.jenkins.api.PrlDevopsApiClient#waitForVmReady} when
 * the VM does not reach the {@code running} state within the allotted timeout.
 */
public class PrlApiTimeoutException extends PrlApiException {

    private final String vmId;
    private final Duration timeout;

    public PrlApiTimeoutException(String vmId, Duration timeout) {
        super("VM '" + vmId + "' did not reach RUNNING state within " + timeout);
        this.vmId = vmId;
        this.timeout = timeout;
    }

    public String getVmId() {
        return vmId;
    }

    public Duration getTimeout() {
        return timeout;
    }
}
