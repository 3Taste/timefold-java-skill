# Testing Patterns：ConstraintVerifier 范式

> `ConstraintVerifier` 是 Timefold 官方的约束单元测试工具。每条约束都应有独立单测。
>
> 依赖：`ai.timefold.solver:timefold-solver-test`（test scope）

---

## 1. 基本骨架

```java
import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MyConstraintProviderTest {

    private ConstraintVerifier<MyConstraintProvider, Schedule> constraintVerifier;

    @BeforeEach
    void setUp() {
        constraintVerifier = ConstraintVerifier.build(
            new MyConstraintProvider(),
            Schedule.class,           // @PlanningSolution
            ProcessStep.class,        // 所有 @PlanningEntity
            Equipment.class);
    }

    @Test
    void roomConflict_penalizesDoubleBooking() {
        Room room = new Room("R1");
        Timeslot ts = new Timeslot(MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 0));
        Lesson a = new Lesson("1", "Math", "Alice", "9A", ts, room);
        Lesson b = new Lesson("2", "Bio", "Bob",   "9A", ts, room);

        constraintVerifier.verifyThat(MyConstraintProvider::roomConflict)
            .given(a, b)
            .penalizesBy(1);
    }
}
```

---

## 2. 核心 API

### 2.1 验证单个约束
```java
constraintVerifier.verifyThat(Provider::constraintMethod)
    .given(fact1, fact2, ...)
    .penalizesBy(N)      // 期望惩罚量 N
    .rewardsWith(N)      // 期望奖励量 N
    .penalizes()         // 至少惩罚 1 次
    .rewards()           // 至少奖励 1 次
    .penalizesByLessThan(N)
    .rewardsWithMoreThan(N);
```

### 2.2 验证整体解的得分
```java
Schedule schedule = buildFullSchedule();
constraintVerifier.verifyThat()
    .givenSolution(schedule)
    .scores(HardSoftScore.of(-2, -10));
```

---

## 3. 每类约束的测试模式

### 3.1 硬约束（互斥）
```java
@Test
void resourceConflict_onePair_penalizesByOne() {
    Task t1 = new Task("1", slot1, res1);
    Task t2 = new Task("2", slot1, res1);
    constraintVerifier.verifyThat(Provider::resourceConflict)
        .given(t1, t2).penalizesBy(1);
}

@Test
void resourceConflict_differentResource_noPenalty() {
    Task t1 = new Task("1", slot1, res1);
    Task t2 = new Task("2", slot1, res2);
    constraintVerifier.verifyThat(Provider::resourceConflict)
        .given(t1, t2).penalizesBy(0);
}
```

### 3.2 计数 / 容量约束
```java
@Test
void capacityExceeded_byTwoDemand_penalizesByTwo() {
    Resource r = new Resource("R", 10);
    Task t1 = new Task("1", r, 6);  // demand=6
    Task t2 = new Task("2", r, 6);  // sum=12, over by 2
    constraintVerifier.verifyThat(Provider::capacityExceeded)
        .given(r, t1, t2).penalizesBy(2);
}
```

### 3.3 奖励（准时交付）
```java
@Test
void onTimeDelivery_rewardsPriority() {
    Lot lot = new Lot("L1", PRIORITY_HIGH, deadline, completion);
    constraintVerifier.verifyThat(Provider::onTimeDelivery)
        .given(lot).rewardsWith(5);   // priority = 5
}
```

### 3.4 Shadow 变量场景
Shadow 字段必须手动 set（ConstraintVerifier 不会自动计算 supplier）：
```java
@Test
void changeoverCost_penalizesDifference() {
    ProcessStep prev = new ProcessStep("s1", recipeA);
    ProcessStep curr = new ProcessStep("s2", recipeB);
    curr.setPrevious(prev);            // 手动设置 shadow
    curr.setEquipment(equipment);
    constraintVerifier.verifyThat(Provider::changeoverCost)
        .given(prev, curr).penalizesBy(30);  // 30 min 换型
}
```

### 3.5 PlanningListVariable 场景
```java
@Test
void listOrdering_penalizesWrongOrder() {
    Visit v1 = new Visit("1", location1);
    Visit v2 = new Visit("2", location2);
    Vehicle vehicle = new Vehicle("V");
    vehicle.setVisits(List.of(v1, v2));
    v1.setVehicle(vehicle); v1.setPreviousVisit(null);
    v2.setVehicle(vehicle); v2.setPreviousVisit(v1);
    // ... test constraint
}
```

---

## 4. 端到端求解测试

不只测约束，也测完整求解管道：
```java
@Test
void solve_producesFeasibleSolution() {
    Schedule problem = buildProblem();
    SolverFactory<Schedule> factory = SolverFactory.create(new SolverConfig()
        .withSolutionClass(Schedule.class)
        .withEntityClasses(ProcessStep.class)
        .withConstraintProviderClass(MyConstraintProvider.class)
        .withTerminationSpentLimit(Duration.ofSeconds(5)));
    Solver<Schedule> solver = factory.buildSolver();
    Schedule solved = solver.solve(problem);

    assertThat(solved.getScore().isFeasible()).isTrue();
}
```

---

## 5. Score 解释与调试

`SolutionManager.analyze(solution)` 提供结构化分数拆解，用于测试或客户端展示：
```java
SolutionManager<Schedule, HardSoftScore> sm = SolutionManager.create(factory);
ScoreAnalysis<HardSoftScore> analysis = sm.analyze(solution);
analysis.constraintMap().forEach((ref, analyze) -> {
    System.out.println(ref.constraintName() + ": " + analyze.score()
        + " from " + analyze.matches().size() + " matches");
});
```

---

## 6. 测试组织

```
src/test/java/
  <pkg>/solver/
    MyConstraintProviderTest.java      # 每条约束一个 @Test
    SolverIntegrationTest.java         # 端到端求解
    ScoreAnalysisTest.java             # 分数解释
```

## 7. 覆盖目标

- **每条约束 ≥ 3 个测试**：违反 N 次 / 违反 0 次 / 边界情况
- **每个 Shadow supplier ≥ 1 个测试**：验证依赖正确
- **端到端 ≥ 1 个测试**：验证 solver 能跑出可行解
- 总体 ≥ 80% 行覆盖率

---

## 8. 常见坑

1. **忘记 build entity 类列表**：`ConstraintVerifier.build` 要列出**所有** `@PlanningEntity`
2. **Shadow 字段必须手动 set**：单测环境不触发 supplier
3. **`given` 传入顺序不重要**：约束流自己会匹配
4. **分数是绝对值**：`penalizesBy(1)` 的意思是触发 1 个 match；动态权重（`penalize(.., weightFn)`）则是权重之和
5. **Joiners.filtering vs .filter 差异可能导致测试数差**：和生产代码保持一致
