# Timefold 能力目录（Capability Catalog）

> 按"问题原语"组织。每条能力 = 一个建模动作。遇到新需求时，先在 [modeling-methodology.md](modeling-methodology.md) 走 6 步法识别需要哪些能力，再到本目录找对应注解 / API。
>
> 所有签名来自 `timefold-solver/core/src/main/java/ai/timefold/solver/core/api/`，对齐版本 1.31+。

---

## §0 顶层结构

### 0.1 @PlanningSolution
**解决的问题**：定义"一个完整解"的容器类。
**注解**：`ai.timefold.solver.core.api.domain.solution.PlanningSolution`
**必含字段**：
- `@PlanningEntityCollectionProperty` 或 `@PlanningEntityProperty` —— 规划实体集合
- `@ProblemFactCollectionProperty` 或 `@ProblemFactProperty` —— 不会变的事实数据（资源、约束参数）
- `@ValueRangeProvider` —— 暴露值域给 PlanningVariable
- `@PlanningScore` —— 求解器写回的分数

**模板**：
```java
@PlanningSolution
public class Schedule {
    @ProblemFactCollectionProperty @ValueRangeProvider
    private List<Equipment> equipments;
    @PlanningEntityCollectionProperty
    private List<ProcessStep> steps;
    @PlanningScore
    private HardSoftScore score;
    public Schedule() {}  // 无参构造必需
}
```
**坑**：必须有无参构造；`@ValueRangeProvider` 字段必须同时是 `@ProblemFactCollectionProperty`（或 entity 集合）。

### 0.2 @PlanningEntity
**解决的问题**：标记"需要被规划的对象"。
**注解**：`ai.timefold.solver.core.api.domain.entity.PlanningEntity`
**关键参数**：
- `pinningFilter` —— 运行时过滤被锁定的实体
- `difficultyComparatorClass` / `difficultyComparatorFactoryClass` —— 构造启发式排序依据

**坑**：实体必须包含至少一个 `@PlanningVariable` / `@PlanningListVariable`（shadow 不算）。

### 0.3 @PlanningId
**注解**：`ai.timefold.solver.core.api.domain.lookup.PlanningId`
用于 ProblemChange / move thread、保证实体有稳定 ID。任何会被 join / Joiners.equal 比较的实体都应加。

---

## §1 单值分配（Basic PlanningVariable）

**解决的问题**：实体从值域中选一个值（教室、设备、时段）。
**触发特征**："给 X 分配一个 Y"，X、Y 各有一对一关系，无序。
**注解**：`@PlanningVariable`
**关键参数**：
- `valueRangeProviderRefs` —— 引用具名 ValueRangeProvider（多值域时）
- `allowsUnassigned = true` —— 允许 null（过约束、可选分配）
- `comparatorClass` —— 值排序，构造启发式优先选"强"的

**模板**：
```java
@PlanningEntity
public class Lesson {
    @PlanningId String id;
    @PlanningVariable Room room;
    @PlanningVariable Timeslot timeslot;
}
```

**变体**：单实体多个 `@PlanningVariable`（如 Lesson 同时分配 room+timeslot）—— 求解器视为独立维度。

**已废弃**：`graphType = CHAINED`、`nullable`、`strengthComparatorClass` —— 用 `@PlanningListVariable` 和 `comparatorClass` 替代。

**示例参考**：`java/hello-world/src/main/java/org/acme/schooltimetabling/domain/Lesson.java`

---

## §2 有序序列（PlanningListVariable）

**解决的问题**：把一组值排成有序列表，挂在某个"载体实体"下（车辆装载访问顺序、设备执行任务序列、机台工艺链）。
**触发特征**：路径、顺序、装载、机台序列、流水线。任何"先后顺序"出现的需求。
**注解**：`@PlanningListVariable`
**关键参数**：
- `valueRangeProviderRefs`
- `allowsUnassignedValues = true` —— 允许某些值不被分配到任何列表

