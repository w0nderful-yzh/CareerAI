from typing import Annotated, Any, cast

import httpx
from fastapi import Header, HTTPException, Request, status

from careerai_agent.clients.careerai import CareerAiApiError, CareerAiClient
from careerai_agent.services.runs import RunService


def get_run_service(request: Request) -> RunService:
    return cast(RunService, request.app.state.run_service)


def get_careerai_client(request: Request) -> CareerAiClient:
    return cast(CareerAiClient, request.app.state.careerai_client)


async def get_current_user(
    request: Request,
    authorization: Annotated[str | None, Header()] = None,
) -> dict[str, Any]:
    if authorization is None or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Bearer token required",
        )

    client = get_careerai_client(request)
    try:
        user = await client.get_current_user(authorization)
    except CareerAiApiError as exc:
        status_code = (
            status.HTTP_401_UNAUTHORIZED if exc.code == 401 else status.HTTP_502_BAD_GATEWAY
        )
        raise HTTPException(status_code=status_code, detail=exc.message) from exc
    except httpx.HTTPError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="careerai-app is unavailable",
        ) from exc
    return dict(user)
