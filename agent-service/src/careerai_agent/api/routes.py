from typing import Annotated, Any

from fastapi import APIRouter, Depends, HTTPException, Request, status

from careerai_agent.api.dependencies import get_current_user, get_run_service
from careerai_agent.domain.models import AgentRun, ApiResult, CreateRunRequest
from careerai_agent.services.runs import RunService

router = APIRouter()


@router.get("/health", response_model=ApiResult[dict[str, str]])
async def health(request: Request) -> ApiResult[dict[str, str]]:
    settings = request.app.state.settings
    return ApiResult(
        data={
            "status": "UP",
            "environment": settings.environment,
            "checkpointer": settings.checkpointer_backend,
        }
    )


@router.post(
    "/api/agent/runs",
    response_model=ApiResult[AgentRun],
    status_code=status.HTTP_201_CREATED,
)
async def create_run(
    payload: CreateRunRequest,
    current_user: Annotated[dict[str, Any], Depends(get_current_user)],
    run_service: Annotated[RunService, Depends(get_run_service)],
) -> ApiResult[AgentRun]:
    run = await run_service.create_run(payload, user_id=str(current_user["id"]))
    return ApiResult(data=run)


@router.get("/api/agent/runs/{run_id}", response_model=ApiResult[AgentRun])
async def get_run(
    run_id: str,
    current_user: Annotated[dict[str, Any], Depends(get_current_user)],
    run_service: Annotated[RunService, Depends(get_run_service)],
) -> ApiResult[AgentRun]:
    run = await run_service.get_run(run_id, user_id=str(current_user["id"]))
    if run is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Agent run not found")
    return ApiResult(data=run)
