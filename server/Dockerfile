FROM rust:1.95.0-slim-bookworm AS matrix-core
WORKDIR /source
COPY Cargo.toml Cargo.lock ./
COPY rust/matrix-core/Cargo.toml rust/matrix-core/Cargo.toml
COPY rust/matrix-core/src rust/matrix-core/src
RUN cargo build --release -p weave-matrix-core --features jni

FROM eclipse-temurin:21-jre-noble
WORKDIR /app

ARG JAR_FILE=server/build/libs/*.jar
COPY ${JAR_FILE} /app/app.jar
COPY --from=matrix-core /source/target/release/libweave_matrix_core.so /app/lib/libweave_matrix_core.so

ENV WEAVE_MATRIX_CORE_LIBRARY_PATH=/app/lib/libweave_matrix_core.so

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
