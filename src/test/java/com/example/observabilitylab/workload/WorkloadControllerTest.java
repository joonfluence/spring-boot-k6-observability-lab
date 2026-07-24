package com.example.observabilitylab.workload;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkloadController.class)
class WorkloadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkloadService workloadService;

    @Test
    void runsTheRequestedCpuWorkload() throws Exception {
        given(workloadService.execute(new WorkloadRequest(WorkloadMode.CPU, 3, 10)))
                .willReturn(new WorkloadResult(WorkloadMode.CPU, 3, 10, 1_758_099_022L, true));

        mockMvc.perform(get("/api/work")
                        .param("mode", "cpu")
                        .param("latencyMs", "10")
                        .param("cpuIterations", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("cpu"))
                .andExpect(jsonPath("$.iterations").value(3))
                .andExpect(jsonPath("$.checksum").value(1_758_099_022L))
                .andExpect(jsonPath("$.executedOnVirtualThread").value(true));

        then(workloadService).should().execute(new WorkloadRequest(WorkloadMode.CPU, 3, 10));
    }
}
