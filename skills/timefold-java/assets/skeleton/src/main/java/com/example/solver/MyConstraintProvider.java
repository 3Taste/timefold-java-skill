package com.example.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;

import com.example.domain.MyEntity;

/**
 * Constraint provider skeleton. Add one method per business rule and list it
 * in {@link #defineConstraints(ConstraintFactory)}.
 */
public class MyConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory cf) {
        return new Constraint[] {
            sameValueConflict(cf)
            // add more constraints here
        };
    }

    Constraint sameValueConflict(ConstraintFactory cf) {
        return cf.forEachUniquePair(MyEntity.class,
                    Joiners.equal(MyEntity::getValue))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Same value conflict");
    }
}
