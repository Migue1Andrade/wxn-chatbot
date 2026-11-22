"""Embeddings: transformam texto em vetores para a busca por sentido.

O modelo roda no próprio container (ONNX, sem GPU) e vem embutido na imagem: nenhum texto de cliente sai do
sistema e nada é baixado ao iniciar. Trocar de modelo é trocar `BOT_EMBEDDING_MODEL` e reprocessar a base.
"""

from typing import Protocol

import numpy as np
import onnxruntime
from fastembed import TextEmbedding

# O onnxruntime (motor que roda o modelo) envia telemetria de uso à Microsoft por padrão. Desligada aqui e por
# ORT_DISABLE_TELEMETRY na imagem: com o modelo local, nada deve sair do container.
onnxruntime.disable_telemetry_events()


class Embedder(Protocol):
    model: str

    def embed(self, texts: list[str]) -> list[np.ndarray]: ...


class FastEmbedEmbedder:
    def __init__(self, model: str, cache_dir: str | None = None) -> None:
        self.model = model
        self._engine = TextEmbedding(model, cache_dir=cache_dir)

    def embed(self, texts: list[str]) -> list[np.ndarray]:
        return list(self._engine.embed(texts))
