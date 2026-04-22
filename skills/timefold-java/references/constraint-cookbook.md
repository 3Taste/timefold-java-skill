# Constraint Cookbook：ConstraintStream 范式库

> 按"约束类别"组织，不按场景。遇到新规则时，先在这里找最接近的范式，再改。
>
> 所有 API 来自 `ai.timefold.solver.core.api.score.stream.*`。
>
> 通用 import：
> ```java
> import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
> import ai.timefold.solver.core.api.score.stream.Joiners;
> import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
> import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
> ```

---

## 起手式

```java
constraintFactory.forEach(MyEntity.class)               // 所有已分配实体（变量非 null）
constraintFactory.forEachIncludingUnassigned(...)       // 包含 null 变量
constraintFactory.forEachUniquePair(MyEntity.class)     // 不重复的两两组合
constraintFactory.forEach(...).join(OtherType.class, Joiners.equal(...))
```

惩罚 / 奖励链尾：
```java
.penalize(HardSoftScore.ONE_HARD)             // 固定权重
.penalize(HardSoftScore.ONE_SOFT, fn::weight) // 动态权重（违反程度）
.penalizeConfigurable(fn::weight)             // 权重由 ConstraintWeightOverrides 控制
.reward(HardSoftScore.ONE_SOFT, fn::weight)   // 奖励（同 penalize 取反）
.justifyWith((a, b, score) -> new Justification(...))  // 解释（用于 SolutionManager.analyze）
.asConstraint("人类可读的约束名")
```

---

## 类别 1：互斥（同时刻不重复占用）

### 1.1 同资源同时段不能两个任务
```java
return cf.forEachUniquePair(Task.class,
        Joiners.equal(Task::getResource),
        Joiners.equal(Task::getTimeslot))
    .penalize(HardSoftScore.ONE_HARD)
    .asConstraint("Resource conflict at same slot");
```

### 1.2 时间区间重叠（更通用）
```java
return cf.forEachUniquePair(Task.class,
        Joiners.equal(Task::getResource),
        Joiners.overlapping(Task::getStart, Task::getEnd))
    .penalize(HardSoftScore.ONE_HARD,
        (t1, t2) -> overlapMinutes(t1, t2))
    .asConstraint("Resource overlapping");
```

> `Joiners.overlapping(startFn, endFn)` 自动比较 `[start, end)` 区间相交。

> 用 `@PlanningListVariable` 时**通常无需写**这条——同一 list 元素天然不重叠。仅在跨 list 共享资源时需要。

---

## 类别 2：前置依赖

### 2.1 前置任务必须先完成
```java
return cf.forEach(Task.class)
    .filter(t -> t.getPredecessor() != null
              && t.getStartTime() != null
              && t.getPredecessor().getEndTime() != null
              && t.getPredecessor().getEndTime().isAfter(t.getStartTime()))
    .penalize(HardSoftScore.ONE_HARD,
        t -> minutesBetween(t.getStartTime(), t.getPredecessor().getEndTime()))
    .asConstraint("Predecessor must finish first");
```

### 2.2 任务间最小间隔
```java
return cf.forEach(Task.class)
    .filter(t -> t.getPrevious() != null)
    .filter(t -> minutesBetween(t.getPrevious().getEnd(), t.getStart()) < 30)
    .penalize(HardSoftScore.ONE_HARD)
    .asConstraint("At least 30min gap between tasks");
```

---

## 类别 3：容量上限 / 总和限制

### 3.1 资源容量
```java
return cf.forEach(Task.class)
    .groupBy(Task::getResource, ConstraintCollectors.sum(Task::getDemand))
    .filter((res, sum) -> sum > res.getCapacity())
    .penalize(HardSoftScore.ONE_HARD,
        (res, sum) -> sum - res.getCapacity())
    .asConstraint("Capacity exceeded");
```

### 3.2 单时段总负载上限
```java
return cf.forEach(Task.class)
    .groupBy(Task::getTimeslot, ConstraintCollectors.sum(Task::getLoad))
    .filter((slot, load) -> load > slot.getMaxLoad())
    .penalize(HardSoftScore.ONE_HARD, (slot, load) -> load - slot.getMaxLoad())
    .asConstraint("Slot overloaded");
```

---

## 类别 4：计数限制

### 4.1 每个资源最多 N 个任务
```java
return cf.forEach(Task.class)
    .groupBy(Task::getResource, ConstraintCollectors.count())
    .filter((res, c) -> c > 5)
    .penalize(HardSoftScore.ONE_HARD, (res, c) -> c - 5)
    .asConstraint("Resource at most 5 tasks");
```

### 4.2 每天每设备换型不超过 3 次
```java
return cf.forEach(Task.class)
    .filter(t -> t.getPrevious() != null
              && t.getPrevious().getKind() != t.getKind())
    .groupBy(Task::getResource,
             t -> t.getStart().toLocalDate(),
             ConstraintCollectors.count())
    .filter((res, day, c) -> c > 3)
    .penalize(HardSoftScore.ONE_HARD)
    .asConstraint("Max 3 changeovers per day per equipment");
```

