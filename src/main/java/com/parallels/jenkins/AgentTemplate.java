package com.parallels.jenkins;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.io.Serializable;
import java.util.Collections;

/**
 * Per-VM-type configuration: base image name, labels, SSH credentials, and
 * working directory. One {@code AgentTemplate} maps to one VM type in
 * Parallels DevOps Service.
 */
public class AgentTemplate extends AbstractDescribableImpl<AgentTemplate> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String templateLabel;
    private final String baseVmName;
    private final String sshCredentialsId;
    private final String remoteFs;
    private int numExecutors = 1;

    @DataBoundConstructor
    public AgentTemplate(String templateLabel, String baseVmName, String sshCredentialsId, String remoteFs) {
        this.templateLabel = templateLabel;
        this.baseVmName = baseVmName;
        this.sshCredentialsId = sshCredentialsId;
        this.remoteFs = remoteFs;
    }

    public String getTemplateLabel() { return templateLabel; }
    public String getBaseVmName() { return baseVmName; }
    public String getSshCredentialsId() { return sshCredentialsId; }
    public String getRemoteFs() { return remoteFs; }
    public int getNumExecutors() { return numExecutors; }

    @DataBoundSetter
    public void setNumExecutors(int numExecutors) {
        this.numExecutors = numExecutors;
    }

    /**
     * Returns {@code true} when this template's label set satisfies the given
     * Jenkins {@link Label} expression (i.e. a queued job requiring
     * {@code label} can be run on a VM provisioned from this template).
     */
    public boolean matches(Label label) {
        if (label == null) {
            return true;
        }
        return label.matches(Label.parse(templateLabel));
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AgentTemplate> {

        @Override
        public String getDisplayName() {
            return "VM Template";
        }

        @POST
        public ListBoxModel doFillSshCredentialsIdItems(@QueryParameter String sshCredentialsId) {
            Jenkins jenkins = Jenkins.get();
            if (!jenkins.hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel().includeCurrentValue(sshCredentialsId);
            }
            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            jenkins,
                            StandardUsernameCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.always()
                    )
                    .includeCurrentValue(sshCredentialsId);
        }
    }
}
