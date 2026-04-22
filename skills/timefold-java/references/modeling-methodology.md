# Timefold 建模方法论：6 步从需求到模型

> 这是 skill 的核心方法论。任何排程类需求来时，按这 6 步走一遍，得到的不是猜测，而是结构化的领域设计。
>
> 每一步都明确指向 [capability-catalog.md](capability-catalog.md) 的具体能力条目。

---

## 整体流程

```
业务描述
   ↓
1. 识别规划实体（被安排的对象是谁？）
   ↓
2. 识别规划变量（实体的什么属性是解空间？）
   ↓
3. ���别值域（变量能取哪些值？静态还是动态？）
   ↓
4. 识别派生状态（哪些字段由变量计算出来？）
   ↓
5. 分类约束（硬/中/软，归到约束原语）
   ↓
6. 选择求解器配置与集成方式
   ↓
领域模型 + Constraint 代码 + SolverConfig
```

---

## Step 1 — 识别规划实体（Planning Entity）

### 提问清单
- 业务描述里"需要被安排"的名词是什么？（订单、任务、工序、班次、车次、批次）
- 这些对象的数量是会随问题变化的"规划集"，还是固定不变的"事实集"？
- 谁是被分配的���标，谁是分配的资源？通常**被分配的是 Entity，资源是 ProblemFact**。

### 抽象原则
- Entity = 主语视角（"我要被分配到哪里 / 什么时候 / 第几位"）
- ProblemFact = 客观资源 / 配置（设备、配方、客户、约束参数）
- 一个 Entity 类必须有 ≥1 个 `@PlanningVariable` / `@PlanningListVariable`

### 反模式
- ❌ 把"可分配的资源"当 Entity（设备应该是 ProblemFact，不是 Entity）—— 除非你在做"反向"建模（把分配关系挂在资源侧的 list 上）
- ❌ 一个 Entity 类塞太多职责（订单 + 订单项 + 客户 → 拆开）

### 对应能力
→ Catalog §0.2 `@PlanningEntity` + §0.3 `@PlanningId`
→ Catalog §0.1 `@ProblemFactCollectionProperty`（资源字段）

---

## Step 2 — 识别规划变量（Planning Variable）

### 提问清单
- 这个 Entity 的"哪个属性"是要让求解器决定的？
- 这个属性的取值是**单选**（一个资源 / 一个时间）？还是**有序列表**（一串任务）？
- 是否需要允许"不分配"？

### 决策树
```
变量是从一个资源池里选 1 个？
  → §1 单值 @PlanningVariable

变量是把一组值排成有序序列，挂在某个载体上？
  → §2 @PlanningListVariable
  → 几乎一定要配 §3.1 @InverseRelationShadowVariable + §3.2/3.3 Previous/Next/Index

可能"不分配"？
  → 加 allowsUnassigned=true / allowsUnassignedValues=true（§4）
  → 软约束惩罚 unassigned

某些实体的部分状态已固定（已发出去的任务、历史班次）？
  → §5 @PlanningPin / @PlanningPinToIndex
```

### 抽象原则
- 变量必须**离散**（求解器不做连续优化；连续值离散化为时段 / 整数 / 等级）
- 变量类型必须是对象类型（不能是 primitive int / long）
- 时间类变量：通常**不直接**做规划变量；用"序列 + 派生时间"模式（startTime 是 shadow，不是 variable）

### 反模式
- ❌ 把"是否完成"当作 PlanningVariable（不是搜索维度，是约束判定）
- ❌ 把派生字段（结束时间、累计成本）当 PlanningVariable
- ❌ 同时用 `@PlanningVariable` 和 `@PlanningListVariable` 表达相同关系

### 对应能力
→ Catalog §1 / §2 / §4 / §5

---

## Step 3 — 识别值域（Value Range）

### 提问清单
- 值域是全局静态（所有时段共享）还是按实体过滤（设备能力匹配）？
- 值域是离散枚举（有限实例）还是数值范围（0~100 的整数）？
- 值域大小估算：< 10²？10²~10⁴？> 10⁴？规模决定调优策略。

### 决策树
```
全局且枚举（所有 Lesson 共享 Timeslot 池）
  → @ValueRangeProvider 在 @PlanningSolution 字段上

按实体动态过滤（每个工艺步骤只能放在能力匹配的设备上）
  → @ValueRangeProvider 在 @PlanningEntity 方法上
  → @PlanningVariable(valueRangeProviderRefs = "compatibleX")

数值范围（开始时间 0~480 分钟以 5 分钟粒度）
  → ValueRangeFactory.createIntValueRange(0, 480, 5)
```

