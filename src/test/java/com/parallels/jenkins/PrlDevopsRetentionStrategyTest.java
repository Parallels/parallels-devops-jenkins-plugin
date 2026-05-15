package com.parallels.jenkins;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }

    @Test
    void agentUsesCustomLauncherAndOneShotRetention() throws Exception {
        AgentTemplate template = new AgentTemplate("test-label");
        template.setSshCredentialsId("ssh-cred");
        template.setAgentWorkspaceDir("/tmp/test-agent");

        PrlDevopsAgent agent = new PrlDevopsAgent("cloud-1", template, "vm-1", "10.0.0.10");

        assertEquals("/tmp/test-agent", agent.getRemoteFS());
        assertInstanceOf(PrlDevopsComputerLauncher.class, agent.getLauncher());
        assertInstanceOf(PrlDevopsRetentionStrategy.class, agent.getRetentionStrategy());
        assertEquals(1, agent.getNumExecutors());
    }

    @Test
    void retentionStrategyIsConstructable() {
        assertNotNull(new PrlDevopsRetentionStrategy());
    }
}
