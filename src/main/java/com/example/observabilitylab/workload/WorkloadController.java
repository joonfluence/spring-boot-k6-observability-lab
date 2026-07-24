package com.example.observabilitylab.workload;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/work")
@Validated
public class WorkloadController {

    private final WorkloadService workloadService;

    public WorkloadController(WorkloadService workloadService) {
        this.workloadService = workloadService;
    }

    /**
     * Runs one bounded workload: {@code cpu} applies {@code cpuIterations} to the deterministic CPU loop,
     * while {@code io} and {@code db} each wait once for {@code latencyMs} milliseconds.
     */
    @GetMapping
    public WorkloadResult run(
            @RequestParam(defaultValue = "cpu") String mode,
            @RequestParam(defaultValue = "10") @Min(1) @Max(WorkloadRequest.MAX_LATENCY_MILLISECONDS) int latencyMs,
            @RequestParam(defaultValue = "1000") @Min(0) @Max(WorkloadRequest.MAX_CPU_ITERATIONS) int cpuIterations) {
        return workloadService.execute(new WorkloadRequest(
                WorkloadMode.from(mode),
                cpuIterations,
                latencyMs));
    }
}
