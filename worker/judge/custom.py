"""Custom checker judge: runs checker.py output expected."""

import subprocess
import os


def judge_custom(stdout: str, expected_path: str, checker_path: str) -> str:
    if not os.path.exists(checker_path):
        return "WA"

    import tempfile
    with tempfile.NamedTemporaryFile(mode="w", suffix=".txt", delete=False) as f:
        f.write(stdout)
        out_file = f.name

    try:
        result = subprocess.run(
            ["python3", checker_path, out_file, expected_path],
            capture_output=True, text=True, timeout=10
        )
        if result.returncode == 0:
            return "AC"
        return "WA"
    except Exception:
        return "WA"
    finally:
        os.unlink(out_file)
