# Solver Tuning：求解器配置与调优

> 所有配置项对齐 `timefold-solver/core/src/main/resources/solver.xsd`（版本 1.31+）。
>
> 两种配置方式：
> - **SolverConfig API**（纯 JAR）
> - **application.properties**（Spring Boot / Quarkus，属性前缀不同）

---

## 1. 核心配置骨架

### 1.1 SolverConfig（Java API）
```java
SolverConfig config = new SolverConfig()
    .withSolutionClass(Schedule.class)
    .withEntityClasses(ProcessStep.class, Equipment.class)
    .withConstraintProviderClass(MyConstraintProvider.class)
    .withTerminationSpentLimit(Duration.ofSeconds(30));
SolverFactory<Schedule> factory = SolverFactory.create(config);
```

### 1.2 Spring Boot `application.properties`
```properties
timefold.solver.solution-class=com.example.Schedule
timefold.solver.entity-classes=com.example.ProcessStep,com.example.Equipment
timefold.solver.constraint-provider-class=com.example.MyConstraintProvider
timefold.solver.termination.spent-limit=30s
timefold.solver.termination.unimproved-spent-limit=10s
```
依赖：`ai.timefold.solver:timefold-solver-spring-boot-starter`

### 1.3 Quarkus `application.properties`
```properties
quarkus.timefold.solver.termination.spent-limit=30s
quarkus.timefold.solver.termination.unimproved-spent-limit=10s
```
> Quarkus 会自动扫描 classpath 下的 `@PlanningSolution` / `@PlanningEntity` / `ConstraintProvider`，无需显式声明类。
> 依赖：`ai.timefold.solver:timefold-solver-quarkus`

---

## 2. Termination（求解停止条件）

最重要的调优项。**至少配一个**，常组合使用。

| 属性 | 含义 | 何时用 |
|---|---|---|
| `spent-limit` | 绝对时间 | 在线服务，严格响应时间 |
| `unimproved-spent-limit` | 多久没改进就停 | 离线长跑，避免 kill 过早 |
| `best-score-limit` | 达到指定分数停 | 有明确目标分 |
| `step-count-limit` | 迭代步数 | benchmark 对比 |
| `unimproved-step-count-limit` | 多少步未改进 | 搭配 local search |

组合（满足**任一**即停）：
```xml
<termination>
    <spentLimit>PT5M</spentLimit>
    <unimprovedSpentLimit>PT30S</unimprovedSpentLimit>
</termination>
```

Properties：
```properties
timefold.solver.termination.spent-limit=5m
timefold.solver.termination.unimproved-spent-limit=30s
```

---

## 3. 两阶段默认流程

不显式配阶段时，Timefold 默认跑：
1. **Construction Heuristic**：First Fit Decreasing（从无到初始解）
2. **Local Search**：Late Acceptance（默认的邻域搜索算法）

90% 场景默认即可。特殊需求才手动改。

### 3.1 手动指定阶段
```xml
<solver>
  <constructionHeuristic>
    <constructionHeuristicType>FIRST_FIT_DECREASING</constructionHeuristicType>
  </constructionHeuristic>
  <localSearch>
    <localSearchType>LATE_ACCEPTANCE</localSearchType>
  </localSearch>
</solver>
```

常见 Construction Heuristic 类型：
- `FIRST_FIT` / `FIRST_FIT_DECREASING`
- `WEAKEST_FIT` / `WEAKEST_FIT_DECREASING`
- `STRONGEST_FIT` / `STRONGEST_FIT_DECREASING`
- `ALLOCATE_ENTITY_FROM_QUEUE` / `CHEAPEST_INSERTION`

常见 Local Search 类型：
- `LATE_ACCEPTANCE`（默认，稳定）
- `TABU_SEARCH`（避免循环，适合小规模）
- `SIMULATED_ANNEALING`（跳出局部最优）
- `HILL_CLIMBING`（快但易卡局部）

---

## 4. 规模与策略决策

| 规模（实体 × 值域） | 建议 |
|---|---|
| < 10⁴ | 默认配置即可 |
| 10⁴ ~ 10⁶ | 开启 Nearby Selection；考虑 `*LongScore` |
| > 10⁶ | Nearby + 分片 + 分阶段 + multi-threaded solving |

