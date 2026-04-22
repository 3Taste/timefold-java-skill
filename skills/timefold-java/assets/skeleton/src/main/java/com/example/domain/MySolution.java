package com.example.domain;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import java.util.List;

/**
 * Planning solution skeleton — the container of one full solution.
 */
@PlanningSolution
public class MySolution {

    @ProblemFactCollectionProperty
    @ValueRangeProvider
    private List<MyValue> values;

    @PlanningEntityCollectionProperty
    private List<MyEntity> entities;

    @PlanningScore
    private HardSoftScore score;

    public MySolution() {}

    public MySolution(List<MyValue> values, List<MyEntity> entities) {
        this.values = values;
        this.entities = entities;
    }

    public List<MyValue> getValues() { return values; }
    public List<MyEntity> getEntities() { return entities; }
    public HardSoftScore getScore() { return score; }
    public void setScore(HardSoftScore score) { this.score = score; }
}
