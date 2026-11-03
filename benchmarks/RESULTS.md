# Z benchmark results

Generated on `2026-11-03T17:53:58Z` from commit `a36a272` (`Merge pull request #221 from the-big-zed/feat/benchmark-suite`) on branch `performance/improve`.

Positive `Z vs C` means Z is faster. The headline uses the fastest run of each binary. The target is a geometric mean of at least 1.20x across the suite.

## Summary

| Metric | Value |
| --- | ---: |
| Cases | 17 |
| Geometric mean speedup (best) | 1.209x |
| Median case speedup (best) | 1.001x |
| Geometric mean speedup (median) | 1.198x |
| Z faster | 11 |
| C faster | 6 |
| Ties | 0 |
| Total C best | 4688.453 ms |
| Total Z best | 4306.519 ms |
| Highest C RSD | 34.93% (fibonacci) |
| Highest Z RSD | 32.67% (collatz) |

Run-to-run variance above 5%: `fibonacci`, `collatz`, `fib`, `digit_sum`, `ptr_accum`. Treat those speedups as indicative and rerun on an idle machine.

## Categories

| Category | Cases | Geometric mean (best) | Geometric mean (median) |
| --- | ---: | ---: | ---: |
| alias | 1 | 1.001x | 1.000x |
| allocation | 1 | 1.983x | 1.969x |
| calls | 1 | 1.000x | 1.000x |
| compute | 7 | 1.281x | 1.256x |
| float | 1 | 1.000x | 1.001x |
| integer | 4 | 1.029x | 1.029x |
| pointers | 1 | 1.000x | 1.000x |
| strings | 1 | 2.014x | 1.994x |

## Results

| Benchmark | Category | C best (ms) | Z best (ms) | Z vs C | C median (ms) | Z median (ms) | C RSD | Z RSD |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| fibonacci | compute | 364.812 | 214.702 | +41.1% | 415.809 | 215.329 | 34.93% | 27.46% |
| collatz | compute | 214.691 | 214.657 | +0.0% | 216.081 | 216.129 | 33.10% | 32.67% |
| pi | compute | 164.671 | 64.285 | +61.0% | 165.072 | 64.805 | 0.36% | 0.49% |
| primes | compute | 615.546 | 615.795 | -0.0% | 616.295 | 616.265 | 0.08% | 0.13% |
| ackermann | compute | 64.479 | 64.413 | +0.1% | 64.863 | 64.843 | 0.32% | 0.30% |
| mandelbrot | compute | 114.764 | 114.667 | +0.1% | 115.058 | 115.014 | 0.37% | 0.18% |
| hash | compute | 214.801 | 165.323 | +23.0% | 215.133 | 215.197 | 0.12% | 3.69% |
| alias | alias | 114.507 | 114.404 | +0.1% | 114.736 | 114.752 | 0.13% | 0.12% |
| alloc_churn | allocation | 32.213 | 16.245 | +49.6% | 32.791 | 16.650 | 1.15% | 2.26% |
| string_concat | strings | 32.017 | 15.900 | +50.3% | 32.373 | 16.238 | 0.64% | 1.28% |
| fib | calls | 515.616 | 515.753 | -0.0% | 516.378 | 516.385 | 31.63% | 32.15% |
| collatz | integer | 214.706 | 214.638 | +0.0% | 215.161 | 215.234 | 3.64% | 0.12% |
| gcd_sum | integer | 365.145 | 365.152 | -0.0% | 365.686 | 365.802 | 0.10% | 2.14% |
| primes | integer | 615.403 | 615.436 | -0.0% | 616.188 | 616.213 | 0.10% | 0.07% |
| digit_sum | integer | 465.100 | 414.958 | +10.8% | 465.862 | 415.996 | 0.07% | 5.74% |
| monte_carlo | float | 264.863 | 264.943 | -0.0% | 265.714 | 265.465 | 0.24% | 0.18% |
| ptr_accum | pointers | 315.116 | 315.248 | -0.0% | 315.868 | 315.849 | 27.74% | 26.64% |

`best` is the fastest timed run and is the headline comparison. `median` and RSD show how stable the sample is.

## Failures

| Benchmark | Error |
| --- | --- |
| concat_chain | Z binary exited with -11:  |

## Method

- C flags: `-O3 -march=native -flto -funroll-loops -mtune=native -std=c17 -fno-builtin`.
- Z pipeline: `opt -O3 -mcpu=native; clang -O3 -flto -march=native -funroll-loops -mtune=native`.
- Warmup runs per binary: 6.
- Timed runs per binary: 40.
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
| CPU governor | performance |
| Load average | [2.16, 2.46, 2.87] |
| Clang | Debian clang version 21.1.8 (13) |
| opt | Debian LLVM version 21.1.8 |
| Java | openjdk version "25.0.5" 2026-10-20 |
| Working tree | dirty |

## Reproduce

```sh
python3 benchmarks/bench.py --runs 40 --warmup 6
```
