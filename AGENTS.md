# CareerAI Development Rules

CareerAI uses a React frontend and a Java 21 Maven backend. The backend is currently a modular
monolith under `backend/careerai-app`; split it into services only after the core job-search flow is
stable.

## Structure

- `frontend/`: React 18, TypeScript, Vite and Tailwind CSS.
- `backend/pom.xml`: Maven parent and dependency management.
- `backend/gateway-service/`: Spring Cloud Gateway MVC static routing module.
- `backend/careerai-app/`: Spring Boot application.
- `backend/knowledge-service/`: first split-out knowledge base and RAG service.
- `backend/agent-service/`: stateless Java bridge for protected Agent config and business adapters.
- `docs/`: architecture, migration and delivery notes.

## Commands

```bash
sdk env
cd backend && mvn clean test
cd backend && mvn -pl careerai-app spring-boot:run
cd backend && mvn -pl knowledge-service spring-boot:run
cd backend && mvn -pl agent-service spring-boot:run
cd backend && mvn -pl gateway-service spring-boot:run
```

```bash
cd frontend && corepack enable
cd frontend && pnpm install
cd frontend && pnpm build
cd frontend && pnpm dev
```

## Backend Rules

- Keep `Controller -> Service -> Repository`; controllers only validate and delegate.
- Use `BusinessException` and the existing `ErrorCode` domains for business failures.
- Do not return JPA entities from controllers.
- Do not call LLM, S3 or external HTTP services inside database transactions.
- Keep AI structured output behind `StructuredOutputInvoker` and provider access behind
  `LlmProviderRegistry`.
- Use OpenFeign clients for synchronous service-to-service calls after a module is split out.
- Keep secrets in `.env`; commit only `.env.example`.
- Use Java 21, constructor injection, 2-space indentation and no wildcard imports.

## Frontend Rules

- Keep API access in `frontend/src/api/` and shared types in `frontend/src/types/`.
- Keep pages in `frontend/src/pages/` and reusable UI in `frontend/src/components/`.
- Preserve loading, success, error and disabled states for async operations.
- Do not add another UI framework without an explicit design decision.

## Verification

- Backend changes: `cd backend && mvn test`.
- Knowledge-service-only changes: `cd backend && mvn -pl knowledge-service test`.
- Gateway-only changes: `cd backend && mvn -pl gateway-service test`.
- Frontend changes: `cd frontend && pnpm build`.
- Configuration changes: verify against the named local Docker containers documented in README,
  especially Nacos registration when touching service discovery.
