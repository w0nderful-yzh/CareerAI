BEGIN;

ALTER TABLE job_match_reports
    ADD COLUMN IF NOT EXISTS evidence_mappings_json TEXT;

COMMENT ON COLUMN job_match_reports.evidence_mappings_json IS
    'JD要求、简历原文证据、覆盖类型与置信度的结构化JSON';

ALTER TABLE resume_improvement_plans
    ADD COLUMN IF NOT EXISTS preparation_tasks_json TEXT;

COMMENT ON COLUMN resume_improvement_plans.preparation_tasks_json IS
    '带优先级、建议周期、验证方式和关联JD要求的准备任务JSON';

COMMIT;
