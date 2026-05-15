package com.parallels.jenkins;

import hudson.plugins.sshslaves.SSHLauncher;
import hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PrlDevopsRetentionStrategyTest {

    @Test
    void launcherUsesTemplateSshSettings() {
        AgentTemplate template = new AgentTemplate("test-label");
        template.setSshCredentialsId("ssh-cred");
        template.setSshPort(2222);
        template.setJavaPath("/usr/bin/java");
        template.setJvmOptions("-Xmx512m");
        template.setSshRetries(7);
        template.setSshRetryDelaySec(11);

        PrlDevopsComputerLauncher launcher = new PrlDevopsComputerLauncher("10.0.0.10", template);

        assertEquals("10.0.0.10", launcher.getVmIp());
        assertEquals("ssh-cred", launcher.getSshCredentialsId());
        assertEquals(2222, launcher.getSshPort());
        assertEquals(7, launcher.getSshRetries());
        assertEquals(11, launcher.getSshRetryDelaySec());
        assertFalse(launcher.hasExhaustedRetries());
    }

    @Test
    void launcherBuildsSshLauncherWithReviewSafeDefaults() {
        AgentTemplate template = new AgentTemplate("test-label");
        template.setSshCredentialsId("ssh-cred");
        template.setSshPort(2222);
        template.setJavaPath("/usr/bin/java");
        template.setJvmOptions("-Xmx512m");
        template.setSshRetries(7);
        template.setSshRetryDelaySec(11);

        PrlDevopsComputerLauncher launcher = new PrlDevopsComputerLauncher("10.0.0.10", template);
        SSHLauncher sshLauncher = launcher.buildSshLauncher();

        assertEquals("10.0.0.10", sshLauncher.getHost());
        assertEquals(2222, sshLauncher.getPort());
        assertEquals("ssh-cred", sshLauncher.getCredentialsId());
        assertEquals("/usr/bin/java", sshLauncher.getJavaPath());
        assertEquals("-Xmx512m", sshLauncher.getJvmOptions());
        assertEquals(7, sshLauncher.getMaxNumRetries());
        assertEquals(11, sshLauncher.getRetryWaitTime());
        assertInstanceOf(NonVerifyingKeyVerificationStrategy.class,
                sshLauncher.getSshHostKeyVerificationStrategy());
    }

    @Test
    void retentionStrategyIsConstructable() {
        assertNotNull(new PrlDevopsRetentionStrategy());
    }
}
