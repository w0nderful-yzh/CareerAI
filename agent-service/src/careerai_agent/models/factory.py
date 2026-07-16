import asyncio

from langchain_core.language_models.chat_models import BaseChatModel
from langchain_openai import ChatOpenAI

from careerai_agent.models.config_client import ModelConfigSource


class DynamicChatModelFactory:
    def __init__(
        self,
        config_source: ModelConfigSource,
        timeout_seconds: float,
    ) -> None:
        self._config_source = config_source
        self._timeout_seconds = timeout_seconds
        self._cache_key: tuple[str, str] | None = None
        self._cached_model: BaseChatModel | None = None
        self._lock = asyncio.Lock()

    async def get_chat_model(self) -> BaseChatModel:
        config = await self._config_source.get_model_config()
        cache_key = (config.provider_id, config.config_version)
        if self._cache_key == cache_key and self._cached_model is not None:
            return self._cached_model

        async with self._lock:
            if self._cache_key == cache_key and self._cached_model is not None:
                return self._cached_model
            model = ChatOpenAI(
                model=config.model,
                base_url=config.base_url,
                api_key=config.api_key,
                temperature=config.temperature,
                timeout=self._timeout_seconds,
                max_retries=1,
            )
            self._cache_key = cache_key
            self._cached_model = model
            return model
