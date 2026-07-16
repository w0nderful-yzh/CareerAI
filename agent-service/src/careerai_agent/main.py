from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from careerai_agent.api.routes import router
from careerai_agent.clients.careerai import CareerAiClient
from careerai_agent.config import Settings, get_settings
from careerai_agent.graph.builder import build_graph
from careerai_agent.logging import configure_logging
from careerai_agent.models.config_client import AgentModelConfigClient
from careerai_agent.models.factory import DynamicChatModelFactory
from careerai_agent.persistence.checkpointer import open_checkpointer
from careerai_agent.services.planner import AgentPlanner, LangChainAgentPlanner
from careerai_agent.services.runs import RunService


def create_app(
    settings: Settings | None = None,
    planner: AgentPlanner | None = None,
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
        resolved_planner = planner
        if resolved_planner is None:
            token = (
                resolved_settings.internal_service_token.get_secret_value()
                if resolved_settings.internal_service_token is not None
                else None
            )
            model_config_client = AgentModelConfigClient(
                base_url=str(resolved_settings.model_config_base_url),
                service_token=token,
                timeout_seconds=resolved_settings.request_timeout_seconds,
            )
            model_factory = DynamicChatModelFactory(
                model_config_client,
                timeout_seconds=resolved_settings.request_timeout_seconds,
            )
            resolved_planner = LangChainAgentPlanner(model_factory)
        async with open_checkpointer(resolved_settings) as checkpointer:
            app.state.run_service = RunService(build_graph(checkpointer, resolved_planner))
            yield
        if model_config_client is not None:
            await model_config_client.aclose()
        await careerai_client.aclose()

    application = FastAPI(
        title=resolved_settings.app_name,
        version="0.1.0",
        lifespan=lifespan,
    )
    application.include_router(router)
    return application


app = create_app()
