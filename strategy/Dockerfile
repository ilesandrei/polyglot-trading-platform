FROM python:3.11-slim

WORKDIR /app

# Install system utilities if needed
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Install python dependencies
COPY strategy/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy proto definitions from workspace root
COPY proto/ /proto/

# Copy strategy source code
COPY strategy/ /app/

# Compile protobuf stubs
RUN python generate_proto.py

# Set python path
ENV PYTHONPATH=/app

CMD ["python", "src/main.py"]
