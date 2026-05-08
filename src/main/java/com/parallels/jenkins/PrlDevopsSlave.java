package com.parallels.jenkins;

import com.parallels.jenkins.api.PrlDevopsApiClient;
import com.parallels.jenkins.api.exception.PrlApiException;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A provisioned VM registered as a Jenkins agent. The agent is bootstrapped
 * via SSH using {@link PrlDevopsComputerLauncher}, which delegates to
 * {@link hudson.plugins.sshslaves.SSHLauncher} with configurable retry logic.
 */
public class PrlDevopsSlave extends AbstractCloudSlave {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(PrlDevopsSlave.class.getName());

    private final String cloudName;
    private final AgentTemplate template;
    private final String vmId;
    private final String vmIp;
    /** Epoch-millis when this node was first created (set once, never changes). */
    private final long provisionedAt;

    /** Guards against double-termination (API delete + node removal). */
    private transient AtomicBoolean terminated = new AtomicBoolean(false);

    /**
     * Restores transient {@link #terminated} after Java-serialization-based
     * deserialization (e.g. Jenkins' own remoting or test infrastructure).
     */
    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        terminated = new AtomicBoolean(false);
    }

    /**
     * Called by XStream after XML deserialization — re-initialises
     * {@link #terminated} so orphan cleanup can fire after a Jenkins restart.
     */
    protected Object readResolve() {
        terminated = new AtomicBoolean(false);
        return this;
    }

    public PrlDevopsSlave(String cloudName, AgentTemplate template, String vmId, String vmIp)
            throws Descriptor.FormException, IOException {
        super(
                "prl-" + vmId,
                "/tmp/jenkins-agent",
                new PrlDevopsComputerLauncher(vmIp, template)
        );
        this.cloudName = cloudName;
        this.template = template;
        this.vmId = vmId;
        this.vmIp = vmIp;
        this.provisionedAt = System.currentTimeMillis();
        setNumExecutors(template.getNumExecutors());
        setLabelString(template.getTemplateLabel());
        setMode(Node.Mode.NORMAL);
        setRetentionStrategy(new PrlDevopsRetentionStrategy());
    }

    public String getCloudName() { return cloudName; }
    public AgentTemplate getTemplate() { return template; }
    public String getVmId() { return vmId; }
    public String getVmIp() { return vmIp; }
    public long getProvisionedAt() { return provisionedAt; }

    @Override
    public int getNumExecutors() {
        return template.getNumExecutors();
    }

    @Override
    public String getLabelString() {
        return template.getTemplateLabel();
    }

    @Override
    public PrlDevopsComputer createComputer() {
        return new PrlDevopsComputer(this);
    }

    /**
     * Deletes the backing VM via the Parallels DevOps API and removes this node
     * from Jenkins. Idempotent — safe to call multiple times; only the first
     * invocation performs work (subsequent calls are silently ignored).
     *
     * <p>API errors are logged but never propagate — a failed delete must not
     * crash Jenkins. Node removal is attempted regardless.
     */
    public void terminate() {
        if (!terminated.compareAndSet(false, true)) {
            LOGGER.info("[PrlDevopsSlave] terminate() already called for VM " + vmId + " — ignoring.");
            return;
        }

        Jenkins jenkins = Jenkins.get();
        Cloud cloud = jenkins.clouds.getByName(cloudName);
        if (cloud instanceof PrlDevopsCloud) {
            try {
                PrlDevopsApiClient client = ((PrlDevopsCloud) cloud).buildApiClient();
                client.deleteVm(vmId);
                LOGGER.info("[PrlDevopsSlave] Deleted VM " + vmId);
            } catch (PrlApiException e) {
                LOGGER.log(Level.WARNING,
                        "[PrlDevopsSlave] Failed to delete VM " + vmId + ": " + e.getMessage(), e);
            }
        } else {
            LOGGER.warning("[PrlDevopsSlave] Cloud '" + cloudName
                    + "' not found — skipping VM deletion for " + vmId);
        }

        try {
            jenkins.removeNode(this);
            LOGGER.info("[PrlDevopsSlave] Removed node " + getNodeName());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    "[PrlDevopsSlave] Failed to remove node " + getNodeName() + ": " + e.getMessage(), e);
        }
    }

    @Override
    protected void _terminate(TaskListener listener) {
        terminate();
    }
}
