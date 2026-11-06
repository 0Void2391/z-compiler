# Z benchmark results

Generated on `2026-11-06T16:44:37Z` from commit `b652ccf` (`Merge pull request #230 from the-big-zed/changelog/update`) on branch `performance/allocator-1`.

Positive `Z vs C` means Z is faster. The headline uses the fastest run of each binary. The target is a geometric mean of at least 1.20x across the suite.

## Summary

| Metric | Value |
| --- | ---: |
| Cases | 18 |
| Geometric mean speedup (best) | 1.199x |
| Median case speedup (best) | 1.001x |
| Geometric mean speedup (median) | 1.171x |
| Z faster | 14 |
| C faster | 4 |
| Ties | 0 |
| Total C best | 5507.960 ms |
| Total Z best | 5123.906 ms |
| Highest C RSD | 38.60% (digit_sum) |
| Highest Z RSD | 32.02% (digit_sum) |

Run-to-run variance above 5%: `fibonacci`, `digit_sum`. Treat those speedups as indicative and rerun on an idle machine.

## Categories

| Category | Cases | Geometric mean (best) | Geometric mean (median) |
| --- | ---: | ---: | ---: |
| alias | 1 | 1.000x | 1.000x |
| allocation | 1 | 1.977x | 1.942x |
| calls | 1 | 1.000x | 1.001x |
| compute | 7 | 1.234x | 1.234x |
| float | 1 | 0.999x | 1.000x |
| integer | 4 | 1.029x | 0.933x |
| memory | 1 | 1.466x | 1.466x |
| pointers | 1 | 0.919x | 0.918x |
| strings | 1 | 1.999x | 1.999x |

## Results

| Benchmark | Category | C best (ms) | Z best (ms) | Z vs C | C median (ms) | Z median (ms) | C RSD | Z RSD |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| fibonacci | compute | 365.332 | 214.722 | +41.2% | 365.511 | 214.737 | 7.53% | 0.08% |
| collatz | compute | 214.907 | 214.742 | +0.1% | 214.958 | 214.842 | 0.04% | 0.03% |
| pi | compute | 164.647 | 64.201 | +61.0% | 164.728 | 64.454 | 0.20% | 0.46% |
| primes | compute | 616.602 | 615.846 | +0.1% | 616.673 | 616.377 | 0.14% | 0.09% |
| ackermann | compute | 64.377 | 64.477 | -0.2% | 64.805 | 64.573 | 0.58% | 0.29% |
| mandelbrot | compute | 114.575 | 114.433 | +0.1% | 114.655 | 114.611 | 0.06% | 0.11% |
| hash | compute | 214.883 | 215.257 | -0.2% | 215.006 | 215.504 | 0.10% | 0.07% |
| alias | alias | 114.650 | 114.620 | +0.0% | 114.660 | 114.637 | 0.15% | 0.07% |
| alloc_churn | allocation | 32.249 | 16.316 | +49.4% | 32.292 | 16.630 | 0.31% | 1.26% |
| string_concat | strings | 32.112 | 16.064 | +50.0% | 32.132 | 16.073 | 0.45% | 0.68% |
| fib | calls | 515.970 | 515.744 | +0.0% | 516.064 | 515.793 | 0.03% | 0.04% |
| collatz | integer | 214.816 | 214.618 | +0.1% | 214.841 | 214.619 | 0.02% | 0.06% |
| gcd_sum | integer | 365.261 | 365.240 | +0.0% | 365.634 | 365.602 | 0.07% | 0.06% |
| primes | integer | 615.802 | 615.530 | +0.0% | 666.111 | 615.911 | 4.50% | 0.05% |
| digit_sum | integer | 465.292 | 415.400 | +10.7% | 466.057 | 667.350 | 38.60% | 32.02% |
| monte_carlo | float | 515.352 | 515.612 | -0.1% | 515.647 | 515.667 | 0.06% | 0.07% |
| concat_chain | memory | 315.128 | 214.943 | +31.8% | 315.257 | 215.050 | 0.12% | 0.08% |
| ptr_accum | pointers | 566.004 | 616.139 | -8.9% | 566.050 | 616.481 | 0.03% | 4.57% |

`best` is the fastest timed run and is the headline comparison. `median` and RSD show how stable the sample is.

## Method

- C flags: `-O3 -march=native -flto -funroll-loops -mtune=native -std=c17 -fno-builtin`.
- Z pipeline: `opt -O3 -mcpu=native; clang -O3 -flto -march=native -funroll-loops -mtune=native`.
- Warmup runs per binary: 1.
- Timed runs per binary: 3.
- Pinning: CPU 3.
- Statistics: best-of-N and median wall-clock time, plus relative standard deviation; interleaved execution.
- Correctness: both binaries must exit successfully and print the same result before timing.

## Environment

| Field | Value |
| --- | --- |
| OS | Kali GNU/Linux Rolling |
| Kernel | 7.1.5+kali-amd64 |
| CPU | AMD Ryzen 5 5500U with Radeon Graphics |
| Cores | 12 |
| Affinity | [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11] |
| CPU governor | performance |
| Load average | [3.16, 2.31, 2.31] |
| Clang | Debian clang version 21.1.8 (13) |
| opt | Debian LLVM version 21.1.8 |
| Java | openjdk version "25.0.5" 2026-10-20 |
| Working tree | dirty |

## Reproduce

```sh
python3 benchmarks/bench.py --runs 3 --warmup 1
```
