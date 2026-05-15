package com.parallels.jenkins;

import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudSlave;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * A provisioned VM registered as a Jenkins agent. The agent is bootstrapped
 * via SSH using {@link PrlDevopsComputerLauncher}, which delegates to
 * {@link hudson.plugins.sshslaves.SSHLauncher} with configurable retry logic.
 */
public class PrlDevopsAgent extends AbstractCloudSlave {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(PrlDevopsAgent.class.getName());

    private final String cloudName;
    private final AgentTemplate template;
    private final String vmId;
    private final String vmIp;
    /** Epoch-millis when this node was first created (set once, never changes). */
    private final long provisionedAt;

    public PrlDevopsAgent(String cloudName, AgentTemplate template, String vmId, String vmIp)
            throws Descriptor.FormException, IOException {
        super("prl-" + vmId, template.getAgentWorkspaceDir(), new PrlDevopsComputerLauncher(vmIp, template));
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

    @Override
    protected void _terminate(TaskListener listener) {
        listener.getLogger().println("[PrlDevopsAgent] terminate() called for VM " + vmId);
        LOGGER.fine("[PrlDevops] _terminate invoked for " + getNodeName());
    }
}
