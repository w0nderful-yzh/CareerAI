from functools import lru_cache
from typing import Literal, Self

from pydantic import AnyHttpUrl, Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env", "../.env"),
        env_prefix="AGENT_",
        extra="ignore",
    )

    app_name: str = "CareerAI Agent Service"
    environment: str = "local"
    host: str = "0.0.0.0"
    port: int = Field(default=8000, ge=1, le=65535)
    log_level: str = "INFO"
    careerai_api_base_url: AnyHttpUrl = AnyHttpUrl("http://localhost:8080")
    model_config_base_url: AnyHttpUrl = AnyHttpUrl("http://localhost:8082")
    internal_service_token: SecretStr | None = None
    request_timeout_seconds: float = Field(default=30.0, gt=0, le=300)
    checkpointer_backend: Literal["memory", "postgres"] = "memory"
    database_url: str | None = None

    @model_validator(mode="after")
    def validate_checkpointer(self) -> Self:
        if self.checkpointer_backend == "postgres" and not self.database_url:
            raise ValueError("AGENT_DATABASE_URL is required for the postgres checkpointer")
        return self


@lru_cache
def get_settings() -> Settings:
    return Settings()
