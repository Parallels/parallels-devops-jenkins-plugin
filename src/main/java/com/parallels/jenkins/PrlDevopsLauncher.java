package com.parallels.jenkins;

import com.parallels.jenkins.api.PrlDevopsApiClient;
import com.parallels.jenkins.api.dto.ExecuteRequest;
import com.parallels.jenkins.api.dto.ExecuteResponse;
import com.parallels.jenkins.api.exception.PrlApiException;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.URL;
import java.util.Base64;
import java.util.Collections;
import java.util.logging.Logger;

/**
 * Custom {@link ComputerLauncher} that starts the Jenkins remoting agent on a
 * Parallels DevOps VM using the execute API (no SSH credentials required).
 *
 * <p>Flow:
 * <ol>
 *   <li>Download {@code agent.jar} from Jenkins onto the VM via {@code curl}.</li>
 *   <li>Start {@code agent.jar} in the background with {@code nohup … &amp;}.</li>
 *   <li>The agent process connects back to Jenkins via WebSocket (no extra TCP port).</li>
 *   <li>Jenkins establishes the remoting channel; the node comes online.</li>
 * </ol>
 *
 * <p>Requirements on the VM: {@code curl}, {@code java} (any JRE ≥ 11), and
 * network connectivity to the Jenkins URL.
 */
public class PrlDevopsLauncher extends ComputerLauncher {

    private static final Logger LOGGER = Logger.getLogger(PrlDevopsLauncher.class.getName());

    private final String vmId;
    private final String cloudName;
    private final String vmUser;

    public PrlDevopsLauncher(String vmId, String cloudName, String vmUser) {
        this.vmId = vmId;
        this.cloudName = cloudName;
        this.vmUser = vmUser;
    }

