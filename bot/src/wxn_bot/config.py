from functools import lru_cache
from pathlib import Path

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

# Multilíngue e pequeno (220 MB); escolhido no ticket 06 junto com a busca híbrida.
DEFAULT_EMBEDDING_MODEL = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"


class Settings(BaseSettings):
    """Configuração lida das variáveis de ambiente. Segredos nunca têm valor padrão."""

    model_config = SettingsConfigDict(extra="ignore")

    bot_api_key: SecretStr = Field(min_length=32)
    anthropic_api_key: SecretStr = Field(min_length=1)
    bot_model: str = Field(min_length=1)
    bot_model_timeout_seconds: float = Field(default=20.0, gt=0)
    # Limite (estimado) de tokens do histórico enviado ao modelo; as mensagens mais antigas saem primeiro.
    bot_history_max_tokens: int = Field(default=4000, ge=0)
    # Modelo tentado quando o principal falha ou dá uma resposta reprovada. Vazio desliga o segundo modelo.
    bot_fallback_model: str = ""
    # Mensagens maiores vão direto para um humano, sem chamar o modelo.
    bot_max_input_chars: int = Field(default=2000, gt=0)
    # Banco vetorial exclusivo do bot (pgvector).
    bot_database_url: SecretStr = Field(min_length=1)
    # Modelo de embeddings local; trocar exige reprocessar a base (`python -m wxn_bot.knowledge_cli ingest`).
    bot_embedding_model: str = Field(default=DEFAULT_EMBEDDING_MODEL, min_length=1)
    bot_knowledge_dir: Path = Path("knowledge")
    # Quantas rodadas de ferramentas o agente pode fazer antes de ter que decidir com o que já tem.
    bot_max_tool_steps: int = Field(default=3, ge=1, le=10)

    def models(self) -> list[str]:
        return [self.bot_model, self.bot_fallback_model] if self.bot_fallback_model else [self.bot_model]


@lru_cache
def get_settings() -> Settings:
    return Settings()
