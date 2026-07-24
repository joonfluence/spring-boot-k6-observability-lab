package com.example.observabilitylab.workload;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class WorkloadServiceTest {

    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @AfterEach
    void shutDownExecutor() {
        virtualThreadExecutor.close();
    }

    @Test
    void executesDeterministicIterationsOnAVirtualThread() {
        var service = new WorkloadService(virtualThreadExecutor, new SimpleMeterRegistry());

        WorkloadResult result = service.execute(3);

        assertThat(result.iterations()).isEqualTo(3);
        assertThat(result.checksum()).isEqualTo(1_758_099_022L);
        assertThat(result.executedOnVirtualThread()).isTrue();
    }

    @Test
    void waitsOnAVirtualThreadForIoMode() {
        var service = new WorkloadService(virtualThreadExecutor, new SimpleMeterRegistry());

        WorkloadResult result = service.execute(new WorkloadRequest(WorkloadMode.IO, 1, 1));

        assertThat(result.mode()).isEqualTo(WorkloadMode.IO);
        assertThat(result.delayMilliseconds()).isEqualTo(1);
        assertThat(result.executedOnVirtualThread()).isTrue();
    }

    @Test
    void executesOneBoundedPostgresSleepQueryForDbMode() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        given(jdbcTemplate.queryForObject("SELECT 1 FROM pg_sleep(?)", Integer.class, 0.002d))
                .willReturn(1);
        var service = new WorkloadService(virtualThreadExecutor, jdbcTemplate, new SimpleMeterRegistry());

        WorkloadResult result = service.execute(new WorkloadRequest(WorkloadMode.DB, 1, 2));

        assertThat(result.mode()).isEqualTo(WorkloadMode.DB);
        assertThat(result.delayMilliseconds()).isEqualTo(2);
        assertThat(result.executedOnVirtualThread()).isTrue();
        then(jdbcTemplate).should().queryForObject("SELECT 1 FROM pg_sleep(?)", Integer.class, 0.002d);
    }

    @Test
    void recordsModeSpecificMicrometerMetricsForSuccessfulWorkloads() {
        var meterRegistry = new SimpleMeterRegistry();
        var service = new WorkloadService(virtualThreadExecutor, meterRegistry);

        service.execute(3);

        assertThat(meterRegistry.get("workload.executions")
                .tag("mode", "cpu")
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.get("workload.execution")
                .tag("mode", "cpu")
                .tag("outcome", "success")
                .timer()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.get("workload.virtual_threads")
                .tag("mode", "cpu")
                .counter()
                .count()).isEqualTo(1);
    }

    @Test
    void rejectsDatabaseLatencyAboveTheBoundedSleepLimit() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new WorkloadRequest(WorkloadMode.DB, 0, 5_001));
    }
}
