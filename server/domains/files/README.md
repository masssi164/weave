# Files domain ownership

Files is a compile-time selectable product domain. It owns Files business semantics, adapters, persistence, API handlers and Spring Boot auto-configuration when the extraction is complete. Core must depend only on public shared contracts, never on Files implementation.

## Extracted today

- `:server:domains:files:model` contains the original pure Java Files model and its existing tests, moved without changing behavior.

## Still to extract

- JPA entities and repositories have moved into `:server:domains:files:persistence`, preserving the existing table names and mapped types. Files service-layer transaction adapters are still in the transitional Server and will move with Files use cases.
- The provider adapters (native Files/OpenDAL, Nextcloud and S3), their own dependencies, API controller/DTO projection, and explicit module-local `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Real `:server:app` composition, OpenAPI-after-controller generation, and optional-domain release qualification.

Run from repository root:

```sh
./gradlew :server:domains:files:model:test
./gradlew coreArchitectureCi
```

Provider choices remain runtime bindings independent of the Gradle domain selection. Do not move historical Flyway scripts out of the canonical catalog or delete retained data.
