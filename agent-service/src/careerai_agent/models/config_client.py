from collections.abc import Mapping
from typing import Protocol

import httpx

from careerai_agent.models.config import AgentModelRuntimeConfig


class ModelConfigError(RuntimeError):
    pass


class ModelConfigSource(Protocol):
    async def get_model_config(self) -> AgentModelRuntimeConfig: ...


class AgentModelConfigClient:
    def __init__(
        self,
        base_url: str,
        service_token: str | None,
        timeout_seconds: float,
    ) -> None:
        self._service_token = service_token
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=timeout_seconds,
        )

    async def aclose(self) -> None:
        await self._client.aclose()

    async def get_model_config(self) -> AgentModelRuntimeConfig:
        if not self._service_token:
            raise ModelConfigError("AGENT_INTERNAL_SERVICE_TOKEN is required")
        response = await self._client.get(
            "/internal/agent/model-config",
            headers={"X-Agent-Service-Token": self._service_token},
        )
        response.raise_for_status()
        data = self._unwrap_result(response.json())
        if not isinstance(data, Mapping):
            raise ModelConfigError("Java agent-service returned an invalid model config")
        return AgentModelRuntimeConfig.model_validate(data)

    @staticmethod
    def _unwrap_result(payload: object) -> object:
        if not isinstance(payload, Mapping):
            raise ModelConfigError("Java agent-service returned an invalid response")
        code = payload.get("code")
        message = payload.get("message")
        if not isinstance(code, int):
            raise ModelConfigError("Java agent-service response is missing a numeric code")
        if code != 200:
            detail = message if isinstance(message, str) else "model config request failed"
            raise ModelConfigError(detail)
        return payload.get("data")
