<div align="center">
 <h1> <b>Z-Compiler</b> </h1>

  <h3><b>A Toy compiler built with Java and LLVM</b></h3>

  <p>
    <img src="https://img.shields.io/github/commit-activity/w/the-big-zed/z-compiler" alt="0">
    <img src="https://img.shields.io/github/issues-pr-closed/the-big-zed/z-compiler" alt="0">
    <img src="https://img.shields.io/github/downloads/the-big-zed/z-compiler/latest/total" alt="0">
    <img src="https://img.shields.io/github/license/the-big-zed/z-compiler" alt="LGPL-2.1">
  </p>

  <p>
    <img src="https://github.com/the-big-zed/z-compiler/actions/workflows/ci.yml/badge.svg" alt="CI">
    <img src="https://github.com/the-big-zed/z-compiler/actions/workflows/lint.yml/badge.svg" alt="Lint">
    <img src="https://github.com/the-big-zed/z-compiler/actions/workflows/release.yml/badge.svg" alt="Release">
  </p>

  <h1><a href="https://discord.gg/HzDX5HSwP"><b>Discord</b></a></h1>
</div>

## Development

```sh
mvn --batch-mode --no-transfer-progress clean verify   # build + all tests
scripts/ci/local-check.sh                              # exactly what CI runs 
```

The end-to-end tests need LLVM (`sudo apt-get install -y clang llvm`, or run
`scripts/ci/llvm.sh --install`); without it they are silently skipped, so CI
checks for the tools explicitly.

## Benchmarks

```sh
cd benchmakrks
Z_BENCH_REPEATS=15 ./run.sh # compares Z against C (clang -O3)
```

## Release channels

| Channel | Trigger | Published as |
| ------- | ------- | ------------ |
| **Nightly** | every night at 03:00 UTC (or manually) | rolling prerelease [`nightly`](https://github.com/the-big-zed/z-compiler/releases/tag/nightly), image `ghcr.io/the-big-zed/z-compiler:nightly` |
| **Staging** | tag `v1.2.0-rc.1` | prerelease on the tag, image `ghcr.io/the-big-zed/z-compiler:rc` |
| **Production** | tag `v1.2.0` | release marked *latest* with a provenance attestation, images `:1.2.0` and `:latest` |

```shell
git tag v1.2.0-rc.1 && git push origin v1.2.0-rc.1   # staging
git tag v1.0.0 && git push origin v1.0.0             # production
```

```shell
docker run --rm -v "$PWD:/workspace" ghcr.io/the-big-zed/z-compiler:nightly test.z -o out
```
