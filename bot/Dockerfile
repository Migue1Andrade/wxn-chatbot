FROM python:3.14-slim

COPY --from=ghcr.io/astral-sh/uv:0.13.9 /uv /bin/uv

# Modelo de embeddings embutido na imagem: o bot não baixa nada ao iniciar e funciona sem internet.
ARG BOT_EMBEDDING_MODEL=sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app/src \
    PATH=/app/.venv/bin:$PATH \
    ORT_DISABLE_TELEMETRY=1 \
    FASTEMBED_CACHE_PATH=/app/models \
    BOT_EMBEDDING_MODEL=${BOT_EMBEDDING_MODEL} \
    BOT_KNOWLEDGE_DIR=/app/knowledge

WORKDIR /app

COPY pyproject.toml uv.lock .python-version ./
RUN uv sync --frozen --no-dev

RUN useradd --system --uid 10001 bot

RUN /app/.venv/bin/python -c "from fastembed import TextEmbedding; TextEmbedding('${BOT_EMBEDDING_MODEL}')" \
    && chown -R bot:bot /app/models

# Depois do download, o modelo é lido só do disco: nenhuma consulta à internet ao iniciar.
ENV HF_HUB_OFFLINE=1

COPY src ./src
COPY knowledge ./knowledge

USER bot

EXPOSE 8000

CMD ["/app/.venv/bin/uvicorn", "wxn_bot.main:app", "--app-dir", "src", "--host", "0.0.0.0", "--port", "8000"]
