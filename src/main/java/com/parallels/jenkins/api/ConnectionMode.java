package com.parallels.jenkins.api;

/**
 * Selects which prl-devops-service topology the client should target.
 *
 * <ul>
 *   <li>{@link #HOST} – speaks directly to a single Parallels Desktop host.
 *       Paths are rooted at {@code /api/v1/machines/...}</li>
 *   <li>{@link #ORCHESTRATOR} – speaks to an orchestrator that federates multiple hosts.
 *       Paths are rooted at {@code /api/v1/orchestrator/hosts/{hostId}/machines/...}</li>
 * </ul>
 */
public enum ConnectionMode {
    HOST,
    ORCHESTRATOR
}
