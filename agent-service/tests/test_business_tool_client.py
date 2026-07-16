from typing import Any

import httpx
import pytest

from careerai_agent.tools.client import (
    AgentBusinessToolClient,
    BusinessToolError,
    ToolCallContext,
)


async def test_write_tool_forwards_context_and_idempotency_key() -> None:
    captured: dict[str, Any] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["headers"] = dict(request.headers)
        captured["body"] = request.read().decode()
        return httpx.Response(
            200,
            json={
                "code": 200,
                "message": "success",
                "data": {
                    "id": 91,
                    "status": "PENDING",
                    "resumeId": 11,
                    "jobId": 22,
                },
            },
        )

    client = AgentBusinessToolClient("http://agent.test", "internal-token", 5)
    await client._client.aclose()
    client._client = httpx.AsyncClient(
        base_url="http://agent.test",
        transport=httpx.MockTransport(handler),
    )
    context = ToolCallContext("Bearer user-token", "run-1", "start_job_match")

    try:
        task = await client.start_job_match(11, 22, context, "run-1:start_job_match")
    finally:
        await client.aclose()

    assert task.id == 91
    headers = captured["headers"]
    assert headers["x-agent-service-token"] == "internal-token"
    assert headers["authorization"] == "Bearer user-token"
    assert headers["x-agent-run-id"] == "run-1"
    assert headers["x-agent-step-id"] == "start_job_match"
    assert headers["idempotency-key"] == "run-1:start_job_match"
    assert captured["body"] == '{"resumeId":11,"jobId":22}'


async def test_business_error_code_is_not_treated_as_success() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"code": 9501, "message": "岗位不存在", "data": None})

    client = AgentBusinessToolClient("http://agent.test", "internal-token", 5)
    await client._client.aclose()
    client._client = httpx.AsyncClient(
        base_url="http://agent.test",
        transport=httpx.MockTransport(handler),
    )

    try:
        with pytest.raises(BusinessToolError, match="岗位不存在") as captured:
            await client.get_job(22, ToolCallContext("Bearer token", "run-1", "load_job"))
    finally:
        await client.aclose()

    assert captured.value.code == 9501
    assert captured.value.retryable is False
