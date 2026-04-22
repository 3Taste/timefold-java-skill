---
name: timefold-java
description: Use when user asks to design or implement a scheduling, planning, or resource-allocation system in Java — including production scheduling, job-shop, rostering, routing, timetabling, capacity planning, batching, or allocation under constraints. Also triggers on Chinese keywords 排程、排产、排班、调度、分配、规划、优化、约束. Guides Claude through a 6-step modeling methodology from business description to Timefold domain classes, constraints, and solver config.
---

# Timefold Java Skill

> **核心定位**：从**业务需求 → Timefold 领域模型 + 约束 + 求解器配置**的方法论 + 能力目录。
>
> 不是"场景查找表"。遇到任何新排程需求（教学排课、车辆路径、人员排班、半导体生产排程、项目调度 ...）都走同一套 6 步法。

## 触发条件

**使用本 skill 当用户请求涉及：**
- 任务 / 资源 / 时间的分配或排程（产线排程、车辆路径、人员排班、课表、会议室）
- 优化问题：在硬约束下最大化 / 最小化某目标
- 显式提到 Timefold / OptaPlanner / ConstraintProvider / @PlanningEntity
- 中文关键词：**排程、排产、排班、调度、分配、规划、优化、约束**
- "给 X 安排 Y"、"在时间 / 机台 / 人员约束下"、"最小化成本 / 最大化吞吐"

## 首要动作：走 6 步建模方法论

拿到需求先做 6 步抽象，**不要直接写代码**。详见 [references/modeling-methodology.md](references/modeling-methodology.md)。

1. **识别规划实体**：哪些对象"需要被安排"？（`@PlanningEntity`）
2. **识别规划变量**：实体的什么属性是解空间？单值 / 有序列表？（`@PlanningVariable` / `@PlanningListVariable`）
3. **识别值域**：变量取值是静态全局还是动态按实体过滤？（`@ValueRangeProvider` 位置）
4. **识别派生状态**：哪些字段是算出来的？（`@InverseRelationShadowVariable` / `@PreviousElementShadowVariable` / `@ShadowVariable` + `@ShadowSources` / `@CascadingUpdateShadowVariable`）
5. **分类约束**：硬 / 中 / 软，每条归到 [cookbook](references/constraint-cookbook.md) 的范式
6. **选择求解器配置与集成**：纯 JAR / Spring Boot / Quarkus + termination 策略

每一步对应的能力都在 [references/capability-catalog.md](references/capability-catalog.md)。

### 输出物清单（每次需求都产出）
1. **领域类骨架**（Entity / Fact / Solution）
2. **变量 / Shadow 选型表**（字段 → 注解 + 理由）
3. **硬/中/软约束分类表**（每条 → 一行 ConstraintStream）
4. **求解器配置建议**（termination + 集成方式 + 是否 ProblemChange）
5. **测试提示**（每条约束对应的 ConstraintVerifier 测试）

---

## 能力索引（快速定位注解 / API）

完整细节见 [references/capability-catalog.md](references/capability-catalog.md)。

| 建模需求 | Timefold 能力 | 能力目录章节 |
|---|---|---|
| 实体从值域选 1 个 | `@PlanningVariable` | §1 |
| 实体排成有序列表（路径 / 工序链 / 装载） | `@PlanningListVariable` + 配套 shadow | §2 |
| 反查列表载体 | `@InverseRelationShadowVariable` | §3.1 |
| 列表中的前 / 后 / 索引 | `@PreviousElementShadowVariable` / `@NextElementShadowVariable` / `@IndexShadowVariable` | §3.2 / §3.3 |
| 派生字段（到达时间、总成本） | `@ShadowVariable(supplierName)` + `@ShadowSources` | §3.4 |
| 沿列表级联派生 | `@CascadingUpdateShadowVariable` | §3.5 |
| 允许部分实体不分配 | `allowsUnassigned=true` / `allowsUnassignedValues=true` | §4 |
| 冻结已确定的排程 | `@PlanningPin` / `@PlanningPinToIndex` | §5 |
| 动态值域（按实体过滤） | 实体级 `@ValueRangeProvider` | §6 |
| 选分数类型 | `HardSoftScore` / `HardMediumSoftScore` / `BendableScore` / `*LongScore` | §7 |
| 约束逻辑 | `ConstraintProvider` + ConstraintStream DSL | §8 |
| 运行时调权重 | `ConstraintWeightOverrides` | §8.2 |
| 异步求解（Web 服务） | `SolverManager` | §9.2 |
| 分数解释 | `SolutionManager.analyze()` | §9.3 |
| 实时变更 | `ProblemChange` | §10 |
| 大规模加速 | Nearby Selection | §11 |
| 区间聚类 / 间隙检测 | `toConnectedRanges` / `toConnectedTemporalRanges` | §13 |
| 方案对比（Preview） | `SolutionManager.diff()` | §14 |
| 自定义 Move（Preview） | Neighborhoods API | §15 |
| 新元启发式（Preview） | Diversified Late Acceptance | §16 |

