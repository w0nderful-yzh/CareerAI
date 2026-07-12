# 数据库边界与拆分路线

## 当前结论

现在不需要立刻拆成多个 PostgreSQL 实例。先做到“数据所有权拆分”，再做物理拆库：

- `careerai-app` 拥有用户、简历、岗位、匹配、面试、日程、AI Provider 配置等业务表。
- `knowledge-service` 只拥有知识库、RAG 会话、RAG 消息、会话与知识库关联表以及 pgvector 向量表。
- 知识服务只保存主应用签发 JWT 中的 `userId`，不访问或外键关联 `users` 表。
- 本地匿名模式使用 `APP_AUTH_ANONYMOUS_USER_ID`；该值必须与主应用中的匿名用户 ID 一致，生产环境应关闭匿名访问。
- 服务之间禁止共享 JPA Entity、跨服务 Join 或跨服务数据库事务。

物理共用同一个 PostgreSQL 实例不会破坏上述边界，也便于本地开发和备份。关键是每张表只能有一个写入方。

## 何时物理拆库

满足以下任一条件再把知识库迁到独立数据库：

1. 向量索引的 CPU、内存或磁盘负载明显影响核心求职流程。
2. 知识库需要独立扩缩容、备份周期、数据保留或访问权限。
3. 已有可重复执行的 schema migration、备份恢复演练和跨服务契约测试。

迁移时设置 `KNOWLEDGE_POSTGRES_HOST`、`KNOWLEDGE_POSTGRES_PORT`、`KNOWLEDGE_POSTGRES_DB`、`KNOWLEDGE_POSTGRES_USER` 和 `KNOWLEDGE_POSTGRES_PASSWORD` 即可让知识服务连接独立数据库。迁移前需先复制知识服务拥有的表和向量数据，并在切换窗口暂停相关写入。

## 后续业务服务拆分顺序

1. 先用包级依赖测试保持 `careerai-app` 内部模块边界。
2. 选择跨模块事务最少、调用方向单一的模块。
3. 定义稳定 DTO/OpenFeign 契约，只传 ID 和快照，不传 Entity。
4. 为目标服务分配独立 schema 和最小权限账号，确认没有跨 schema Join。
5. 完成双写/回填/校验和回滚方案后，再迁移路由与物理数据库。

不要先创建空服务再复制整个源码树；这会让 Controller、消息消费者、定时任务和 Hibernate schema 更新重复运行。
