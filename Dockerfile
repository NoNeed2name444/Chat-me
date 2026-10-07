FROM python:3.12-slim

WORKDIR /app

COPY pyproject.toml ./
COPY api ./api
COPY agents ./agents
COPY orchestration ./orchestration
COPY governance ./governance
COPY tools ./tools
COPY evals ./evals
COPY tests ./tests

RUN pip install --no-cache-dir -e ".[dev]"

EXPOSE 8000

CMD ["uvicorn","api.main:app","--host","0.0.0.0","--port","8000"]
