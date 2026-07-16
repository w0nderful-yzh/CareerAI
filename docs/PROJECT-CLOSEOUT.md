# CareerAI 项目收口与演示手册

## 1. 最终定位

CareerAI 是一个“Java 业务系统 + Python Agent 编排”的求职准备项目，核心不是聊天，
而是 Agent 读取业务事实、选择策略、调用受控写 Tool，并把结果保存成可审计业务数据。

最终只验收两条主线：

1. 简历 → JD → 证据匹配 → Agent 策略 → 改进计划；
2. 面试蓝图 → 增量出题 → 自适应决策 → 能力画像 → 结束任务 → 下一场复测。

知识库、语音、投递、日历和更多微服务不属于默认演示范围。

## 2. 默认运行模块

| 模块 | 端口 | 演示职责 |
| --- | --- | --- |
| React | 5173 | 用户操作与 Agent 业务产物展示 |
| Gateway | 8090 | 浏览器统一入口 |
| careerai-app | 8080 | 业务事实、校验、事务、幂等与持久化 |
| Java agent-service | 8082 | 内部令牌、模型配置和 Tool 白名单 |
| Python agent-service | 8000 | LangGraph 规划、决策、等待与恢复 |

`knowledge-service` 不启动也不影响主线。

## 3. 启动与检查

确保 PostgreSQL、Redis、RabbitMQ 和 S3 兼容存储已经启动，然后执行：

```bash
./scripts/dev-start.sh
```

另开终端检查：

```bash
./scripts/smoke-test.sh
```

## 4. 十分钟演示脚本

### 4.1 模型与业务准备

1. 登录演示账号；
2. 在“设置”确认 Agent 默认模型可用；
3. 在“简历管理”选择已有简历，或上传一份简历；
4. 在“岗位中心”录入 Java 后端 JD。

### 4.2 简历–JD–规划

1. 创建匹配任务，展示 RabbitMQ 异步状态；
2. 打开 Agent 工作台，让 Agent 读取简历和岗位并启动匹配；
3. 展示 `WAITING_ASYNC` 后恢复执行；
4. 展示 JD 要求–简历证据矩阵；
5. 展示 Agent 选择的准备策略及 Java 保存的结构化改进任务。

重点说明：模型不能直接改数据库，只能通过带 JWT、Run、Step 和幂等键的业务 Tool。

### 4.3 自适应模拟面试

1. 从“模拟面试”选择岗位定向或专项强化；
2. 展示创建前蓝图，包括真实 requirementId、题型、主题和跨场次复测项；
3. 回答首题，展示 Agent 的多维评价和 `FOLLOW_UP` / `SWITCH_TOPIC` /
   `ADJUST_DIFFICULTY` 决策；
4. 使用“提示”“讲解”“跳过”“结束”验证意图区分；
5. 面试结束后进入“面试记录”，等待异步评估完成；
6. 打开详情，展示结束总结、证据索引、改进任务和下一场建议；
7. 再创建一场面试，展示上一场任务、下降项或冲突画像如何进入新蓝图。

重点说明：未考察项只标记为“待验证（非弱项）”；能力画像由 Java 根据不可变观察投影，
模型不能直接写画像分数。

## 5. 收口后的 API 边界

浏览器创建面试和提交轮次统一走：

- `POST /api/agent/interviews/sessions`
- `POST /api/agent/interviews/{sessionId}/turns`

Java 对浏览器保留面试查询、详情、结束产物、导出和删除接口。旧的直连创建、固定答题、
同步生成报告、暂存答案和手动结束入口已经撤下，避免绕过 Agent 主链。

## 6. 验证命令

```bash
cd backend && mvn test
cd agent-service && uv run ruff check . && uv run ruff format --check .
cd agent-service && uv run mypy src && uv run pytest
cd frontend && pnpm build
```

## 7. 有意保留的后续项

以下能力不是本次收口缺陷，不应在简历项目演示前继续扩张：

- 基于时间衰减的间隔复测；
- 面试结果自动回写岗位准备计划；
- Run 取消、SSE、完整 ToolCall 审计；
- 多份简历并行比较；
- 生产级 PostgreSQL Checkpoint 和部署编排。

如果继续开发，优先补“任务完成状态和准备计划回写”，不要重新扩展语音、投递或多 Agent。