---

## 类别 5：存在性 / 必需性

### 5.1 每个客户至少一个服务
```java
return cf.forEach(Customer.class)
    .ifNotExists(Service.class, Joiners.equal(c -> c, Service::getCustomer))
    .penalize(HardSoftScore.ONE_HARD)
    .asConstraint("Every customer must have a service");
```

### 5.2 未分配实体的惩罚（过约束）
```java
return cf.forEachIncludingUnassigned(Visit.class)
    .filter(v -> v.getVehicle() == null)
    .penalize(HardSoftScore.ONE_SOFT, v -> v.getMissedRevenue())
    .asConstraint("Unassigned visit penalty");
```

---

## 类别 6：禁止组合 / 黑名单

### 6.1 两个特定实体不能在同一资源
```java
return cf.forEach(Task.class)
    .filter(t -> t.isHazardous())
    .join(Task.class, Joiners.equal(Task::getResource))
    .filter((t1, t2) -> t1 != t2 && t2.isFlammable())
    .penalize(HardSoftScore.ONE_HARD)
    .asConstraint("No hazardous + flammable on same resource");
```

### 6.2 已存在某关系则禁止（ifExists）
```java
return cf.forEach(NewAssignment.class)
    .ifExists(BlockedPair.class,
        Joiners.equal(NewAssignment::getEmployee, BlockedPair::getEmployee),
        Joiners.equal(NewAssignment::getShift, BlockedPair::getShift))
    .penalize(HardSoftScore.ONE_HARD)
    .asConstraint("Blocked employee-shift combination");
```

---

## 类别 7：能力 / 资格匹配

```java
return cf.forEach(ProcessStep.class)
    .filter(s -> s.getEquipment() != null
              && !s.getEquipment().getCapabilities().contains(s.getRequiredCapability()))
    .penalize(HardSoftScore.ONE_HARD)
    .asConstraint("Equipment must have required capability");
```

> **建议**：能力匹配用约束而非 `@ValueRangeProvider` 过滤，便于求解器搜索过程中暂时违反并自我修复；过滤死掉的搜索空间反而拖慢算法。

---

## 类别 8：时间窗 / 截止期

### 8.1 准时奖励
```java
return cf.forEach(Lot.class)
    .filter(l -> l.getCompletionTime() != null
              && !l.getCompletionTime().isAfter(l.getDeliveryDate()))
    .reward(HardSoftScore.ONE_SOFT, l -> l.getPriority())
    .asConstraint("On-time delivery bonus");
```

### 8.2 迟到惩罚（按延误时长平方）
```java
return cf.forEach(Lot.class)
    .filter(l -> l.getCompletionTime() != null
              && l.getCompletionTime().isAfter(l.getDeliveryDate()))
    .penalize(HardSoftScore.ONE_SOFT,
        l -> {
            long min = minutesBetween(l.getDeliveryDate(), l.getCompletionTime());
            return (int)(min * min / 60);
        })
    .asConstraint("Late delivery penalty");
```

### 8.3 时间窗内（不在窗内则惩罚）
```java
return cf.forEach(Visit.class)
    .filter(v -> v.getArrivalTime() != null
              && (v.getArrivalTime().isBefore(v.getReadyTime())
               || v.getArrivalTime().isAfter(v.getDueTime())))
    .penalize(HardSoftScore.ONE_HARD)
    .asConstraint("Outside time window");
```

---

## 类别 9：负载均衡

### 9.1 双流对差值（min vs max）
```java
return cf.forEach(Equipment.class)
    .groupBy(ConstraintCollectors.loadBalance(
        Equipment::getId, Equipment::getTotalLoadMinutes))
    .penalize(HardSoftScore.ONE_SOFT, lb -> (int) lb.unfairness())
    .asConstraint("Equipment load balance");
```

### 9.2 简化版：按方差近似
```java
return cf.forEach(Task.class)
    .groupBy(Task::getResource, ConstraintCollectors.sum(Task::getLoad))
    .penalize(HardSoftScore.ONE_SOFT, (res, load) -> load * load)
    .asConstraint("Penalize quadratic load (drives balance)");
```

> 平方惩罚使求解器倾向于均匀分布而非把一台压满。

---

## 类别 10：换型 / 切换成本

```java
return cf.forEach(ProcessStep.class)
    .filter(s -> s.getPrevious() != null)
    .penalize(HardSoftScore.ONE_SOFT,
        s -> s.getEquipment().changeoverCost(
                s.getPrevious().getRecipeStep(),
                s.getRecipeStep()))
    .asConstraint("Minimize changeover cost");
```

---

## 类别 11：连续班次 / 序列约束（人员排班）

### 11.1 至少连续 N 天休息
```java
return cf.forEach(Shift.class)
    .join(Shift.class,
        Joiners.equal(Shift::getEmployee),
        Joiners.lessThan(Shift::getId))
    .filter((s1, s2) -> daysBetween(s1.getEnd(), s2.getStart()) < 2)
    .penalize(HardSoftScore.ONE_HARD)
    .asConstraint("At least 2 days rest");
```

