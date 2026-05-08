package com.parallels.jenkins;

import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.CloudRetentionStrategy;

import java.util.logging.Logger;

/**
 * ONE_SHOT retention strategy for {@link PrlDevopsSlave}.
 *
 * <p>Every provisioned VM is single-use: the moment a build finishes and the
 * computer becomes idle, {@link PrlDevopsSlave#terminate()} is called to delete
 * the VM and remove the node from Jenkins.
 *
 * <p>There is no configurable idle-timeout for MVP — every VM is ephemeral.
 * The {@code IDLE_TIMEOUT} strategy is deferred to a future release and must
 * not be added here.
 */
public class PrlDevopsRetentionStrategy extends CloudRetentionStrategy {

    private static final Logger LOGGER =
            Logger.getLogger(PrlDevopsRetentionStrategy.class.getName());

    public PrlDevopsRetentionStrategy() {
        super(0); // idleMinutes=0: lifecycle is managed entirely inside check()
    }

    /**
     * ONE_SHOT check: if the computer is a {@link PrlDevopsComputer} that has
     * completed at least one build and all executors are now free, terminate
     * the VM immediately.
     *
     * @return {@code 0} when termination is triggered (stops further scheduling);
     *         {@code 1} (minute) otherwise so Jenkins keeps calling us.
     */
    @Override
    public long check(AbstractCloudComputer c) {
        if (!(c instanceof PrlDevopsComputer)) {
            return 1;
        }
        PrlDevopsComputer computer = (PrlDevopsComputer) c;
        if (computer.isIdle() && !computer.getBuilds().isEmpty()) {
            PrlDevopsSlave slave = computer.getNode();
            if (slave != null) {
                LOGGER.info("[PrlDevops] Computer " + c.getName()
                        + " is idle after completing a build — terminating VM.");
                slave.terminate();
            }
            return 0; // no further checks needed
        }
        return 1; // check again in 1 minute
    }
}
