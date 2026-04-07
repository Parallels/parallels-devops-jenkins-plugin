package com.parallels.jenkins;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Collection;
import java.util.Collections;

public class PrlDevopsCloud extends Cloud {

    @DataBoundConstructor
    public PrlDevopsCloud(String name) {
        super(name);
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(CloudState state, int excessWorkload) {
        return Collections.emptyList();
    }

    @Override
    public boolean canProvision(CloudState state) {
        return false;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
    }
}
