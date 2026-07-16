import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from careerai_agent.api.routes import router
from careerai_agent.clients.careerai import CareerAiClient
from careerai_agent.config import Settings, get_settings
from careerai_agent.domain.models import ApiResult
from careerai_agent.graph.builder import build_graph
from careerai_agent.graph.interview import AdaptiveInterviewService, build_interview_graph
from careerai_agent.graph.interview_creation import (
    InterviewCreationService,
    build_interview_creation_graph,
)
from careerai_agent.logging import configure_logging
from careerai_agent.models.config_client import AgentModelConfigClient, ModelConfigError
from careerai_agent.models.factory import DynamicChatModelFactory
from careerai_agent.persistence.checkpointer import open_checkpointer
from careerai_agent.services.blueprint import (
    InterviewBlueprintError,
    InterviewBlueprintPlanner,
    LangChainInterviewBlueprintPlanner,
)
from careerai_agent.services.decision import (
    LangChainPreparationDecisionMaker,
    PreparationDecisionMaker,
)
from careerai_agent.services.interview import (
    InterviewDecisionError,
    InterviewDecisionMaker,
    LangChainInterviewDecisionMaker,
)
from careerai_agent.services.planner import AgentPlanner, LangChainAgentPlanner
from careerai_agent.services.runs import RunService
from careerai_agent.tools.client import (
    AgentBusinessToolClient,
    BusinessToolClient,
    BusinessToolError,
)

logger = logging.getLogger(__name__)


def create_app(
    settings: Settings | None = None,
    planner: AgentPlanner | None = None,
    decision_maker: PreparationDecisionMaker | None = None,
    interview_decision_maker: InterviewDecisionMaker | None = None,
    interview_blueprint_planner: InterviewBlueprintPlanner | None = None,
    business_client: BusinessToolClient | None = None,
) -> FastAPI:
    resolved_settings = settings or get_settings()
    configure_logging(resolved_settings.log_level)

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        app.state.settings = resolved_settings
        careerai_client = CareerAiClient(
            base_url=str(resolved_settings.careerai_api_base_url),
            timeout_seconds=resolved_settings.request_timeout_seconds,
        )
        app.state.careerai_client = careerai_client
        model_config_client: AgentModelConfigClient | None = None
        created_business_client: AgentBusinessToolClient | None = None
        token = (
            resolved_settings.internal_service_token.get_secret_value()
            if resolved_settings.internal_service_token is not None
            else None
        )
        resolved_business_client = business_client
        if resolved_business_client is None:
            created_business_client = AgentBusinessToolClient(
                base_url=str(resolved_settings.business_tool_base_url),
                service_token=token,
                timeout_seconds=resolved_settings.request_timeout_seconds,
            )
            resolved_business_client = created_business_client
        resolved_planner = planner
        resolved_decision_maker = decision_maker
        resolved_interview_decision_maker = interview_decision_maker
        resolved_interview_blueprint_planner = interview_blueprint_planner
        if (
            resolved_planner is None
            or resolved_decision_maker is None
            or resolved_interview_decision_maker is None
            or resolved_interview_blueprint_planner is None
        ):
            model_config_client = AgentModelConfigClient(
                base_url=str(resolved_settings.model_config_base_url),
                service_token=token,
                timeout_seconds=resolved_settings.request_timeout_seconds,
            )
            model_factory = DynamicChatModelFactory(
                model_config_client,
                timeout_seconds=resolved_settings.request_timeout_seconds,
            )
            if resolved_planner is None:
                resolved_planner = LangChainAgentPlanner(model_factory)
            if resolved_decision_maker is None:
                resolved_decision_maker = LangChainPreparationDecisionMaker(model_factory)
            if resolved_interview_decision_maker is None:
                resolved_interview_decision_maker = LangChainInterviewDecisionMaker(model_factory)
            if resolved_interview_blueprint_planner is None:
                resolved_interview_blueprint_planner = LangChainInterviewBlueprintPlanner(
                    model_factory
                )
        async with open_checkpointer(resolved_settings) as checkpointer:
            app.state.run_service = RunService(
                build_graph(
                    checkpointer,
                    resolved_planner,
                    resolved_decision_maker,
                    resolved_business_client,
                )
            )
            app.state.adaptive_interview_service = AdaptiveInterviewService(
                build_interview_graph(
                    resolved_interview_decision_maker,
                    resolved_business_client,
                ),
                resolved_interview_decision_maker,
                resolved_business_client,
            )
            app.state.interview_creation_service = InterviewCreationService(
                build_interview_creation_graph(
                    resolved_interview_blueprint_planner,
                    resolved_business_client,
                )
            )
            yield
        if model_config_client is not None:
            await model_config_client.aclose()
        if created_business_client is not None:
            await created_business_client.aclose()
        await careerai_client.aclose()

    application = FastAPI(
        title=resolved_settings.app_name,
        version="0.1.0",
        lifespan=lifespan,
    )

    @application.exception_handler(ModelConfigError)
    async def handle_model_config_error(
        _request: Request,
        exc: ModelConfigError,
    ) -> JSONResponse:
        # 配置故障需要明确返回给前端，避免只留下难以定位的 500 堆栈。
        logger.warning("Agent model config is unavailable: %s", exc)
        result = ApiResult[None](code=9501, message=str(exc), data=None)
        return JSONResponse(status_code=502, content=result.model_dump(mode="json"))

    @application.exception_handler(InterviewDecisionError)
    async def handle_interview_decision_error(
        _request: Request,
        exc: InterviewDecisionError,
    ) -> JSONResponse:
        logger.warning("Adaptive interview decision failed: %s", exc)
        result = ApiResult[None](code=9502, message=str(exc), data=None)
        return JSONResponse(status_code=502, content=result.model_dump(mode="json"))

    @application.exception_handler(InterviewBlueprintError)
    async def handle_interview_blueprint_error(
        _request: Request,
        exc: InterviewBlueprintError,
    ) -> JSONResponse:
        logger.warning("Interview blueprint planning failed: %s", exc)
        result = ApiResult[None](code=9503, message=str(exc), data=None)
        return JSONResponse(status_code=502, content=result.model_dump(mode="json"))

    @application.exception_handler(BusinessToolError)
    async def handle_business_tool_error(
        _request: Request,
        exc: BusinessToolError,
    ) -> JSONResponse:
        logger.warning("Agent business tool failed: %s", exc)
        result = ApiResult[None](code=exc.code, message=exc.message, data=None)
        return JSONResponse(status_code=502, content=result.model_dump(mode="json"))

    application.include_router(router)
    return application


app = create_app()