### 抽象原则
- 值域**必须可枚举或可生成**
- 实体级 ValueRangeProvider 的返回值**只能依赖 problem facts**，不能依赖其他规划变量
- 多个变量取自同名值域时用 `valueRangeProviderRefs` 显式绑定

### 对应能力
→ Catalog §6 ValueRangeProvider

---

## Step 4 — 识别派生状态（Shadow Variables）

### 提问清单
- 哪些字段是"算出来的"？典型：startTime、endTime、arrivalTime、totalLoad、cumulativeCost
- 这些字段是**只看局部**（前一个元素 + 自身），还是**看一长串依赖**（沿列表传播）？
- 是否需要反向查询（Visit 知道挂在哪个 Vehicle 下）？

### 决策树
```
要反查列表载体？
  → §3.1 @InverseRelationShadowVariable

要前/后/索引？
  → §3.2 @PreviousElementShadowVariable / @NextElementShadowVariable / §3.3 @IndexShadowVariable

派生只依赖少量已知字段（如 endTime = startTime + duration）？
  → §3.4 @ShadowVariable(supplierName=...) + @ShadowSources

派生需要沿列表向后级联（前一个的 endTime → 当前的 startTime → 下一个的 startTime）？
  → §3.5 @CascadingUpdateShadowVariable

某些上游组合非法（前置任务未完成时无法计算到达时间）？
  → §3.6 @ShadowVariablesInconsistent
```

### 抽象原则
- supplier 方法**必须无副作用**、纯函数
- `@ShadowSources` 路径用点号串导航；source 必须是 genuine variable 或其他 shadow
- **永远不要**用废弃的 `variableListenerClass / sourceVariableName` 写法

### 反模式
- ❌ 把派生字段标 `@PlanningVariable`（求解器会浪费时间在"假"维度上）
- ❌ supplier 里调用外部服务、写数据库
- ❌ 忘了声明所有依赖（导致脏数据 / 求解结果错误）

### 对应能力
→ Catalog §3 全套 Shadow

---

## Step 5 — 分类约束 + 归到约束原语

### 提问清单
- 这条规则违反时**解是否可行**？可行性判定 → 硬约束；只是偏好 → 软约束
- 业务里有几层优先级？（有些公司：法规硬 / 业务规则中 / 偏好软）
- 可量化吗？（违反次数、违反程度、违反时长）

### 分类格式
| 层级 | 含义 | 例 |
|---|---|---|
| Hard | 违反 = 不可行解 | 资源不重叠、前置依赖、容量上限 |
| Medium | 必须满足的业务规则 | 必须按 SLA 排期 |
| Soft | 偏好 / 优化目标 | 换型时间最小化、负载均衡、准时交付奖励 |

→ Score 类型选 §7：HardSoft / HardMediumSoft / Bendable

### 约束 → 原语映射
对每条约束，从 [constraint-cookbook.md](constraint-cookbook.md) 找最接近的范式：

| 业务规则关键词 | Cookbook 范式 |
|---|---|
| "同时间不重叠" / "互斥" | uniquePair + equal + overlapping |
| "前置必须先完成" | join + filter (predecessor.endTime > current.startTime) |
| "容量上限" / "总和不能超过" | groupBy + sum + filter |
| "最少 / 最多 N 个" | groupBy + count + filter |
| "存在性 / 至少一个 X" | ifNotExists / forEachIncludingUnassigned |
| "禁止组合 / 黑名单" | forEach + filter / ifExists |
| "负载均衡" | groupBy 双流对差值 / loadBalance collector |
| "换型时间" | forEach + filter(has previous) + previous + cost |
| "准时交付奖励" | forEach + filter + reward |
| "未分配惩罚" | forEachIncludingUnassigned + filter(null) + penalize |

### 抽象原则
- 一条约束 = 一个方法 = 一个 `.asConstraint("名字")`
- 约束名要可��（出现在 ScoreAnalysis、justifications）
- 权重通过 `.penalize(Score.ofXxx(N))` 或 `.penalizeConfigurable()` + ConstraintWeightOverrides

### 对应能力
→ Catalog §7 Score、§8 ConstraintProvider、cookbook 全文

---

## Step 6 — 选择求解器配置与集成方式

