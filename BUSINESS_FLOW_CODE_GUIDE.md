# CareerAI 主业务完整链路与代码导读（后端版）

> 这份文档用于从业务视角读懂 CareerAI 的核心代码，只讲后端、Agent、异步任务、数据流和关键测试，不展开 React 页面实现。
>
> 文中的代码链接都使用仓库相对路径并带 `#L行号`。在 GitHub 等支持行锚点的 Markdown 查看器中可以直接跳到对应行；不支持行锚点时也会打开对应文件。

## 目录

1. [先建立正确的项目心智模型](#一先建立正确的项目心智模型)
2. [模块分工和请求路由](#二模块分工和请求路由)
3. [闭环一：简历和 JD 到岗位准备计划](#三闭环一简历和-jd-到岗位准备计划)
4. [闭环二：自适应面试到下一轮复测](#四闭环二自适应面试到下一轮复测)
5. [两条闭环如何互相反馈](#五两条闭环如何互相反馈)
6. [状态、消息队列和幂等机制](#六状态消息队列和幂等机制)
7. [核心数据对象](#七核心数据对象)
8. [AI 能做什么，不能做什么](#八ai-能做什么不能做什么)
9. [建议的源码阅读顺序](#九建议的源码阅读顺序)
10. [调试一条真实链路时看哪里](#十调试一条真实链路时看哪里)
11. [测试如何固定业务规则](#十一测试如何固定业务规则)
12. [当前架构边界和容易误解的地方](#十二当前架构边界和容易误解的地方)

---

## 一、先建立正确的项目心智模型

CareerAI 的核心不是“上传简历后让 AI 打一次分”，也不是“让大模型扮演面试官随便聊天”。它实现的是一条以目标岗位为中心、以可追溯证据为依据、能够跨轮次迭代的求职准备链路：

```text
目标岗位 JD
  +
候选人简历
  ↓
逐项岗位要求和简历证据匹配
  ↓
Agent 决定准备策略
  ↓
简历 / 项目 / 学习 / 面试改进计划
  ↓
岗位定向自适应面试
  ↓
从用户原始回答中提取能力证据
  ↓
跨场次能力画像、冲突、趋势、待验证项
  ↓
下一轮面试复测
```

项目实际包含两个相连的主闭环：

### 闭环一：岗位准备闭环

```text
简历上传和分析
  → JD 解析和岗位保存
  → 岗位匹配任务
  → 要求—证据矩阵
  → Agent 准备决策
  → 改进计划
```

### 闭环二：面试训练闭环

```text
面试蓝图
  → 开场题
  → 用户回答
  → 自适应决策
  → 追问 / 换题 / 调难度 / 结束
  → 能力观察
  → 能力画像
  → 收口任务
  → 下一轮蓝图读取历史结果并复测
```

项目根 README 也把这两条闭环列为当前优先验证对象：

- [项目总体说明](README.md)
- [项目收口说明](docs/PROJECT-CLOSEOUT.md)

### 一句话理解系统职责

> Python Agent 负责“规划和受约束决策”，Java 核心应用负责“身份、业务真相、校验、幂等、状态和持久化”。

这条边界贯穿整个项目。大模型不能直接改数据库，也不能绕过 Java 业务校验。

---

## 二、模块分工和请求路由

### 2.1 四个主要后端模块

| 模块 | 技术 | 核心职责 |
|---|---|---|
| `backend/gateway-service` | Spring Cloud Gateway MVC | 统一入口和静态路由 |
| `backend/careerai-app` | Spring Boot | 用户数据、简历、岗位、匹配、面试、能力画像、报告等业务真相 |
| `agent-service` | Python + LangGraph | Agent Run、准备策略、面试蓝图、自适应面试决策 |
| `backend/agent-service` | Java + OpenFeign | Python Agent 到 `careerai-app` 的受保护业务桥接 |

另有 `backend/knowledge-service`，用于知识库和 RAG。它是重要的扩展能力，但不是本文两条默认主链路的中心。

### 2.2 网关如何分流

网关配置在 [gateway application.yml](backend/gateway-service/src/main/resources/application.yml#L29)：

```text
/api/agent/**
  → Python agent-service，默认 http://localhost:8000

/api/knowledgebase/**、/api/rag-chat/**
  → knowledge-service，默认 http://localhost:8081

其余 /api/**
  → careerai-app，默认 http://localhost:8080
```

对应代码位置：

- [知识服务路由](backend/gateway-service/src/main/resources/application.yml#L33)
- [Python Agent 路由](backend/gateway-service/src/main/resources/application.yml#L37)
- [核心 Java 应用路由](backend/gateway-service/src/main/resources/application.yml#L41)

### 2.3 为什么同时有两个 `agent-service`

这是读项目时最容易混淆的地方：

```text
agent-service/
  Python Agent 本体：LangGraph、模型调用、状态机、SSE

backend/agent-service/
  Java 桥：校验内部调用头，通过 OpenFeign 转发给 careerai-app
```

桥接控制器：

- [AgentBusinessToolController](backend/agent-service/src/main/java/com/yzh666/careerai/agentservice/controller/AgentBusinessToolController.java#L35)

桥接服务：

- [AgentBusinessToolBridgeService](backend/agent-service/src/main/java/com/yzh666/careerai/agentservice/service/AgentBusinessToolBridgeService.java#L28)

Feign 客户端：

- [CareerAiBusinessToolClient](backend/agent-service/src/main/java/com/yzh666/careerai/agentservice/client/CareerAiBusinessToolClient.java#L26)

核心应用内部入口：

- [AgentBusinessToolInternalController](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/agenttool/controller/AgentBusinessToolInternalController.java#L33)

领域适配层：

- [AgentBusinessToolService](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/agenttool/service/AgentBusinessToolService.java#L58)

完整调用方向是：

```text
Python Agent
  → backend/agent-service 的 /internal/agent/tools/**
  → OpenFeign
  → careerai-app 的 /internal/agent/tools/**
  → 原有领域 Service
  → Repository / Redis / RabbitMQ / LLM
```

### 2.4 Agent 内部调用的身份和幂等头

Python 工具客户端发送：

```http
X-Agent-Service-Token: <服务凭证>
Authorization: Bearer <原始用户 JWT>
X-Agent-Run-Id: <本次 Agent Run>
X-Agent-Step-Id: <当前 Agent 步骤>
Idempotency-Key: <写操作幂等键>
```

组装位置：

- [Python BusinessToolClient 请求头](agent-service/src/careerai_agent/tools/client.py#L360)

Java 统一验证位置：

- [AgentInternalAccessService](backend/careerai-shared/src/main/java/com/yzh666/careerai/common/agent/AgentInternalAccessService.java#L11)
- [幂等键校验](backend/careerai-shared/src/main/java/com/yzh666/careerai/common/agent/AgentInternalAccessService.java#L71)

这里同时保留原始用户 JWT，是为了让 Agent 发起的业务访问仍然服从用户数据边界。Agent 服务凭证只能证明“调用来自可信 Agent”，不能代替“当前操作属于哪个用户”。

---

## 三、闭环一：简历和 JD 到岗位准备计划

## 3.1 上传简历：同步保存，异步分析

入口接口：

```http
POST /api/resumes/upload
Content-Type: multipart/form-data
```

Controller 只负责接收文件并委托 Service：

- [ResumeController.uploadAndAnalyze](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/controller/ResumeController.java#L52)

核心上传逻辑：

- [ResumeUploadService.uploadAndAnalyze](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/service/ResumeUploadService.java#L48)

按代码执行顺序，上传会完成这些动作：

1. 校验文件大小和类型。
2. 计算文件内容哈希，在当前用户范围内检查重复文件。
3. 从 PDF、Word 等文件中提取文本；扫描版 PDF 无文本时会报业务错误。
4. 把原文件写入对象存储。
5. 保存简历记录，分析状态设为 `PENDING`。
6. 向 Redis Stream 投递分析任务。
7. 立即返回 `resumeId` 和 `PENDING`，不等待模型分析结束。

关键位置：

- [文本为空时拒绝继续](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/service/ResumeUploadService.java#L75)
- [原文件上传到存储](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/service/ResumeUploadService.java#L82)
- [先以 PENDING 保存](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/service/ResumeUploadService.java#L87)
- [返回异步状态](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/service/ResumeUploadService.java#L97)
- [用户范围的去重和持久化](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/service/ResumePersistenceService.java#L47)

### 3.1.1 Redis Stream 简历分析

生产者：

- [AnalyzeStreamProducer](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/listener/AnalyzeStreamProducer.java#L20)

消费者：

- [AnalyzeStreamConsumer](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/listener/AnalyzeStreamConsumer.java#L26)
- [处理一条分析消息](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/listener/AnalyzeStreamConsumer.java#L100)

状态流转：

```text
PENDING
  → PROCESSING
  → COMPLETED

异常时：
PROCESSING
  → RETRY
  → FAILED（达到重试边界或重新入队失败）
```

消费者真正调用：

- [ResumeGradingService.analyzeResume](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/service/ResumeGradingService.java#L87)

模型访问被放在 `LlmProviderRegistry` 和 `StructuredOutputInvoker` 后面，因此业务代码依赖的是结构化结果，不依赖某个具体模型 SDK 的自由文本。

### 3.1.2 这一阶段的结果

概念上会形成两类数据：

```text
Resume
  - 用户、文件名、文件地址、简历文本、内容哈希、分析状态

ResumeAnalysis
  - 总体评价、维度得分、优势、问题、建议等结构化分析结果
```

后续岗位匹配以保存后的简历和文本作为事实来源，而不是再次从前端接收一份不受控文本。

---

## 3.2 录入 JD 并保存目标岗位

相关接口：

```http
POST /api/jobs/parse-jd
POST /api/jobs
```

入口：

- [JobController](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/job/controller/JobController.java#L26)
- [解析 JD 接口](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/job/controller/JobController.java#L37)
- [保存岗位接口](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/job/controller/JobController.java#L42)

业务服务：

- [JobService.parseJd](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/job/service/JobService.java#L33)
- [JobService.createJob](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/job/service/JobService.java#L39)

JD 解析由：

- [InterviewSkillService.parseJd](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/skill/InterviewSkillService.java#L182)

负责。它会把自然语言 JD 解析成结构化类别，供岗位匹配和面试蓝图复用。

保存岗位时，如果调用方没有提交完整类别，`JobService` 会再次解析 JD，而不是保存一条没有结构化要求的岗位。

---

## 3.3 创建准备 Agent Run

Python Agent 暴露：

```http
POST /api/agent/runs
GET  /api/agent/runs/{run_id}
POST /api/agent/runs/{run_id}/resume
```

路由：

- [Agent Run 创建和查询路由](agent-service/src/careerai_agent/api/routes.py#L64)
- [Run 恢复路由](agent-service/src/careerai_agent/api/routes.py#L95)

Run 服务：

- [RunService.create_run](agent-service/src/careerai_agent/services/runs.py#L23)
- [RunService.resume_run](agent-service/src/careerai_agent/services/runs.py#L62)
- [使用 run_id 作为 LangGraph thread_id](agent-service/src/careerai_agent/services/runs.py#L81)

初始 State 会记录：

- 当前用户上下文
- `resumeId`
- `jobId`
- 用户目标和限制条件
- 当前计划和步骤
- 中间 artifacts
- 异步任务 ID
- Run 状态

### 3.3.1 准备 Agent 不是开放式循环

主图在：

- [build_career_graph](agent-service/src/careerai_agent/graph/builder.py#L36)
- [图节点和边](agent-service/src/careerai_agent/graph/builder.py#L363)

标准节点是：

```text
dispatch
  → initialize_plan
  → load_context
  → start_job_match
  → poll_job_match
  → decide_preparation_strategy
  → create_improvement_plan（按决策可选）
  → END
```

节点实现：

- [初始化计划](agent-service/src/careerai_agent/graph/builder.py#L45)
- [加载简历和岗位上下文](agent-service/src/careerai_agent/graph/builder.py#L57)
- [创建岗位匹配任务](agent-service/src/careerai_agent/graph/builder.py#L118)
- [轮询异步任务](agent-service/src/careerai_agent/graph/builder.py#L155)
- [生成准备策略](agent-service/src/careerai_agent/graph/builder.py#L276)
- [创建改进计划](agent-service/src/careerai_agent/graph/builder.py#L220)

Planner 可以用模型理解用户目标，但被限制为已知业务阶段：

- [PreparationPlanner](agent-service/src/careerai_agent/services/planner.py#L20)

这能避免 Agent 随意发明工具或跳过必要的业务步骤。

---

## 3.4 Agent 创建岗位匹配任务

Python 调用的内部工具路径是：

```http
POST /internal/agent/tools/job-match-tasks
```

经过 Java 桥：

- [桥接 Controller 的创建任务入口](backend/agent-service/src/main/java/com/yzh666/careerai/agentservice/controller/AgentBusinessToolController.java#L77)
- [桥接 Service](backend/agent-service/src/main/java/com/yzh666/careerai/agentservice/service/AgentBusinessToolBridgeService.java#L28)

到达核心应用：

- [核心内部 Controller](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/agenttool/controller/AgentBusinessToolInternalController.java#L75)
- [AgentBusinessToolService](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/agenttool/service/AgentBusinessToolService.java#L58)

最后委托：

- [JobMatchTaskService.createTask](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/service/JobMatchTaskService.java#L35)
- [幂等创建任务](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/service/JobMatchTaskService.java#L40)

该服务先做业务校验：

1. 当前用户是否存在。
2. `resumeId` 是否属于当前用户。
3. `jobId` 是否属于当前用户。
4. 同一幂等键是否已有任务。
5. 没有已有任务时，创建 `PENDING` 任务并投递消息。

业务任务和 Agent Run 分开保存：

```text
Agent Run：记录编排进行到哪里。
AI Analysis Task：记录岗位匹配这一项耗时业务是否完成。
```

这使 Agent 即使暂停，Java 异步任务仍可继续执行。

---

## 3.5 RabbitMQ 执行岗位匹配

消息生产者：

- [JobMatchRabbitProducer](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/messaging/JobMatchRabbitProducer.java#L18)

消息消费者：

- [JobMatchRabbitListener](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/messaging/JobMatchRabbitListener.java#L24)
- [处理任务消息](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/messaging/JobMatchRabbitListener.java#L52)

匹配状态通常为：

```text
PENDING → PROCESSING → COMPLETED
                    ↘ RETRY / FAILED / DLQ
```

RabbitMQ 未启用时，任务服务有同步执行回退，方便本地运行；正式异步链路则提供重试和死信处理能力。

岗位匹配核心：

- [JobMatchService](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/service/JobMatchService.java#L38)
- [执行一次匹配](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/service/JobMatchService.java#L108)
- [构造模型分析](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/service/JobMatchService.java#L185)

### 3.5.1 证据矩阵是岗位匹配的核心产物

系统不是只返回一个“匹配度 82%”，而是把 JD 要求逐条映射到简历证据。

四种覆盖类型定义在：

- [JobMatchService.COVERAGE_TYPES](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/service/JobMatchService.java#L42)

| coverageType | 业务含义 |
|---|---|
| `SUPPORTED` | 简历里已经存在足够支持证据 |
| `EXPRESSION_GAP` | 可能做过，但简历表达不清楚或位置不突出 |
| `EVIDENCE_GAP` | 有相关陈述，但缺结果、规模、指标等证明 |
| `CAPABILITY_GAP` | 当前简历看不到具备该能力的证据 |

提示词要求只根据现有简历证据判断：

- [job-match-system.st](backend/careerai-app/src/main/resources/prompts/job-match-system.st#L13)

模型输出之后，Java 还会做归一化：

- [normalizeEvidenceMappings](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/service/JobMatchService.java#L267)

主要约束包括：

- 最多保留 12 项岗位要求。
- coverageType 必须在白名单中，非法值回退为 `CAPABILITY_GAP`。
- 每项岗位要求只保留有限条简历证据。
- 重要度、证据强度等枚举必须合法。
- 输入给模型的简历和 JD 都有长度上限。

所以岗位匹配报告的真正价值是：

```text
requirement
  ↔ resumeEvidence[]
  ↔ coverageType
  ↔ importance
  ↔ gapReason / suggestion
```

后面的 Agent 决策、改进计划和岗位面试都复用这组 requirement ID。

---

## 3.6 Agent 如何等待异步匹配结果

当任务仍是 `PENDING` 或 `PROCESSING` 时，Agent 不会占着进程等待，而是把 Run 标记成：

```text
WAITING_ASYNC
```

位置：

- [poll_job_match 中进入 WAITING_ASYNC](agent-service/src/careerai_agent/graph/builder.py#L155)

恢复 Run 后，`dispatch` 会根据 checkpoint 中已有的 `match_task_id` 重新进入轮询，而不是重复创建任务。

```text
第一次执行：
start_job_match → 保存 task_id → WAITING_ASYNC → checkpoint

后续恢复：
dispatch → poll_job_match → 未完成则继续暂停
                         → 完成则加载 report 并进入策略决策
```

### 3.6.1 Checkpoint 的当前默认值

配置默认是内存：

- [checkpointer_backend 默认 memory](agent-service/src/careerai_agent/config.py#L25)
- [内存和 PostgreSQL checkpointer 实现](agent-service/src/careerai_agent/persistence/checkpointer.py#L15)

因此当前默认本地行为是：

- 同一 Python 进程内可以暂停和恢复。
- 进程重启后，内存 checkpoint 消失。
- 配置 `postgres` 和 `AGENT_DATABASE_URL` 后才具备持久化恢复能力。

不要把“代码支持 PostgreSQL checkpoint”误读成“默认已经持久化”。

---

## 3.7 Agent 决定准备策略

匹配报告完成后，Agent 会根据证据做结构化决策：

- [准备决策节点](agent-service/src/careerai_agent/graph/builder.py#L276)
- [DecisionService](agent-service/src/careerai_agent/services/decision.py#L20)
- [准备决策提示词构造](agent-service/src/careerai_agent/services/decision.py#L67)

决策重点不是最低分项，而是：

```text
岗位重要度高
  且
简历没有充分支持
```

Agent 可以选择类似策略：

| 策略 | 适合情况 |
|---|---|
| `RESUME_FIRST` | 能力可能具备，但表达或证据组织差 |
| `PROJECT_FIRST` | 需要通过项目产出补足可信证据 |
| `INTERVIEW_FIRST` | 简历基础足够，更需要验证真实掌握程度 |
| `BALANCED` | 多种缺口并存，需要并行推进 |

Agent 同时决定是否执行：

```text
CREATE_IMPROVEMENT_PLAN
```

也就是说，改进计划是证据驱动的可选步骤，不是每次机械调用模型。

---

## 3.8 生成简历和求职准备计划

核心服务：

- [ResumeImprovementPlanService](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resumeplan/service/ResumeImprovementPlanService.java#L44)
- [Agent 幂等创建入口](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resumeplan/service/ResumeImprovementPlanService.java#L129)

生成计划前会加载：

- 岗位匹配报告
- 对应简历
- 对应目标岗位和 JD
- Agent 的准备策略和理由
- 该岗位最近一次面试评价（如果存在）

读取最近面试评价：

- [findLatestJobEvaluation](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resumeplan/service/ResumeImprovementPlanService.java#L246)

Agent 决策也会被写进模型上下文：

- [buildAgentDecisionContext](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resumeplan/service/ResumeImprovementPlanService.java#L282)

计划提示词：

- [resume-improvement-plan-system.st](backend/careerai-app/src/main/resources/prompts/resume-improvement-plan-system.st#L1)

输出任务经过：

- [normalizePreparationTasks](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resumeplan/service/ResumeImprovementPlanService.java#L337)

归一化为：

- 类别：`RESUME / PROJECT / LEARNING / INTERVIEW`
- 优先级：`P0 / P1 / P2`
- 关联岗位要求 ID
- 建议完成天数
- 验证方式
- 预期产出
- 最多 12 个任务

Agent 创建计划时使用用户级幂等约束：

- [ResumeImprovementPlanEntity 唯一约束](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resumeplan/model/ResumeImprovementPlanEntity.java#L18)

这样同一个 Agent 步骤即使因网络或恢复机制重复提交，也不会重复生成多份计划。

到这里，第一条主闭环完成：

```text
Resume + Job
  → JobMatchReport
  → AgentDecision
  → ResumeImprovementPlan
```

---

## 四、闭环二：自适应面试到下一轮复测

## 4.1 创建面试 Session

Python Agent 入口：

```http
POST /api/agent/interviews/sessions
```

路由：

- [创建面试路由](agent-service/src/careerai_agent/api/routes.py#L30)

创建图：

- [build_interview_creation_graph](agent-service/src/careerai_agent/graph/interview_creation.py#L40)
- [加载规划上下文](agent-service/src/careerai_agent/graph/interview_creation.py#L49)
- [生成面试蓝图](agent-service/src/careerai_agent/graph/interview_creation.py#L77)
- [创建 Java Session](agent-service/src/careerai_agent/graph/interview_creation.py#L90)
- [创建图的节点和边](agent-service/src/careerai_agent/graph/interview_creation.py#L120)

完整流程：

```text
load_context
  → plan_blueprint
  → create_session
  → END
```

### 4.1.1 面试规划上下文

创建岗位定向面试时可以加载：

- 简历
- 目标岗位和 JD
- 岗位匹配报告
- requirement evidence matrix
- 当前能力画像
- 之前未完成的面试改进任务
- 待验证能力
- 跨场次冲突能力

Java 暴露规划上下文工具：

- [Agent 工具接口](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/agenttool/controller/AgentBusinessToolInternalController.java#L168)
- [InterviewClosureService.getPlanningContext](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewClosureService.java#L100)

### 4.1.2 Interview Blueprint 的职责

蓝图模型：

- [InterviewBlueprintPlanner.plan](agent-service/src/careerai_agent/services/blueprint.py#L48)
- [蓝图提示词](agent-service/src/careerai_agent/services/blueprint.py#L87)
- [蓝图归一化](agent-service/src/careerai_agent/services/blueprint.py#L129)
- [从历史能力中选择复测主题](agent-service/src/careerai_agent/services/blueprint.py#L186)

蓝图不是整套面试题，而是后续动态出题的约束，包括：

- 训练模式
- 题型和能力覆盖目标
- requirement ID
- 难度
- 问题预算
- 追问上限
- 历史复测目标

支持的业务模式：

```text
GENERAL
JOB_TARGETED
FOCUS_DRILL
RESUME_DEFENSE
```

蓝图规划会区别对待历史信息：

- 未完成任务：优先复测。
- 跨场次评分冲突：优先澄清。
- 低分或下降趋势：继续验证。
- 尚未覆盖：标记为待验证，不判弱项。
- 已稳定掌握：升级问题场景，不重复基础题。

---

## 4.2 Java 创建 Session 和开场题

Agent 写工具：

```http
POST /internal/agent/tools/interview-sessions
```

桥接入口：

- [Java 桥 createInterviewSession](backend/agent-service/src/main/java/com/yzh666/careerai/agentservice/controller/AgentBusinessToolController.java#L183)

核心应用入口：

- [内部创建面试接口](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/agenttool/controller/AgentBusinessToolInternalController.java#L179)
- [AgentBusinessToolService.createInterviewSession](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/agenttool/service/AgentBusinessToolService.java#L140)

领域服务：

- [InterviewSessionService 内部创建实现](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L85)
- [幂等创建 Session](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L74)

Java 会：

1. 校验并规范化蓝图。
2. 加载岗位匹配上下文。
3. 读取跨场次历史问题。
4. 生成开场问题候选。
5. 选择较新、不与历史高度重复的问题。
6. 保存 Session 和第一道题。
7. 写入 Redis 当前会话缓存和 PostgreSQL 持久化记录。

开场题生成：

- [InterviewQuestionService.generateOpeningQuestion](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewQuestionService.java#L118)

代码会生成 3 个开场候选，再做历史去重：

- [开场候选数量与追问上限](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewQuestionService.java#L46)

Agent 面试只在创建时生成一题，不预先生成固定题单。后面的题取决于用户上一轮的真实回答。

---

## 4.3 一次正式回答的完整 Turn

SSE 接口：

```http
POST /api/agent/interviews/{session_id}/turns/stream
```

路由：

- [面试 Turn SSE 路由](agent-service/src/careerai_agent/api/routes.py#L139)

Turn 图：

- [build_interview_graph](agent-service/src/careerai_agent/graph/interview.py#L35)
- [加载 Turn Context](agent-service/src/careerai_agent/graph/interview.py#L44)
- [模型决策](agent-service/src/careerai_agent/graph/interview.py#L55)
- [应用决策](agent-service/src/careerai_agent/graph/interview.py#L62)

基础图是：

```text
load_context → decide → apply → END
```

SSE 版本会把上下文加载、决策生成、问题生成和完成结果分阶段推送：

- [stream_turn](agent-service/src/careerai_agent/graph/interview.py#L171)

### 4.3.1 意图识别

支持：

```text
ANSWER
SKIP
HINT
EXPLAIN
CONTINUE
END
```

定义和解析：

- [InterviewIntent 枚举](agent-service/src/careerai_agent/services/interview.py#L26)
- [resolve_interview_intent](agent-service/src/careerai_agent/services/interview.py#L101)

优先级是：

1. 调用方显式提交的控制 intent。
2. 对非常短的控制文本做识别。
3. 其他文本按正式回答处理。

这可以避免用户的长回答因为出现“提示”“解释”几个字而被误判成控制命令。

### 4.3.2 加载 Turn Context

Java 工具返回：

- 当前题
- 已有题目
- 面试蓝图
- 当前能力画像
- 岗位要求和简历证据矩阵
- 已回答数量
- 剩余预算

入口：

- [turn-context 内部接口](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/agenttool/controller/AgentBusinessToolInternalController.java#L156)

### 4.3.3 Agent 只输出“决策”，不直接落库

决策动作：

```text
FOLLOW_UP
SWITCH_TOPIC
ADJUST_DIFFICULTY
END_INTERVIEW
```

定义：

- [InterviewAction](agent-service/src/careerai_agent/services/interview.py#L19)

模型决策服务：

- [StructuredInterviewDecisionService.decide](agent-service/src/careerai_agent/services/interview.py#L126)

输出包括：

1. 本轮回答评价。
2. 下一步动作。
3. `NextQuestionIntent`，描述下一题的题型、能力目标、难度、是否追问、父问题等。
4. 如果结束，给出结束原因。

Python 本地先做结构校验：

- `FOLLOW_UP` 必须是追问。
- `SWITCH_TOPIC` 不能携带追问标志。
- `ADJUST_DIFFICULTY` 必须真的调整难度。
- `END_INTERVIEW` 不应再给下一题意图。

位置：

- [InterviewDecision.validate_next_question_intent](agent-service/src/careerai_agent/services/interview.py#L66)

---

## 4.4 Java 校验并执行自适应决策

Agent 把结构化决策交给：

- [InterviewSessionService.applyAdaptiveTurn](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L305)

Java 不会无条件信任 Agent，而是再次校验：

- Session 是否属于当前用户。
- 当前题索引是否一致。
- 同一题是否已经执行过决策。
- 题型和 requirement ID 是否属于蓝图。
- 难度是否合法。
- 追问父题是否正确。
- 是否超过最大追问次数。
- 动作和下一题 intent 是否互相矛盾。

下一题意图校验：

- [validateNextQuestionIntent](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L543)

回答评价校验：

- [validateTurnEvaluation](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L668)

其中最重要的一条是：

> 模型返回的 evidence snippet 必须能在用户原始回答中找到，不能把模型自己的总结当作用户证据。

### 4.4.1 Java 生成最终题目

Python 决定“下一步的方向”，Java 才生成“最终展示的问题文本”：

- [InterviewSessionService.generateNextQuestion](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L614)
- [InterviewQuestionService.generateNextQuestion](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewQuestionService.java#L246)

问题生成会收到：

- 受控的 NextQuestionIntent
- 当前题和用户回答
- 简历和 JD
- 岗位匹配上下文
- 蓝图
- 已问问题

模型只负责生成一条符合这些约束的实际问题。

最终保存：

- 当前回答和评价
- Agent 自适应决策
- 下一道问题
- Session 当前索引和状态

持久化代码：

- [InterviewPersistenceService](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewPersistenceService.java#L46)
- [保存自适应 Turn 相关数据](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewPersistenceService.java#L193)

---

## 4.5 HINT、EXPLAIN、SKIP、END 为什么不走同一条评分链

### HINT / EXPLAIN

这两个操作只基于当前上下文流式生成辅助内容：

- [辅助分支](agent-service/src/careerai_agent/graph/interview.py#L181)
- [assist_stream](agent-service/src/careerai_agent/services/interview.py#L183)

它们不会：

- 保存正式回答
- 生成能力得分
- 写能力观察
- 消耗已回答题数

### SKIP / CONTINUE / END

控制动作在 Python 中走：

- [_apply_control](agent-service/src/careerai_agent/graph/interview.py#L298)

Java 中走控制 Turn，而不是正式评分 Turn：

- [InterviewSessionService.applyControlTurn](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L417)

业务语义：

- `SKIP`：不打分，生成下一道主问题。
- `CONTINUE`：看完解释或提示后继续。
- `END`：统一进入结束流程。

因此“用户跳过一道题”不等于“系统判断用户不会这道题”。

---

## 4.6 每个正式回答如何形成能力画像

一轮正式回答落库后，Session Service 调用：

- [abilityProfileService.recordTurn 调用点](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L362)
- [AbilityProfileService.recordTurn](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/AbilityProfileService.java#L44)

它先写不可变的 `AbilityObservation`，可能覆盖：

- 技术维度
- 项目维度
- 沟通维度
- 分数
- 证据片段
- 错误点
- 缺失点
- Session 和题目来源

然后重建能力投影：

- [rebuildProjection](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/AbilityProfileService.java#L123)

### 4.6.1 为什么不是简单平均所有问题

代码先按 Session 聚合，再跨 Session 聚合。这样同一场里连续追问很多次，不会等价于经过很多次独立验证。

状态判定：

```text
CONFLICT：至少两场结果明显矛盾
STABLE：至少两场验证，且置信度达到阈值
CANDIDATE：有证据，但还不足以稳定确认
```

位置：

- [能力状态判定](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/AbilityProfileService.java#L150)

趋势判定：

```text
IMPROVING
DECLINING
STABLE
```

- [趋势计算](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/AbilityProfileService.java#L186)

这说明能力画像表达的是“跨场次证据的稳定程度”，不是一次聊天里的即时印象。

---

## 4.7 结束 Session 和计算覆盖情况

结束入口：

- [InterviewSessionService.finishInterview](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L850)

统一完成逻辑：

- [completeSession](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L798)

结束时会根据蓝图和实际回答计算：

- 已覆盖目标
- 未验证目标
- 是否完整执行
- 结束原因

Session 可以是完整完成，也可以是部分完成。主动结束不会伪造未回答部分的评价。

如果没有任何正式回答，系统不会投递整体评估任务。

---

## 4.8 Redis Stream 异步生成整场面试报告

生产者：

- [EvaluateStreamProducer](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/listener/EvaluateStreamProducer.java#L20)

消费者：

- [EvaluateStreamConsumer](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/listener/EvaluateStreamConsumer.java#L35)
- [处理评估任务](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/listener/EvaluateStreamConsumer.java#L117)

整体评估服务：

- [AnswerEvaluationService](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/AnswerEvaluationService.java#L90)

它只读取真正回答过的问题，生成：

- 总体评价
- 分维度评分
- 优势和不足
- 证据
- 通用建议
- 岗位定向评价（有 Job 上下文时）

保存报告：

- [InterviewPersistenceService.saveReport](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewPersistenceService.java#L305)
- [消费者保存报告并触发收口](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/listener/EvaluateStreamConsumer.java#L150)

---

## 4.9 确定性生成 Closure 和改进任务

面试报告之后调用：

- [InterviewClosureService.finalizeSession](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewClosureService.java#L52)

这一阶段不再调用一次自由模型，而是从已经结构化的回答证据中确定性构建：

- 本场能力证据
- 弱项摘要
- 待验证项
- 下一轮建议
- 改进任务

任务构建：

- [buildTaskDrafts](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewClosureService.java#L161)

规则：

- 任务只能来自正式回答中出现的错误或缺失点。
- 每场最多 6 个任务。
- 任务带优先级和验证方式。
- 没被问到的能力只能标记为“待验证（非弱项）”。

最大任务数：

- [InterviewClosureService.MAX_TASKS](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewClosureService.java#L38)

这一步是防止系统把“没收集到证据”错误解释成“用户能力差”的关键业务保护。

---

## 4.10 下一轮面试如何复用历史结果

下次创建面试时，`getPlanningContext` 会重新读取：

- 能力画像
- 未完成的面试改进任务
- 冲突能力
- 下降趋势
- 待验证能力

入口：

- [InterviewClosureService.getPlanningContext](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewClosureService.java#L100)

Python 蓝图再将这些信息变成下一轮覆盖目标：

- [历史重点选择](agent-service/src/careerai_agent/services/blueprint.py#L186)

第二条闭环由此闭合：

```text
Answer
  → TurnEvaluation
  → AbilityObservation
  → AbilityProfile
  → InterviewImprovementTask
  → Next InterviewBlueprint
  → Retest
```

---

## 五、两条闭环如何互相反馈

## 5.1 岗位匹配结果进入面试

`JobMatchReport` 中的 requirement ID、重要度、覆盖类型和简历证据会进入岗位定向面试蓝图。

因此面试关注的不只是通用技术题，而是：

```text
这个岗位很重要
  且
简历目前没有充分证明
  的能力
```

这把“简历上看起来缺”转化成“面试中实际验证”。

## 5.2 面试结果回流改进计划

生成准备计划时，会读取该岗位最近一次面试评价：

- [ResumeImprovementPlanService.findLatestJobEvaluation](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resumeplan/service/ResumeImprovementPlanService.java#L246)

因此可能出现：

```text
简历证据看起来充分
  但
面试回答暴露出掌握不稳
  ↓
后续计划增加学习或面试复测任务
```

也可能出现：

```text
面试证明真实能力不错
  但
简历没有表达出来
  ↓
后续计划重点变成简历表达和证据包装
```

## 5.3 Career Report 是聚合快照

综合报告接口：

```http
GET /api/career-reports/{matchReportId}
```

入口：

- [CareerReportController](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/careerreport/controller/CareerReportController.java#L21)

聚合服务：

- [CareerReportService](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/careerreport/service/CareerReportService.java#L32)
- [读取最近一次面试](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/careerreport/service/CareerReportService.java#L100)
- [读取最近一份计划](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/careerreport/service/CareerReportService.java#L120)

它组合：

- 岗位和简历快照
- 岗位匹配报告
- 最近面试结果
- 最近改进计划

这里没有再发起一轮 AI 分析，所以 Career Report 是两条闭环的聚合视图，不是第三套业务真相。

---

## 六、状态、消息队列和幂等机制

## 6.1 三条异步链路

| 任务 | 中间件 | 入口 | 消费者 | 结果 |
|---|---|---|---|---|
| 简历分析 | Redis Stream | `AnalyzeStreamProducer` | `AnalyzeStreamConsumer` | `ResumeAnalysis` |
| 岗位匹配 | RabbitMQ | `JobMatchRabbitProducer` | `JobMatchRabbitListener` | `JobMatchReport` |
| 整场面试评估 | Redis Stream | `EvaluateStreamProducer` | `EvaluateStreamConsumer` | `InterviewReport + Closure` |

为什么分开：

- 简历上传需要尽快返回。
- 岗位匹配是独立业务任务，需要显式任务状态、重试和死信处理。
- 面试结束要立刻结束 Session，但整体报告可以稍后完成。

## 6.2 Agent Run 状态和业务任务状态不是一回事

```text
Agent Run 状态
  描述：编排走到哪里
  例：RUNNING / WAITING_ASYNC / COMPLETED / FAILED

Job Match Task 状态
  描述：Java 岗位匹配任务执行到哪里
  例：PENDING / PROCESSING / COMPLETED / FAILED
```

典型组合：

```text
Agent Run = WAITING_ASYNC
Job Match Task = PROCESSING
```

业务任务完成后，需要再次恢复 Agent，Agent 才会继续做策略决策。

## 6.3 幂等保护分层

### Agent 写工具层

每个写工具要求 `Idempotency-Key`：

- [Python 添加幂等头](agent-service/src/careerai_agent/tools/client.py#L380)
- [Java 要求合法幂等键](backend/careerai-shared/src/main/java/com/yzh666/careerai/common/agent/AgentInternalAccessService.java#L71)

### 岗位匹配任务层

- [JobMatchTaskService.createTaskIdempotently](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/service/JobMatchTaskService.java#L40)

### 改进计划层

- [ResumeImprovementPlanService.createPlanIdempotently](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resumeplan/service/ResumeImprovementPlanService.java#L129)
- [数据库唯一约束](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resumeplan/model/ResumeImprovementPlanEntity.java#L18)

### 面试 Session 和 Turn 层

- [InterviewSessionService.createSessionIdempotently](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L74)
- [applyAdaptiveTurn](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L305) 会检查当前题已有决策，避免同一回答重复推进 Session。

设计目的不是让请求“永远不失败”，而是让超时重试、Agent 恢复、消息重复投递不会制造重复业务数据。

---

## 七、核心数据对象

下面按业务角色理解数据，比先背表结构更容易。

| 领域对象 | 它回答的问题 |
|---|---|
| `Resume` | 用户上传的是哪份简历，文本和文件在哪里，分析到什么状态？ |
| `ResumeAnalysis` | 这份简历自身有哪些优点、问题和维度评分？ |
| `Job` | 用户的目标岗位是什么，JD 和结构化类别是什么？ |
| `AiAnalysisTask` | 一次岗位匹配异步任务执行到哪里？ |
| `JobMatchReport` | JD 每条要求在简历中有什么证据，属于哪种缺口？ |
| `ResumeImprovementPlan` | 面向这个岗位，用户下一步应该完成哪些准备任务？ |
| `InterviewSession` | 这一场面试的蓝图、进度、状态和总体结果是什么？ |
| `InterviewQuestion` | 实际向用户展示过哪些问题？ |
| `InterviewAnswer` | 用户对某个问题真正回答了什么，本轮如何评价？ |
| `AbilityObservation` | 某次回答为某项能力提供了什么不可变证据？ |
| `AbilityProfile` | 跨场次聚合后，这项能力当前是否稳定、冲突或待确认？ |
| `InterviewClosure` | 这场面试最终确认了什么、还没验证什么？ |
| `InterviewImprovementTask` | 下一轮需要复测或改进的具体任务是什么？ |

### 7.1 最关键的关联关系

```text
User
 ├─ Resume
 │   └─ ResumeAnalysis
 ├─ Job
 │   └─ JobMatchReport ── Resume
 │        └─ ResumeImprovementPlan
 └─ InterviewSession ── Resume / Job / JobMatchReport
      ├─ InterviewQuestion
      ├─ InterviewAnswer
      ├─ AbilityObservation
      └─ InterviewClosure
           └─ InterviewImprovementTask

User + abilityKey
 └─ AbilityProfile（由多个 AbilityObservation 重建）
```

### 7.2 事实、推断和投影要分清

```text
事实：
  Resume.resumeText
  Job.jdText
  InterviewAnswer.answerText

AI 结构化推断：
  ResumeAnalysis
  JobMatchReport
  TurnEvaluation
  InterviewReport

确定性投影：
  AbilityProfile
  InterviewClosure 中的任务集合
  CareerReport 聚合快照
```

调试时先确认事实输入，再确认 AI 输出，最后看确定性投影。不要一上来只盯最终页面上的总分。

---

## 八、AI 能做什么，不能做什么

## 8.1 AI 负责的部分

- 从简历文本生成结构化分析。
- 从 JD 解析技能类别。
- 建立岗位要求和简历证据映射。
- 根据证据矩阵决定准备策略。
- 生成准备任务候选。
- 规划面试蓝图。
- 评价一次正式回答。
- 决定追问、换题、调难度或结束。
- 在受控 intent 下生成实际问题。
- 生成整场面试评价。

## 8.2 Java 业务代码最终控制的部分

- 用户身份和数据归属。
- Agent 内部服务身份。
- 幂等。
- 业务对象是否存在。
- 状态机能否推进。
- 枚举、数量和长度限制。
- requirement ID 是否来自真实报告。
- 追问父题是否有效。
- 证据片段是否来自用户原始回答。
- 数据持久化。
- 异步任务重试。
- 能力画像的跨场次聚合。
- Closure 任务的确定性生成。

## 8.3 关键设计原则

可以把它概括成：

```text
模型提出结构化建议
  ↓
业务代码校验和归一化
  ↓
领域服务决定是否执行
  ↓
Repository 保存最终事实
```

而不是：

```text
模型输出什么
  ↓
数据库就保存什么
```

---

## 九、建议的源码阅读顺序

如果目标是尽快吃透主链路，建议分五遍看。

## 第一遍：只看边界和入口

1. [Gateway 路由](backend/gateway-service/src/main/resources/application.yml#L29)
2. [Python Agent API](agent-service/src/careerai_agent/api/routes.py#L30)
3. [ResumeController](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/controller/ResumeController.java#L52)
4. [JobController](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/job/controller/JobController.java#L26)
5. [Agent 内部工具 Controller](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/agenttool/controller/AgentBusinessToolInternalController.java#L33)

读完应该能回答：哪个请求进哪个服务？

## 第二遍：看岗位准备状态机

1. [Career Agent 图](agent-service/src/careerai_agent/graph/builder.py#L45)
2. [RunService](agent-service/src/careerai_agent/services/runs.py#L23)
3. [Planner](agent-service/src/careerai_agent/services/planner.py#L20)
4. [DecisionService](agent-service/src/careerai_agent/services/decision.py#L20)
5. [JobMatchTaskService](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/service/JobMatchTaskService.java#L35)
6. [JobMatchService](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/service/JobMatchService.java#L108)
7. [ResumeImprovementPlanService](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resumeplan/service/ResumeImprovementPlanService.java#L129)

读完应该能回答：Agent 为什么暂停？恢复后为什么不会重复创建任务？

## 第三遍：看面试动态决策

1. [面试创建图](agent-service/src/careerai_agent/graph/interview_creation.py#L40)
2. [Blueprint Planner](agent-service/src/careerai_agent/services/blueprint.py#L48)
3. [Interview Turn 图](agent-service/src/careerai_agent/graph/interview.py#L35)
4. [Interview Decision Service](agent-service/src/careerai_agent/services/interview.py#L126)
5. [InterviewSessionService.applyAdaptiveTurn](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L305)
6. [InterviewQuestionService.generateNextQuestion](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewQuestionService.java#L246)

读完应该能回答：为什么 Agent 不直接返回下一道题并写数据库？

## 第四遍：看证据如何跨场次沉淀

1. [AbilityProfileService.recordTurn](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/AbilityProfileService.java#L44)
2. [AbilityProfileService.rebuildProjection](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/AbilityProfileService.java#L123)
3. [InterviewClosureService.finalizeSession](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewClosureService.java#L52)
4. [InterviewClosureService.getPlanningContext](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewClosureService.java#L100)
5. [Blueprint 的历史重点算法](agent-service/src/careerai_agent/services/blueprint.py#L186)

读完应该能回答：同一场连续追问为什么不能证明能力已经稳定？

## 第五遍：看异常、重试和测试

1. [AnalyzeStreamConsumer](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/listener/AnalyzeStreamConsumer.java#L100)
2. [JobMatchRabbitListener](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/messaging/JobMatchRabbitListener.java#L52)
3. [EvaluateStreamConsumer](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/listener/EvaluateStreamConsumer.java#L117)
4. [AgentInternalAccessService](backend/careerai-shared/src/main/java/com/yzh666/careerai/common/agent/AgentInternalAccessService.java#L11)
5. [InterviewSessionServiceAdaptiveTest](backend/careerai-app/src/test/java/com/yzh666/careerai/modules/interview/service/InterviewSessionServiceAdaptiveTest.java#L78)
6. [AbilityProfileServiceTest](backend/careerai-app/src/test/java/com/yzh666/careerai/modules/interview/service/AbilityProfileServiceTest.java#L78)
7. [InterviewClosureServiceTest](backend/careerai-app/src/test/java/com/yzh666/careerai/modules/interview/service/InterviewClosureServiceTest.java#L84)
8. [Python Agent API 测试](agent-service/tests/test_api.py#L605)

读完应该能回答：超时、重复请求、消息失败、部分完成时数据会变成什么状态？

---

## 十、调试一条真实链路时看哪里

## 10.1 简历一直停在 PENDING

检查顺序：

1. [ResumeUploadService 是否成功 enqueue](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/service/ResumeUploadService.java#L87)
2. [AnalyzeStreamProducer 是否写入 Stream](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/listener/AnalyzeStreamProducer.java#L20)
3. [AnalyzeStreamConsumer 是否收到消息](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/listener/AnalyzeStreamConsumer.java#L100)
4. [ResumeGradingService 是否调用模型成功](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/resume/service/ResumeGradingService.java#L87)
5. Redis Stream consumer group 和 pending entry 是否正常。

## 10.2 Agent 一直 WAITING_ASYNC

检查顺序：

1. Agent checkpoint 中是否有 `match_task_id`。
2. [poll_job_match](agent-service/src/careerai_agent/graph/builder.py#L155) 查询到的任务状态是什么。
3. [JobMatchRabbitProducer](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/messaging/JobMatchRabbitProducer.java#L18) 是否发出消息。
4. [JobMatchRabbitListener](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/jobmatch/messaging/JobMatchRabbitListener.java#L52) 是否处理。
5. `ai_analysis_tasks` 是 `PROCESSING`、`FAILED` 还是 `COMPLETED`。
6. 如果任务已完成，调用方是否再次请求 resume Agent Run。

## 10.3 Agent 服务重启后 Run 找不到

先看：

- [默认 checkpointer 配置](agent-service/src/careerai_agent/config.py#L25)
- [checkpointer 实现](agent-service/src/careerai_agent/persistence/checkpointer.py#L15)

如果仍是 `memory`，这是当前默认行为，不是 Java 任务数据丢失。需要配置 PostgreSQL checkpointer 才能跨进程恢复 Agent State。

## 10.4 面试下一题生成失败

检查顺序：

1. [Python 决策结构校验](agent-service/src/careerai_agent/services/interview.py#L66)
2. [Java NextQuestionIntent 校验](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L543)
3. requirement ID 是否属于蓝图。
4. follow-up 的父题 ID 是否正确。
5. 是否达到最大追问次数。
6. [问题生成服务](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewQuestionService.java#L246) 是否返回结构化结果。

## 10.5 面试结束但暂时没有报告

这是异步设计，检查：

1. 是否至少存在一个正式回答。
2. [finishInterview](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewSessionService.java#L850) 是否投递评估任务。
3. [EvaluateStreamConsumer](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/listener/EvaluateStreamConsumer.java#L117) 是否消费。
4. [saveReport](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewPersistenceService.java#L305) 是否成功。
5. [finalizeSession](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/InterviewClosureService.java#L52) 是否生成 Closure。

## 10.6 能力画像看起来和单题分数不同

这是预期行为。能力画像按 Session 聚合并考虑：

- 跨场次数量
- 分数差异
- 证据置信度
- 冲突惩罚
- 最近趋势

应直接读：

- [AbilityProfileService.rebuildProjection](backend/careerai-app/src/main/java/com/yzh666/careerai/modules/interview/service/AbilityProfileService.java#L123)

而不是拿最后一道题分数和最终 profile 做一一比较。

---

## 十一、测试如何固定业务规则

测试是理解业务“不变量”的捷径。

## 11.1 Java 自适应面试测试

- [InterviewSessionServiceAdaptiveTest](backend/careerai-app/src/test/java/com/yzh666/careerai/modules/interview/service/InterviewSessionServiceAdaptiveTest.java#L78)

覆盖的关键语义包括：

- 正常生成追问。
- 开场题跨场次去重。
- 换主题不能带追问标志。
- evidence snippet 必须来自回答原文。
- 部分覆盖面试正确完成。
- 用户无回答直接结束时不生成评价。
- Skip 不进入评分链。

## 11.2 能力画像测试

- [AbilityProfileServiceTest](backend/careerai-app/src/test/java/com/yzh666/careerai/modules/interview/service/AbilityProfileServiceTest.java#L78)

关键语义：

- 同一场面试中的多次追问不能单独把能力变成 `STABLE`。
- 跨场次明显矛盾应该形成 `CONFLICT`。

## 11.3 Closure 测试

- [InterviewClosureServiceTest](backend/careerai-app/src/test/java/com/yzh666/careerai/modules/interview/service/InterviewClosureServiceTest.java#L84)

关键语义：

- 部分完成时只根据已回答证据创建任务。
- 重复 finalize 不应重复创建 Closure 和任务。

## 11.4 Python Agent 测试

- [Agent API 测试](agent-service/tests/test_api.py#L605)
- [蓝图历史语义测试](agent-service/tests/test_interview_blueprint.py#L1)
- [业务工具请求头测试](agent-service/tests/test_business_tool_client.py#L13)

覆盖：

- 面试创建上下文和蓝图。
- 追问动作。
- Hint 不记分。
- SSE 阶段输出。
- 主动结束。
- 用户隔离。
- 工具调用头和幂等键。

---

## 十二、当前架构边界和容易误解的地方

### 12.1 当前仍以模块化单体为业务中心

`careerai-app` 仍然拥有绝大多数核心业务真相。`knowledge-service` 和 Java Agent Bridge 已拆出，但不要按“所有模块都是完整独立微服务”来理解当前代码。

### 12.2 Python Agent 不拥有业务数据库

Python checkpoint 保存的是编排状态，不是 Resume、Job、Interview 的最终事实。业务数据必须通过受保护工具读写 Java 服务。

### 12.3 checkpoint 默认不持久化

代码支持 PostgreSQL，但默认是内存。判断恢复能力时必须以实际环境配置为准。

### 12.4 “未验证”不等于“不会”

这条规则在蓝图、Session 覆盖、Closure 和能力画像中都非常重要。没有回答证据时，系统应该继续验证，而不是生成弱项结论。

### 12.5 岗位匹配不是关键词命中

它围绕 requirement ID 建立证据矩阵，并区分表达缺口、证据缺口和能力缺口。总分只是摘要，矩阵才是下游业务依据。

### 12.6 Agent 决策不是最终命令

Python 返回的策略、面试 intent 和评价都必须经过 Java 白名单、引用关系、状态和用户边界校验才能执行。

### 12.7 Career Report 不产生新的业务真相

它聚合现有匹配、面试和计划结果。出现数据差异时，应回到对应来源对象排查，而不是只排查 Career Report。

---

## 最后用一条调用链记住整个项目

```text
简历上传
→ Java 保存文件和 Resume
→ Redis Stream 异步生成 ResumeAnalysis
→ Java 解析和保存 Job/JD
→ Python LangGraph 创建岗位匹配任务
→ Java Bridge 转发受保护工具调用
→ careerai-app 校验用户和幂等
→ RabbitMQ 异步生成 JobMatchReport 和 evidence matrix
→ Agent 从 WAITING_ASYNC 恢复
→ Agent 生成准备策略
→ Java 生成并保存 ResumeImprovementPlan
→ Agent 读取岗位、证据矩阵和历史能力，规划 InterviewBlueprint
→ Java 创建 InterviewSession 和开场题
→ 用户每次正式回答后，Agent 生成结构化评价和 NextQuestionIntent
→ Java 校验 intent、保存回答并生成下一题
→ Java 写 AbilityObservation 并重建 AbilityProfile
→ 面试结束后 Redis Stream 异步生成 InterviewReport
→ Java 确定性生成 InterviewClosure 和 ImprovementTask
→ 下一轮 Blueprint 读取任务、冲突、趋势和待验证能力继续复测
```

最核心的数据闭环是：

```text
岗位要求
→ 简历证据
→ 匹配判断
→ 准备策略
→ 面试回答证据
→ 能力判断
→ 改进任务
→ 再次验证
```
