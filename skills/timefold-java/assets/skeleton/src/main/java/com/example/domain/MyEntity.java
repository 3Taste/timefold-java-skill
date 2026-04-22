package com.example.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;

/**
 * Planning entity skeleton.
 * Replace fields with your domain attributes; keep at least one @PlanningVariable
 * (or @PlanningListVariable on a "carrier" entity).
 */
@PlanningEntity
public class MyEntity {

    @PlanningId
    private String id;

    // The single-value planning variable. Swap with @PlanningListVariable if you need an ordered list.
    @PlanningVariable
    private MyValue value;

    public MyEntity() {}

    public MyEntity(String id) {
        this.id = id;
    }

    public String getId() { return id; }

    public MyValue getValue() { return value; }
    public void setValue(MyValue value) { this.value = value; }
}
