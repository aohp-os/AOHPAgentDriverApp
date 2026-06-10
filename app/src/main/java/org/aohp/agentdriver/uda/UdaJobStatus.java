package org.aohp.agentdriver.uda;

/** Lifecycle state for a UDA generation job. */
public enum UdaJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
