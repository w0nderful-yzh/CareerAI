-- 保存 Agent 规划、Java 校验后的面试蓝图，支持恢复、展示和审计。
ALTER TABLE interview_sessions
  ADD COLUMN IF NOT EXISTS interview_blueprint_json TEXT;

-- Python 调用 Java 创建会话时使用，避免 Tool 重试产生重复业务数据。
ALTER TABLE interview_sessions
  ADD COLUMN IF NOT EXISTS agent_creation_key VARCHAR(160);

CREATE UNIQUE INDEX IF NOT EXISTS uk_interview_session_agent_creation_key
  ON interview_sessions (agent_creation_key)
  WHERE agent_creation_key IS NOT NULL;
