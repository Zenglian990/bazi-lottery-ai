# 生产级容器镜像（统一用于 Docker Compose 与 Google Cloud Run 部署）
FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app \
    ENV=production \
    PORT=8080 \
    TZ=Asia/Shanghai \
    LOTTERY_DB_PATH=/app/data/lottery.db

WORKDIR /app

# 仅安装最小系统时区数据（健康检查改用标准库 urllib，去除多余 curl 攻击面）
RUN apt-get update && apt-get install -y --no-install-recommends \
    tzdata \
    && rm -rf /var/lib/apt/lists/*

COPY backend/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

RUN groupadd --gid 10001 appuser \
    && useradd --uid 10001 --gid appuser --shell /bin/false --create-home appuser \
    && mkdir -p /app/backend/app/db /app/data \
    && chown -R appuser:appuser /app

COPY --chown=appuser:appuser backend/ /app/backend/

RUN chmod -R a+rX /app/backend \
    && chown -R appuser:appuser /app/backend/app/db /app/data

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=8s --start-period=12s --retries=3 \
    CMD python -c "import os, urllib.request; urllib.request.urlopen(f'http://127.0.0.1:{os.getenv(\"PORT\", \"8080\")}/api/health', timeout=5).read()" || exit 1

CMD ["sh", "-c", "exec uvicorn backend.app.main:app --host 0.0.0.0 --port ${PORT:-8080} --proxy-headers --forwarded-allow-ips='*'"]
