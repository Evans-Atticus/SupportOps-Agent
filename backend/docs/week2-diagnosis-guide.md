# 第二周规则诊断引擎：代码与设计讲解

## 1. 一次诊断如何流转

```text
POST /api/v1/diagnoses
  -> DiagnosisController：校验 JSON、取得登录用户和幂等键
  -> DiagnosisApplicationService：确定场景并控制事务
  -> ScenarioPlanRegistry：读取该场景允许执行的查询计划
  -> DiagnosisContextFactory：查询业务数据并构造只读快照
  -> DiagnosisHandlerRegistry：按 ScenarioType 找到唯一 Handler
  -> ScenarioDiagnosisHandler：执行 Java 确定性规则
  -> DiagnosisRepository：原子保存任务、步骤、报告和证据
  -> GET /api/v1/diagnoses/{id}/report：读取聚合报告和 SOP
```

这里有意不调用大模型。第二周的目标是先证明：即使 AI 不可用，根因、证据和处置步骤仍然可以稳定复现。第三周的 AI 只负责工单理解与客服表达，不能修改业务事实。

## 2. 主要 Java 语法

### `record`：不可变数据载体

```java
public record DiagnosisStep(
        String code,
        String title,
        String status,
        long durationMs,
        String detail
) { }
```

`record` 会由编译器生成构造器、同名访问器（如 `code()`）、`equals`、`hashCode` 和 `toString`。它适合 DTO、领域快照和返回值，因为这些对象主要表达“数据是什么”，不需要大量可变 setter。

`DiagnosisEvidence` 使用紧凑构造器：

```java
public DiagnosisEvidence {
    if (confidence < 0 || confidence > 1) {
        throw new IllegalArgumentException(...);
    }
}
```

紧凑构造器不重复参数列表，进入构造器时可以校验所有分量。它把“置信度只能在 0 到 1 之间”固化为对象不变量。

### 接口与多态：Strategy 模式

```java
public interface ScenarioDiagnosisHandler {
    ScenarioType supports();
    DiagnosisResult diagnose(DiagnosisContext context);
}
```

七个 Handler 都实现同一接口。应用服务只依赖接口，不依赖具体类。`supports()` 声明策略键，`diagnose()` 声明统一输入输出。新增场景时增加一个实现，而不是继续扩大中央 `if/else`。

### 枚举与 `switch`

`ScenarioType` 是有限白名单。`DiagnosisContextFactory` 使用 Java 21 的箭头式 `switch`：

```java
switch (scenario) {
    case COUPON_UNAVAILABLE -> { /* 只查询订单和优惠券 */ }
    case API_FREQUENT_FAILURE -> { /* 只查询 API 记录 */ }
    default -> ...
}
```

箭头分支不会像传统 `case:` 一样意外贯穿。枚举新增常量后，编译器也更容易提示遗漏分支。

### 泛型

业务查询使用 `BusinessSnapshotVO<T>`，其中 `T extends BusinessQueryRecord`。这表示快照只能装入已声明的业务记录类型。与 `Map<String, Object>` 相比，字段名和类型在编译期可检查，IDE 也能安全重构。

### Stream API

会员 Handler 用 `stream().filter(...).findFirst()` 寻找失败权益；API Handler 用：

```java
Collectors.groupingBy(ApiCallRecord::errorCode, Collectors.counting())
```

按错误码分组并计数。`ApiCallRecord::errorCode` 是方法引用，等价于 `record -> record.errorCode()`。

### 构造器注入

Spring 组件全部使用构造器注入：

```java
public DiagnosisHandlerRegistry(List<ScenarioDiagnosisHandler> candidates) {
    ...
}
```

Spring 会收集全部 Handler Bean 传入列表。构造器注入使依赖不可为空、便于单元测试，也避免字段注入隐藏依赖。

### 事务注解

```java
@Transactional
public DiagnosisDetailVO diagnose(...) { ... }
```

