# Production Container Image for Bazi Lottery Backend
# Base image: Python 3.12-slim for optimal footprint and security
FROM python:3.12-slim

# Set environment variables
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app \
    PORT=8000 \
    LOTTERY_DB_PATH=/app/data/lottery.db

# Set work directory
WORKDIR /app

# Install system dependencies (curl for health check probe)
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy dependency specifications and install Python packages
COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt

# Copy backend application source and data assets
COPY backend/ /app/backend/
COPY data/ /app/data/

# Ensure persistent data directory exists
RUN mkdir -p /app/data

# Expose FastAPI service port
EXPOSE 8000

# Healthcheck directive querying /api/health
HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8000/api/health || exit 1

# Entrypoint / CMD: Start FastAPI app with Uvicorn
CMD ["uvicorn", "backend.app.main:app", "--host", "0.0.0.0", "--port", "8000"]
