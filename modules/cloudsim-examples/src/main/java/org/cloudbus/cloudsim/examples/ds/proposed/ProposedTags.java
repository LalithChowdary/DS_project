package org.cloudbus.cloudsim.examples.ds.proposed;

import org.cloudbus.cloudsim.core.CloudSimTags;

public enum ProposedTags implements CloudSimTags {
    PERIODIC_MONITOR,
    VM_HEARTBEAT,
    VM_RESTART_COMPLETE,
    LB_HEARTBEAT,
    INJECT_VM_FAILURE,
    INJECT_LB_FAILURE
}