    public String getVmId() { return vmId; }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException {
        PrintStream log = listener.getLogger();

        Jenkins jenkins = Jenkins.get();
        String rootUrl = jenkins.getRootUrl();
        if (rootUrl == null || rootUrl.isBlank()) {
            throw new IOException("[PrlDevops] Jenkins root URL is not configured. "
                    + "Set it via Manage Jenkins → System → Jenkins URL.");
        }
        if (!rootUrl.endsWith("/")) {
            rootUrl += "/";
        }

        // If root URL points to localhost/127.0.0.1 the VM cannot reach it.
        // Substitute the machine's LAN IP automatically so the agent can connect back.
        if (rootUrl.contains("localhost") || rootUrl.contains("127.0.0.1")) {
            try {
                String lanIp = InetAddress.getLocalHost().getHostAddress();
                rootUrl = rootUrl.replaceFirst("localhost|127\\.0\\.0\\.1", lanIp);
                log.println("[PrlDevops] Root URL contained localhost — substituted LAN IP: " + rootUrl);
            } catch (IOException e) {
                log.println("[PrlDevops] WARNING: Could not resolve LAN IP; keeping root URL as-is. "
                        + "Agent may fail to connect back if Jenkins is on a different host.");
            }
        }

        hudson.slaves.Cloud cloud = jenkins.clouds.getByName(cloudName);
        if (!(cloud instanceof PrlDevopsCloud)) {
            throw new IOException("[PrlDevops] Cloud '" + cloudName + "' not found.");
        }

        PrlDevopsApiClient client;
        try {
            client = ((PrlDevopsCloud) cloud).buildApiClient();
        } catch (PrlApiException e) {
            throw new IOException("[PrlDevops] Cannot build API client: " + e.getMessage(), e);
        }

        String nodeName = computer.getName();
        String secret   = computer.getJnlpMac();
        String workDir  = "/tmp/jenkins-agent";
        String agentJar = workDir + "/agent.jar";

        // Prepend common macOS Homebrew and system paths so java is found even when
        // the execute API runs in a non-login shell (no .zshrc / .bash_profile sourced).
        String pathPreamble =
                "export PATH=\"/opt/homebrew/bin" +
                ":/opt/homebrew/opt/openjdk/bin" +
                ":/opt/homebrew/opt/openjdk@21/bin" +
                ":/opt/homebrew/opt/openjdk@17/bin" +
                ":/opt/homebrew/opt/openjdk@11/bin" +
                ":/usr/local/bin:/usr/bin:/bin:$PATH\"";

        // Step 1 — a fast pre-flight check that fails immediately (non-zero exit)
        // if java or curl are missing, rather than waiting minutes for a silent timeout.
        String preflightScript =
                pathPreamble + " && " +
                "command -v java  >/dev/null 2>&1 || { echo '[PrlDevops] PREFLIGHT FAIL: java not found on PATH'; exit 127; } && " +
                "command -v curl  >/dev/null 2>&1 || { echo '[PrlDevops] PREFLIGHT FAIL: curl not found on PATH'; exit 127; } && " +
                "echo '[PrlDevops] Preflight OK: java='$(java -version 2>&1 | head -1)";

        log.println("[PrlDevops] Running preflight check on VM " + vmId + "...");
        try {
            ExecuteResponse preflight = client.executeCommand(
                    vmId, new ExecuteRequest(preflightScript, vmUser, Collections.emptyMap()));
            log.println("[PrlDevops] Preflight output: " + preflight.getStdout());
            if (preflight.getExitCode() != 0) {
                throw new IOException("[PrlDevops] Preflight failed on VM " + vmId
                        + " (exit=" + preflight.getExitCode() + "): " + preflight.getStdout());
            }
        } catch (PrlApiException e) {
            throw new IOException("[PrlDevops] Execute API error during preflight on VM "
                    + vmId + ": " + e.getMessage(), e);
        }

        // Step 2 — transfer agent.jar to the VM via base64 encoding.
        // We download agent.jar locally from Jenkins (localhost always works on the Mac),
        // base64-encode it, then decode it on the VM via the execute API.
        // This avoids needing the VM to reach Jenkins over HTTP for the file download.
        log.println("[PrlDevops] Transferring agent.jar to VM " + vmId + " via execute API...");
        String agentJarBase64 = downloadAgentJarAsBase64(log);

        // Split into 50 KB chunks to stay within execute API command-length limits.
        int chunkSize = 50_000;
        String[] chunks = splitIntoChunks(agentJarBase64, chunkSize);
        log.println("[PrlDevops] Sending agent.jar in " + chunks.length + " chunk(s)...");

        // First chunk — create/overwrite the file.
        String firstCmd = "mkdir -p '" + workDir + "' && " +
                "printf '%s' '" + chunks[0] + "' > '" + workDir + "/agent.jar.b64'";
        executeOrThrow(client, vmId, vmUser, firstCmd, "agent.jar transfer chunk 1");

        // Remaining chunks — append.
        for (int i = 1; i < chunks.length; i++) {
            String appendCmd = "printf '%s' '" + chunks[i] + "' >> '" + workDir + "/agent.jar.b64'";
            executeOrThrow(client, vmId, vmUser, appendCmd, "agent.jar transfer chunk " + (i + 1));
        }

        // Decode the accumulated base64 file into the actual jar.
        executeOrThrow(client, vmId, vmUser,
                "base64 -d '" + workDir + "/agent.jar.b64' > '" + agentJar + "' && " +
                "rm '" + workDir + "/agent.jar.b64'",
                "agent.jar base64 decode");
        log.println("[PrlDevops] agent.jar transferred successfully.");

        // Step 3 — start the agent in the background.
        // -webSocket: agent opens an outbound WebSocket to Jenkins (no inbound port needed on VM).
        String startScript =
                pathPreamble + " && " +
                "nohup java -jar '" + agentJar + "'" +
                        " -url '" + rootUrl + "'" +
                        " -secret '" + secret + "'" +
                        " -name '" + nodeName + "'" +
                        " -webSocket" +
                        " -workDir '" + workDir + "'" +
                        " >'" + workDir + "/agent.log' 2>&1 &" +
                " && echo '[PrlDevops] Agent process started (PID='$!')'";

        log.println("[PrlDevops] Starting Jenkins agent on VM " + vmId
                + " (user=" + vmUser + ", node=" + nodeName + ", url=" + rootUrl + ")");
        LOGGER.info("[PrlDevops] Dispatching agent startup for " + nodeName + " on VM " + vmId);

        try {
            ExecuteResponse resp = client.executeCommand(
                    vmId, new ExecuteRequest(startScript, vmUser, Collections.emptyMap()));
            log.println("[PrlDevops] Start script output: " + resp.getStdout());
            if (resp.getExitCode() != 0) {
                throw new IOException("[PrlDevops] Agent startup script failed on VM " + vmId
                        + " (exit=" + resp.getExitCode() + "): " + resp.getStdout());
            }
        } catch (PrlApiException e) {
            throw new IOException("[PrlDevops] Execute API error starting agent on VM "
                    + vmId + ": " + e.getMessage(), e);
        }

        // Block this thread (keeping isConnecting()=true) until the agent.jar process
        // connects back via WebSocket and Jenkins sets the channel, or until timeout.
        PrlDevopsSlave slave = (PrlDevopsSlave) computer.getNode();
        long timeoutMs = slave != null
                ? slave.getTemplate().getVmReadyTimeoutSeconds() * 1000L
                : 300_000L;
        long deadline = System.currentTimeMillis() + timeoutMs;
        long lastLogPoll = 0;

        log.println("[PrlDevops] Waiting for agent WebSocket connection (timeout="
                + (timeoutMs / 1000) + "s). Check " + workDir + "/agent.log on the VM if this hangs.");

        while (!computer.isOnline()) {
            if (System.currentTimeMillis() > deadline) {
                // Fetch the last lines of agent.log to surface the real error.
                String agentLog = tailAgentLog(client, vmId, vmUser, workDir);
                throw new IOException("[PrlDevops] Timed out waiting for agent on VM "
                        + vmId + " after " + (timeoutMs / 1000) + "s.\n"
                        + "Last lines of agent.log:\n" + agentLog);
            }
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("[PrlDevops] Interrupted while waiting for agent to connect", e);
            }
            // Poll agent.log every 15s to surface errors early in the Jenkins build log.
            long now = System.currentTimeMillis();
            if (now - lastLogPoll > 15_000) {
                lastLogPoll = now;
                String agentLog = tailAgentLog(client, vmId, vmUser, workDir);
                if (!agentLog.isBlank()) {
                    log.println("[PrlDevops] agent.log tail:\n" + agentLog);
                }
            }
        }

