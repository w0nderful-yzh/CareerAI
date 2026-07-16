BEGIN;

ALTER TABLE interview_sessions
  ADD COLUMN IF NOT EXISTS agent_decisions_json TEXT;

COMMENT ON COLUMN interview_sessions.agent_decisions_json IS
  'Agent 每轮回答评分、追问/换题/调难度/结束决策 JSON';

COMMIT;
