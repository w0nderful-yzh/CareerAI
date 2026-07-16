-- 不可变能力观察：每条都能追溯到具体面试回答。
CREATE TABLE IF NOT EXISTS ability_observations (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  session_id VARCHAR(36) NOT NULL,
  question_index INTEGER NOT NULL,
  dimension VARCHAR(24) NOT NULL,
  ability_key VARCHAR(160) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100),
  confidence INTEGER NOT NULL CHECK (confidence BETWEEN 0 AND 100),
  signal VARCHAR(16) NOT NULL,
  evidence_type VARCHAR(32) NOT NULL,
  evidence_id BIGINT NOT NULL REFERENCES interview_answers(id) ON DELETE CASCADE,
  evidence_json TEXT NOT NULL,
  missing_points_json TEXT,
  errors_json TEXT,
  observed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_ability_observation_evidence_dimension_key
    UNIQUE (evidence_id, dimension, ability_key)
);

CREATE INDEX IF NOT EXISTS idx_ability_observation_user_key_time
  ON ability_observations (user_id, dimension, ability_key, observed_at);
CREATE INDEX IF NOT EXISTS idx_ability_observation_session
  ON ability_observations (session_id);

-- 当前稳定画像是观察的可重建投影，不保存模型自由文本结论。
CREATE TABLE IF NOT EXISTS ability_profiles (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  dimension VARCHAR(24) NOT NULL,
  ability_key VARCHAR(160) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 100),
  confidence INTEGER NOT NULL CHECK (confidence BETWEEN 0 AND 100),
  status VARCHAR(16) NOT NULL,
  trend VARCHAR(16) NOT NULL,
  observation_count INTEGER NOT NULL,
  session_count INTEGER NOT NULL,
  latest_observation_id BIGINT NOT NULL,
  last_observed_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_ability_profile_user_dimension_key
    UNIQUE (user_id, dimension, ability_key)
);

CREATE INDEX IF NOT EXISTS idx_ability_profile_user_status
  ON ability_profiles (user_id, status, last_observed_at DESC);
