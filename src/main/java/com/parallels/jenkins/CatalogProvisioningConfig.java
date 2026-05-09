package com.parallels.jenkins;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.util.Collections;

/**
 * Provisioning config for <em>catalog</em> mode: a new VM is created from a
 * Parallels DevOps catalog entry managed by an orchestrator.
 */
public final class CatalogProvisioningConfig extends ProvisioningConfig {

    private static final long serialVersionUID = 1L;

    private final String catalogId;
    private String architecture = "arm64";
    private String catalogVersion = "latest";
    private String catalogUrl;
    private String catalogCredentialsId;

    @DataBoundConstructor
    public CatalogProvisioningConfig(String catalogId) {
        this.catalogId = catalogId;
    }

    public String getCatalogId() { return catalogId; }
    public String getArchitecture() { return architecture; }
    public String getCatalogVersion() { return catalogVersion; }
    public String getCatalogUrl() { return catalogUrl; }
    public String getCatalogCredentialsId() { return catalogCredentialsId; }

    @DataBoundSetter
    public void setArchitecture(String architecture) { this.architecture = architecture; }

    @DataBoundSetter
    public void setCatalogVersion(String catalogVersion) { this.catalogVersion = catalogVersion; }

    @DataBoundSetter
    public void setCatalogUrl(String catalogUrl) { this.catalogUrl = catalogUrl; }

    @DataBoundSetter
    public void setCatalogCredentialsId(String catalogCredentialsId) {
        this.catalogCredentialsId = catalogCredentialsId;
    }

    @Override
    public VmProvisioningMode getMode() { return VmProvisioningMode.CATALOG; }

    @Extension
    public static class DescriptorImpl extends Descriptor<ProvisioningConfig> {

        @Override
        public String getDisplayName() { return "Create from catalog (Orchestrator mode)"; }

        public ListBoxModel doFillArchitectureItems(@QueryParameter String architecture) {
            ListBoxModel items = new ListBoxModel();
            items.add("arm64", "arm64");
            items.add("x86_64", "x86_64");
            return items;
        }

        @POST
        public ListBoxModel doFillCatalogCredentialsIdItems(
                @QueryParameter String catalogCredentialsId) {
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

        @POST
        public hudson.util.FormValidation doCheckCatalogId(@QueryParameter String catalogId) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (catalogId == null || catalogId.isBlank()) {
                return hudson.util.FormValidation.error("Catalog ID is required");
            }
            return hudson.util.FormValidation.ok();
        }
    }
}