### 提问清单
- **规模**：实体数 × 平均值域大小 = 搜索空间量级？
- **时间预算**：在线（秒级）/ 准实时（分钟级）/ 离线（小时级）？
- **质量要求**：合理就行 vs 接近最优？
- **集成方式**：纯 JAR / Spring Boot / Quarkus？

### 集成决策树
```
纯算法库 / 离线脚本
  → §9.1 SolverFactory（同步 .solve()）

REST 服务、需要异步、多用户并发
  → §9.2 SolverManager（async + best solution consumer）
  → 框架选择见 integration.md

需要分数解释 / UI 展示违反原因
  → §9.3 SolutionManager.analyze() / explain()

实时排程（运行中插入新任务、变更）
  → §10 ProblemChange + §5 PlanningPin
```

### 调优入口
配置见 [solver-tuning.md](solver-tuning.md)：
- termination：spentLimit / unimprovedSpentLimit / bestScoreLimit
- 阶段：Construction Heuristic + Local Search（默认即可，特殊场景调）
- Nearby Selection：实体数 > 1000 时建议开启
- Score 类型：超大数值用 `*LongScore`

### 集成骨架
见 [integration.md](integration.md)：
- Spring Boot：`timefold-solver-spring-boot-starter` + application.properties
- Quarkus：`timefold-solver-quarkus` + application.properties
- 纯 JAR：自己 new SolverConfig + SolverFactory

---

## 端到端演练：半导体材料生产排程系统

### 业务假设
1. 一个工厂有多台设备（Equipment），每台有"能力标签"（如 Etching、Deposition、CMP）
2. 客户订单 → 拆成批次（Lot），每个 Lot 按配方（Recipe）走多个工艺步骤（ProcessStep）
3. ProcessStep 必须按配方顺序执行；每个步骤需要特定能力的设备
4. 同一设备同一时间只能执行一个步骤
5. 设备从一种工艺切换到另一种工艺需要换型时间（ChangeoverMatrix 给出）
6. 每个 Lot 有交付期；按期交付奖励，迟到惩罚
7. 需要负载尽量均衡（避免某些设备空闲、其他爆满）
8. 已发出去执行的步骤不能重排（实时排程）

### Step 1 → Entity / Fact 划分
- **Entity**：`ProcessStep`（被安排到设备 + 决定执行顺序）
- **Fact**：`Equipment`（资源）、`Recipe`（配方）、`Lot`（订单批次，含交付期）、`ChangeoverMatrix`（换型成本表）

> 思路：被分配的是工艺步骤；机台是被使用的资源；配方/批次/换型矩阵是不变的事实。

### Step 2 → Planning Variables
- 设备的"任务序列" = 有序 → **`Equipment.steps` 用 `@PlanningListVariable`**（类似 Vehicle.visits）
- 这样一次性表达了"步骤分到哪台设备"+"执行顺序"

```java
@PlanningEntity
public class Equipment {
    @PlanningId String id;
    private Set<Capability> capabilities;
    @PlanningListVariable
    private List<ProcessStep> steps = new ArrayList<>();
}
```

### Step 3 → Value Range
- `Equipment.steps` 的元素来自 `@ValueRangeProvider` 在 Schedule.steps 上（全局）
- 不在这里做能力过滤——做硬约束更好（保留搜索空间，约束侧禁止违反）

```java
@PlanningSolution
public class Schedule {
    @ProblemFactCollectionProperty private List<Equipment> equipments;
    @PlanningEntityCollectionProperty private List<Equipment> equipmentEntities;  // 同一对象既是 fact 也是 entity？不行——选 entity
    @ProblemFactCollectionProperty @ValueRangeProvider
    private List<ProcessStep> steps;  // 值域
    @PlanningEntityCollectionProperty
    private List<Equipment> equipmentList;  // entity 集合
    @PlanningScore HardMediumSoftScore score;
}
```

> 注意：Equipment 既需要被规划（其 list 是规划变量），又是资源。它本身就是 entity，不是 fact。

### Step 4 → Shadow Variables
- `ProcessStep.equipment` ← 反查载体 → `@InverseRelationShadowVariable("steps")`
- `ProcessStep.previousStep` ← `@PreviousElementShadowVariable("steps")`
- `ProcessStep.startTime` ← 派生（前一步骤 endTime + 换型时间）→ `@ShadowVariable + @ShadowSources` 或 `@CascadingUpdateShadowVariable`
- `ProcessStep.endTime` ← startTime + 工艺时长

