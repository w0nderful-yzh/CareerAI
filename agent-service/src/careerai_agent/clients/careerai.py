from collections.abc import Mapping
from typing import Any

import httpx


class CareerAiApiError(RuntimeError):
    def __init__(self, code: int, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.message = message


class CareerAiClient:
    def __init__(self, base_url: str, timeout_seconds: float) -> None:
        self._client = httpx.AsyncClient(
            base_url=base_url.rstrip("/"),
            timeout=timeout_seconds,
        )

    async def aclose(self) -> None:
        await self._client.aclose()

    async def get_current_user(self, authorization: str) -> Mapping[str, Any]:
        response = await self._client.get(
            "/api/auth/me",
            headers={"Authorization": authorization},
        )
        response.raise_for_status()
        data = self._unwrap_result(response.json())
        if not isinstance(data, Mapping) or "id" not in data:
            raise CareerAiApiError(502, "careerai-app returned an invalid current user")
        return data

    @staticmethod
    def _unwrap_result(payload: object) -> object:
        if not isinstance(payload, Mapping):
            raise CareerAiApiError(502, "careerai-app returned an invalid response")

        code = payload.get("code")
        message = payload.get("message")
        if not isinstance(code, int):
            raise CareerAiApiError(502, "careerai-app response is missing a numeric code")
        if code != 200:
            raise CareerAiApiError(code, message if isinstance(message, str) else "request failed")
        return payload.get("data")
