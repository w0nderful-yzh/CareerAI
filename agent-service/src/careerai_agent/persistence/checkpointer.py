import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from langgraph.checkpoint.base import BaseCheckpointSaver
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.checkpoint.postgres.aio import AsyncPostgresSaver

from careerai_agent.config import Settings

logger = logging.getLogger(__name__)


@asynccontextmanager
async def open_checkpointer(settings: Settings) -> AsyncIterator[BaseCheckpointSaver[str]]:
    if settings.checkpointer_backend == "memory":
        logger.warning("Using in-memory checkpoints; Agent runs will not survive a restart")
        yield InMemorySaver()
        return

    if settings.database_url is None:
        raise RuntimeError("PostgreSQL checkpointer selected without AGENT_DATABASE_URL")

    async with AsyncPostgresSaver.from_conn_string(settings.database_url) as checkpointer:
        await checkpointer.setup()
        yield checkpointer
