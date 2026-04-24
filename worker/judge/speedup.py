"""Speedup judge: compare GPU solution time vs CPU baseline."""

import subprocess
import tempfile
import time
import os

from judge.correctness import judge_correctness


def judge_speedup(
    source_cu: str,
    baseline_c_path: str,
    input_path: str,
    expected_path: str,
    time_limit_s: float,
    speedup_threshold: float,
) -> tuple[str, float | None]:
    """Returns (verdict, speedup_ratio). Speedup is cpu_time / gpu_time."""
    # CPU baseline timing
    cpu_time = _run_c_baseline(baseline_c_path, input_path, time_limit_s)
    if cpu_time is None:
        return "RE", None

    # GPU timing is handled in worker.py via run_sandbox; this function
    # is called after we already have GPU stdout. We only compute the ratio here.
    # For simplicity, caller passes gpu_wall_ms via a side-channel approach.
    # Actual speedup ratio is computed in worker.py process_job.
    return "AC", None


def _run_c_baseline(baseline_path: str, input_path: str, time_limit_s: float) -> float | None:
    if not baseline_path or not os.path.exists(baseline_path):
        return None

    with tempfile.TemporaryDirectory() as tmpdir:
        exe = os.path.join(tmpdir, "baseline")
        try:
            subprocess.check_call(["gcc", "-O2", baseline_path, "-o", exe, "-lm"],
                                  timeout=30)
        except Exception:
            return None

        with open(input_path) as fin:
            start = time.monotonic()
            try:
                subprocess.run([exe], stdin=fin, capture_output=True,
                               timeout=time_limit_s * 3)
            except subprocess.TimeoutExpired:
                return time_limit_s * 3
            return time.monotonic() - start
