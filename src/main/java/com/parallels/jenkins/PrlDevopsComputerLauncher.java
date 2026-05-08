package com.parallels.jenkins;

import hudson.model.TaskListener;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Logger;

/**
 * {@link ComputerLauncher} that bootstraps a Jenkins agent on a Parallels DevOps
 * VM via SSH, delegating to {@link SSHLauncher} with configurable retry logic.
 *
 * <p>Constructed from the VM's dynamic IP (resolved at provision time) and the
 * {@link AgentTemplate} SSH settings. {@link SSHLauncher} handles copying
 * {@code agent.jar} and starting the remoting process.
 *
 * <p>Retry behaviour: if the SSH daemon is not yet up when {@code launch()} is
 * first called, the launcher retries up to {@code sshRetries} times, waiting
 * {@code sshRetryDelaySec} seconds between attempts, before marking the node
 * offline with a descriptive error.
 */
public class PrlDevopsComputerLauncher extends ComputerLauncher {

    private static final Logger LOGGER =
            Logger.getLogger(PrlDevopsComputerLauncher.class.getName());

    private final String vmIp;
    private final int sshPort;
    private final String sshCredentialsId;
    private final String javaPath;
    private final String jvmOptions;
    private final int sshRetries;
    private final int sshRetryDelaySec;

    public PrlDevopsComputerLauncher(String vmIp, AgentTemplate template) {
        this.vmIp = vmIp;
        this.sshPort = template.getSshPort();
        this.sshCredentialsId = template.getSshCredentialsId();
        this.javaPath = template.getJavaPath();
        this.jvmOptions = template.getJvmOptions();
        this.sshRetries = template.getSshRetries();
        this.sshRetryDelaySec = template.getSshRetryDelaySec();
    }

    // Package-private for tests
    String getVmIp() { return vmIp; }
    int getSshPort() { return sshPort; }
    String getSshCredentialsId() { return sshCredentialsId; }
    int getSshRetries() { return sshRetries; }
    int getSshRetryDelaySec() { return sshRetryDelaySec; }

    @Override
    public void launch(SlaveComputer computer, TaskListener listener) throws IOException {
        PrintStream log = listener.getLogger();
        log.println("[PrlDevops] Connecting to VM " + vmIp + " via SSH"
                + " (port=" + sshPort
                + ", retries=" + sshRetries
                + ", retryDelay=" + sshRetryDelaySec + "s)");

        for (int attempt = 1; attempt <= sshRetries; attempt++) {
            log.println("[PrlDevops] SSH attempt " + attempt + "/" + sshRetries
                    + " to " + vmIp + ":" + sshPort);

            SSHLauncher sshLauncher = buildSshLauncher();
            try {
                sshLauncher.launch(computer, listener);
                if (computer.isOnline()) {
                    LOGGER.info("[PrlDevops] SSH agent online after attempt " + attempt
                            + " for VM " + vmIp);
                    return;
                }
                log.println("[PrlDevops] Attempt " + attempt
                        + " did not bring node online — SSH daemon may still be starting.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("[PrlDevops] SSH launch interrupted for VM " + vmIp, e);
            } catch (Exception e) {
                log.println("[PrlDevops] Attempt " + attempt + " failed: " + e.getMessage());
                LOGGER.fine("[PrlDevops] SSH attempt " + attempt + " exception: " + e);
            }

            if (attempt < sshRetries) {
                log.println("[PrlDevops] Waiting " + sshRetryDelaySec
                        + "s before next SSH attempt...");
                try {
                    Thread.sleep(sshRetryDelaySec * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("[PrlDevops] SSH retry wait interrupted", e);
                }
            }
        }

        // All attempts exhausted
        String msg = "[PrlDevops] All " + sshRetries + " SSH attempts failed for VM " + vmIp
                + ":" + sshPort + ". Marking node offline.";
        log.println(msg);
        LOGGER.warning(msg);
        computer.setAcceptingTasks(false);
    }

    private SSHLauncher buildSshLauncher() {
        SSHLauncher launcher = new SSHLauncher(vmIp, sshPort, sshCredentialsId);
        if (javaPath != null && !javaPath.isBlank() && !javaPath.equals("java")) {
            launcher.setJavaPath(javaPath);
        }
        if (jvmOptions != null && !jvmOptions.isBlank()) {
            launcher.setJvmOptions(jvmOptions);
        }
        return launcher;
    }
}