**模板**：
```java
@PlanningEntity
public class Equipment {
    @PlanningId String id;
    @PlanningListVariable(allowsUnassignedValues = true)
    private List<ProcessStep> steps = new ArrayList<>();
}
```

**配套必备 shadow**（在被装入列表的元素侧）：
- `@InverseRelationShadowVariable(sourceVariableName = "steps")` → 反查载体
- `@PreviousElementShadowVariable(sourceVariableName = "steps")` / `@NextElementShadowVariable`
- `@IndexShadowVariable(sourceVariableName = "steps")` —— 当前在列表中第几位

**坑**：不要把 `@PlanningListVariable` 和 `@PlanningVariable` 混在同一实体上指向同一目标关系；列表语义已经隐含了 vehicle 字段，反向用 inverse shadow。

**示例参考**：`java/vehicle-routing/src/main/java/org/acme/vehiclerouting/domain/Vehicle.java` + `Visit.java`

---

## §3 派生状态（Shadow Variables）

派生状态 = 由规划变量计算出来、不参与搜索但供约束使用的字段。**永远不要把派生字段标成 @PlanningVariable**。

### 3.1 @InverseRelationShadowVariable
**注解**：`ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable`
**用途**：列表元素反查载体（Visit → Vehicle）。
```java
@InverseRelationShadowVariable(sourceVariableName = "visits")
private Vehicle vehicle;
```

### 3.2 @PreviousElementShadowVariable / @NextElementShadowVariable
**用途**：列表中的前一个 / 后一个元素。
```java
@PreviousElementShadowVariable(sourceVariableName = "visits")
private Visit previousVisit;
```

### 3.3 @IndexShadowVariable
**用途**：当前元素在列表中的索引（0-based）。

### 3.4 @ShadowVariable + @ShadowSources（推荐方式，1.27+）
**解决的问题**：自定义派生（到达时间、累计成本、级联终止时间）。
**注解**：
- `ai.timefold.solver.core.api.domain.variable.ShadowVariable`（`supplierName`）
- `ai.timefold.solver.core.api.domain.variable.ShadowSources`（声明依赖）

**模板**：
```java
@ShadowVariable(supplierName = "arrivalTimeSupplier")
private LocalDateTime arrivalTime;

@ShadowSources({"vehicle", "previousVisit.arrivalTime"})
public LocalDateTime arrivalTimeSupplier() {
    if (vehicle == null) return null;
    LocalDateTime depart = (previousVisit == null)
        ? vehicle.getDepartureTime()
        : previousVisit.getDepartureTime();
    return depart.plus(travelTimeFrom(previousVisit));
}
```

**坑**：
- `@ShadowSources` 路径用点号串导航字段；source 必须是 genuine variable 或其他 shadow
- supplier 方法必须无副作用，纯计算
- 不要用废弃的 `variableListenerClass / sourceVariableName` 写法（1.27 起 deprecated）

### 3.5 @CascadingUpdateShadowVariable
**用途**：当上游 shadow 改变时，沿列表向后级联更新（VRP 的到达时间链式传播经典场景）。
**注解**：`ai.timefold.solver.core.api.domain.variable.CascadingUpdateShadowVariable`
**模板**：
```java
@CascadingUpdateShadowVariable(targetMethodName = "updateArrivalTime")
private LocalDateTime arrivalTime;

public void updateArrivalTime() { /* 计算并 set */ }
```

### 3.6 @ShadowVariablesInconsistent
**用途**：标记"上游变量组合非法"，让求解器不要把这种实体当合法解；自定义校验。

**示例参考**：`java/vehicle-routing/src/main/java/org/acme/vehiclerouting/domain/Visit.java`

---

## §4 可选分配 / 过约束（allowsUnassigned）

