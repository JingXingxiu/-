package com.bookdecision.application;

import com.bookdecision.solver.OrToolsBookAllocationSolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SolverProperties.class)
public class DecisionApplicationConfiguration {

    @Bean
    OrToolsBookAllocationSolver bookAllocationSolver(SolverProperties properties) {
        // Four lexicographic phases, each bounded so one demo request cannot monopolize the API.
        return new OrToolsBookAllocationSolver(properties.toSolverOptions());
    }

    @Bean
    SolverRequestBulkhead solverRequestBulkhead(SolverProperties properties) {
        return new SolverRequestBulkhead(properties);
    }
}
