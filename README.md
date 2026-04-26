# GPU Online Judge (Helios)

A GPU-focused competitive programming judge. Users submit CUDA code; the system compiles, sandboxes, and evaluates it for correctness, speed-up over a CPU baseline, memory usage, or custom criteria.

## Architecture

```
Browser → React (Nginx :5173)
             ↓ /api proxy
         Spring WebFlux (Kotlin :8080)
          ↙               ↘
    PostgreSQL          Kafka :9092
     :5432           ↙           ↘
               gpu-jobs       gpu-results
                  ↓                ↑
             Python GPU Worker
             (requires NVIDIA GPU)
```

| Service  | Technology                        | Port |
|----------|-----------------------------------|------|
| Frontend | React 18 + Vite, served by Nginx  | 5173 |
| Backend  | Kotlin, Spring WebFlux, R2DBC     | 8080 |
| Database | PostgreSQL 16                     | 5432 |
| Broker   | Apache Kafka 3.7 (KRaft, no ZK)   | 9092 |
| Worker   | Python 3.12 + Docker SDK + CUDA   | —    |

## Prerequisites

- [Docker](https://docs.docker.com/get-docker/) ≥ 24
- [Docker Compose](https://docs.docker.com/compose/install/) ≥ 2 (ships with Docker Desktop)

The GPU worker additionally requires an NVIDIA GPU with `nvidia-container-toolkit` installed, but it is optional for running the rest of the stack.

## Quick Start

```bash
git clone <repo-url>
cd gpu-online-judge
docker compose up --build
```

On first run Docker will:
1. Pull `postgres:16-alpine` and `apache/kafka:3.7.0`
2. Build the backend JAR with Gradle (inside the JDK container, ~1 min)
3. Build the frontend with Vite (inside the Node container, ~15 s)
4. Start all four services

Once you see `Started HeliosApplicationKt` in the logs the stack is ready.

Problems are not loaded automatically. After the stack is up, sync them once:

```bash
# Register an admin user (first time only)
curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","email":"admin@example.com","password":"admin123"}'

# Promote to admin via the database
docker exec gpu-online-judge-postgres-1 \
  psql -U gpuoj -d gpuoj -c "UPDATE users SET role='ADMIN' WHERE username='admin';"

# Login and sync problems
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

curl -X POST http://localhost:8080/api/admin/problems/sync \
  -H "Authorization: Bearer $TOKEN"
```

| URL                        | Description            |
|----------------------------|------------------------|
| http://localhost:5173      | Frontend               |
| http://localhost:8080/api  | Backend API            |

To stop:

```bash
docker compose down          # stop and remove containers
docker compose down -v       # also remove the postgres volume
```

## Smoke-test the API

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","email":"alice@example.com","password":"secret123"}'

# Login → get JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"secret123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# List problems
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/problems

# Submit code
curl -X POST http://localhost:8080/api/submissions \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"problemId":"matrix-multiplication","language":"CUDA","sourceCode":"__global__ void kernel(){}"}'
```

## API Reference

### Auth

| Method | Path                    | Body                                      | Returns              |
|--------|-------------------------|-------------------------------------------|----------------------|
| POST   | `/api/auth/register`    | `{username, email, password}`             | `{token, username, role}` |
| POST   | `/api/auth/login`       | `{username, password}`                    | `{token, username, role}` |

### Problems

| Method | Path                    | Auth | Description           |
|--------|-------------------------|------|-----------------------|
| GET    | `/api/problems`         | JWT  | List all problems     |
| GET    | `/api/problems/{id}`    | JWT  | Get problem detail    |

### Submissions

| Method | Path                          | Auth  | Description                        |
|--------|-------------------------------|-------|------------------------------------|
| POST   | `/api/submissions`            | JWT   | Submit code                        |
| GET    | `/api/submissions/{id}`       | JWT   | Get submission result              |
| GET    | `/api/submissions/{id}/status`| JWT   | Stream status via SSE              |

### Admin

| Method | Path                          | Auth  | Description                        |
|--------|-------------------------------|-------|------------------------------------|
| POST   | `/api/admin/problems/sync`    | JWT   | Sync problems from Git repo        |

## Environment Variables

All variables have working defaults for local development. Override them for production.

| Variable             | Default                                    | Description                              |
|----------------------|--------------------------------------------|------------------------------------------|
| `DB_HOST`            | `localhost`                                | PostgreSQL host                          |
| `DB_PORT`            | `5432`                                     | PostgreSQL port                          |
| `DB_NAME`            | `gpuoj`                                    | Database name                            |
| `DB_USER`            | `gpuoj`                                    | Database user                            |
| `DB_PASS`            | `gpuoj`                                    | Database password                        |
| `KAFKA_BOOTSTRAP`    | `localhost:9092`                           | Kafka bootstrap servers                  |
| `JWT_SECRET`         | `change-me-in-production-32-chars-min!!`   | HMAC secret — **change in production**   |
| `JWT_EXPIRY_MS`      | `86400000`                                 | Token TTL (24 h)                         |
| `PROBLEMS_LOCAL_PATH`| `/problems`                                | Path to problem definitions on disk      |
| `PROBLEMS_GIT_URL`   | *(empty)*                                  | Optional Git URL to sync problems from   |

## Problems

Problems live under `problems/<id>/` and consist of:

```
problems/
└── matrix-multiplication/
    ├── problem.yaml       # metadata (title, difficulty, judge_type, limits)
    ├── statement.md       # problem statement
    ├── baseline_cpu.c     # CPU reference solution (used for speedup judging)
    └── tests/             # input/output test cases
```

To sync a remote problem set:

```bash
curl -X POST http://localhost:8080/api/admin/problems/sync \
  -H "Authorization: Bearer $TOKEN"
```

## GPU Worker (optional)

The worker compiles and runs submitted CUDA code in an isolated Docker container with GPU access.

**Requirements:** NVIDIA GPU + [nvidia-container-toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html)

```bash
cd worker
pip install -r requirements.txt

python worker.py \
  --kafka localhost:9092 \
  --problems-dir ./problems
```

| Flag              | Default        | Description                  |
|-------------------|----------------|------------------------------|
| `--kafka`         | `localhost:9092` | Kafka bootstrap servers     |
| `--problems-dir`  | `/problems`    | Path to problem definitions  |
| `--jobs-topic`    | `gpu-jobs`     | Kafka topic to consume       |
| `--results-topic` | `gpu-results`  | Kafka topic to produce       |
| `--group-id`      | `gpu-worker`   | Kafka consumer group         |

## Kubernetes

Manifests are in `k8s/`. Apply in order:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/pvc.yaml
kubectl apply -f k8s/postgres/
kubectl apply -f k8s/kafka/
kubectl apply -f k8s/backend/
kubectl apply -f k8s/frontend/
kubectl apply -f k8s/ingress.yaml
```
