# Production Container Image for Bazi Lottery Backend
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app \
    ENV=production \
    PORT=8080 \
    TZ=Asia/Shanghai \
    LOTTERY_DB_PATH=/app/backend/app/db/lottery.db

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    tzdata \
    && rm -rf /var/lib/apt/lists/*

COPY backend/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

RUN groupadd --gid 10001 appuser \
    && useradd --uid 10001 --gid appuser --shell /bin/false --create-home appuser \
    && mkdir -p /app/backend/app/db /app/data \
    && chown -R appuser:appuser /app

COPY --chown=appuser:appuser backend/ /app/backend/

RUN chmod -R 755 /app/backend \
    && chown -R appuser:appuser /app/backend/app/db /app/data

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:${PORT:-8080}/api/health || exit 1

CMD ["sh", "-c", "exec uvicorn backend.app.main:app --host 0.0.0.0 --port ${PORT:-8080}"]
