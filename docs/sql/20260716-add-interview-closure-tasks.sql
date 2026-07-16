-- 第五阶段：面试结束总结与可执行改进任务。
CREATE TABLE IF NOT EXISTS interview_closures (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  session_id VARCHAR(36) NOT NULL REFERENCES interview_sessions(session_id) ON DELETE CASCADE,
  idempotency_key VARCHAR(160) NOT NULL,
  completion_type VARCHAR(16),
  end_reason VARCHAR(32),
  overall_score INTEGER CHECK (overall_score BETWEEN 0 AND 100),
  summary TEXT NOT NULL,
  strengths_json TEXT NOT NULL,
  observed_weaknesses_json TEXT NOT NULL,
  covered_targets_json TEXT NOT NULL,
  unverified_targets_json TEXT NOT NULL,
  key_evidence_json TEXT NOT NULL,
  next_interview_suggestions_json TEXT NOT NULL,
  generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_interview_closure_session UNIQUE (session_id),
  CONSTRAINT uk_interview_closure_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_interview_closure_user_time
  ON interview_closures (user_id, generated_at DESC);

-- 任务只能来自已回答轮次的错误或缺失点，idempotency_key 保证异步重试不重复写入。
CREATE TABLE IF NOT EXISTS interview_improvement_tasks (
  id BIGSERIAL PRIMARY KEY,
  closure_id BIGINT NOT NULL REFERENCES interview_closures(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  session_id VARCHAR(36) NOT NULL REFERENCES interview_sessions(session_id) ON DELETE CASCADE,
  idempotency_key VARCHAR(200) NOT NULL,
  question_index INTEGER NOT NULL,
  category VARCHAR(120) NOT NULL,
  priority VARCHAR(16) NOT NULL,
  status VARCHAR(16) NOT NULL,
  title VARCHAR(300) NOT NULL,
  rationale TEXT NOT NULL,
  verification_method TEXT NOT NULL,
  evidence_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_interview_improvement_task_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_interview_improvement_task_session
  ON interview_improvement_tasks (session_id);
CREATE INDEX IF NOT EXISTS idx_interview_improvement_task_user_status
  ON interview_improvement_tasks (user_id, status, created_at DESC);
