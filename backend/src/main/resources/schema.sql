CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'USER',
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS problems (
    id VARCHAR(100) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    difficulty VARCHAR(20),
    judge_type VARCHAR(20) NOT NULL,
    time_limit_ms INT NOT NULL,
    vram_limit_mb INT,
    speedup_threshold FLOAT,
    git_commit_hash VARCHAR(40),
    synced_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    problem_id VARCHAR(100) REFERENCES problems(id),
    source_code TEXT NOT NULL,
    language VARCHAR(20) DEFAULT 'CUDA',
    status VARCHAR(20) DEFAULT 'PENDING',
    verdict VARCHAR(20),
    wall_time_ms INT,
    peak_vram_mb INT,
    speedup FLOAT,
    stdout TEXT,
    stderr TEXT,
    submitted_at TIMESTAMPTZ DEFAULT now()
);
