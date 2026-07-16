# CareerAI Agent Service

Python orchestration service for the CareerAI execution Agent. It owns Agent run state and
LangGraph checkpoints, while Java remains the only owner of resume, job, match, interview and
schedule business data.

## Current capability

- FastAPI application and health endpoint.
- Authenticated Agent run creation and lookup.
- LangGraph state compiled with a configurable checkpointer.
- In-memory checkpointer for tests/local smoke runs.
- PostgreSQL checkpointer option for restart recovery.
- Typed client for validating the current user against `careerai-app`.
- Protected model-config client for the Java `backend/agent-service`.
- Dynamic `ChatOpenAI` factory keyed by Provider and config version.
- LangChain planning node that uses the dynamically selected Agent model.
- Evidence-driven decision node that selects a preparation strategy and the next write Tool.
- Evidence-matrix input that links each core JD requirement to exact resume evidence and a typed gap.
- Twelve typed LangChain business Tools backed by the protected Java bridge.
- Executable `resume -> job -> match task -> match report -> decision -> improvement plan` graph.
- `WAITING_ASYNC` checkpoint and authenticated resume endpoint for polling match tasks.
- Gateway route at `/api/agent/**`.
- Adaptive interview subgraph: `read turn context -> score and decide -> apply bounded Java Tool`.
- Persistent ability profile in turn context, including status, trend, confidence and latest evidence.
- Interview-creation subgraph: `load resume/JD/evidence -> plan blueprint -> create via Java Tool`.
- Cross-session planning context with profiles, unfinished interview tasks and unverified targets.

The interview subgraph evaluates the current answer and emits a bounded `NextQuestionIntent`
instead of final question text. Its actions are `FOLLOW_UP`, `SWITCH_TOPIC`,
`ADJUST_DIFFICULTY`, and `END_INTERVIEW`; Java validates the intended question type, topic,
difficulty, parent question and JD requirement, generates and appends the final question with the
trusted resume/JD context, and triggers the existing asynchronous final evaluation when the
interview ends.
Each scored turn is also converted by Java into immutable technical, project and communication
observations. The model can read the resulting profile but cannot directly write profile scores or
overwrite historical evidence.

Before a new interview starts, the creation graph reads the selected resume, job and match report
through authenticated Tools. It also reads the current user's cross-session profile, unfinished
improvement tasks and recent unverified targets. Unfinished tasks and declining/conflicting profiles
are scheduled for retesting; high-confidence stable strengths are upgraded to scenario and
troubleshooting questions, while unverified targets remain explicitly marked as non-weaknesses.
The model emits only an `InterviewBlueprint` containing the training
mode, objective, real JD requirement IDs, focus topics, question-type mix, avoid list and follow-up
limit. Java filters the requirement IDs again, renders the blueprint into the existing safe question
prompts, persists the normalized blueprint and uses an idempotency key to prevent duplicate sessions.

```http
POST /api/agent/interviews/sessions
Authorization: Bearer <user token>
Content-Type: application/json

{
  "resumeId": 2,
  "jobId": 1,
  "matchReportId": 3,
  "skillId": "java-backend",
  "difficulty": "mid",
  "questionCount": 6,
  "trainingMode": "FOCUS_DRILL",
  "userFocus": "结合项目强化 Redis 一致性与线上故障定位"
}
```

The first executable flow requires `constraints.jobId`. `constraints.resumeId` is optional; when it
is omitted, the graph selects the user's resume with the highest latest analysis score. If the Java
match task is still pending, the run stops at `WAITING_ASYNC` and can be continued with:

```http
POST /api/agent/runs/{runId}/resume
Authorization: Bearer <user token>
```

The user token is passed through LangGraph runtime context and is never stored in graph state or a
checkpoint. Write Tools derive stable idempotency keys from `runId`.

After the match report is ready, the decision node emits a typed artifact containing the strategy,
rationale, prioritized gaps, supporting evidence and interview focus. The graph then either finishes
with the report or passes those bounded fields to `create_resume_improvement_plan`; Java validates
the command and remains responsible for persistence. The saved plan contains structured preparation
tasks with priority, suggested duration, verification method and related requirement IDs.

## Setup

```bash
uv sync
cp .env.example .env
uv run uvicorn careerai_agent.main:app --reload --port 8000
```

The default run endpoints require `careerai-app` on `http://localhost:8080` for `/api/auth/me`, and
the Java Agent bridge on `http://localhost:8082` for model config and `/internal/agent/tools/**`.
Configure the same `AGENT_INTERNAL_SERVICE_TOKEN` in the Python service and both Java services.

Example request:

```json
{
  "goal": "为目标 Java 后端岗位生成简历改进计划",
  "constraints": {
    "jobId": 1,
    "resumeId": 2
  }
}
```

## Checkpointer

Local development defaults to `memory`. To persist runs across restarts:

```env
AGENT_CHECKPOINTER_BACKEND=postgres
AGENT_DATABASE_URL=postgresql://user:password@localhost:5432/careerai?sslmode=disable
```

The service calls the LangGraph PostgreSQL checkpointer `setup()` during startup. Use a dedicated
database/schema and least-privilege credentials before production deployment.

## Verification

```bash
uv run ruff check .
uv run ruff format --check .
uv run mypy src
uv run pytest
```
