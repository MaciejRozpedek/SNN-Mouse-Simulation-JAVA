package com.macroz.snnmousesimulation.api;

import com.macroz.snnmousesimulation.service.SimulationBenchmarkService;
import com.macroz.snnmousesimulation.service.SimulationEngine;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SimulationControllerTest {

    @Test
    void returnsBadRequestForInvalidBenchmarkParameters() throws Exception {
        var benchmarkService = mock(SimulationBenchmarkService.class);
        when(benchmarkService.run(anyDouble(), anyDouble(), anyDouble(), anyInt(), anyLong()))
                .thenThrow(new IllegalArgumentException("invalid"));
        MockMvc mockMvc = standaloneSetup(new SimulationController(mock(SimulationEngine.class), benchmarkService))
                .build();

        mockMvc.perform(post("/api/benchmark").param("durationMs", "0"))
                .andExpect(status().isBadRequest());
    }
}
