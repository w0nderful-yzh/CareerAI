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
- Eight typed LangChain business Tools backed by the protected Java bridge.
- Executable `resume -> job -> match task -> match report -> improvement plan` graph.
- `WAITING_ASYNC` checkpoint and authenticated resume endpoint for polling match tasks.
- Gateway route at `/api/agent/**`.

The first executable flow requires `constraints.jobId`. `constraints.resumeId` is optional; when it
is omitted, the graph selects the user's resume with the highest latest analysis score. If the Java
match task is still pending, the run stops at `WAITING_ASYNC` and can be continued with:

```http
POST /api/agent/runs/{runId}/resume
Authorization: Bearer <user token>
```

The user token is passed through LangGraph runtime context and is never stored in graph state or a
checkpoint. Write Tools derive stable idempotency keys from `runId`.

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
