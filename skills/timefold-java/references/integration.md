# Integration：三种集成骨架

> Timefold 支持三种集成方式：**Spring Boot**、**Quarkus**、**纯 JAR**。选一种贴上去即可用。
>
> 版本：对齐 timefold-solver 1.33+（使用 `ai.timefold.solver` group）。

---

## 1. 共同 Maven 依赖

所有三种方式共用：
```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>ai.timefold.solver</groupId>
      <artifactId>timefold-solver-bom</artifactId>
      <version>1.33.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

测试依赖（所有方式都用）：
```xml
<dependency>
  <groupId>ai.timefold.solver</groupId>
  <artifactId>timefold-solver-test</artifactId>
  <scope>test</scope>
</dependency>
```

Java 17+ required。

---

## 2. Spring Boot 集成

### 2.1 依赖
```xml
<dependency>
  <groupId>ai.timefold.solver</groupId>
  <artifactId>timefold-solver-spring-boot-starter</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### 2.2 配置 `src/main/resources/application.properties`
```properties
timefold.solver.termination.spent-limit=30s
timefold.solver.termination.unimproved-spent-limit=10s
# logging.level.ai.timefold.solver=DEBUG
```

> Spring Boot starter 自动扫描 classpath 下的 `@PlanningSolution` / `@PlanningEntity` / `ConstraintProvider` 并注入 `SolverManager` / `SolutionManager` bean。

### 2.3 Controller 骨架
```java
@RestController
@RequestMapping("/schedules")
public class ScheduleController {

    private final SolverManager<Schedule, String> solverManager;
    private final SolutionManager<Schedule, HardSoftScore> solutionManager;
    private final Map<String, Schedule> repo = new ConcurrentHashMap<>();

    public ScheduleController(
            SolverManager<Schedule, String> solverManager,
            SolutionManager<Schedule, HardSoftScore> solutionManager) {
        this.solverManager = solverManager;
        this.solutionManager = solutionManager;
    }

    @PostMapping
    public String solve(@RequestBody Schedule problem) {
        String jobId = UUID.randomUUID().toString();
        repo.put(jobId, problem);
        solverManager.solveBuilder()
            .withProblemId(jobId)
            .withProblemFinder(repo::get)
            .withBestSolutionEventConsumer(event -> repo.put(jobId, event.solution()))
            .run();
        return jobId;
    }

    @GetMapping("/{id}")
    public Schedule get(@PathVariable String id) { return repo.get(id); }
}
```

### 2.4 启动类
```java
@SpringBootApplication
public class App {
    public static void main(String[] args) { SpringApplication.run(App.class, args); }
}
```

**示例参考**：`java/spring-boot-integration/`

---

## 3. Quarkus 集成

### 3.1 依赖
```xml
<dependency>
  <groupId>ai.timefold.solver</groupId>
  <artifactId>timefold-solver-quarkus</artifactId>
</dependency>
<dependency>
  <groupId>ai.timefold.solver</groupId>
  <artifactId>timefold-solver-quarkus-jackson</artifactId>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
</dependency>
```

### 3.2 配置 `src/main/resources/application.properties`
```properties
quarkus.timefold.solver.termination.spent-limit=30s
quarkus.timefold.solver.termination.unimproved-spent-limit=10s
```

### 3.3 Resource 骨架（JAX-RS）
```java
@Path("/schedules")
public class ScheduleResource {

    @Inject SolverManager<Schedule, String> solverManager;
    @Inject SolutionManager<Schedule, HardSoftScore> solutionManager;
    private final Map<String, Schedule> repo = new ConcurrentHashMap<>();

    @POST
    public String solve(Schedule problem) {
        String jobId = UUID.randomUUID().toString();
        repo.put(jobId, problem);
        solverManager.solveBuilder()
            .withProblemId(jobId)
            .withProblemFinder(repo::get)
            .withBestSolutionEventConsumer(event -> repo.put(jobId, event.solution()))
            .withExceptionHandler((id, ex) -> Log.error("Solve failed", ex))
            .run();
        return jobId;
    }

    @GET @Path("{id}")
    public Schedule get(String id) { return repo.get(id); }
}
```

**示例参考**：`java/employee-scheduling/`（完整的 Quarkus REST + SolverManager 异步 + 异常处理）、`java/vehicle-routing/`、`java/school-timetabling/`

---

## 4. 纯 JAR（无框架）

### 4.1 依赖
```xml
<dependency>
  <groupId>ai.timefold.solver</groupId>
  <artifactId>timefold-solver-core</artifactId>
</dependency>
```

