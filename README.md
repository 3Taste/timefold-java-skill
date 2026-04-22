# Timefold Java Skill for Claude Code

A Claude Code skill plugin that guides you through designing and implementing scheduling, planning, and resource-allocation systems with [Timefold Solver](https://timefold.ai/) (Java).

Claude Code 技能插件 — 引导你使用 [Timefold Solver](https://timefold.ai/)（Java）设计和实现排程、规划与资源分配系统。

## Install / 安装

```bash
claude plugins add 3Taste/timefold-java-skill
```

## What it does / 功能

When you describe a scheduling or planning problem, this skill activates automatically and walks you through a **6-step modeling methodology**:

当你描述一个排程或规划问题时，此技能会自动触发，引导你完成 **6 步建模方法论**：

1. **Identify Planning Entities** / 识别规划实体 — `@PlanningEntity`
2. **Identify Planning Variables** / 识别规划变量 — `@PlanningVariable` / `@PlanningListVariable`
3. **Identify Value Ranges** / 识别值域 — `@ValueRangeProvider`
4. **Identify Shadow Variables** / 识别派生状态 — `@ShadowVariable` / `@CascadingUpdateShadowVariable`
5. **Classify Constraints** / 分类约束 — Hard / Medium / Soft with ConstraintStream DSL
6. **Choose Solver Config & Integration** / 选择求解器配置 — Spring Boot / Quarkus / plain JAR

### Trigger keywords / 触发关键词

- English: scheduling, planning, resource allocation, timetabling, rostering, routing, Timefold, OptaPlanner
- 中文: 排程、排产、排班、调度、分配、规划、优化、约束

### Included references / 包含参考文档

| Document | Description |
|----------|-------------|
| Capability Catalog | Full annotation/API index |
| Constraint Cookbook | Common constraint patterns with ConstraintStream |
| Modeling Methodology | Detailed 6-step process |
| Solver Tuning | Termination, multi-thread, reproducibility |
| Integration | Spring Boot, Quarkus, plain JAR setup |
| Testing Patterns | ConstraintVerifier patterns |

### Starter skeleton / 骨架项目

Includes a ready-to-use Maven project template under `skills/timefold-java/assets/skeleton/` with Entity, Value, Solution, ConstraintProvider, App, and Test classes.

## Usage / 使用

Invoke directly:
```
/timefold-java
```

Or just describe your scheduling problem — the skill triggers automatically on relevant keywords.

## Requirements / 要求

- Timefold Solver 1.31+ (`ai.timefold.solver`)
- Java 17+

## License

Apache License 2.0
