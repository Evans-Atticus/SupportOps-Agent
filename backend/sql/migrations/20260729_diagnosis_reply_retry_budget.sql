-- Allow one format-correction retry after the normal understanding and reply calls.
-- Existing databases must apply this migration before enabling
-- supportops.ai.max-model-calls-per-diagnosis=3.
ALTER TABLE diagnosis_tasks
    DROP CHECK ck_diagnosis_tasks_model_calls;

ALTER TABLE diagnosis_tasks
    ADD CONSTRAINT ck_diagnosis_tasks_model_calls
        CHECK (model_call_count <= 3);
