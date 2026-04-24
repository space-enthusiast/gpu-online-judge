# GPU Programming Online Judge — Architecture Plan

## Context

Building a GPU-focused online judge where users solve CUDA programming problems. The system runs on a personal k3s cloud cluster. A desktop Ubuntu machine with an NVIDIA GPU serves as the execution environment, connected via Kafka (decoupled worker model) so it can be started/stopped independently from the cloud backend. The architecture is designed for extensibility — additional GPU providers (cloud GPU rentals, etc.) can be added by running the same worker anywhere.

---

## Decisions Summary

| Area | Decision |
|---|---|
| GPU framework | CUDA only |
| GPU connectivity | Decoupled Kafka worker (desktop pulls jobs) |
| Message broker | Kafka (KRaft mode, no ZooKeeper) |
| Backend | Spring WebFlux + R2DBC (reactive) |
| Database | PostgreSQL |
| Problem management | Polygon-style Git repo (YAML + Markdown + test cases) |
| Judging | Correctness, speedup, VRAM limit, custom checker |
| Sandboxing | Docker + NVIDIA Container Toolkit |
| Frontend | React + Vite + Monaco Editor |
| Auth | JWT (register/login) |
| Contest mode | None |

---

## System Architecture

```
┌────────────────────────────────────────────────────────────┐
│                     Cloud (k3s)                            │
│                                                            │
│  [React + Vite]  ──REST/SSE──►  [Spring WebFlux API]       │
│                                        │       ▲           │
│                                   R2DBC│       │SSE push   │
│                                        ▼       │           │
│                                   [PostgreSQL] │           │
│                                        │       │           │
│                              publish   │       │           │
│                                        ▼       │           │
│                                    [Kafka]      │           │
│                                   /       \     │           │
│                         gpu-jobs /         \ gpu-results   │
└──────────────────────────────────┼──────────┼─────────────┘
                                   │          │
                              consumed    published
                                   │          │
┌──────────────────────────────────┼──────────┼─────────────┐
│              Desktop (Ubuntu, manual start)                │
│                                   ▼          │             │
│                           [Python GPU Worker]              │
│                                   │                        │
│                     docker run --gpus all ...              │
│                                   │                        │
│                           [CUDA Sandbox Container]         │
│                               nvcc + ./sol                 │
└────────────────────────────────────────────────────────────┘
```

---

## Components

### 1. Spring WebFlux Backend

**Responsibilities:**
- REST API for users, problems, submissions
- JWT auth (issue + validate tokens)
- Publish submission jobs to Kafka `gpu-jobs` topic
- Consume verdicts from Kafka `gpu-results` topic, update DB
- Serve real-time verdict updates to frontend via SSE
- Problem sync: pull from Git repo, parse YAML/MD, store metadata in PostgreSQL

**Key reactive flows:**
- `POST /api/submissions` → save to DB → publish to Kafka → return submissionId
- `GET /api/submissions/{id}/status` → SSE stream → emits verdict when Kafka result arrives
- `POST /api/admin/problems/sync` → pull Git repo → parse problems → upsert DB

**Stack:**
- Spring WebFlux + Spring Boot 3
- Spring Data R2DBC (reactive PostgreSQL driver)
- Spring Kafka (reactive Kafka producer/consumer)
- Spring Security + JWT (jjwt library)
- JGit (for Git repo sync)

**Kafka topics:**
- `gpu-jobs` — backend produces, worker consumes
- `gpu-results` — worker produces, backend consumes

**Job message schema (JSON):**
```json
{
  "submissionId": "uuid",
  "problemId": "string",
  "sourceCode": "string (base64)",
  "testCasesPath": "/problems/matrix-mul/tests/",
  "judgeType": "correctness|speedup|memory|custom",
  "timeLimitMs": 5000,
  "vramLimitMb": 2048,
  "speedupThreshold": 10.0
}
```