**解决的问题**：值域无法满足所有实体（容量不够 / 资源不足）时，允许部分实体不被分配，并通过软约束惩罚 unassigned。
**API**：
- 单值：`@PlanningVariable(allowsUnassigned = true)` → 值可以是 null
- 列表：`@PlanningListVariable(allowsUnassignedValues = true)` → 列表里可少装某些值

**约束侧**：用 `forEachIncludingUnassigned(...)` / `complement(...)` 来抓 null 实体并惩罚。

**示例参考**：`java/order-picking/`、`java/vehicle-routing/`（Visit allowsUnassignedValues）

---

## §5 固定 / 已分配部分（Pinning）

### 5.1 @PlanningPin
**注解**：`ai.timefold.solver.core.api.domain.entity.PlanningPin`
布尔字段，`true` = 该实体的所有规划变量在求解中冻结。

### 5.2 @PlanningPinToIndex
**用途**：列表变量场景，固定列表前 N 个元素不变（实时排程已经发出去的任务不能重排）。
```java
@PlanningPinToIndex
private int pinnedIndex;  // 列表前 pinnedIndex 个元素冻结
```

**触发场景**：实时排程、Continuous Planning、ProblemChange 增量更新。

**示例参考**：`java/employee-scheduling/`（含历史已确定的班次）

---

## §6 值域（ValueRangeProvider）

**注解**：`ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider`
**两种位置**：
- 在 `@PlanningSolution` 字段上（全局值域）
- 在 `@PlanningEntity` 字段上（实体级动态值域，如"该工艺步骤只能放在能力匹配的设备上"）

**模板（实体级动态值域）**：
```java
@PlanningEntity
public class ProcessStep {
    @PlanningVariable(valueRangeProviderRefs = "compatibleEquipments")
    private Equipment equipment;

    @ValueRangeProvider(id = "compatibleEquipments")
    public List<Equipment> compatibleEquipments() {
        return recipe.getCompatibleEquipments();  // 只暴露兼容机台
    }
}
```

**ValueRangeFactory**：连续 / 离散范围生成器（`ValueRangeFactory.createIntValueRange(0, 100)`）。

**坑**：动态值域里不要包含规划变量本身的状态；返回结果应仅依赖 problem facts。

**示例参考**：`java/maintenance-scheduling/`、`java/conference-scheduling/`

---

## §7 分数类型（Score）

**包**：`ai.timefold.solver.core.api.score.buildin.*`

| 类型 | 用途 | 包 |
|---|---|---|
| `HardSoftScore` | 最常用，硬约束 + 软约束两层 | `hardsoft` |
| `HardMediumSoftScore` | 三层（硬 / 业务规则 / 偏好） | `hardmediumsoft` |
| `BendableScore` | 自定义任意层数 | `bendable` |
| `*LongScore` | 大数量级（超 21 亿）用 long | `*long` |
| `*BigDecimalScore` | 货币 / 精确计算 | `*bigdecimal` |
| `SimpleScore` | 单一数值 | `simple` |

**选择决策**：
- 默认 `HardSoftScore`
- 加约束类别（如硬 / 法规 / 偏好）→ `HardMediumSoftScore` 或 `BendableScore`
- 超大规模分数（百万实体 × 大权重）→ `*LongScore`
- 涉及金额精确计算 → `*BigDecimalScore`

**配置在 PlanningSolution**：
```java
@PlanningScore
private HardSoftScore score;
```

---

## §8 ConstraintProvider 与权重

### 8.1 ConstraintProvider
**接口**：`ai.timefold.solver.core.api.score.stream.ConstraintProvider`
```java
public class MyConstraintProvider implements ConstraintProvider {
    @Override
    public Constraint[] defineConstraints(ConstraintFactory cf) {
        return new Constraint[] { rule1(cf), rule2(cf) };
    }
}
```
所有约束在此聚合；具体 DSL 见 [constraint-cookbook.md](constraint-cookbook.md)。

