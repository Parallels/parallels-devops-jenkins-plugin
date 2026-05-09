package com.parallels.jenkins;

import hudson.Extension;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Provisioning config for <em>clone</em> mode: a new VM is cloned from an
 * existing base VM registered in the prl-devops-service host.
 */
public final class CloneProvisioningConfig extends ProvisioningConfig {

    private static final long serialVersionUID = 1L;

    private final String baseVmName;

    @DataBoundConstructor
    public CloneProvisioningConfig(String baseVmName) {
        this.baseVmName = baseVmName;
    }

    public String getBaseVmName() { return baseVmName; }

    @Override
    public VmProvisioningMode getMode() { return VmProvisioningMode.CLONE; }

    @Extension
    public static class DescriptorImpl extends Descriptor<ProvisioningConfig> {
        @Override
        public String getDisplayName() { return "Clone existing VM (Host mode)"; }
    }
}
