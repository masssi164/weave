FROM docker.io/library/golang:1.26.7-bookworm AS go-toolchain

FROM mcr.microsoft.com/devcontainers/java:3-21-bookworm

USER root

COPY --from=go-toolchain /usr/local/go /usr/local/go

ENV GOROOT=/usr/local/go \
    GOPATH=/home/vscode/go \
    PATH=/usr/local/go/bin:/home/vscode/go/bin:${PATH}

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        jq \
        make \
        openssl \
        postgresql-client \
        python3 \
        python3-yaml \
    && mkdir -p /home/vscode/go/bin /home/vscode/go/pkg/mod \
    && chown -R vscode:vscode /home/vscode/go \
    && rm -rf /var/lib/apt/lists/*

USER vscode
