#!/usr/bin/env python3
"""GPU Worker — polls Kafka gpu-jobs, runs CUDA sandbox, publishes verdict to gpu-results."""

import argparse
import base64
import json
import logging
import os
import shutil
import subprocess
import tempfile
import time

import docker
from confluent_kafka import Consumer, Producer, KafkaError

from judge.correctness import judge_correctness
from judge.speedup import judge_speedup
from judge.custom import judge_custom

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger(__name__)

SANDBOX_IMAGE = "cuda-sandbox:latest"


def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--kafka", default="localhost:9092", help="Kafka bootstrap servers")
    p.add_argument("--problems-dir", default="/problems", help="Path to problems directory")
    p.add_argument("--jobs-topic", default="gpu-jobs")
    p.add_argument("--results-topic", default="gpu-results")
    p.add_argument("--group-id", default="gpu-worker")
    return p.parse_args()


def create_consumer(bootstrap, group_id, topic):
    c = Consumer({
        "bootstrap.servers": bootstrap,
        "group.id": group_id,
        "auto.offset.reset": "earliest",
    })
    c.subscribe([topic])
    return c


def create_producer(bootstrap):
    return Producer({"bootstrap.servers": bootstrap})


def run_sandbox(docker_client, submission_id, source_cu, input_path, time_limit_s):
    """Compile and run CUDA solution in isolated container. Returns (stdout, stderr, exit_code, wall_ms)."""
    workdir = tempfile.mkdtemp(prefix=f"gpuoj_{submission_id}_")
    try:
        sol_cu = os.path.join(workdir, "solution.cu")
        with open(sol_cu, "w") as f:
            f.write(source_cu)

        if input_path and os.path.exists(input_path):
            shutil.copy(input_path, os.path.join(workdir, "input.txt"))
        else:
            open(os.path.join(workdir, "input.txt"), "w").close()

        cmd = (
            f"nvcc /workspace/solution.cu -o /workspace/sol 2>/workspace/compile.err && "
            f"timeout {time_limit_s}s /workspace/sol < /workspace/input.txt "
            f"> /workspace/output.txt 2>/workspace/stderr.txt; "
            f"echo $? > /workspace/exit_code.txt"
        )

        start = time.monotonic()
        container = docker_client.containers.run(
            SANDBOX_IMAGE,
            command=["bash", "-c", cmd],
            volumes={workdir: {"bind": "/workspace", "mode": "rw"}},
            network_disabled=True,
            mem_limit="512m",
            pids_limit=100,
            remove=True,
            detach=False,
            device_requests=[docker.types.DeviceRequest(count=-1, capabilities=[["gpu"]])],
        )
        wall_ms = int((time.monotonic() - start) * 1000)

        compile_err = _read_file(os.path.join(workdir, "compile.err"))
        if compile_err:
            return "", compile_err, 1, wall_ms, True

        stdout = _read_file(os.path.join(workdir, "output.txt"))
        stderr = _read_file(os.path.join(workdir, "stderr.txt"))
        exit_code_str = _read_file(os.path.join(workdir, "exit_code.txt")).strip()
        exit_code = int(exit_code_str) if exit_code_str.isdigit() else 1

        return stdout, stderr, exit_code, wall_ms, False
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


def _read_file(path, default=""):
    try:
        with open(path) as f:
            return f.read()
    except FileNotFoundError:
        return default


def query_peak_vram(submission_id):
    """Query nvidia-smi for peak VRAM of recent compute process (best-effort)."""
    try:
        out = subprocess.check_output(
            ["nvidia-smi", "--query-compute-apps=used_memory", "--format=csv,noheader,nounits"],
            text=True
        )
        values = [int(v.strip()) for v in out.strip().splitlines() if v.strip().isdigit()]
        return max(values) if values else None
    except Exception:
        return None


