package com.parallels.jenkins;

import hudson.model.Label;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class PrlDevopsCloudTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testConfigurationRoundTrip() throws Exception {
        PrlDevopsCloud cloud = new PrlDevopsCloud("TestCloud");
        cloud.setServiceUrl("http://localhost:8080");
        cloud.setCredentialsId("test-credentials");
        cloud.setConnectionMode(ConnectionMode.HOST);
        cloud.setMaxAgents(10);

        r.jenkins.clouds.add(cloud);

        r.configRoundtrip();

        PrlDevopsCloud loaded = (PrlDevopsCloud) r.jenkins.clouds.getByName("TestCloud");
        assertNotNull(loaded);
        assertEquals("http://localhost:8080", loaded.getServiceUrl());
        assertEquals("test-credentials", loaded.getCredentialsId());
        assertEquals(ConnectionMode.HOST, loaded.getConnectionMode());
        assertEquals(10, loaded.getMaxAgents());
    }
}
