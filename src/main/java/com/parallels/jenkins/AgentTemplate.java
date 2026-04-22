package com.parallels.jenkins;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
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
 * Parallels DevOps Service. Commands are executed via the Parallels DevOps
 * execute API — no SSH connection is required.
 */
public class AgentTemplate extends AbstractDescribableImpl<AgentTemplate> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String templateLabel;
    private final String baseVmName;
    /** OS user account used to run commands on the VM via the execute API. */
    private String vmUser = "parallels";
    private int numExecutors = 1;
    private int vmReadyTimeoutSeconds = 300;
    private int vmReadyPollIntervalSeconds = 10;

    // ---- Catalog provisioning fields (only used when provisioningMode == CATALOG) ----
    private VmProvisioningMode provisioningMode = VmProvisioningMode.CLONE;
    private String architecture = "arm64";
    private String catalogId;
    private String catalogVersion = "latest";
    private String catalogUrl;
    private String catalogCredentialsId;

    @DataBoundConstructor
    public AgentTemplate(String templateLabel, String baseVmName) {
        this.templateLabel = templateLabel;
        this.baseVmName = baseVmName;
    }

    public String getTemplateLabel() { return templateLabel; }
    public String getBaseVmName() { return baseVmName; }
    public String getVmUser() { return vmUser; }
    public int getNumExecutors() { return numExecutors; }
    public int getVmReadyTimeoutSeconds() { return vmReadyTimeoutSeconds; }
    public int getVmReadyPollIntervalSeconds() { return vmReadyPollIntervalSeconds; }
    public VmProvisioningMode getProvisioningMode() { return provisioningMode; }
    public String getArchitecture() { return architecture; }
    public String getCatalogId() { return catalogId; }
    public String getCatalogVersion() { return catalogVersion; }
    public String getCatalogUrl() { return catalogUrl; }
    public String getCatalogCredentialsId() { return catalogCredentialsId; }

    @DataBoundSetter
    public void setVmUser(String vmUser) {
        this.vmUser = (vmUser != null && !vmUser.isBlank()) ? vmUser : "parallels";
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
    public void setProvisioningMode(VmProvisioningMode provisioningMode) {
        this.provisioningMode = provisioningMode != null ? provisioningMode : VmProvisioningMode.CLONE;
    }

    @DataBoundSetter
    public void setArchitecture(String architecture) { this.architecture = architecture; }

    @DataBoundSetter
    public void setCatalogId(String catalogId) { this.catalogId = catalogId; }

    @DataBoundSetter
    public void setCatalogVersion(String catalogVersion) { this.catalogVersion = catalogVersion; }

    @DataBoundSetter
    public void setCatalogUrl(String catalogUrl) { this.catalogUrl = catalogUrl; }

    @DataBoundSetter
    public void setCatalogCredentialsId(String catalogCredentialsId) {
        this.catalogCredentialsId = catalogCredentialsId;
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
        public ListBoxModel doFillCatalogCredentialsIdItems(@QueryParameter String catalogCredentialsId) {
            Jenkins jenkins = Jenkins.get();
            if (!jenkins.hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel().includeCurrentValue(catalogCredentialsId);
            }
            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            jenkins,
                            StandardUsernamePasswordCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.always()
                    )
                    .includeCurrentValue(catalogCredentialsId);
        }

        public ListBoxModel doFillArchitectureItems(@QueryParameter String architecture) {
            ListBoxModel items = new ListBoxModel();
            items.add("arm64", "arm64");
            items.add("x86_64", "x86_64");
            return items;
        }

        public hudson.util.FormValidation doCheckCatalogId(@QueryParameter String catalogId,
                                                           @QueryParameter String provisioningMode) {
            if ("CATALOG".equals(provisioningMode)
                    && (catalogId == null || catalogId.isBlank())) {
                return hudson.util.FormValidation.error("Catalog ID is required");
            }
            return hudson.util.FormValidation.ok();
        }
    }
}