**Result message schema (JSON):**
```json
{
  "submissionId": "uuid",
  "verdict": "AC|WA|TLE|MLE|RE|CE",
  "stdout": "string",
  "stderr": "string",
  "wallTimeMs": 1234,
  "peakVramMb": 512,
  "speedup": 12.5,
  "compileError": "string|null"
}
```

---

### 2. PostgreSQL Schema

```sql
-- Users
CREATE TABLE users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username VARCHAR(50) UNIQUE NOT NULL,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(20) DEFAULT 'USER',  -- USER | ADMIN
  created_at TIMESTAMPTZ DEFAULT now()
);

-- Problems (metadata only; files live in Git)
CREATE TABLE problems (
  id VARCHAR(100) PRIMARY KEY,  -- slug from Git e.g. "matrix-multiplication"
  title VARCHAR(255) NOT NULL,
  difficulty VARCHAR(20),       -- EASY | MEDIUM | HARD
  judge_type VARCHAR(20) NOT NULL,
  time_limit_ms INT NOT NULL,
  vram_limit_mb INT,
  speedup_threshold FLOAT,
  git_commit_hash VARCHAR(40),  -- which commit this was synced from
  synced_at TIMESTAMPTZ DEFAULT now()
);

-- Submissions
CREATE TABLE submissions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id),
  problem_id VARCHAR(100) REFERENCES problems(id),
  source_code TEXT NOT NULL,
  language VARCHAR(20) DEFAULT 'CUDA',
  status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING | JUDGING | AC | WA | TLE | MLE | RE | CE
  verdict VARCHAR(20),
  wall_time_ms INT,
  peak_vram_mb INT,
  speedup FLOAT,
  stdout TEXT,
  stderr TEXT,
  submitted_at TIMESTAMPTZ DEFAULT now()
);
```

---

### 3. Problem Git Repository (Polygon-style)

Structure per problem:
```
problems/
└── matrix-multiplication/
    ├── problem.yaml          # metadata
    ├── statement.md          # problem description (rendered in frontend)
    ├── tests/
    │   ├── 01.in
    │   ├── 01.ans
    │   ├── 02.in
    │   └── 02.ans
    ├── checker.py            # optional custom judge
    └── baseline_cpu.c        # optional CPU baseline for speedup judging
```

`problem.yaml` format:
```yaml
id: matrix-multiplication
title: "Parallel Matrix Multiplication"
difficulty: MEDIUM
judge_type: speedup           # correctness | speedup | memory | custom
time_limit_ms: 5000
vram_limit_mb: 2048
speedup_threshold: 10.0
checker: checker.py           # omit if not custom
```

Backend syncs on `POST /api/admin/problems/sync` using JGit. Test case files are stored in a PVC (PersistentVolumeClaim) accessible to the backend pod.

---

### 4. GPU Worker (Python, runs on Desktop Ubuntu)

**Start command:**
```bash
python worker.py --kafka bootstrap.cloud-server:9092 --problems-dir /mnt/problems
```

**Worker loop:**
```python
1. Connect to Kafka, subscribe to `gpu-jobs`
2. Poll for job message
3. Write sourceCode to /tmp/{submissionId}/solution.cu
4. docker run:
     --gpus all
     --network none
     --memory 512m
     --pids-limit 100
     --rm
     -v /tmp/{submissionId}:/workspace
     cuda-sandbox:latest
     bash -c "nvcc /workspace/solution.cu -o /workspace/sol && \
              timeout {timeLimitMs/1000}s /workspace/sol < /workspace/input.txt \
              > /workspace/output.txt 2>/workspace/stderr.txt"
5. Collect: stdout, stderr, exit code, wall time
6. Query `nvidia-smi --query-compute-apps` for peak VRAM
7. Run judge:
     - correctness: diff output.txt vs expected.ans
     - speedup: run baseline_cpu, compare times
     - memory: check peak VRAM vs limit
     - custom: run checker.py output.txt expected.ans
8. Publish verdict to Kafka `gpu-results`
9. Cleanup /tmp/{submissionId}
```

