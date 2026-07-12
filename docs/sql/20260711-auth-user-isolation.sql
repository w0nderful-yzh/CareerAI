-- CareerAI auth + user isolation migration for existing local PostgreSQL databases.
-- Run after pulling this change if your database was created before user isolation.
--
-- This keeps old rows visible to the local development user and removes the old
-- global file-hash uniqueness that would block two users from uploading the same file.

INSERT INTO users (username, password_hash, display_name, enabled, created_at, updated_at)
VALUES (
  'local-dev',
  '$2a$10$7jJmYJJj0J0yZ4rMTrR3pOsbP6pD1RhzlClVvBoec6TJ3Lw6i2ScS',
  '本地开发用户',
  true,
  now(),
  now()
)
ON CONFLICT (username) DO NOTHING;

ALTER TABLE resumes ADD COLUMN IF NOT EXISTS user_id bigint;
UPDATE resumes
SET user_id = (SELECT id FROM users WHERE username = 'local-dev')
WHERE user_id IS NULL;
ALTER TABLE resumes DROP CONSTRAINT IF EXISTS idx_resume_hash;
DROP INDEX IF EXISTS idx_resume_hash;
CREATE INDEX IF NOT EXISTS idx_resume_user_hash ON resumes (user_id, file_hash);
CREATE INDEX IF NOT EXISTS idx_resume_user_uploaded ON resumes (user_id, uploaded_at);

ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS user_id bigint;
UPDATE interview_sessions s
SET user_id = r.user_id
FROM resumes r
WHERE s.resume_id = r.id AND s.user_id IS NULL;
UPDATE interview_sessions
SET user_id = (SELECT id FROM users WHERE username = 'local-dev')
WHERE user_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_interview_session_user_created ON interview_sessions (user_id, created_at);
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS job_id bigint;
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS match_report_id bigint;
CREATE INDEX IF NOT EXISTS idx_interview_session_user_job_created ON interview_sessions (user_id, job_id, created_at);

ALTER TABLE knowledge_bases ADD COLUMN IF NOT EXISTS user_id bigint;
UPDATE knowledge_bases
SET user_id = (SELECT id FROM users WHERE username = 'local-dev')
WHERE user_id IS NULL;
ALTER TABLE knowledge_bases DROP CONSTRAINT IF EXISTS idx_kb_hash;
DROP INDEX IF EXISTS idx_kb_hash;
CREATE INDEX IF NOT EXISTS idx_kb_user_hash ON knowledge_bases (user_id, file_hash);
CREATE INDEX IF NOT EXISTS idx_kb_user_category ON knowledge_bases (user_id, category);
CREATE INDEX IF NOT EXISTS idx_kb_user_uploaded ON knowledge_bases (user_id, uploaded_at);

ALTER TABLE rag_chat_sessions ADD COLUMN IF NOT EXISTS user_id bigint;
UPDATE rag_chat_sessions
SET user_id = (SELECT id FROM users WHERE username = 'local-dev')
WHERE user_id IS NULL;
CREATE INDEX IF NOT EXISTS idx_rag_session_user_updated ON rag_chat_sessions (user_id, updated_at);

CREATE TABLE IF NOT EXISTS jobs (
  id bigserial PRIMARY KEY,
  user_id bigint,
  title varchar(160) NOT NULL,
  company varchar(160),
  location varchar(120),
  source_url varchar(500),
  status varchar(20) NOT NULL DEFAULT 'TRACKING',
  jd_text text NOT NULL,
  parsed_categories_json text,
  created_at timestamp NOT NULL DEFAULT now(),
  updated_at timestamp NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_jobs_user_updated ON jobs (user_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_jobs_user_status ON jobs (user_id, status);

CREATE TABLE IF NOT EXISTS job_match_reports (
  id bigserial PRIMARY KEY,
  user_id bigint NOT NULL,
  resume_id bigint NOT NULL,
  job_id bigint NOT NULL,
  resume_filename varchar(255) NOT NULL,
  job_title varchar(160) NOT NULL,
  overall_score integer NOT NULL,
  skill_score integer NOT NULL,
  project_score integer NOT NULL,
  keyword_score integer NOT NULL,
  summary text NOT NULL,
  matched_highlights_json text,
  gaps_json text,
  action_items_json text,
  created_at timestamp NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_job_match_user_created ON job_match_reports (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_job_match_user_job_created ON job_match_reports (user_id, job_id, created_at);
CREATE INDEX IF NOT EXISTS idx_job_match_user_resume_created ON job_match_reports (user_id, resume_id, created_at);