```java
@PlanningEntity
public class ProcessStep {
    @PlanningId String id;
    private Lot lot;
    private RecipeStep recipeStep;          // fact: 工艺时长 + 所需能力 + 顺序号

    @InverseRelationShadowVariable(sourceVariableName = "steps")
    private Equipment equipment;
    @PreviousElementShadowVariable(sourceVariableName = "steps")
    private ProcessStep previousStep;

    @ShadowVariable(supplierName = "startTimeSupplier")
    private LocalDateTime startTime;

    @ShadowSources({"equipment", "previousStep.endTime", "recipeStep"})
    public LocalDateTime startTimeSupplier() {
        if (equipment == null) return null;
        if (previousStep == null) return equipment.getReadyTime();
        long changeover = equipment.changeoverMinutes(previousStep.recipeStep, this.recipeStep);
        return previousStep.getEndTime().plusMinutes(changeover);
    }

    public LocalDateTime getEndTime() {
        return startTime == null ? null : startTime.plusMinutes(recipeStep.getDurationMinutes());
    }
}
```

### Step 5 → 约束分类
**硬约束（Hard）**：
1. 设备能力匹配：`forEach(ProcessStep) filter(equipment != null && !equipment.canRun(recipeStep))` → penalize HARD
2. 同一 Lot 的工艺顺序：`forEachUniquePair(ProcessStep) filter(同 lot && 顺序号倒置 vs 实际开始时间)` → penalize HARD
3. 前置步骤已完成：`forEach(ProcessStep) filter(predecessor.endTime > startTime)` → penalize HARD
4. （列表自动保证同一设备同时段不重叠，无需写）

**中等约束（Medium，可选）**：
5. SLA 必须按合同期限排：`forEach(ProcessStep)` 过滤 lot.contractDeadline 严重违反

**软约束（Soft）**：
6. 换型时间最小化：`forEach(ProcessStep) filter(previousStep != null) penalize(changeoverMinutes)`
7. 准时交付奖励：`forEach(Lot)` 算最后一个步骤 endTime，filter ≤ deliveryDate → reward
8. 迟到惩罚：filter > deliveryDate → penalize 时长平方
9. 设备负载均衡：`groupBy(Equipment, sum(workMinutes))` 用 loadBalance collector
10. 高优先级 Lot 提前完成奖励

→ 用 `HardMediumSoftScore`（或 `BendableScore` 若要后续添加更多层）

### Step 6 → 求解器选择
- 规模：假设 50 设备 × 1000 步骤 = 中等规模
- 在线：实时排程 → SolverManager 异步
- 实时插单：ProblemChange + `@PlanningPinToIndex`（每台 Equipment 已开工的前 N 个步骤冻结）

```properties
# Quarkus 配置
quarkus.timefold.solver.termination.spent-limit=30s
quarkus.timefold.solver.termination.unimproved-spent-limit=10s
# 中等规模可考虑 nearby selection
```

### 输出物清单（这个演练给 Claude 用）
对任何新需求，6 步走完应当产出：
1. **领域类清单**（哪些是 Entity / Fact + 类骨架）
2. **变量 / Shadow 选型表**（哪个字段用哪个注解 + 理由）
3. **硬/中/软约束分类清单**（条目 + 一行 ConstraintStream 草稿）
4. **求解器配置建议**（termination + 集成方式 + 是否 ProblemChange）
5. **测试范式提示**（针对每条约束写 ConstraintVerifier 测试）

---

## 通用反模式速查

| 错误 | 修正 |
|---|---|
| 把派生字段标 @PlanningVariable | 改用 @ShadowVariable / @CascadingUpdateShadowVariable |
| 用废弃的 sourceVariableName/variableListenerClass | 用 supplierName + @ShadowSources |
| 用 @PlanningVariable(graphType=CHAINED) 表达序列 | 用 @PlanningListVariable |
| ValueRangeProvider 返回值依赖其他规划变量 | 只能依赖 problem facts |
| 一个 Entity 多个 @PlanningVariable 表达同一关系 | 拆字段或改 list |
| 约束写成大方法堆 if-else | 一条业务规则一个方法一个 .asConstraint |
| 时间用 LocalDateTime 直接当 PlanningVariable | 时间通常是 shadow，PlanningVariable 是序列位置或离散时段 |
| 在 supplier 里调用 IO / 修改其他字段 | supplier 必须纯函数 |
