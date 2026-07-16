-- Agent 增量出题在每一轮都要重建简历、JD 和自定义方向上下文。
ALTER TABLE interview_sessions
  ADD COLUMN IF NOT EXISTS resume_text_snapshot TEXT,
  ADD COLUMN IF NOT EXISTS jd_text_snapshot TEXT,
  ADD COLUMN IF NOT EXISTS custom_categories_json TEXT;

COMMENT ON COLUMN interview_sessions.resume_text_snapshot IS '创建面试时的简历文本快照';
COMMENT ON COLUMN interview_sessions.jd_text_snapshot IS '创建面试时的 JD 文本快照';
COMMENT ON COLUMN interview_sessions.custom_categories_json IS '创建面试时的自定义方向快照';
