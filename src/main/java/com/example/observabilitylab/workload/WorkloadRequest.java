package com.example.observabilitylab.workload;

import java.util.Objects;

public record WorkloadRequest(WorkloadMode mode, int iterations, int delayMilliseconds) {

    public static final int MAX_CPU_ITERATIONS = 1_000_000;
    public static final int MAX_LATENCY_MILLISECONDS = 5_000;

    public WorkloadRequest {
        Objects.requireNonNull(mode, "mode must not be null");
        if (iterations < 0 || iterations > MAX_CPU_ITERATIONS) {
            throw new IllegalArgumentException("iterations must be between 0 and " + MAX_CPU_ITERATIONS);
        }
        if (delayMilliseconds < 1 || delayMilliseconds > MAX_LATENCY_MILLISECONDS) {
            throw new IllegalArgumentException(
                    "delayMilliseconds must be between 1 and " + MAX_LATENCY_MILLISECONDS);
        }
    }
}
