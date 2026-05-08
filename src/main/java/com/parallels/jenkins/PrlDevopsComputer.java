package com.parallels.jenkins;

import hudson.slaves.AbstractCloudComputer;

/**
 * Jenkins computer (agent connection) for a Parallels DevOps provisioned VM.
 * Paired with {@link PrlDevopsSlave} and used as the type parameter of
 * {@link PrlDevopsRetentionStrategy}.
 */
public class PrlDevopsComputer extends AbstractCloudComputer<PrlDevopsSlave> {

    public PrlDevopsComputer(PrlDevopsSlave slave) {
        super(slave);
    }
}
