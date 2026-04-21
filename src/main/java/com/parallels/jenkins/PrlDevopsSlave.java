package com.parallels.jenkins;

import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.RetentionStrategy;

import java.io.IOException;

/**
 * A live cloned VM registered as a Jenkins node. Constructed when
 * Parallels DevOps Service has cloned a VM and returned its ID and IP
 * address. Retention strategy is ONE_SHOT: the VM is terminated after its
 * build completes (full teardown logic in PRL-JNK-09).
 */
public class PrlDevopsSlave extends AbstractCloudSlave {

    private static final long serialVersionUID = 1L;

    private final String cloudName;
    private final AgentTemplate template;
    private final String vmId;
    private final String ipAddress;
    /** Epoch-millis when this node was first created (set once, never changes). */
    private final long provisionedAt;

    public PrlDevopsSlave(String cloudName, AgentTemplate template, String vmId, String ipAddress)
            throws Descriptor.FormException, IOException {
        super(
                "prl-" + vmId,
                template.getRemoteFs(),
                new SSHLauncher(ipAddress, 22, template.getSshCredentialsId())
        );
        this.cloudName = cloudName;
        this.template = template;
        this.vmId = vmId;
        this.ipAddress = ipAddress;
        this.provisionedAt = System.currentTimeMillis();
        setNumExecutors(template.getNumExecutors());
        setLabelString(template.getTemplateLabel());
        setMode(Node.Mode.NORMAL);
        setRetentionStrategy(RetentionStrategy.NOOP);
    }

    public String getCloudName() { return cloudName; }
    public AgentTemplate getTemplate() { return template; }
    public String getVmId() { return vmId; }
    public String getIpAddress() { return ipAddress; }
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
    public AbstractCloudComputer<PrlDevopsSlave> createComputer() {
        return new AbstractCloudComputer<>(this);
    }

    /**
     * Stub implementation — VM termination is wired up in PRL-JNK-09.
     * Safe to call; does not contact the Parallels DevOps API.
     */
    @Override
    protected void _terminate(TaskListener listener) {
        listener.getLogger().println(
                "[PrlDevopsSlave] terminate() called for VM " + vmId + " — no-op stub (PRL-JNK-09)");
    }
}
