package com.parallels.jenkins.api;

import com.parallels.jenkins.api.dto.CloneRequest;
import com.parallels.jenkins.api.dto.CloneResponse;
import com.parallels.jenkins.api.dto.VmStatusResponse;
import com.parallels.jenkins.api.exception.PrlApiException;
import com.parallels.jenkins.api.exception.PrlApiTimeoutException;

import java.time.Duration;

/**
 * Thin abstraction over the prl-devops-service REST API.
 *
 * <p>All three core operations needed by the Jenkins plugin are declared here.
 * Callers should depend on this interface, not on the concrete HTTP implementation,
 * so the client can be mocked in upstream unit tests.
 *
 * <p>Every method throws {@link PrlApiException} for:
 * <ul>
 *   <li>non-2xx HTTP responses (carries the status code and parsed error body), or</li>
 *   <li>low-level network failures.</li>
 * </ul>
 */
public interface PrlDevopsApiClient {

    /**
     * Clones the VM identified by {@code sourceVmId}.
     *
     * <p>Maps to {@code PUT /api/v1/machines/{sourceVmId}/clone} (host mode) or
     * {@code PUT /api/v1/orchestrator/hosts/{hostId}/machines/{sourceVmId}/clone}
     * (orchestrator mode).
     *
     * @param sourceVmId ID of the VM to clone.
     * @param request    Clone options (clone name, destination path); fields are optional.
     * @return {@link CloneResponse} containing the new VM's ID.
     * @throws PrlApiException on HTTP error or network failure.
     */
    CloneResponse cloneVm(String sourceVmId, CloneRequest request) throws PrlApiException;

    /**
     * Returns the lightweight status of a VM.
     *
     * <p>Maps to {@code GET /api/v1/machines/{vmId}/status} (host mode) or
     * {@code GET /api/v1/orchestrator/hosts/{hostId}/machines/{vmId}/status}
     * (orchestrator mode).
     *
     * @param vmId ID of the VM to query.
     * @return {@link VmStatusResponse} with {@code id}, {@code status}, and {@code ip_configured}.
     * @throws PrlApiException on HTTP error or network failure.
     */
    VmStatusResponse getVmStatus(String vmId) throws PrlApiException;

    /**
     * Deletes a VM.
     *
     * <p>Maps to {@code DELETE /api/v1/machines/{vmId}} (host mode) or
     * {@code DELETE /api/v1/orchestrator/hosts/{hostId}/machines/{vmId}}
     * (orchestrator mode). The API returns {@code 202 Accepted} with no body.
     *
     * @param vmId ID of the VM to delete.
     * @throws PrlApiException on HTTP error or network failure.
     */
    void deleteVm(String vmId) throws PrlApiException;

    /**
     * Polls {@link #getVmStatus} until the VM reaches the {@code running} state or
     * {@code timeout} is exceeded.
     *
     * <p>State machine:
     * <pre>
     *   pending  → keep polling
     *   starting → keep polling
     *   running  → return (success)
     *   error    → throw {@link PrlApiException}
     *   (timeout)→ throw {@link PrlApiTimeoutException}
     * </pre>
     *
     * @param vmId     ID of the VM to wait for.
     * @param timeout  Maximum time to wait before giving up.
     * @param interval Time to sleep between polling attempts.
     * @return Final {@link VmStatusResponse} once the VM is running.
     * @throws PrlApiException        if the VM enters an error state or a network error occurs.
     * @throws PrlApiTimeoutException if the VM does not reach running within {@code timeout}.
     */
    VmStatusResponse waitForVmReady(String vmId, Duration timeout, Duration interval)
            throws PrlApiException, PrlApiTimeoutException;
}
