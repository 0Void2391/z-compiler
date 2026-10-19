FROM ubuntu:24.04

RUN apt-get update \
    && apt-get install -y --no-install-recommends clang llvm ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY z-linux-amd64 /usr/local/bin/z-compiler

WORKDIR /workspace

ENTRYPOINT ["/usr/local/bin/z-compiler"]
