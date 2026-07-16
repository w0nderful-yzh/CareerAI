from langchain_core.language_models.chat_models import BaseChatModel
from pydantic import SecretStr

from careerai_agent.models.config import AgentModelRuntimeConfig
from careerai_agent.models.factory import DynamicChatModelFactory


class FakeConfigSource:
    def __init__(self) -> None:
        self.version = "v1"

    async def get_model_config(self) -> AgentModelRuntimeConfig:
        return AgentModelRuntimeConfig(
            providerId="dashscope",
            baseUrl="https://example.test/v1",
            apiKey=SecretStr("secret"),
            model="qwen-plus",
            temperature=0.2,
            configVersion=self.version,
        )


async def test_factory_reuses_same_config_and_rebuilds_after_version_change() -> None:
    source = FakeConfigSource()
    factory = DynamicChatModelFactory(source, timeout_seconds=5)

    first = await factory.get_chat_model()
    second = await factory.get_chat_model()
    source.version = "v2"
    third = await factory.get_chat_model()

    assert isinstance(first, BaseChatModel)
    assert first is second
    assert third is not first
