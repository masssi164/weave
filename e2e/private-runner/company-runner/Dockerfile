ARG WEAVE_RUNNER_BASE_IMAGE=ghcr.io/masssi164/weave-runner:e2e

FROM docker.io/library/golang:1.26.7-alpine AS handler-build
WORKDIR /src
COPY handler/go.mod handler/main.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/private-fixture ./main.go

FROM ${WEAVE_RUNNER_BASE_IMAGE}
COPY capabilities.json /etc/weave/capabilities.json
COPY schemas/ /opt/company/schemas/
COPY --from=handler-build /out/private-fixture /opt/company/bin/private-fixture
