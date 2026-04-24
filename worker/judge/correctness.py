"""Correctness judge: diff stdout vs expected answer."""

import os


def judge_correctness(stdout: str, expected_path: str) -> str:
    if not os.path.exists(expected_path):
        return "AC"  # no expected file → skip check

    with open(expected_path) as f:
        expected = f.read()

    if _normalize(stdout) == _normalize(expected):
        return "AC"
    return "WA"


def _normalize(text: str) -> str:
    return "\n".join(line.rstrip() for line in text.strip().splitlines())
