# CareerAI Agent Service

Python orchestration service for the CareerAI execution Agent. It owns Agent run state and
LangGraph checkpoints, while Java remains the only owner of resume, job, match, interview and
schedule business data.

## Current scaffold

- FastAPI application and health endpoint.
- Authenticated Agent run creation and lookup.
- LangGraph state compiled with a configurable checkpointer.
- In-memory checkpointer for tests/local smoke runs.
- PostgreSQL checkpointer option for restart recovery.
- Typed client for validating the current user against `careerai-app`.
- Protected model-config client for the Java `backend/agent-service`.
- Dynamic `ChatOpenAI` factory keyed by Provider and config version.
- LangChain planning node that uses the dynamically selected Agent model.
- Gateway route at `/api/agent/**`.

The initial graph intentionally pauses after creating the execution plan. Business tools are added
in the next phase; the scaffold does not pretend that a user goal has been completed.

## Setup

```bash
uv sync
cp .env.example .env
uv run uvicorn careerai_agent.main:app --reload --port 8000
```

The default run endpoints require `careerai-app` on `http://localhost:8080` for `/api/auth/me`, and
the Java Agent bridge on `http://localhost:8082` for `/internal/agent/model-config`. Configure the
same `AGENT_INTERNAL_SERVICE_TOKEN` in the Python service and both Java services.

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
