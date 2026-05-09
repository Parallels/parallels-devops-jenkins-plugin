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
 * Per-VM-type configuration. One {@code AgentTemplate} maps to one VM type in
 * Parallels DevOps Service. The provisioning strategy (clone vs. catalog) is
 * expressed as a {@link ProvisioningConfig} Describable, rendered via
 * {@code <f:dropdownDescriptorSelector>} with no inline JavaScript.
 */
public class AgentTemplate extends AbstractDescribableImpl<AgentTemplate> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String templateLabel;
    /** OS user account used to run commands on the VM via the execute API. */
    private String vmUser = "parallels";
    /** Jenkins credentials ID for SSH agent bootstrap (username + password or key). */
    private String sshCredentialsId;
    private int numExecutors = 1;
    private int vmReadyTimeoutSeconds = 300;
    private int vmReadyPollIntervalSeconds = 10;

    /**
     * Encapsulates all provisioning-strategy-specific fields (e.g. base VM name
     * for clone mode, catalog ID/URL for catalog mode).
     * Defaults to a {@link CloneProvisioningConfig} with an empty base-VM name
     * so existing round-trip tests keep working without explicit configuration.
     */
    private ProvisioningConfig provisioningConfig = new CloneProvisioningConfig("");

    @DataBoundConstructor
    public AgentTemplate(String templateLabel) {
        this.templateLabel = templateLabel;
    }

    public String getTemplateLabel() { return templateLabel; }
    public String getVmUser() { return vmUser; }
    public String getSshCredentialsId() { return sshCredentialsId; }
    public int getNumExecutors() { return numExecutors; }
    public int getVmReadyTimeoutSeconds() { return vmReadyTimeoutSeconds; }
    public int getVmReadyPollIntervalSeconds() { return vmReadyPollIntervalSeconds; }
    public ProvisioningConfig getProvisioningConfig() { return provisioningConfig; }

    // ---------------------------------------------------------------------------
    // Convenience accessors used by PrlDevopsCloud — delegate to provisioningConfig
    // ---------------------------------------------------------------------------

    public VmProvisioningMode getProvisioningMode() {
        return provisioningConfig != null ? provisioningConfig.getMode() : VmProvisioningMode.CLONE;
    }

    public String getBaseVmName() {
        return provisioningConfig instanceof CloneProvisioningConfig c ? c.getBaseVmName() : null;
    }

    public String getArchitecture() {
        return provisioningConfig instanceof CatalogProvisioningConfig c ? c.getArchitecture() : "arm64";
    }

    public String getCatalogId() {
        return provisioningConfig instanceof CatalogProvisioningConfig c ? c.getCatalogId() : null;
    }

    public String getCatalogVersion() {
        return provisioningConfig instanceof CatalogProvisioningConfig c ? c.getCatalogVersion() : "latest";
    }

    public String getCatalogUrl() {
        return provisioningConfig instanceof CatalogProvisioningConfig c ? c.getCatalogUrl() : null;
    }

    public String getCatalogCredentialsId() {
        return provisioningConfig instanceof CatalogProvisioningConfig c ? c.getCatalogCredentialsId() : null;
    }

    @DataBoundSetter
    public void setVmUser(String vmUser) {
        this.vmUser = (vmUser != null && !vmUser.isBlank()) ? vmUser : "parallels";
    }

    @DataBoundSetter
    public void setSshCredentialsId(String sshCredentialsId) {
        this.sshCredentialsId = sshCredentialsId;
    }

    @DataBoundSetter
    public void setNumExecutors(int numExecutors) {
        this.numExecutors = numExecutors;
    }

    @DataBoundSetter
    public void setVmReadyTimeoutSeconds(int vmReadyTimeoutSeconds) {
        this.vmReadyTimeoutSeconds = vmReadyTimeoutSeconds;
    }

    @DataBoundSetter
    public void setVmReadyPollIntervalSeconds(int vmReadyPollIntervalSeconds) {
        this.vmReadyPollIntervalSeconds = vmReadyPollIntervalSeconds;
    }

    @DataBoundSetter
    public void setProvisioningConfig(ProvisioningConfig provisioningConfig) {
        this.provisioningConfig = provisioningConfig != null
                ? provisioningConfig : new CloneProvisioningConfig("");
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

        /** Supplies the list of {@link ProvisioningConfig} descriptors for {@code dropdownDescriptorSelector}. */
        public java.util.List<Descriptor<ProvisioningConfig>> getProvisioningConfigDescriptors() {
            return jenkins.model.Jenkins.get().getDescriptorList(ProvisioningConfig.class);
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