### 4.2 Main 骨架
```java
public class ScheduleApp {
    public static void main(String[] args) {
        SolverFactory<Schedule> factory = SolverFactory.create(new SolverConfig()
            .withSolutionClass(Schedule.class)
            .withEntityClasses(ProcessStep.class, Equipment.class)
            .withConstraintProviderClass(MyConstraintProvider.class)
            .withTerminationSpentLimit(Duration.ofSeconds(30)));

        Schedule problem = loadProblem();
        Solver<Schedule> solver = factory.buildSolver();
        Schedule solved = solver.solve(problem);
        printResult(solved);
    }
}
```

### 4.3 异步场景（纯 JAR 也可用 SolverManager）
```java
SolverManager<Schedule, UUID> sm = SolverManager.create(factory);
SolverJob<Schedule, UUID> job = sm.solve(UUID.randomUUID(), problem);
Schedule result = job.getFinalBestSolution();
```

**示例参考**：`java/hello-world/`（最简单纯 JAR，展示完整 SolverFactory → Solver → solve 流程）

---

## 5. 三种方式怎么选

| 场景 | 推荐 |
|---|---|
| 已在用 Spring Boot 栈 | Spring Boot starter |
| 云原生 / native image / 低启动内存 | Quarkus |
| 离线批处理 / CLI 工具 / 嵌入其他应用 | 纯 JAR |
| 纯算法库（不对外暴露 API） | 纯 JAR |

**功能完全一致** —— 两个框架的 starter 只是把 SolverManager/SolutionManager 做成 DI bean，底层 API 相同。

---

## 6. 常见集成问题

### 6.1 多 Solution 类
一个应用可能需要支持多种排程（订单 + 班次）：
- 纯 JAR：create 多个 SolverFactory
- Spring Boot / Quarkus：starter 默认只支持一种；多种时需手动 `SolverFactory.create(...)`

### 6.2 DTO 与领域分离
REST API 常用 DTO 而非直接暴露 `@PlanningSolution`。映射层在 controller / resource 里做。

### 6.3 持久化
Timefold 本身不管持久化。典型做法：
- JPA / Mongo 存 problem + solution 的 DTO
- 求解完成后 `withBestSolutionConsumer` 回调里持久化
- `@PlanningId` 用数据库 PK

### 6.4 并发求解
一个 SolverManager 实例可同时处理多个 jobId（线程池内）。
```properties
timefold.solver.move-thread-count=AUTO   # Spring Boot
quarkus.timefold.solver.move-thread-count=AUTO  # Quarkus
```

### 6.5 实时变更
Long-running solve 过程中，业务事件触发 problem change：
```java
solverManager.addProblemChange(jobId, (solution, director) -> {
    director.addEntity(newStep, solution.getSteps()::add);
});
```

---

## 7. 骨架复用

最小骨架已抽取到 `assets/skeleton/`（见该目录）。复制即用：
```bash
cp -r ~/.claude/skills/timefold-java/assets/skeleton/ ./my-scheduler
```
然后按 [modeling-methodology.md](modeling-methodology.md) 的 6 步走，填充领域类 + 约束即可。

---

## 8. 1.33 新增集成 API

### 8.1 SolverJobBuilder 事件回调（1.28+ 推荐）

旧 API（deprecated，将移除）→ 新 API：

| 旧方法 | 新方法 |
|---|---|
| `withBestSolutionConsumer(Consumer<Solution>)` | `withBestSolutionEventConsumer(Consumer<NewBestSolutionEvent<Solution>>)` |
| `withFinalBestSolutionConsumer(Consumer<Solution>)` | `withFinalBestSolutionEventConsumer(Consumer<...>)` |
| `withFirstInitializedSolutionConsumer(Consumer<Solution>)` | `withFirstInitializedSolutionEventConsumer(Consumer<...>)` |

新 API 通过 event 对象提供更丰富的上下文（不仅仅是 solution）。

### 8.2 SolutionManager.diff()（Preview，1.33+）

对比两个方案的差异：
```java
// 需要启用 Preview：PLANNING_SOLUTION_DIFF
PlanningSolutionDiff<Schedule> diff = solutionManager.diff(oldSchedule, newSchedule);
// diff 包含每个实体的变量变更列表
```

### 8.3 SolutionManager.recommendAssignment()

快速评估一个实体/元素的所有可能分配方案，按分数排序返回推荐列表：
```java
List<RecommendedAssignment<Room, HardSoftScore>> recommendations =
    solutionManager.recommendAssignment(solution, unassignedLesson, Lesson::getRoom);
// recommendations 按分数从优到劣排序
```
