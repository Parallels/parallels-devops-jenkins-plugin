package com.parallels.jenkins;

import com.parallels.jenkins.api.PrlDevopsApiClient;
import com.parallels.jenkins.api.dto.ExecuteRequest;
import com.parallels.jenkins.api.dto.ExecuteResponse;
import com.parallels.jenkins.api.exception.PrlApiException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Jenkins Build Step that executes a shell command on the provisioned
 * Parallels DevOps VM via {@code PUT /api/v1/machines/{id}/execute}.
 *
 * <p>Add it to a freestyle job via <strong>Build Steps → Execute on Parallels VM</strong>,
 * or use {@code parallelsDevopsCommand} in a pipeline script.
 *
 * <p>The build fails if the command exits with a non-zero exit code.
 */
public class PrlDevopsBuilder extends Builder implements SimpleBuildStep {

    private final String command;
    private List<EnvVar> environmentVariables = Collections.emptyList();

    @DataBoundConstructor
    public PrlDevopsBuilder(String command) {
        this.command = command;
    }

    public String getCommand() { return command; }
    public List<EnvVar> getEnvironmentVariables() { return environmentVariables; }

    @DataBoundSetter
    public void setEnvironmentVariables(List<EnvVar> environmentVariables) {
        this.environmentVariables = environmentVariables != null
                ? environmentVariables : Collections.emptyList();
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws IOException {
        PrintStream log = listener.getLogger();

        Computer computer = workspace.toComputer();
        Node node = computer != null ? computer.getNode() : null;

        if (!(node instanceof PrlDevopsAgent agent)) {
            log.println("[PrlDevops] ERROR: This build step requires a Parallels DevOps agent."
                    + " Current node: " + (node != null ? node.getNodeName() : "null"));
            throw new hudson.AbortException(
                    "This build step requires a Parallels DevOps agent.");
        }

        if (!(Jenkins.get().clouds.getByName(agent.getCloudName()) instanceof PrlDevopsCloud cloud)) {
            log.println("[PrlDevops] ERROR: Cloud '" + agent.getCloudName() + "' not found.");
            throw new hudson.AbortException(
                    "Cloud '" + agent.getCloudName() + "' not found.");
        }

        PrlDevopsApiClient client;
        try {
            client = cloud.buildApiClient();
        } catch (PrlApiException e) {
            log.println("[PrlDevops] ERROR: Cannot build API client: " + e.getMessage());
            throw new IOException("Cannot build API client: " + e.getMessage(), e);
        }

        Map<String, String> envMap = new LinkedHashMap<>();
        for (EnvVar ev : environmentVariables) {
            envMap.put(ev.getEnvKey(), ev.getEnvValue());
        }

        String vmUser = agent.getTemplate().getVmUser();
        log.println("[PrlDevops] Executing on VM " + agent.getVmId()
                + " as user '" + vmUser + "': " + command);

        ExecuteRequest request = new ExecuteRequest(command, vmUser, envMap);
        try {
            ExecuteResponse response = client.executeCommand(agent.getVmId(), request);
            if (response.getStdout() != null && !response.getStdout().isBlank()) {
                log.println(response.getStdout());
            }
            if (response.getExitCode() != 0) {
                log.println("[PrlDevops] Command exited with code " + response.getExitCode());
                throw new hudson.AbortException(
                        "Command exited with code " + response.getExitCode());
            }
        } catch (PrlApiException e) {
            log.println("[PrlDevops] ERROR executing command: " + e.getMessage());
            throw new IOException("Error executing command: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Key=Value pair for environment variables in the UI
    // -------------------------------------------------------------------------

    public static final class EnvVar {
        /**
         * The environment variable name (key). Not a password — suppressing
         * the plaintext-storage finding which is a false positive here.
         */
        @SuppressWarnings("lgtm[jenkins/plaintext-storage]")
        private final String envKey;
        private final String envValue;

        @DataBoundConstructor
        public EnvVar(String envKey, String envValue) {
            this.envKey = envKey;
            this.envValue = envValue;
        }

        public String getEnvKey() { return envKey; }
        public String getEnvValue() { return envValue; }
    }

    // -------------------------------------------------------------------------
    // Descriptor
    // -------------------------------------------------------------------------

    @Extension
    @Symbol("parallelsDevopsCommand")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Execute on Parallels DevOps VM";
        }
    }
}