### 8.2 @ConstraintConfiguration + @ConstraintWeight（旧）/ ConstraintWeightOverrides（新，推荐）
**注解**：`ai.timefold.solver.core.api.domain.constraintweight.ConstraintWeightOverrides`
**用途**：运行时调整约束权重而不改代码。
```java
@ConstraintWeightOverrides
private ConstraintWeightOverrides<HardSoftScore> overrides;
// overrides.put("Room conflict", HardSoftScore.ofHard(10));
```

约束侧：`.penalizeConfigurable().asConstraint("Room conflict")` 让权重可被 override。

---

## §9 求解器入口

### 9.1 SolverFactory（同步、纯 JAR）
**类**：`ai.timefold.solver.core.api.solver.SolverFactory`
```java
SolverFactory<Schedule> factory = SolverFactory.create(new SolverConfig()
    .withSolutionClass(Schedule.class)
    .withEntityClasses(ProcessStep.class)
    .withConstraintProviderClass(MyConstraintProvider.class)
    .withTerminationSpentLimit(Duration.ofSeconds(30)));
Solver<Schedule> solver = factory.buildSolver();
Schedule solved = solver.solve(problem);
```

### 9.2 SolverManager（异步、推荐用于 web 服务）
**类**：`ai.timefold.solver.core.api.solver.SolverManager`
```java
SolverJob<Schedule, String> job = solverManager.solveBuilder()
    .withProblemId(jobId)
    .withProblemFinder(id -> repo.find(id))
    .withBestSolutionConsumer(s -> repo.save(s))
    .withExceptionHandler((id, ex) -> log.error(...))
    .run();
```

### 9.3 SolutionManager（分数解释 / justifications）
**类**：`ai.timefold.solver.core.api.solver.SolutionManager`
- `.analyze(solution)` → `ScoreAnalysis`，给出每条约束触发了多少匹配 + 各自贡献分数
- `.explain(solution)` → 文本解释（调试 / 客户端展示）

---

## §10 增量更新（Real-time Planning）

**接口**：`ai.timefold.solver.core.api.solver.change.ProblemChange`
**用途**：求解过程中变更问题（新订单插入、车辆故障下线、设备故障）。
```java
solverJob.addProblemChange((solution, problemChangeDirector) -> {
    problemChangeDirector.addEntity(newStep, solution.getSteps()::add);
});
```
配合 `@PlanningPin` / `@PlanningPinToIndex` 保证已确认的部分不被打乱。

---

## §11 多阶段求解 / 调优入口

**配置文件 / Config 类**：见 [solver-tuning.md](solver-tuning.md)

核心阶段：
- **Construction Heuristic**：从 0 起构造初始解（First Fit Decreasing 等）
- **Local Search**：在初始解上邻域搜索（Tabu / Late Acceptance / Simulated Annealing）
- **Custom Phase**：嵌入业务定制启发式

**Nearby Selection**：大规模 VRP 必备，`nearbySelection` 限制 move 选取相邻候选，避免笛卡尔积爆炸。

---

## §12 Benchmarker

**模块**：`ai.timefold.solver:timefold-solver-benchmark`
对比多套 solver 配置在同一数据集上的表现，输出 HTML 报告。用于参数调优后期。

---

## 跨能力组合速查

| 业务原语 | 能力组合 |
|---|---|
| 任务→机台 + 任务有序 | §2 PlanningListVariable + §3.1 InverseRelation + §3.2 Previous/Next |
| 任务有时间窗 + 链式到达 | §2 + §3.4/3.5 ShadowVariable / Cascading |
| 部分订单可拒接 | §1/§2 + §4 allowsUnassigned + cookbook 中 unassigned penalty |
| 历史排程不动 + 增量插单 | §5 PlanningPinToIndex + §10 ProblemChange |
| 设备能力匹配（动态值域） | §6 entity-level @ValueRangeProvider |
| 多目标加权可调 | §7 BendableScore + §8.2 ConstraintWeightOverrides |
| 大规模（>10k 实体） | §11 Nearby Selection + §7 *LongScore |
