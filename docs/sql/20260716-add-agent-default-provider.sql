ALTER TABLE llm_global_setting
  ADD COLUMN IF NOT EXISTS default_agent_provider_id VARCHAR(64);

UPDATE llm_global_setting
SET default_agent_provider_id = default_chat_provider_id
WHERE default_agent_provider_id IS NULL;

ALTER TABLE llm_global_setting
  ALTER COLUMN default_agent_provider_id SET NOT NULL;
