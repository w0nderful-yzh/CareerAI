# Java Agent Internal Service

This module is the protected Java bridge used by the Python Agent orchestrator. It owns no
database and does not initialize LangChain or an LLM.

Current request chain:

```text
Python agent-service
  -> GET :8082/internal/agent/model-config
  -> Java backend/agent-service
  -> OpenFeign GET :8080/internal/agent/model-config
  -> careerai-app Provider configuration
```

Both internal endpoints require `X-Agent-Service-Token`. Configure the same
`AGENT_INTERNAL_SERVICE_TOKEN` in `careerai-app`, this service, and the Python service.

Run locally:

```bash
cd backend
mvn -pl agent-service -am test
mvn -pl agent-service spring-boot:run
```