Spring 通过代理在方法进入时开启事务，正常返回时提交，运行时异常时回滚。任务、步骤、报告、证据必须一起成功或一起失败，因此事务边界放在应用服务，而不是单条 DAO 方法上。

`get()` 使用 `@Transactional(readOnly = true)`，表达只读意图，并允许数据库连接池做相应优化。

## 3. 关键设计写法

### Strategy + Registry

`DiagnosisHandlerRegistry` 在启动时把 Handler 列表转换成 `EnumMap`。查找复杂度为 O(1)，同时检测重复注册。如果两个 Bean 都声称支持同一场景，应用启动即失败，避免运行时随机选中错误策略。

### 查询计划白名单

`ScenarioPlanRegistry` 明确登记每个场景允许读取的数据。模型或客户端不能传入 Java 类名、SQL 或工具名。这个边界既降低越权风险，也让每份报告能说明自己执行过哪些查询。

### Handler 不访问 DAO

`DiagnosisContextFactory` 负责 I/O，Handler 只接收不可变 `DiagnosisContext`。因此：

- 相同输入一定得到相同规则结论；
- 单元测试不需要启动 Spring 或数据库；
- 业务查询与规则判断可以分别演进；
- Handler 不可能临时绕开计划多查敏感数据。

### 可追溯证据链

每条 `DiagnosisEvidence` 保存 `source`、`sourceRecordId`、`field`、`value` 和说明。例如结论“支付回调失败”同时引用 `payment_records.id=1` 的 `callback_status`。报告不是只存一段自然语言，而是能反查原始事实。

### 幂等

接口读取 `Idempotency-Key`，持久层按 `(requested_by, idempotency_key)` 查询已有任务。相同用户重复提交同一键时直接返回原报告。数据库唯一索引提供最终约束，避免前端重试产生重复诊断。

### SOP 与规则分离

根因规则写在 Java Handler，SOP 存在 `sop_definitions.content_json`。规则决定“发生了什么”，SOP 决定“按什么步骤处理”。运营可以升级 SOP 版本，而不改变已经验证的根因规则。

## 4. 七个场景的判断核心

1. 支付：支付成功、订单仍待支付、回调失败三项同时成立。
2. 取消扣款：订单已取消、支付成功、退款不是成功状态。
3. 优惠券：依次检查状态、领取状态、有效期、金额门槛、商品范围。
4. 会员：会员有效且目标权益存在失败发放记录。
5. 物流：承运商节点更新、时间更新且状态与本地不同。
6. API：以样本最新调用为窗口终点，计算十分钟失败率并按错误码分组。
7. 发票：先验证订单已完成且已支付，再检查抬头、企业税号和邮箱。

## 5. 为什么 API 场景不用系统当前时间

演示数据是固定历史时间。若使用 `LocalDateTime.now()` 作为十分钟窗口终点，几天后测试数据会全部落到窗口外，演示结论变得不稳定。因此规则以样本中最新调用时间为窗口终点。真实在线版本可在查询层只读取当前窗口，或把 `Clock` 注入规则以便测试控制时间。

## 6. 如何新增第八个场景

1. 在 `ScenarioType` 增加枚举值。
2. 在 `ScenarioPlanRegistry` 登记名称、标题和允许的查询步骤。
3. 在 `DiagnosisContextFactory` 增加明确的数据查询分支。
4. 新建一个 `ScenarioDiagnosisHandler` 实现并标注 `@Component`。
5. 在 `sop_definitions` 增加对应 SOP。
6. 添加正常、异常和边界值测试。

应用服务、Controller 和持久化结构通常不需要改动，这就是策略模式在此处带来的扩展性。

## 7. 本地验证

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn test
```

接口样例见 `requests/diagnoses.http`。先调用登录接口取得 Token，再提交工单号；`scenarioType` 省略时使用数据库中工单的 `scenario_hint`。
