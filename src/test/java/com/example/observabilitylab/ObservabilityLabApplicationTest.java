package com.example.observabilitylab;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:observability;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class ObservabilityLabApplicationTest {

    @Autowired
    @Qualifier("workloadVirtualThreadExecutor")
    private ExecutorService workloadVirtualThreadExecutor;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void configuresVirtualThreadWorkloadsAndHikariMetrics() throws Exception {
        assertThat(workloadVirtualThreadExecutor.submit(() -> Thread.currentThread().isVirtual()).get()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        assertThat(meterRegistry.find("hikaricp.connections").gauge()).isNotNull();
    }
}
