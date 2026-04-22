package com.example;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.example.domain.MyEntity;
import com.example.domain.MySolution;
import com.example.domain.MyValue;
import com.example.solver.MyConstraintProvider;

import java.time.Duration;
import java.util.List;

public class App {
    public static void main(String[] args) {
        SolverFactory<MySolution> factory = SolverFactory.create(new SolverConfig()
                .withSolutionClass(MySolution.class)
                .withEntityClasses(MyEntity.class)
                .withConstraintProviderClass(MyConstraintProvider.class)
                .withTerminationSpentLimit(Duration.ofSeconds(5)));

        MySolution problem = new MySolution(
                List.of(new MyValue("A"), new MyValue("B"), new MyValue("C")),
                List.of(new MyEntity("e1"), new MyEntity("e2"), new MyEntity("e3")));

        Solver<MySolution> solver = factory.buildSolver();
        MySolution solved = solver.solve(problem);

        System.out.println("Score: " + solved.getScore());
        solved.getEntities().forEach(e ->
                System.out.println("  " + e.getId() + " -> " + e.getValue()));
    }
}