        log.println("[PrlDevops] Agent " + nodeName + " connected successfully.");
    }

    /** Downloads agent.jar from the local Jenkins instance and returns it as a Base64 string. */
    private String downloadAgentJarAsBase64(PrintStream log) throws IOException {
        // Always download from localhost — Jenkins is running on this machine.
        // This works regardless of how the Jenkins root URL is configured.
        Jenkins jenkins = Jenkins.get();
        int port = jenkins.getTcpSlaveAgentListener() != null
                ? jenkins.getTcpSlaveAgentListener().getPort()
                : 8080;
        String localUrl = "http://localhost:" + System.getProperty("jetty.port", "8080")
                + "/jnlpJars/agent.jar";
        log.println("[PrlDevops] Downloading agent.jar from " + localUrl);
        URL url = new URL(localUrl);
        try (InputStream in = url.openStream()) {
            byte[] bytes = in.readAllBytes();
            log.println("[PrlDevops] agent.jar size: " + bytes.length + " bytes");
            return Base64.getEncoder().encodeToString(bytes);
        }
    }

    /** Splits a string into chunks of at most {@code size} characters. */
    private static String[] splitIntoChunks(String s, int size) {
        int count = (int) Math.ceil((double) s.length() / size);
        String[] chunks = new String[count];
        for (int i = 0; i < count; i++) {
            int start = i * size;
            int end = Math.min(start + size, s.length());
            chunks[i] = s.substring(start, end);
        }
        return chunks;
    }

    /** Executes a command and throws {@link IOException} if exit code is non-zero. */
    private void executeOrThrow(PrlDevopsApiClient client, String vmId, String vmUser,
                                String command, String stepName) throws IOException {
        try {
            ExecuteResponse r = client.executeCommand(
                    vmId, new ExecuteRequest(command, vmUser, Collections.emptyMap()));
            if (r.getExitCode() != 0) {
                throw new IOException("[PrlDevops] Step '" + stepName + "' failed on VM "
                        + vmId + " (exit=" + r.getExitCode() + "): " + r.getStdout());
            }
        } catch (PrlApiException e) {
            throw new IOException("[PrlDevops] Execute API error during '" + stepName
                    + "' on VM " + vmId + ": " + e.getMessage(), e);
        }
    }

    /** Reads the last ~20 lines of agent.log from the VM via the execute API. */
    private String tailAgentLog(PrlDevopsApiClient client, String vmId,
                                String vmUser, String workDir) {
        try {
            ExecuteResponse r = client.executeCommand(vmId,
                    new ExecuteRequest("tail -20 '" + workDir + "/agent.log' 2>/dev/null || echo '(log not found)'",
                            vmUser, Collections.emptyMap()));
            return r.getStdout() != null ? r.getStdout() : "";
        } catch (PrlApiException e) {
            return "(could not read agent.log: " + e.getMessage() + ")";
        }
    }

    @Override
    public Descriptor<ComputerLauncher> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(PrlDevopsLauncher.class);
    }

    @hudson.Extension
    public static class DescriptorImpl extends Descriptor<ComputerLauncher> {
        @Override
        public String getDisplayName() {
            return "Parallels DevOps Launcher";
        }
    }
}

