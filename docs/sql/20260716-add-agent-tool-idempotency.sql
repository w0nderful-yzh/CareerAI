-- Agent 业务 Tool 写操作幂等键（PostgreSQL）
ALTER TABLE ai_analysis_tasks
    ADD COLUMN IF NOT EXISTS agent_idempotency_key VARCHAR(120);

ALTER TABLE resume_improvement_plans
    ADD COLUMN IF NOT EXISTS agent_idempotency_key VARCHAR(120);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_ai_task_agent_idempotency'
    ) THEN
        ALTER TABLE ai_analysis_tasks
            ADD CONSTRAINT uk_ai_task_agent_idempotency
            UNIQUE (user_id, task_type, agent_idempotency_key);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_resume_plan_agent_idempotency'
    ) THEN
        ALTER TABLE resume_improvement_plans
            ADD CONSTRAINT uk_resume_plan_agent_idempotency
            UNIQUE (user_id, agent_idempotency_key);
    END IF;
END $$;
