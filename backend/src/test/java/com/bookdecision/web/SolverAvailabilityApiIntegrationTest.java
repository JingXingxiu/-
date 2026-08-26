package com.bookdecision.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "book-decision.solver.max-total-time-seconds-per-options-request=0.000000001")
@AutoConfigureMockMvc
class SolverAvailabilityApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesStrategyBoundaryBudgetExhaustionAsAnExplicit503Problem() throws Exception {
        mockMvc.perform(post("/api/v1/decision-options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "datasetVersion": "mixed-demo-v1",
                                  "inventory": [
                                    {"isbn": "9787020002207", "quantity": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode")
                        .value("SOLVER_OPTIONS_TIME_BUDGET_EXCEEDED"));
    }
}
