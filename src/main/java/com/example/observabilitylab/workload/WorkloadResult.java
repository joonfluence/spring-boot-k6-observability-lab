package com.example.observabilitylab.workload;

public record WorkloadResult(
        WorkloadMode mode,
        int iterations,
        int delayMilliseconds,
        long checksum,
        boolean executedOnVirtualThread) {

    public WorkloadResult(int iterations, long checksum, boolean executedOnVirtualThread) {
        this(WorkloadMode.CPU, iterations, 0, checksum, executedOnVirtualThread);
    }
}
