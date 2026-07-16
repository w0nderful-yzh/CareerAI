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
reading a match report, and creating/reading a resume-improvement plan. The bridge owns no business
database and never accepts a model-supplied `userId`.

Run locally:

```bash
cd backend
mvn -pl agent-service -am test
mvn -pl careerai-shared install -DskipTests
mvn -pl agent-service spring-boot:run
```

The install step refreshes the local shared-contract jar used when Maven runs this child module by
itself. A full `mvn install` also satisfies this requirement.
