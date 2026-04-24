# Parallel Matrix Multiplication

Implement CUDA matrix multiplication `C = A × B` where A, B, and C are N×N matrices of `float`.

## Input

The first line contains an integer N (1 ≤ N ≤ 1024).  
The next N lines each contain N floats — matrix A.  
The next N lines each contain N floats — matrix B.

## Output

Print matrix C: N lines, each with N floats separated by spaces, rounded to 2 decimal places.

## Scoring

Your solution is accepted if the output matches the reference within 1e-3 tolerance **and** your GPU runtime is at least **10×** faster than the provided CPU baseline.

## Example

**Input:**
```
2
1.0 2.0
3.0 4.0
5.0 6.0
7.0 8.0
```

**Output:**
```
19.00 22.00
43.00 50.00
```