### 11.2 周内最多上 5 班
```java
return cf.forEach(Shift.class)
    .groupBy(Shift::getEmployee,
             s -> weekOf(s.getStart()),
             ConstraintCollectors.count())
    .filter((emp, week, c) -> c > 5)
    .penalize(HardSoftScore.ONE_HARD, (e, w, c) -> c - 5)
    .asConstraint("Max 5 shifts per week");
```

---

## 类别 12：偏好 / 软规则

### 12.1 同一员工尽量分到同一区域（软约束 - 按违反对数惩罚）
```java
return cf.forEachUniquePair(Shift.class,
        Joiners.equal(Shift::getEmployee))
    .filter((s1, s2) -> !s1.getRegion().equals(s2.getRegion()))
    .penalize(HardSoftScore.ONE_SOFT)
    .asConstraint("Region stability");
```

### 12.2 高优先级订单优先排到前面
```java
return cf.forEach(ProcessStep.class)
    .filter(s -> s.getIndex() != null && s.getLot().isHighPriority())
    .penalize(HardSoftScore.ONE_SOFT, s -> s.getIndex())
    .asConstraint("High priority lots first");
```

---

## 类别 13：区间聚类 / 间隙检测（1.33+）

### 13.1 连续工作时段间必须休息（使用 toConnectedTemporalRanges）
```java
return cf.forEach(Shift.class)
    .groupBy(Shift::getEmployee,
        ConstraintCollectors.toConnectedTemporalRanges(
            Shift::getStart, Shift::getEnd))
    .flattenLast(ConnectedRangeChain::getBreaks)
    .filter((emp, brk) -> brk.length().toHours() < 8)
    .penalize(HardSoftScore.ONE_HARD)
    .asConstraint("At least 8h rest between shifts");
```

### 13.2 检测设备空闲间隙（使用 toConnectedRanges + long）
```java
return cf.forEach(Task.class)
    .groupBy(Task::getEquipment,
        ConstraintCollectors.toConnectedRanges(
            Task::getStartMinute, Task::getEndMinute,
            (a, b) -> b - a))
    .flattenLast(ConnectedRangeChain::getBreaks)
    .penalize(HardSoftScore.ONE_SOFT,
        (equip, brk) -> (int) brk.length())
    .asConstraint("Minimize equipment idle gaps");
```

> `toConnectedRanges(startFn, endFn, differenceFn)` — 通用版本，差值类型自定义。
> `toConnectedTemporalRanges(startFn, endFn)` — 时间特化版本，差值为 `Duration`。

---

## 关键 Joiners 速查

| Joiner | 含义 |
|---|---|
| `equal(a, b)` | a == b |
| `lessThan` / `lessThanOrEqual` / `greaterThan` | 大小比较（去重对常用） |
| `overlapping(startFn, endFn)` | 区间重叠 |
| `filtering((a,b) -> bool)` | join 时过滤（比 .filter 更高效，能下推） |

## 关键 Collectors 速查

| Collector | 含义 |
|---|---|
| `count()` / `countDistinct()` | 计数 |
| `sum(fn)` / `sumLong(fn)` / `sumBigDecimal(fn)` | 求和 |
| `min(fn)` / `max(fn)` | 最值 |
| `average(fn)` | 平均 |
| `toList()` / `toSet()` | 收集 |
| `loadBalance(idFn, valueFn)` | 不公平度（专为均衡设计） |
| `compose(c1, c2, ...)` | 多个 collector 同时算 |
| `consecutive(idFn, indexFn)` | 连续序列分组 |
| `toConnectedRanges(startFn, endFn, diffFn)` | 区间聚类 + 间隙检测（1.33+） |
| `toConnectedTemporalRanges(startFn, endFn)` | 时间区间聚类（Duration 差值）（1.33+） |

---

## 调试与解释

```java
// 在约束链尾加 justification，让 SolutionManager.analyze 输出详情
.justifyWith((a, b, score) -> new ConflictJustification(a, b, score))
.indictWith((a, b) -> List.of(a, b))   // 标记哪些实体导致违反
.asConstraint("...");
```

`SolutionManager.analyze(solution)` → `ScoreAnalysis` 列出每条约束触发了多少 match + 各自分数贡献，调试 / 客户端展示必备。

---

## 常见坑

1. **null 检查**：`forEach` 默认排除 null 变量；用 `forEachIncludingUnassigned` 时**必须** filter null
2. **uniquePair vs join**：找两个不同实体用 `forEachUniquePair`（自动去对称对），用 `join` 自己 filter id 不等
3. **filter 下推**：能放进 Joiners.filtering 的尽量放进去；`.filter` 在 stream 末端会扫所有匹配
4. **shadow 字段必须先全部计算完**：约束读 shadow 时，如果声明的 sources 不全，可能读到旧值
5. **Score 类型一致**：所有约束用同一个 Score 类（HardSoftScore vs HardMediumSoftScore 不能混）
6. **不要在 reward 里用负权重**：用 penalize；不要在 penalize 里用 reward 期望抵消