**cuda-sandbox Docker image:**
```dockerfile
FROM nvidia/cuda:12.3.0-devel-ubuntu22.04
RUN apt-get install -y build-essential python3
# no network, minimal tools
```

**Worker dependencies:** `confluent-kafka`, `docker` Python SDK

---

### 5. React + Vite Frontend

**Pages:**
```
/                    → redirect to /problems
/problems            → problem list (title, difficulty, AC rate)
/problems/:id        → problem statement + Monaco editor + submit
/submissions/:id     → verdict page (status, output diff, timing, VRAM, speedup)
/login               → login form
/register            → register form
```

**Key libraries:**
- `@monaco-editor/react` — code editor with CUDA syntax (C++ mode)
- `react-router-dom` — routing
- `axios` — API calls
- `EventSource` (native browser API) — SSE for real-time verdict updates

**Submission flow (frontend):**
1. User clicks Submit → POST /api/submissions → get `submissionId`
2. Open SSE connection to `/api/submissions/{id}/status`
3. Show "Judging..." spinner
4. SSE event fires with verdict → navigate to `/submissions/{id}`

---

### 6. k3s Deployment

**Kubernetes manifests needed:**
```
k8s/
├── namespace.yaml
├── postgres/
│   ├── statefulset.yaml
│   └── service.yaml
├── kafka/
│   ├── statefulset.yaml      # KRaft mode (no ZooKeeper)
│   └── service.yaml
├── backend/
│   ├── deployment.yaml
│   ├── service.yaml
│   └── configmap.yaml        # Kafka bootstrap, DB URL
├── frontend/
│   ├── deployment.yaml
│   └── service.yaml
├── ingress.yaml              # NGINX ingress, routes /api/* to backend
└── pvc.yaml                  # for problem test case files
```

**Kafka in KRaft mode** — single-node for personal use, no ZooKeeper needed.

---

## Submission Lifecycle (End-to-End)

```
User submits code
      │
      ▼
POST /api/submissions
      │
      ├─ Save to DB (status: PENDING)
      ├─ Publish to Kafka `gpu-jobs`
      └─ Return { submissionId }
            │
            ▼
GET /api/submissions/{id}/status  (SSE)
            │
            │          [Desktop GPU Worker polling Kafka]
            │                     │
            │          consume job message
            │                     │
            │          docker run cuda-sandbox
            │                     │
            │          collect verdict
            │                     │
            │          publish to `gpu-results`
            │                     │
            ▼                     ▼
[Backend consumes gpu-results]
      │
      ├─ Update DB (verdict, timing, VRAM)
      └─ Push SSE event to waiting client
            │
            ▼
Frontend shows verdict
```

---

## Repository Structure

```
gpu-oj/
├── backend/                  # Spring WebFlux (Gradle or Maven)
│   └── src/main/java/...
├── worker/                   # Python GPU worker
│   ├── worker.py
│   ├── judge/
│   │   ├── correctness.py
│   │   ├── speedup.py
│   │   └── custom.py
│   └── Dockerfile            # cuda-sandbox image
├── frontend/                 # React + Vite
│   └── src/
│       ├── pages/
│       └── components/
├── problems/                 # Git-managed problem bank
│   └── matrix-multiplication/
└── k8s/                      # Kubernetes manifests
```

---

## Verification Plan

1. **Local backend test:** Run Spring WebFlux + PostgreSQL + Kafka locally via Docker Compose. Test auth endpoints and submission creation.
2. **Worker test:** On Ubuntu desktop, run `worker.py` pointing to local Kafka. Submit a simple CUDA hello-world. Verify verdict returned.
3. **SSE test:** Use `curl -N` to connect to SSE endpoint, submit a job, verify event is pushed when worker returns verdict.
4. **Frontend test:** `vite dev` locally, connect to local backend, submit a problem end-to-end.
5. **k3s deploy:** Apply manifests, verify pods Running, test ingress routing.
6. **GPU worker remote test:** With k3s running in cloud and worker on Ubuntu desktop, submit a real CUDA problem end-to-end.
