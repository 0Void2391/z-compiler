# Z benchmark results

Generated on `2026-11-03T15:35:16Z` from commit `39ff09e` (`feat: added align system for more performance`) on branch `feat/benchmark-suite`.

Positive `Z vs C` means Z is faster. The headline uses the fastest run of each binary. The target is a geometric mean of at least 1.20x across the suite.

## Summary

| Metric | Value |
| --- | ---: |
| Cases | 10 |
| Geometric mean speedup (best) | 1.100x |
| Median case speedup (best) | 1.000x |
| Geometric mean speedup (median) | 1.099x |
| Z faster | 5 |
| C faster | 5 |
| Ties | 0 |
| Total C best | 8715.312 ms |
| Total Z best | 8635.941 ms |
| Highest C RSD | 7.12% (collatz) |
| Highest Z RSD | 7.14% (collatz) |

Run-to-run variance above 5%: `fibonacci`, `collatz`. Treat those speedups as indicative and rerun on an idle machine.

## Categories

| Category | Cases | Geometric mean (best) | Geometric mean (median) |
| --- | ---: | ---: | ---: |
| alias | 1 | 1.000x | 1.001x |
| allocation | 1 | 1.994x | 1.968x |
| compute | 7 | 1.038x | 1.038x |
| strings | 1 | 1.001x | 1.000x |

## Results

| Benchmark | Category | C best (ms) | Z best (ms) | Z vs C | C median (ms) | Z median (ms) | C RSD | Z RSD |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| fibonacci | compute | 716.530 | 717.361 | -0.1% | 816.835 | 816.818 | 5.37% | 4.45% |
| collatz | compute | 215.400 | 215.313 | +0.0% | 215.673 | 215.587 | 7.12% | 7.14% |
| pi | compute | 265.367 | 265.374 | -0.0% | 265.665 | 265.622 | 0.09% | 0.07% |
| primes | compute | 6376.330 | 6378.429 | -0.0% | 6382.521 | 6383.954 | 0.04% | 0.06% |
| ackermann | compute | 114.825 | 115.182 | -0.3% | 115.243 | 115.389 | 0.28% | 0.18% |
| mandelbrot | compute | 215.598 | 165.434 | +23.3% | 215.653 | 165.666 | 0.11% | 0.08% |
| hash | compute | 415.544 | 415.774 | -0.1% | 415.912 | 415.935 | 0.09% | 0.03% |
| alias | alias | 215.209 | 215.142 | +0.0% | 215.641 | 215.466 | 0.14% | 0.16% |
| alloc_churn | allocation | 65.070 | 32.631 | +49.9% | 65.254 | 33.159 | 0.23% | 0.77% |
| string_concat | strings | 115.440 | 115.301 | +0.1% | 115.546 | 115.551 | 0.19% | 0.20% |

`best` is the fastest timed run and is the headline comparison. `median` and RSD show how stable the sample is.

## Method

- C flags: `-O3 -march=native -flto -funroll-loops -mtune=native -std=c17 -fno-builtin`.
- Z pipeline: `opt -O3 -mcpu=native; clang -O3 -flto -march=native -funroll-loops -mtune=native`.
- Warmup runs per binary: 2.
- Timed runs per binary: 10.
- Pinning: CPU 1.
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
| CPU governor | powersave |
| Load average | [1.38, 1.98, 2.27] |
| Clang | Debian clang version 21.1.8 (13) |
| opt | Debian LLVM version 21.1.8 |
| Java | openjdk version "25.0.5" 2026-10-20 |
| Working tree | dirty |

## Reproduce

```sh
python3 benchmarks/bench.py --runs 10 --warmup 2
```