def process_job(job, docker_client, problems_dir, producer, results_topic):
    submission_id = job["submissionId"]
    problem_id = job["problemId"]
    judge_type = job.get("judgeType", "CORRECTNESS").upper()
    time_limit_ms = job.get("timeLimitMs", 5000)
    vram_limit_mb = job.get("vramLimitMb")
    speedup_threshold = job.get("speedupThreshold")

    source_cu = base64.b64decode(job["sourceCode"]).decode("utf-8")
    tests_dir = os.path.join(problems_dir, problem_id, "tests")
    time_limit_s = time_limit_ms / 1000.0

    result = {
        "submissionId": submission_id,
        "verdict": "RE",
        "stdout": "",
        "stderr": "",
        "wallTimeMs": None,
        "peakVramMb": None,
        "speedup": None,
        "compileError": None,
    }

    try:
        # Find test cases
        test_inputs = sorted([
            f for f in os.listdir(tests_dir) if f.endswith(".in")
        ]) if os.path.isdir(tests_dir) else []

        if not test_inputs:
            # Run without test cases (smoke test)
            stdout, stderr, exit_code, wall_ms, is_ce = run_sandbox(
                docker_client, submission_id, source_cu, None, time_limit_s
            )
            result.update(stdout=stdout, stderr=stderr, wallTimeMs=wall_ms)
            if is_ce:
                result["verdict"] = "CE"
                result["compileError"] = stderr
            elif exit_code == 124:
                result["verdict"] = "TLE"
            elif exit_code != 0:
                result["verdict"] = "RE"
            else:
                result["verdict"] = "AC"
        else:
            verdicts = []
            total_wall_ms = 0
            for test_file in test_inputs:
                base = test_file[:-3]
                input_path = os.path.join(tests_dir, test_file)
                expected_path = os.path.join(tests_dir, base + ".ans")

                stdout, stderr, exit_code, wall_ms, is_ce = run_sandbox(
                    docker_client, submission_id, source_cu, input_path, time_limit_s
                )
                total_wall_ms += wall_ms

                if is_ce:
                    result["verdict"] = "CE"
                    result["compileError"] = stderr
                    result["wallTimeMs"] = wall_ms
                    producer.produce(results_topic, key=submission_id, value=json.dumps(result))
                    producer.flush()
                    return

                if exit_code == 124:
                    verdicts.append("TLE")
                elif exit_code != 0:
                    verdicts.append("RE")
                elif judge_type == "CORRECTNESS":
                    v = judge_correctness(stdout, expected_path)
                    verdicts.append(v)
                elif judge_type == "SPEEDUP":
                    baseline_path = os.path.join(problems_dir, problem_id, "baseline_cpu.c")
                    v, speedup = judge_speedup(
                        source_cu, baseline_path, input_path, expected_path,
                        time_limit_s, speedup_threshold
                    )
                    verdicts.append(v)
                    result["speedup"] = speedup
                elif judge_type == "CUSTOM":
                    checker_path = os.path.join(problems_dir, problem_id, "checker.py")
                    v = judge_custom(stdout, expected_path, checker_path)
                    verdicts.append(v)
                elif judge_type == "MEMORY":
                    peak = query_peak_vram(submission_id)
                    result["peakVramMb"] = peak
                    if vram_limit_mb and peak and peak > vram_limit_mb:
                        verdicts.append("MLE")
                    else:
                        v = judge_correctness(stdout, expected_path)
                        verdicts.append(v)

            result["wallTimeMs"] = total_wall_ms
            result["stdout"] = stdout
            result["stderr"] = stderr
            result["peakVramMb"] = query_peak_vram(submission_id)

            if "CE" in verdicts:
                result["verdict"] = "CE"
            elif "TLE" in verdicts:
                result["verdict"] = "TLE"
            elif "MLE" in verdicts:
                result["verdict"] = "MLE"
            elif "RE" in verdicts:
                result["verdict"] = "RE"
            elif "WA" in verdicts:
                result["verdict"] = "WA"
            else:
                result["verdict"] = "AC"

    except Exception as e:
        log.error("Error processing submission %s: %s", submission_id, e, exc_info=True)
        result["verdict"] = "RE"
        result["stderr"] = str(e)

    log.info("Verdict for %s: %s", submission_id, result["verdict"])
    producer.produce(results_topic, key=submission_id, value=json.dumps(result))
    producer.flush()


def main():
    args = parse_args()
    docker_client = docker.from_env()
    consumer = create_consumer(args.kafka, args.group_id, args.jobs_topic)
    producer = create_producer(args.kafka)

    log.info("Worker started, listening on %s/%s", args.kafka, args.jobs_topic)

    try:
        while True:
            msg = consumer.poll(timeout=1.0)
            if msg is None:
                continue
            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    continue
                log.error("Kafka error: %s", msg.error())
                continue

            try:
                job = json.loads(msg.value().decode("utf-8"))
                log.info("Processing submission %s (problem: %s)", job.get("submissionId"), job.get("problemId"))
                process_job(job, docker_client, args.problems_dir, producer, args.results_topic)
            except Exception as e:
                log.error("Failed to process message: %s", e, exc_info=True)
    finally:
        consumer.close()


if __name__ == "__main__":
    main()