### 4.1 Nearby Selection（大规模必备）
```xml
<localSearch>
  <unionMoveSelector>
    <changeMoveSelector>
      <entitySelector id="es1"/>
      <valueSelector>
        <nearbySelection>
          <originEntitySelector mimicSelectorRef="es1"/>
          <nearbyDistanceMeterClass>com.example.LocationDistance</nearbyDistanceMeterClass>
          <parabolicDistributionSizeMaximum>80</parabolicDistributionSizeMaximum>
        </nearbySelection>
      </valueSelector>
    </changeMoveSelector>
  </unionMoveSelector>
</localSearch>
```
> 原理：不在全局值域选 move 目标，只在"距离近"的邻域里选，避免笛卡尔积爆炸。VRP 必备。

### 4.2 Multi-threaded Solving
```properties
timefold.solver.move-thread-count=AUTO
```
或具体数字（如 `4`）。AUTO 按 CPU 核数自动推断。

---

## 5. 分数类型选择

见 [capability-catalog.md §7](capability-catalog.md#7-分数类型score)：
- 默认 `HardSoftScore`
- 三层（法规/业务/偏好） → `HardMediumSoftScore`
- 可自定义层数 → `BendableScore`
- 大数值 → `*LongScore`
- 金额精确 → `*BigDecimalScore`

---

## 6. 其他常用配置

### 6.1 随机种子（可复现性）
```properties
timefold.solver.environment-mode=REPRODUCIBLE
timefold.solver.random-seed=0
```

### 6.2 环境模式（调试用）
```properties
timefold.solver.environment-mode=FULL_ASSERT    # 严格校验，慢，排查问题
# REPRODUCIBLE（默认）/ NO_ASSERT / NON_REPRODUCIBLE
```

### 6.3 日志级别
Spring Boot / Quarkus：
```properties
logging.level.ai.timefold.solver=DEBUG
```

---

## 7. 调优检查清单

按顺序：
1. **约束逻辑对吗**：用 ConstraintVerifier 逐条测（见 [testing-patterns.md](testing-patterns.md)）
2. **Score 趋势**：开 INFO 日志看每秒 score 变化；如果早早停滞，换 localSearchType 或加 spent-limit
3. **搜索空间合理吗**：实体数 × 平均值域 vs 你的时间预算；不匹配考虑 Nearby Selection
4. **是否开了多线程**：`move-thread-count=AUTO`
5. **Benchmarker 对比**：最后阶段用 `timefold-solver-benchmark` 跑同数据集对比多套配置

---

## 8. Benchmarker

依赖：`ai.timefold.solver:timefold-solver-benchmark`
用途：同一个问题数据跑多套 SolverConfig，生成 HTML 对比报告。
```java
PlannerBenchmarkFactory factory = PlannerBenchmarkFactory.createFromXmlResource(
    "benchmarkConfig.xml");
factory.buildPlannerBenchmark(problems).benchmarkAndShowReportInBrowser();
```

---

## 9. 实时排程（Continuous Planning）

配合 [capability-catalog.md §10](capability-catalog.md#10-增量更新real-time-planning)：
- SolverManager 异步 solve
- 运行中 `addProblemChange` 注入变更
- `@PlanningPin` / `@PlanningPinToIndex` 冻结已执行部分
- 设置足够长的 `unimproved-spent-limit` 而非固定 `spent-limit`，让求解器持续改进

---

## 10. 常见问题

| 症状 | 排查 |
|---|---|
| 求解器一直返回同样差的解 | 检查硬约束是否互相冲突导致无可行解；放宽某条约束验证 |
| 求解器很快停但分数差 | 增加 `unimproved-spent-limit`；换更强的 localSearchType |
| 求解慢（实体 > 1k） | 启用 Nearby Selection + multi-thread |
| 结果不可复现 | `environment-mode=REPRODUCIBLE` + 固定 random-seed |
| ConstraintProvider 报错 | 单跑 `ConstraintVerifier` 单测隔离问题 |
| shadow 字段为 null | 检查 `@ShadowSources` 是否声明了全部依赖 |
