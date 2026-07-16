from pydantic import BaseModel, ConfigDict, Field, SecretStr


class AgentModelRuntimeConfig(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    provider_id: str = Field(alias="providerId", min_length=1)
    base_url: str = Field(alias="baseUrl", min_length=1)
    api_key: SecretStr = Field(alias="apiKey")
    model: str = Field(min_length=1)
    temperature: float | None = None
    config_version: str = Field(alias="configVersion", min_length=1)
