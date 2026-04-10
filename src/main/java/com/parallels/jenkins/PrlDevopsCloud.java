package com.parallels.jenkins;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.Util;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import java.net.URI;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

public class PrlDevopsCloud extends Cloud {

    private String serviceUrl;
    private String credentialsId;
    private ConnectionMode connectionMode;
    private int maxAgents;
    private List<AgentTemplate> templates = new ArrayList<>();

    @DataBoundConstructor
    public PrlDevopsCloud(String name) {
        super(name);
    }

    public String getServiceUrl() { return serviceUrl; }
    public String getCredentialsId() { return credentialsId; }
    public ConnectionMode getConnectionMode() { return connectionMode; }
    public int getMaxAgents() { return maxAgents; }
    public List<AgentTemplate> getTemplates() { return Collections.unmodifiableList(templates); }

    @DataBoundSetter
    public void setServiceUrl(String serviceUrl) { this.serviceUrl = serviceUrl; }
    @DataBoundSetter
    public void setCredentialsId(String credentialsId) { this.credentialsId = credentialsId; }
    @DataBoundSetter
    public void setConnectionMode(ConnectionMode connectionMode) { this.connectionMode = connectionMode; }
    @DataBoundSetter
    public void setMaxAgents(int maxAgents) { this.maxAgents = maxAgents; }
    @DataBoundSetter
    public void setTemplates(List<AgentTemplate> templates) {
        this.templates = templates != null ? new ArrayList<>(templates) : new ArrayList<>();
    }

    /**
     * Returns the first {@link AgentTemplate} whose label set satisfies the
     * given Jenkins {@link hudson.model.Label}, or {@code null} if none match.
     */
    public AgentTemplate getTemplateForLabel(hudson.model.Label label) {
        for (AgentTemplate t : templates) {
            if (t.matches(label)) {
                return t;
            }
        }
        return null;
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
        @Override
        public String getDisplayName() {
            return "Parallels Devops Cloud";
        }

        @POST
        public ListBoxModel doFillConnectionModeItems() {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            ListBoxModel model = new ListBoxModel();
            for (ConnectionMode mode : ConnectionMode.values()) {
                model.add(mode.name(), mode.name());
            }
            return model;
        }

        @POST
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId) {
            Jenkins jenkins = Jenkins.get();
            if (!jenkins.hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }

            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            jenkins,
                            StringCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.always()
                    )
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            jenkins,
                            StandardUsernamePasswordCredentials.class,
                            Collections.emptyList(),
                            CredentialsMatchers.always()
                    )
                    .includeCurrentValue(credentialsId);
        }

        @POST
        public FormValidation doTestConnection(
                @QueryParameter("serviceUrl") String serviceUrl,
                @QueryParameter("credentialsId") String credentialsId) {

            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            if (Util.fixEmptyAndTrim(serviceUrl) == null) {
                return FormValidation.error("Service URL is required");
            }

            // Extract token
            String token = "";
            if (Util.fixEmptyAndTrim(credentialsId) != null) {
                StringCredentials sc = CredentialsMatchers.firstOrNull(
                        CredentialsProvider.lookupCredentials(
                                StringCredentials.class,
                                Jenkins.get(),
                                ACL.SYSTEM,
                                Collections.emptyList()
                        ),
                        CredentialsMatchers.withId(credentialsId)
                );
                if (sc != null) {
                    // Secret text token directly provided
                    token = sc.getSecret().getPlainText();
                } else {
                    StandardUsernamePasswordCredentials upc = CredentialsMatchers.firstOrNull(
                            CredentialsProvider.lookupCredentials(
                                    StandardUsernamePasswordCredentials.class,
                                    Jenkins.get(),
                                    ACL.SYSTEM,
                                    Collections.emptyList()
                            ),
                            CredentialsMatchers.withId(credentialsId)
                    );
                    if (upc != null) {
                        try {
                            String baseUrl = serviceUrl;
                            if (!baseUrl.endsWith("/")) { baseUrl += "/"; }
                            String authUrl = baseUrl + "api/v1/auth/token";
                            
                            String username = upc.getUsername().replace("\"", "\\\"");
                            String password = upc.getPassword().getPlainText().replace("\"", "\\\"");
                            String jsonBody = "{\"email\":\"" + username + "\",\"password\":\"" + password + "\"}";

                            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                            HttpRequest authReq = HttpRequest.newBuilder()
                                    .uri(URI.create(authUrl))
                                    .header("Content-Type", "application/json")
                                    .header("Accept", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                                    .build();

                            HttpResponse<String> authRes = client.send(authReq, HttpResponse.BodyHandlers.ofString());
                            if (authRes.statusCode() == 200) {
                                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(authRes.body());
                                if (m.find()) {
                                    token = m.group(1);
                                } else {
                                    return FormValidation.error("Auth successful but no 'token' property found in JSON.");
                                }
                            } else {
                                return FormValidation.error("Auth token POST failed. HTTP " + authRes.statusCode() + " " + authRes.body());
                            }
                        } catch (IOException | IllegalArgumentException e) {
                            return FormValidation.error("Authentication error: " + e.getMessage());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return FormValidation.error("Authentication interrupted");
                        }
                    }
                }
            }

            if (Util.fixEmptyAndTrim(token) == null) {
                return FormValidation.error("Credentials not found or empty");
            }

            try {
                String urlText = serviceUrl;
                if (!urlText.endsWith("/")) {
                    urlText += "/";
                }
                urlText += "api/v1/health/system?full=true";

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlText))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return FormValidation.ok("Connected");
                } else {
                    return FormValidation.error("Failed to connect. HTTP Status: " + response.statusCode());
                }
            } catch (IOException | IllegalArgumentException e) {
                return FormValidation.error("Connection error: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return FormValidation.error("Connection interrupted");
            }
        }
    }
}
