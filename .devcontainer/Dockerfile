FROM mcr.microsoft.com/devcontainers/java:3-21-bookworm

USER root

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
    && rm -rf /var/lib/apt/lists/*

USER vscode
