package com.example.observabilitylab.workload;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class WorkloadService {

    private final ExecutorService virtualThreadExecutor;
    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    public WorkloadService(ExecutorService virtualThreadExecutor, MeterRegistry meterRegistry) {
        this(virtualThreadExecutor, null, meterRegistry);
    }

    @Autowired
    public WorkloadService(
            @Qualifier("workloadVirtualThreadExecutor") ExecutorService virtualThreadExecutor,
            JdbcTemplate jdbcTemplate,
            MeterRegistry meterRegistry) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.jdbcTemplate = jdbcTemplate;
        this.meterRegistry = meterRegistry;
    }

    public WorkloadResult execute(int iterations) {
        return execute(new WorkloadRequest(WorkloadMode.CPU, iterations, 1));
    }

    public WorkloadResult execute(WorkloadRequest request) {
        long startedAt = System.nanoTime();
        try {
            WorkloadResult result = virtualThreadExecutor.submit(() -> executeOnVirtualThread(request)).get();
            recordExecution(request.mode(), "success", startedAt, result.executedOnVirtualThread());
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordExecution(request.mode(), "interrupted", startedAt, false);
            throw new IllegalStateException("Workload execution was interrupted", exception);
        } catch (ExecutionException exception) {
            recordExecution(request.mode(), "failure", startedAt, false);
            throw new IllegalStateException("Workload execution failed", exception.getCause());
        }
    }

    private void recordExecution(
            WorkloadMode mode,
            String outcome,
            long startedAt,
            boolean executedOnVirtualThread) {
        Counter.builder("workload.executions")
                .tags("mode", mode.value(), "outcome", outcome)
                .register(meterRegistry)
                .increment();
        Timer.builder("workload.execution")
                .tags("mode", mode.value(), "outcome", outcome)
                .register(meterRegistry)
                .record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
        if (executedOnVirtualThread) {
            Counter.builder("workload.virtual_threads")
                    .tag("mode", mode.value())
                    .register(meterRegistry)
                    .increment();
        }
    }

    private WorkloadResult executeOnVirtualThread(WorkloadRequest request) throws InterruptedException {
        return switch (request.mode()) {
            case CPU -> runCpu(request);
            case IO -> runIo(request);
            case DB -> runDatabase(request);
        };
    }

    private WorkloadResult runCpu(WorkloadRequest request) {
        long checksum = 0L;
        for (int index = 0; index < request.iterations(); index++) {
            checksum = (checksum * 1_103_515_245L + 12_345L + index) & 0x7fff_ffffL;
        }
        return new WorkloadResult(
                WorkloadMode.CPU,
                request.iterations(),
                request.delayMilliseconds(),
                checksum,
                Thread.currentThread().isVirtual());
    }

    private WorkloadResult runIo(WorkloadRequest request) throws InterruptedException {
        Thread.sleep(request.delayMilliseconds());
        return new WorkloadResult(
                WorkloadMode.IO,
                request.iterations(),
                request.delayMilliseconds(),
                0,
                Thread.currentThread().isVirtual());
    }

    private WorkloadResult runDatabase(WorkloadRequest request) {
        jdbcTemplate.queryForObject(
                "SELECT 1 FROM pg_sleep(?)",
                Integer.class,
                request.delayMilliseconds() / 1_000d);
        return new WorkloadResult(
                WorkloadMode.DB,
                request.iterations(),
                request.delayMilliseconds(),
                0,
                Thread.currentThread().isVirtual());
    }
}