## 约束食谱快速指引

写 ConstraintStream 前先查 [references/constraint-cookbook.md](references/constraint-cookbook.md)：

- **互斥 / 重叠** → `forEachUniquePair` + `Joiners.overlapping`
- **前置依赖** → `forEach` + filter 前置.endTime vs 当前.startTime
- **容量 / 总和** → `groupBy` + `sum` + `filter`
- **计数限制** → `groupBy` + `count` + `filter`
- **存在 / 必需** → `ifNotExists` / `forEachIncludingUnassigned`
- **禁止组合** → `ifExists` / filter
- **负载均衡** → `ConstraintCollectors.loadBalance` 或平方惩罚
- **换型成本** → filter has previous + penalize(changeover cost)
- **准时奖励** / **迟到惩罚** → filter + reward / penalize
- **区间聚类 / 间隙** → `toConnectedRanges` / `toConnectedTemporalRanges`

## 求解器配置

见 [references/solver-tuning.md](references/solver-tuning.md)：
- Termination 组合：`spent-limit` + `unimproved-spent-limit`
- 大规模：Nearby Selection + multi-thread
- 可复现：`environment-mode=REPRODUCIBLE` + `random-seed`
- Preview 特性：`enablePreviewFeature`（Diversified Late Acceptance / Neighborhoods / Solution Diff）

## 集成方式

见 [references/integration.md](references/integration.md)：
- **Spring Boot**：`timefold-solver-spring-boot-starter` + `timefold.solver.*` properties
- **Quarkus**：`timefold-solver-quarkus` + `quarkus.timefold.solver.*` properties
- **纯 JAR**：`SolverFactory.create(new SolverConfig()...)`

骨架可直接复用：`assets/skeleton/`（pom.xml + Entity/Value/Solution/ConstraintProvider/App/Test 全套）。

## 测试

见 [references/testing-patterns.md](references/testing-patterns.md)：
- 每条约束 ≥ 3 个 `ConstraintVerifier` 单测（违反 N 次 / 违反 0 次 / 边界）
- 端到端求解测试 ≥ 1 个
- 分数解释用 `SolutionManager.analyze`

---

## 通用反模式（务必避开）

| 错误 | 修正 |
|---|---|
| 派生字段标 `@PlanningVariable` | 改 `@ShadowVariable` / `@CascadingUpdateShadowVariable` |
| 用 `sourceVariableName` / `variableListenerClass`（deprecated since 1.27） | 用 `supplierName` + `@ShadowSources` |
| 用 `@PlanningVariable(graphType=CHAINED)`（deprecated since 1.31） | 用 `@PlanningListVariable` |
| `@ValueRangeProvider` 返回值依赖规划变量 | 只能依赖 problem facts |
| 时间直接当 PlanningVariable | 时间通常是 shadow，变量是序列位置或离散时段 |
| supplier 里做 IO / 改字段 | supplier 必须纯函数 |
| 一个方法堆多条约束 | 一条业务规则 = 一个方法 = 一个 `.asConstraint("名")` |

## 版本注意

对齐 **timefold-solver 1.33+**（group `ai.timefold.solver`；旧版 `org.optaplanner.*` 已迁移）。Java 17+ required。
