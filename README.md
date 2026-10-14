# idemkit

幂等与可靠消息套件：幂等键状态机、可插拔幂等存储、本地消息表（Outbox）、SAGA 补偿编排与重试退避。核心零 Spring 依赖，starter 提供 Spring Boot 自动装配。

适用于支付、订单这类「同一笔请求不能重复生效」「写库和发消息要一起成功或一起失败」「跨服务操作要能回滚」的场景。

## 模块

| 模块 | 说明 |
| --- | --- |
| `idemkit-core` | 零 Spring 核心：幂等键与存储、Outbox 与存储、SAGA 编排与重试策略、多租户上下文。 |
| `idemkit-starter` | Spring Boot 自动装配：注册幂等存储、Outbox 存储 Bean，按 `X-Tenant-Id` 解析租户，提供 `/idemkit/idempotent` 与 `/idemkit/outbox` 端点。 |
| `idemkit-sample` | 示例应用：启动即演示同键防重、Outbox 异步投递、SAGA 失败补偿三类能力。 |

## 用法

### 幂等键

同一个 `key` 只生效一次；`fingerprint` 用来区分请求内容，内容不一致直接拒绝。

```java
IdempotentStore store = new InMemoryIdempotentStore();

IdempotencyKey first = store.getOrCreate("pay-1001", "amount=10");
// first.status() == IN_PROGRESS
store.complete("pay-1001", "paid");

// 同指纹重复请求：直接返回已完成的记录，不再执行业务
IdempotencyKey replay = store.getOrCreate("pay-1001", "amount=10");
// replay.status() == SUCCESS，replay.result() == "paid"

// 指纹不一致：抛 IdempotencyConflictException
store.getOrCreate("pay-1001", "amount=20"); // 抛异常
```

### 本地消息表（Outbox）

业务写库和写消息放在同一个事务里，后台定时扫描未投递的记录异步发送，避免「库写了但消息丢了」。

```java
OutboxStore outbox = new InMemoryOutboxStore();
OutboxRecord record = outbox.append("order.created", "{\"id\":1}");
// 事务提交后，定时任务扫描 pending()
List<OutboxRecord> todo = outbox.pending();
// 发送成功后标记
outbox.markSent(record.id());
```

### SAGA 补偿

步骤按顺序正向执行；某步失败后，已完成的步骤按相反顺序补偿。

```java
SagaOrchestrator orch =
    SagaOrchestrator.builder()
        .step(reserveStep) // 扣库存
        .step(payStep)      // 扣款
        .retryPolicy(RetryPolicy.fixed(3, Duration.ofMillis(100)))
        .build();

try {
  orch.run(SagaContext.of(IdemkitTenant.of("acme")));
} catch (SagaExecutionException e) {
  // 已对前面完成的步骤做了逆序补偿，e.failedStep() 是失败的那一步
}
```

步骤定义：

```java
SagaStep reserve =
    new SagaStep() {
      public String name() {
        return "扣库存";
      }

      public void execute(SagaContext ctx) {
        // 正向操作
      }

      public void compensate(SagaContext ctx) {
        // 回滚操作
      }
    };
```

重试策略：

```java
RetryPolicy.none();                                 // 不重试
RetryPolicy.fixed(3, Duration.ofMillis(100));       // 固定间隔，最多 3 次
RetryPolicy.exponential(4, Duration.ofMillis(10), Duration.ofMillis(100)); // 指数退避，封顶 100ms
```

## 引入 starter

```yaml
idemkit:
  enabled: true
  tenant-header: X-Tenant-Id
```

```bash
curl -X POST http://localhost:8080/idemkit/idempotent \
  -H 'Content-Type: application/json' \
  -d '{"key":"pay-1001","fingerprint":"amount=10"}'

curl -X POST http://localhost:8080/idemkit/outbox \
  -H 'Content-Type: application/json' \
  -d '{"topic":"order.created","payload":"{}"}'
```

## 构建

```bash
./mvnw -B verify
```

`verify` 会跑编译、单元测试、集成测试和 JaCoCo 门禁（整体 ≥ 0.60，core ≥ 0.75）。

## 架构约束

`idemkit-core` 严禁依赖 `org.springframework.*`（CI 的 `architecture-boundaries` job 强制扫描）。
