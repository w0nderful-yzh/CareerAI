from typing import Annotated, Any

from fastapi import APIRouter, Depends, Header, HTTPException, Request, status

from careerai_agent.api.dependencies import (
    get_adaptive_interview_service,
    get_current_user,
    get_interview_creation_service,
    get_run_service,
)
from careerai_agent.domain.models import (
    AgentRun,
    ApiResult,
    CreateInterviewSessionRequest,
    CreateRunRequest,
    SubmitInterviewTurnRequest,
)
from careerai_agent.graph.interview import AdaptiveInterviewService
from careerai_agent.graph.interview_creation import InterviewCreationService
from careerai_agent.services.runs import RunService
from careerai_agent.tools.models import InterviewSession, InterviewTurnResult

router = APIRouter()


@router.post(
    "/api/agent/interviews/sessions",
    response_model=ApiResult[InterviewSession],
    status_code=status.HTTP_201_CREATED,
)
async def create_interview_session(
    payload: CreateInterviewSessionRequest,
    authorization: Annotated[str, Header()],
    current_user: Annotated[dict[str, Any], Depends(get_current_user)],
    interview_creation_service: Annotated[
        InterviewCreationService,
        Depends(get_interview_creation_service),
    ],
) -> ApiResult[InterviewSession]:
    session = await interview_creation_service.create_session(
        payload,
        authorization=authorization,
        user_id=str(current_user["id"]),
    )
    return ApiResult(data=session)


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
    authorization: Annotated[str, Header()],
    current_user: Annotated[dict[str, Any], Depends(get_current_user)],
    run_service: Annotated[RunService, Depends(get_run_service)],
) -> ApiResult[AgentRun]:
    run = await run_service.create_run(
        payload,
        user_id=str(current_user["id"]),
        authorization=authorization,
    )
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


@router.post(
    "/api/agent/runs/{run_id}/resume",
    response_model=ApiResult[AgentRun],
)
async def resume_run(
    run_id: str,
    authorization: Annotated[str, Header()],
    current_user: Annotated[dict[str, Any], Depends(get_current_user)],
    run_service: Annotated[RunService, Depends(get_run_service)],
) -> ApiResult[AgentRun]:
    run = await run_service.resume_run(
        run_id,
        user_id=str(current_user["id"]),
        authorization=authorization,
    )
    if run is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Agent run not found")
    return ApiResult(data=run)


@router.post(
    "/api/agent/interviews/{session_id}/turns",
    response_model=ApiResult[InterviewTurnResult],
)
async def submit_interview_turn(
    session_id: str,
    payload: SubmitInterviewTurnRequest,
    authorization: Annotated[str, Header()],
    _current_user: Annotated[dict[str, Any], Depends(get_current_user)],
    interview_service: Annotated[
        AdaptiveInterviewService,
        Depends(get_adaptive_interview_service),
    ],
) -> ApiResult[InterviewTurnResult]:
    result = await interview_service.submit_turn(
        session_id=session_id,
        question_index=payload.question_index,
        answer=payload.answer,
        intent=payload.intent,
        authorization=authorization,
    )
    return ApiResult(data=result)
