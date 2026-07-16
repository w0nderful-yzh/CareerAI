# Java Agent Internal Service

This module is the protected Java bridge used by the Python Agent orchestrator. It owns no
database and does not initialize LangChain or an LLM.

Current request chains:

```text
Python agent-service
  -> GET :8082/internal/agent/model-config
  -> Java backend/agent-service
  -> OpenFeign GET :8080/internal/agent/model-config
  -> careerai-app Provider configuration

Python agent-service
  -> :8082/internal/agent/tools/**
  -> Java backend/agent-service
  -> OpenFeign :8080/internal/agent/tools/**
  -> careerai-app business services and database
```

All internal endpoints require `X-Agent-Service-Token`. Business Tool endpoints additionally
require `Authorization`, `X-Agent-Run-Id`, and `X-Agent-Step-Id`; write operations also require
`Idempotency-Key`. Configure the same
`AGENT_INTERNAL_SERVICE_TOKEN` in `careerai-app`, this service, and the Python service.

The first Tool set covers resume listing/detail, job detail, starting/polling a job-match task,
reading a match report, and creating/reading a resume-improvement plan. Plan creation accepts only a
bounded strategy decision (strategy, rationale, gaps, evidence and interview focus). The bridge owns
no business database and never accepts a model-supplied `userId`. Match-report responses expose the
JD–resume evidence matrix, while plan responses expose bounded structured preparation tasks.
The interview Tool pair exposes a read-only turn context and a bounded write command. The model can
submit only a structured next-question intent; the core application validates it, generates the
final question from trusted resume/JD context, and persists every turn.
The interview-creation Tool accepts a structured blueprint rather than free-form prompt text. The
core application validates its mode, difficulty, requirement IDs, topic limits and question types,
then owns question generation and persistence. `Idempotency-Key` is stored with the session so an
internal retry cannot create duplicate interviews.

Run locally:

```bash
cd backend
mvn -pl agent-service -am test
mvn -pl careerai-shared install -DskipTests
mvn -pl agent-service spring-boot:run
```

The install step refreshes the local shared-contract jar used when Maven runs this child module by
itself. A full `mvn install` also satisfies this requirement.
