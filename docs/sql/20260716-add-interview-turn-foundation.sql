-- 第一阶段：规范化面试问题，并为每轮多维评价和结束覆盖范围补齐持久化字段。
CREATE TABLE IF NOT EXISTS interview_questions (
  id BIGSERIAL PRIMARY KEY,
  session_id BIGINT NOT NULL REFERENCES interview_sessions(id) ON DELETE CASCADE,
  question_index INTEGER NOT NULL,
  question TEXT NOT NULL,
  skill_key VARCHAR(64),
  category VARCHAR(80),
  stage VARCHAR(32),
  difficulty VARCHAR(16),
  source_type VARCHAR(32),
  source_ref VARCHAR(64),
  requirement_id VARCHAR(40),
  parent_question_index INTEGER,
  follow_up BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_interview_question_session_index UNIQUE (session_id, question_index)
);

CREATE INDEX IF NOT EXISTS idx_interview_question_session_index
  ON interview_questions (session_id, question_index);

ALTER TABLE interview_answers
  ADD COLUMN IF NOT EXISTS evaluation_json TEXT,
  ADD COLUMN IF NOT EXISTS agent_action VARCHAR(32),
  ADD COLUMN IF NOT EXISTS decision_rationale TEXT,
  ADD COLUMN IF NOT EXISTS question_record_id BIGINT;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_interview_answer_question_record'
  ) THEN
    ALTER TABLE interview_answers
      ADD CONSTRAINT fk_interview_answer_question_record
      FOREIGN KEY (question_record_id) REFERENCES interview_questions(id) ON DELETE SET NULL;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_interview_answer_question_record
  ON interview_answers (question_record_id);

ALTER TABLE interview_sessions
  ADD COLUMN IF NOT EXISTS planned_main_questions INTEGER,
  ADD COLUMN IF NOT EXISTS end_reason VARCHAR(32),
  ADD COLUMN IF NOT EXISTS completion_type VARCHAR(16),
  ADD COLUMN IF NOT EXISTS covered_targets_json TEXT,
  ADD COLUMN IF NOT EXISTS unverified_targets_json TEXT;
