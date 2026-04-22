package com.example.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;

import com.example.domain.MyEntity;
import com.example.domain.MySolution;
import com.example.domain.MyValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MyConstraintProviderTest {

    private ConstraintVerifier<MyConstraintProvider, MySolution> cv;

    @BeforeEach
    void setUp() {
        cv = ConstraintVerifier.build(new MyConstraintProvider(), MySolution.class, MyEntity.class);
    }

    @Test
    void sameValueConflict_penalizesEachConflict() {
        MyValue v = new MyValue("A");
        MyEntity e1 = new MyEntity("1"); e1.setValue(v);
        MyEntity e2 = new MyEntity("2"); e2.setValue(v);

        cv.verifyThat(MyConstraintProvider::sameValueConflict)
            .given(e1, e2)
            .penalizesBy(1);
    }

    @Test
    void sameValueConflict_differentValues_noPenalty() {
        MyEntity e1 = new MyEntity("1"); e1.setValue(new MyValue("A"));
        MyEntity e2 = new MyEntity("2"); e2.setValue(new MyValue("B"));

        cv.verifyThat(MyConstraintProvider::sameValueConflict)
            .given(e1, e2)
            .penalizesBy(0);
    }
}
