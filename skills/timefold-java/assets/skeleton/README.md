# Timefold Skeleton

A minimal, runnable skeleton for a Timefold Solver Java project. Copy this
directory and fill in your domain.

## Run
```
mvn -q compile exec:java -Dexec.mainClass=com.example.App
mvn -q test
```

## What to change
1. `domain/MyEntity.java` — the thing to be planned; add domain attributes.
2. `domain/MyValue.java` — the values the variable can take (Room / Equipment / Timeslot / ...).
3. `domain/MySolution.java` — wire up entity/value lists, keep the `@PlanningScore`.
4. `solver/MyConstraintProvider.java` — one method per business rule.
5. `App.java` — replace the sample data with your problem loading.

See the methodology doc for how to decide what goes where.
