# Vector Addition

Given two vectors **A** and **B** of N floats, compute their element-wise sum **C = A + B** using CUDA.

## Input

The first line contains an integer N (1 ≤ N ≤ 10,000,000).  
The second line contains N floats — vector A.  
The third line contains N floats — vector B.

## Output

Print N floats on a single line separated by spaces, rounded to 2 decimal places.

## Example

**Input:**
```
5
1.0 2.0 3.0 4.0 5.0
10.0 20.0 30.0 40.0 50.0
```

**Output:**
```
11.00 22.00 33.00 44.00 55.00
```

## Notes

- Use `cudaMalloc` / `cudaMemcpy` to move data to and from the device.
- Launch enough threads to cover all N elements. A block size of 256 is a good starting point.
- Results must match the reference within an absolute tolerance of **1e-3**.
