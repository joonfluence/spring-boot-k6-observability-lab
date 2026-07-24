package com.example.observabilitylab.workload;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkloadExecutionConfiguration {

    @Bean(name = "workloadVirtualThreadExecutor", destroyMethod = "close")
    public ExecutorService workloadVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
