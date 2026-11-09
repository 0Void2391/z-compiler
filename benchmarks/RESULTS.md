# Z benchmark results

Generated on `2026-11-09T12:43:40Z` from commit `a36a272` (`Merge pull request #221 from the-big-zed/feat/benchmark-suite`) on branch `main`.

Positive `Z vs C` means Z is faster. The headline uses the fastest run of each binary. The target is a geometric mean of at least 1.20x across the suite.

## Summary

| Metric | Value |
| --- | ---: |
| Cases | 10 |
| Geometric mean speedup (best) | 1.170x |
| Median case speedup (best) | 1.001x |
| Geometric mean speedup (median) | 1.169x |
| Z faster | 7 |
| C faster | 3 |
| Ties | 0 |
| Total C best | 2464.025 ms |
| Total Z best | 2365.074 ms |
| Highest C RSD | 0.35% (alloc_churn) |
| Highest Z RSD | 35.15% (string_concat) |

Run-to-run variance above 5%: `string_concat`. Treat those speedups as indicative and rerun on an idle machine.

## Categories

| Category | Cases | Geometric mean (best) | Geometric mean (median) |
| --- | ---: | ---: | ---: |
| alias | 1 | 1.000x | 1.000x |
| allocation | 1 | 2.017x | 2.008x |
| compute | 7 | 1.025x | 1.025x |
| strings | 1 | 1.999x | 1.993x |

## Results

| Benchmark | Category | C best (ms) | Z best (ms) | Z vs C | C median (ms) | Z median (ms) | C RSD | Z RSD |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| fibonacci | compute | 314.681 | 264.574 | +15.9% | 314.802 | 264.766 | 0.05% | 0.05% |
| collatz | compute | 164.561 | 164.516 | +0.0% | 164.614 | 164.600 | 0.03% | 0.05% |
| pi | compute | 114.425 | 114.332 | +0.1% | 114.532 | 114.569 | 0.06% | 0.09% |
| primes | compute | 1267.100 | 1266.345 | +0.1% | 1267.685 | 1267.657 | 0.02% | 0.04% |
| ackermann | compute | 64.139 | 64.095 | +0.1% | 64.216 | 64.208 | 0.06% | 0.10% |
| mandelbrot | compute | 114.319 | 114.436 | -0.1% | 114.484 | 114.512 | 0.08% | 0.04% |
| hash | compute | 214.513 | 214.517 | -0.0% | 214.573 | 214.587 | 0.04% | 0.05% |
| alias | alias | 114.439 | 114.451 | -0.0% | 114.588 | 114.555 | 0.07% | 0.05% |
| alloc_churn | allocation | 31.875 | 15.802 | +50.4% | 32.034 | 15.952 | 0.35% | 0.64% |
| string_concat | strings | 63.973 | 32.007 | +50.0% | 64.145 | 32.189 | 0.21% | 35.15% |

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
| OS | Ubuntu 26.04.1 LTS |
| Kernel | 7.0.0-1019-azure |
| CPU | AMD EPYC 7763 64-Core Processor |
| Cores | 4 |
| Affinity | [0, 1, 2, 3] |
| CPU governor | unavailable |
| Load average | [1.01, 0.46, 0.2] |
| Clang | Ubuntu clang version 22.1.2 (1ubuntu1) |
| opt | Ubuntu LLVM version 22.1.2 |
| Java | openjdk version "21.0.13" 2026-10-20 LTS |
| Working tree | clean |

## Reproduce

```sh
python3 benchmarks/bench.py --runs 10 --warmup 2
```
