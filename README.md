# CareerAI

面向大学生实习与校招场景的智能求职辅助和模拟面试平台。

CareerAI 计划基于 [InterviewGuide](https://github.com/Snailclimb/interview-guide) 进行二次开发，围绕“简历、目标岗位和面试表现”建立完整的求职闭环，而不是仅对上游项目进行改名或界面换皮。

> 当前状态：项目规划阶段。已完成上游代码盘点、目标架构设计和改造任务拆分，业务代码尚未正式导入本仓库。

## 项目定位

CareerAI 面向个人求职者，核心流程如下：

```mermaid
flowchart LR
    A["注册 / 登录"] --> B["上传并解析简历"]
    B --> C["录入目标岗位 JD"]
    C --> D["AI 岗位匹配报告"]
    D --> E["简历优化建议"]
    D --> F["针对性模拟面试"]
    F --> G["多轮回答评分"]
    G --> H["面试报告与练习计划"]
    B --> I["个人知识库 / RAG 问答"]
    C --> I
    H --> I
```

## 核心能力

- 简历上传、Tika 文本解析、内容去重和结构化 AI 诊断。
- 岗位管理、JD 结构化解析、简历与岗位匹配分析。
- 结合简历和岗位生成模拟面试题、智能追问及回答评分。
- 基于 PostgreSQL + pgvector 的个人知识库和 RAG 问答。
- 基于 RabbitMQ 的简历分析异步任务、重试、死信、幂等和补偿。
- 基于 Gateway + JWT 的统一认证和用户数据隔离。
- 基于 Nacos 和 OpenFeign 的服务治理与内部调用。
- 基于 SSE 的 AI 回答和任务进度流式返回。

## 与上游项目的差异

| 方向 | 上游现状 | CareerAI 改造目标 |
| --- | --- | --- |
| 产品主线 | 简历分析、模拟面试、知识库等功能集合 | 登录 → 简历 → 岗位 → 匹配 → 面试 → 改进计划 |
| 用户体系 | 缺少完整认证，部分数据没有用户隔离 | JWT 登录、令牌刷新、全资源归属和越权测试 |
| 岗位能力 | 没有独立岗位与匹配模型 | 岗位中心、JD 解析、证据化匹配报告 |
| 应用架构 | Spring Boot 单体 | 业务稳定后拆分为 Spring Cloud Alibaba 微服务 |
| 异步任务 | Redis Stream | RabbitMQ Confirm、ACK、重试、死信、幂等和补偿 |
| 数据存储 | PostgreSQL + pgvector | MySQL 存业务数据，PostgreSQL + pgvector 存向量数据 |
| 面试上下文 | 以通用 Skill 和简历为主 | 由用户、简历、目标岗位和知识库共同驱动 |

## 目标技术栈

### 后端

- Java 21、Spring Boot、Spring Cloud Alibaba
- Spring AI、Nacos、Spring Cloud Gateway、OpenFeign
- RabbitMQ、Redis、MySQL、PostgreSQL + pgvector
- Apache Tika、S3 兼容对象存储、SSE、WebSocket
- Gradle、JUnit 5、Testcontainers

### 前端

- React、TypeScript、Vite、Tailwind CSS
- React Router、Axios、Recharts

### 工程化

- Docker Compose、GitHub Actions
- OpenAPI、Micrometer、结构化日志与 Trace ID

> 上述列表是目标技术栈，不代表当前仓库中的所有能力均已完成。只有通过实现、测试和演示验收的能力才会写入最终项目简历。

## 目标架构

```mermaid
flowchart TB
    WEB["React Web"] --> GW["gateway-service"]
    GW --> USER["user-service"]
    GW --> RESUME["resume-service"]
    GW --> JOB["job-service"]
    GW --> INTERVIEW["interview-service"]
    GW --> KNOWLEDGE["knowledge-service"]

    RESUME -->|"OpenFeign / RabbitMQ"| AI["ai-service"]
    JOB -->|"OpenFeign"| AI
    INTERVIEW -->|"OpenFeign"| AI
    KNOWLEDGE -->|"OpenFeign"| AI

    USER --> MYSQL1[("MySQL")]
    RESUME --> MYSQL2[("MySQL")]
    JOB --> MYSQL3[("MySQL")]
    INTERVIEW --> MYSQL4[("MySQL")]
    KNOWLEDGE --> PG[("PostgreSQL + pgvector")]
    RESUME --> OSS[("S3 / MinIO")]
    GW --> REDIS[("Redis")]

    NACOS["Nacos"] -.-> GW
    NACOS -.-> USER
    NACOS -.-> RESUME
    NACOS -.-> JOB
    NACOS -.-> INTERVIEW
    NACOS -.-> KNOWLEDGE
    NACOS -.-> AI
```

计划中的服务边界：

| 服务 | 职责 |
| --- | --- |
| `gateway-service` | 路由、CORS、JWT 初检、限流、Trace ID |
| `user-service` | 注册、登录、刷新/注销、个人资料 |
| `resume-service` | 简历文件、文本解析、分析任务和报告 |
| `job-service` | 岗位管理、JD 解析和匹配报告 |
| `interview-service` | 面试会话、题目、回答、评估和报告 |
| `knowledge-service` | 文档、分块、向量化、检索和 RAG 会话 |
| `ai-service` | Spring AI、Prompt、结构化输出和模型调用审计 |

## 改造原则

1. 先完成业务闭环，再拆微服务，避免产生只有 CRUD 和配置的空服务。
2. 每条 AI 结论尽量附带简历或 JD 原文证据，降低模型幻觉。
3. 用户数据隔离先于服务拆分，所有资源查询同时校验资源 ID 和当前用户。
4. 关系数据和向量数据职责分离；如果无法说明两种数据库的必要性，就只保留 PostgreSQL。
5. Redis 和 RabbitMQ 职责分离，不让两套消息机制处理同一种任务。
6. 简历中的技术描述必须有代码、自动化测试或故障演练支撑。

## 路线图

- [x] 盘点上游功能、依赖、测试和架构差距。
- [x] 输出 CareerAI 目标业务闭环和改造工作清单。
- [ ] 导入干净的上游代码并完成品牌、包名和配置改造。
- [ ] 修复前端 TypeScript 构建，建立后端测试与 CI 基线。
- [ ] 实现用户认证和全链路数据隔离。
- [ ] 实现岗位中心、JD 解析和岗位匹配报告。
- [ ] 将简历分析迁移为 RabbitMQ 可靠异步链路。
- [ ] 完成面向目标岗位的文字模拟面试。
- [ ] 将 RAG 与简历、岗位和面试薄弱点融合。
- [ ] 接入 Gateway、Nacos、OpenFeign 并按边界拆分微服务。
- [ ] 完成端到端测试、可观测性、部署和项目演示材料。

完整任务和验收标准见 [CareerAI 改造工作清单](docs/CareerAI-改造工作清单.md)。

## 当前目录

```text
CareerAI/
├── docs/
│   └── CareerAI-改造工作清单.md
└── README.md
```

业务代码正式导入后，本节将更新为实际模块结构和启动说明。

## 开发顺序建议

第一阶段只完成以下链路：

1. 用户注册和登录。
2. 上传并解析简历。
3. 新建目标岗位并解析 JD。
4. 生成有原文证据的岗位匹配报告。
5. 基于简历和岗位发起文字模拟面试。
6. 生成面试报告和下一步练习计划。

语音面试、日历、HR 企业端、招聘网站爬虫和支付功能暂不进入首版范围。

## 上游与许可证

本项目计划基于 [Snailclimb/interview-guide](https://github.com/Snailclimb/interview-guide) 修改。上游项目使用 AGPL-3.0 License；导入上游代码时将保留原许可证、版权声明和修改说明，并按许可证要求公开对应源码。

项目完成前，请勿将尚未实现或未验证的目标能力作为已完成成果写入简历。
