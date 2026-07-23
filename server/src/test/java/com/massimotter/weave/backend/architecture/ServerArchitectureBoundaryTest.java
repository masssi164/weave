package com.massimotter.weave.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ServerArchitectureBoundaryTest {

    private static final String ENTERPRISE_TARGET_BOUNDARY_GATE = "ENTERPRISE_TARGET_BOUNDARY_GATE";
    private static final String BACKEND_PACKAGE = "com.massimotter.weave.backend.";
    private static final List<String> DOMAIN_FORBIDDEN_IMPORT_PREFIXES = List.of(
            BACKEND_PACKAGE + "controller.",
            BACKEND_PACKAGE + "model.",
            BACKEND_PACKAGE + "provider.",
            BACKEND_PACKAGE + "service.",
            BACKEND_PACKAGE + "config.",
            BACKEND_PACKAGE + "weaver.",
            BACKEND_PACKAGE + "audit.",
            BACKEND_PACKAGE + "persistence.",
            BACKEND_PACKAGE + "boards.openproject.",
            BACKEND_PACKAGE + "boards.vikunja.",
            BACKEND_PACKAGE + "boards.deck.",
            BACKEND_PACKAGE + "boards.local.",
            BACKEND_PACKAGE + "identity.realm.");
    private static final List<String> PROVIDER_ADAPTER_IMPORT_PREFIXES = List.of(
            BACKEND_PACKAGE + "chat.provider.",
            BACKEND_PACKAGE + "boards.openproject.",
            BACKEND_PACKAGE + "boards.vikunja.",
            BACKEND_PACKAGE + "boards.deck.",
            BACKEND_PACKAGE + "boards.local.",
            BACKEND_PACKAGE + "service.files.NextcloudFilesAdapter",
            BACKEND_PACKAGE + "service.calendar.CalDavCalendarAdapter",
            BACKEND_PACKAGE + "identity.realm.HttpKeycloakRealmAdminClient",
            BACKEND_PACKAGE + "identity.realm.KeycloakRealmAdminClient");
    private static final List<String> NATIVE_OR_MCP_CONTRACT_FORBIDDEN_LITERALS = List.of(
            "nextcloud",
            "/remote.php/dav",
            "davx5://",
            "providerurl",
            "providerendpoint",
            "tenantid",
            "secretref",
            "apppassword",
            "app password",
            "bearertoken",
            "bearer token value",
            "rawdiagnostics",
            "raw diagnostics",
            "downstreampayload",
            "downstream payload",
            "rawproviderpayload",
            "raw provider payload");
    private static final List<String> NATIVE_OR_MCP_CONTRACT_EXEMPT_LITERALS = List.of(
            "rawproviderpayloadincluded",
            "rawproviderpayload\", \"redacted\"",
            "rawproviderpayload\",",
            "secretref.value",
            "providerurl\"",
            "providerurl\",",
            "normalized.equals(\"rawproviderpayload\")",
            "normalized.equals(\"providerpayload\")",
            "normalized.equals(\"rawpayload\")",
            "normalized.equals(\"secretref\")",
            "normalized.equals(\"secretref.value\")",
            "normalized.contains(\"payload\")",
            "credentialref://weave/runtime/short-lived",
            "raw provider payloads are forbidden",
            "raw provider payloads, credential-bearing locations");
    private static final List<String> ACCEPTED_FILE_KEY_CUSTODY_ALLOWLIST = List.of(
            "/agentruntime/adapter/FileRuntimeProfileSigningKeyStore.java",
            "/agentruntime/adapter/FileRuntimeStateKeyWrapper.java");
    private static final List<String> DIRECT_RELATIONAL_BYPASS_IMPORTS = List.of(
            "org.springframework.jdbc.",
            "java.sql.");
    private static final List<String> REVIEWED_NATIVE_QUERY_ADAPTERS = List.of(
            "ChatCallbackClaimNativeRepository.java",
            "OperationIntentLeaseNativeRepository.java");
    private static final List<String> FILE_RUNTIME_AUTHORITY_MARKERS = List.of(
            "Path storagePath",
            "readValue(storagePath.toFile()",
            "writeValue(storagePath.toFile()",
            "Files.readAllLines(",
            "Files.readString(",
            "Files.newBufferedReader(",
            "Files.write(",
            "Files.writeString(",
            "Files.createTempFile(",
            "Files.move(",
            "Files.newBufferedWriter(",
            "Files.newOutputStream(",
            "new FileOutputStream(",
            "new FileWriter(");

    @Test
    void domainPackagesDoNotDependOnDeliveryProviderOrRuntimeLayers() throws IOException {
        assertThat(ENTERPRISE_TARGET_BOUNDARY_GATE).isNotBlank();
        List<String> violations = productionSources().stream()
                .filter(ServerArchitectureBoundaryTest::isCanonicalDomainPackage)
                .flatMap(source -> source.imports().stream()
                        .filter(ServerArchitectureBoundaryTest::isDomainForbiddenImport)
                        .map(importName -> violation(source, importName)))
                .sorted()
                .toList();

        assertThat(violations)
                .as("Domain packages must stay canonical and not import delivery, provider, runtime, or DTO layers.")
                .isEmpty();
    }

    @Test
    void canonicalCollaborationPortsDoNotDependOnDtoProtocolOrProviderLayers() throws IOException {
        List<String> violations = productionSources().stream()
                .filter(ServerArchitectureBoundaryTest::isCanonicalCollaborationPort)
                .flatMap(source -> source.imports().stream()
                        .filter(ServerArchitectureBoundaryTest::isDomainForbiddenImport)
                        .map(importName -> violation(source, importName)))
                .sorted()
                .toList();

        assertThat(violations)
                .as("Canonical collaboration ports must use domain values, not DTO, protocol, runtime, or provider types.")
                .isEmpty();

        assertThat(productionSources().stream()
                .filter(ServerArchitectureBoundaryTest::isCanonicalCollaborationPort)
                .map(JavaSource::path)
                .toList())
                .anyMatch(path -> path.endsWith(Path.of("files", "port", "FilesProviderPort.java")))
                .anyMatch(path -> path.endsWith(Path.of("calendar", "port", "CalendarProviderPort.java")))
                .anyMatch(path -> path.endsWith(Path.of("chat", "port", "ChatProviderPort.java")))
                .anyMatch(path -> path.endsWith(Path.of("boards", "port", "BoardsRepository.java")));
    }

    @Test
    void dtoShapedCollaborationCompatibilityPortsHaveBeenRemoved() throws IOException {
        assertThat(productionSources())
                .extracting(source -> source.path().getFileName().toString())
                .doesNotContain(
                        "FilesStorageAdapter.java",
                        "FilesStorageReadiness.java",
                        "VersionedFileListResponse.java",
                        "CalendarAdapter.java");
    }

    @Test
    void publicDeliveryContractsDoNotImportProviderAdapters() throws IOException {
        List<String> violations = productionSources().stream()
                .filter(ServerArchitectureBoundaryTest::isPublicDeliveryContract)
                .flatMap(source -> source.imports().stream()
                        .filter(ServerArchitectureBoundaryTest::isProviderAdapterImport)
                        .map(importName -> violation(source, importName)))
                .sorted()
                .toList();

        assertThat(violations)
                .as("Controllers and public DTO/model contracts must call Weave facades, not concrete provider adapters.")
                .isEmpty();
    }

    @Test
    void protocolProjectionAndMcpCodeDoNotImportProviderAdapters() throws IOException {
        List<String> violations = productionSources().stream()
                .filter(ServerArchitectureBoundaryTest::isProtocolProjectionOrMcpSurface)
                .flatMap(source -> source.imports().stream()
                        .filter(ServerArchitectureBoundaryTest::isProviderAdapterImport)
                        .map(importName -> violation(source, importName)))
                .sorted()
                .toList();

        assertThat(violations)
                .as("WebDAV/CalDAV/CardDAV/Matrix/MCP surfaces must route through Weave facades/use cases, not providers directly.")
                .isEmpty();
    }

    @Test
    void memberNativeAndMcpContractsDoNotExposeProviderNativeSecretsOrUrls() throws IOException {
        List<String> violations = productionSources().stream()
                .filter(ServerArchitectureBoundaryTest::isMemberNativeOrMcpContract)
                .flatMap(source -> forbiddenNativeOrMcpLiterals(source).stream()
                        .map(term -> source.path() + " exposes forbidden native/MCP contract literal: " + term))
                .sorted()
                .toList();

        assertThat(violations)
                .as("Member native setup and MCP contracts must stay Weave-owned and support-safe.")
                .isEmpty();
    }

    @Test
    void strategicJsonAndFileRuntimeAuthorityDoesNotExpandBeyondFencedDebt() throws IOException {
        List<String> violations = productionSources().stream()
                .filter(ServerArchitectureBoundaryTest::usesFileRuntimeStore)
                .filter(source -> !isAllowedFileRuntimeAuthority(source))
                .map(source -> source.path()
                        + " uses file-backed runtime persistence outside operator-mounted cryptographic key custody")
                .sorted()
                .toList();

        assertThat(violations)
                .as("Runtime truth must remain relational; only canonical operator-mounted cryptographic key custody may use files.")
                .isEmpty();
    }

    @Test
    void productionPersistenceCannotReintroduceJdbcRepositoriesOrDirectSqlAccess() throws IOException {
        List<String> violations = productionSources().stream()
                .flatMap(source -> {
                    var sourceViolations = new java.util.ArrayList<String>();
                    String fileName = source.path().getFileName().toString();
                    if (fileName.startsWith("Jdbc") || source.text().contains("class Jdbc")) {
                        sourceViolations.add(source.path() + " retains a legacy Jdbc production type");
                    }
                    source.imports().stream()
                            .filter(importName -> DIRECT_RELATIONAL_BYPASS_IMPORTS.stream()
                                    .anyMatch(importName::startsWith))
                            .map(importName -> source.path() + " bypasses the JPA boundary through " + importName)
                            .forEach(sourceViolations::add);
                    return sourceViolations.stream();
                })
                .sorted()
                .toList();

        assertThat(violations)
                .as("Production relational adapters must enter through the JPA/Hibernate boundary, not JDBC types.")
                .isEmpty();
    }

    @Test
    void genericSqlExecutorsCannotReturnAndNativeQueriesStayExplicitlyAllowlisted()
            throws IOException {
        List<JavaSource> sources = productionSources();
        assertThat(sources)
                .extracting(source -> source.path().getFileName().toString())
                .doesNotContain("JpaSqlExecutor.java", "JpaRow.java");

        List<JavaSource> nativeQuerySources = sources.stream()
                .filter(source -> source.text().contains("createNativeQuery("))
                .toList();
        assertThat(nativeQuerySources)
                .extracting(source -> source.path().getFileName().toString())
                .containsExactlyInAnyOrderElementsOf(REVIEWED_NATIVE_QUERY_ADAPTERS);
        assertThat(nativeQuerySources)
                .allSatisfy(source -> {
                    assertThat(source.text())
                            .as("native query text must be a static adapter-owned constant")
                            .doesNotContain("createNativeQuery(sql")
                            .doesNotContain("createNativeQuery(query")
                            .doesNotContain("createNativeQuery(\"\" +")
                            .doesNotContain("createNativeQuery(String.format");
                    assertThat(source.text())
                            .as("native query values must remain bound parameters")
                            .contains(".setParameter(");
                });
    }

    @Test
    void concreteSpringRepositoriesRemainProxyable() throws IOException {
        List<Path> finalRepositories = productionSources().stream()
                .filter(source -> source.text().contains("@Repository"))
                .filter(source -> source.text().matches(
                        "(?s).*@Repository\\s+(?:public\\s+)?"
                                + "final\\s+class\\s+[A-Za-z0-9_]+.*"))
                .map(JavaSource::path)
                .sorted()
                .toList();

        assertThat(finalRepositories)
                .as("Spring exception translation and transaction advice require proxyable concrete repositories")
                .isEmpty();
    }

    @Test
    void domainAndApplicationPackagesCannotDependOnJpaOrHibernate() throws IOException {
        List<String> violations = productionSources().stream()
                .filter(ServerArchitectureBoundaryTest::isDomainOrApplicationPackage)
                .flatMap(source -> source.imports().stream()
                        .filter(importName -> importName.startsWith("jakarta.persistence.")
                                || importName.startsWith("org.hibernate.")
                                || importName.startsWith(BACKEND_PACKAGE + "persistence."))
                        .map(importName -> violation(source, importName)))
                .sorted()
                .toList();

        assertThat(violations)
                .as("Domain and application/use-case packages must depend on ports, never persistence technology.")
                .isEmpty();
    }

    @Test
    void productionCannotReintroduceVolatileOrFilePersistenceFallbacks() throws IOException {
        String production = productionSources().stream()
                .map(JavaSource::text)
                .collect(java.util.stream.Collectors.joining("\n"));

        assertThat(production)
                .doesNotContain("new InMemory")
                .doesNotContain("WeavePersistenceProperties")
                .doesNotContain("storage.mode")
                .doesNotContain("weave.persistence.mode")
                .doesNotContain("FileAuditEventPublisher")
                .doesNotContain("FileProviderSelectionRepository")
                .doesNotContain("FileOrganizationBootstrapRepository")
                .doesNotContain("FileProductProfileOverrideRepository")
                .doesNotContain("FileMigrationRunEvidenceRepository");
    }

    @Test
    void filesOpenApiControllerRemainsControlPlaneOnly() throws IOException {
        JavaSource filesController = productionSources().stream()
                .filter(source -> source.path().endsWith(Path.of("controller", "FilesController.java")))
                .findFirst()
                .orElseThrow();

        assertThat(filesController.text())
                .doesNotContain("@GetMapping(\"/api/files\")")
                .doesNotContain("@PostMapping(\"/api/files/upload\")")
                .doesNotContain("@PostMapping(\"/api/files/folders\")")
                .doesNotContain("@DeleteMapping(\"/api/files/{id}\")");
    }

    @Test
    void filesWebDavWriteMethodsRouteThroughFacadeUseCases() throws IOException {
        JavaSource filesWebDavController = productionSources().stream()
                .filter(source -> source.path().endsWith(Path.of("controller", "FilesWebDavController.java")))
                .findFirst()
                .orElseThrow();

        assertThat(filesWebDavController.text())
                .contains("case \"PUT\" -> put(request)")
                .contains("case \"MKCOL\" -> mkcol(request)")
                .contains("case \"DELETE\" -> delete(request)")
                .contains("filesFacadeService.putWebDavFile(")
                .contains("filesFacadeService.createWebDavFolder(")
                .contains("filesFacadeService.deleteWebDavPath(")
                .doesNotContain("NextcloudFilesAdapter")
                .doesNotContain("RestClient");
    }

    @Test
    void calendarCalDavMethodsRouteThroughCalendarFacadeUseCases() throws IOException {
        JavaSource calDavController = productionSources().stream()
                .filter(source -> source.path().endsWith(Path.of("controller", "CalDavCalendarController.java")))
                .findFirst()
                .orElseThrow();

        assertThat(calDavController.text())
                .contains("case \"PROPFIND\" -> propfind(request)")
                .contains("case \"REPORT\" -> report(request)")
                .contains("case \"GET\" -> get(request, false)")
                .contains("case \"PUT\" -> put(request)")
                .contains("case \"DELETE\" -> delete(request)")
                .contains("calendarFacadeService.listCalDavResources(")
                .contains("calendarFacadeService.readCalDavResource(")
                .contains("calendarFacadeService.putCalDavEventIcs(")
                .contains("calendarFacadeService.deleteCalDavEventIcs(")
                .doesNotContain("CalDavCalendarAdapter")
                .doesNotContain("Nextcloud")
                .doesNotContain("RestClient");
    }

    @Test
    void boardsControllerRoutesThroughCanonicalFacadeRatherThanConcreteAdapters() throws IOException {
        JavaSource boardsController = productionSources().stream()
                .filter(source -> source.path().endsWith(Path.of("controller", "BoardsController.java")))
                .findFirst()
                .orElseThrow();

        assertThat(boardsController.text())
                .contains("BoardsFacadeService")
                .doesNotContain("OpenProjectBoardsRepository")
                .doesNotContain("LocalWorkspaceBoardsRepository")
                .doesNotContain("VikunjaBoardsRepository")
                .doesNotContain("NextcloudDeckBoardsRepository");
    }

    @Test
    void matrixClientServerProjectionUsesCanonicalChatAndNativeCoreNotRestDtos() throws IOException {
        JavaSource matrixProjection = productionSources().stream()
                .filter(source -> source.path().endsWith(Path.of("controller", "MatrixClientServerProjectionController.java")))
                .findFirst()
                .orElseThrow();

        assertThat(matrixProjection.text())
                .contains("\"/_matrix/client/**\"")
                .contains("matrixProtocolCoreService.versions()")
                .contains("matrixProtocolCoreService.sync(")
                .contains("matrixProtocolCoreService.parseEvent(")
                .contains("matrixProtocolCoreService.parseObject(")
                .contains("chatDomainFacadeService.conversations(jwt)")
                .contains("chatDomainFacadeService.timeline(")
                .contains("chatDomainFacadeService.sendEvent(")
                .doesNotContain("/api/chat/conversations")
                .doesNotContain("ChatFacadeService")
                .doesNotContain("ChatConversationResponse")
                .doesNotContain("ChatMessageResponse")
                .doesNotContain("ObjectMapper")
                .doesNotContain("BridgeAdapter")
                .doesNotContain("providerAccessToken")
                .doesNotContain("RestClient");
    }

    @Test
    void synapseApplicationServiceRemainsASouthboundPrivateAdapter() throws IOException {
        JavaSource runtimeConfiguration = sourceEndingWith(
                Path.of("config", "ChatRuntimeConfiguration.java"));
        JavaSource canonicalAdapter = sourceEndingWith(
                Path.of("chat", "provider", "synapse", "SynapseBackedCanonicalChatAdapter.java"));
        JavaSource southboundAdapter = sourceEndingWith(
                Path.of("chat", "provider", "synapse", "MatrixSynapseChatSouthboundAdapter.java"));
        JavaSource callbackController = sourceEndingWith(
                Path.of("chat", "provider", "synapse", "MatrixApplicationServiceController.java"));
        JavaSource callbackSecurity = sourceEndingWith(
                Path.of("config", "MatrixApplicationServiceSecurityConfiguration.java"));
        JavaSource secrets = sourceEndingWith(
                Path.of("chat", "provider", "synapse", "MatrixApplicationServiceSecrets.java"));

        assertThat(runtimeConfiguration.text())
                .contains("CanonicalChatStore")
                .contains("JpaCanonicalChatStore")
                .contains("MatrixSynapseChatSouthboundAdapter")
                .contains("SynapseBackedCanonicalChatAdapter")
                .doesNotContain("requireJdbc(properties)")
                .doesNotContain("storage.mode");
        assertThat(canonicalAdapter.text())
                .contains("CanonicalChatStore")
                .contains("MatrixSynapseChatSouthboundAdapter")
                .contains("store.prepareEvent(")
                .contains("store.acknowledgeEvent(")
                .doesNotContain("Jwt")
                .doesNotContain("Keycloak")
                .doesNotContain("accessToken");
        assertThat(southboundAdapter.text())
                .contains("implements ChatSouthboundProvider")
                .contains("authenticatedReadiness")
                .doesNotContain("Jwt")
                .doesNotContain("Keycloak");
        assertThat(callbackController.text())
                .contains("/api/internal/chat/matrix/appservice")
                .contains("beginCallback(")
                .contains("completeCallback(")
                .contains("boundedBody(request)")
                .contains("@Hidden");
        assertThat(callbackSecurity.text())
                .contains("MatrixApplicationServiceAuthenticationFilter")
                .contains(".securityMatcher(\"/api/internal/chat/matrix/appservice/**\")")
                .contains("SessionCreationPolicy.STATELESS");
        assertThat(secrets.text())
                .contains("requiredAsTokenFile()")
                .contains("requiredHsTokenFile()")
                .contains("MessageDigest.isEqual")
                .doesNotContain("System.getenv")
                .doesNotContain("toString()");
    }

    @Test
    void obsoletePaChatTransportCannotReturnToProductionSources() throws IOException {
        String production = productionSources().stream()
                .map(JavaSource::text)
                .collect(java.util.stream.Collectors.joining("\n"));

        assertThat(production)
                .doesNotContain("WeaverPaChat")
                .doesNotContain("channels.weave-chat")
                .doesNotContain("WEAVE_WEAVER_PA_CHAT")
                .doesNotContain("weaver.pa_chat.turn_completed");
    }

    @Test
    void matrixProtocolCoreBoundaryDefinesRustJniAndFlutterBridgeTarget() throws IOException {
        JavaSource matrixCore = productionSources().stream()
                .filter(source -> source.path().endsWith(Path.of("matrix", "MatrixProtocolCoreService.java")))
                .findFirst()
                .orElseThrow();
        JavaSource nativeCore = productionSources().stream()
                .filter(source -> source.path().endsWith(Path.of("matrix", "NativeMatrixCore.java")))
                .findFirst()
                .orElseThrow();

        assertThat(matrixCore.text())
                .contains("spring-boot-resource-server")
                .contains("ruma-serde-serde_json-thiserror-tracing")
                .contains("server-jni-wrapper")
                .contains("flutter-rust-bridge")
                .contains("NativeMatrixCore.ensureLoaded()")
                .contains("NativeMatrixCore.projectJson(")
                .doesNotContain("Synapse")
                .doesNotContain("RestClient");
        assertThat(nativeCore.text())
                .contains("public static native String projectJson")
                .contains("weave_matrix_core");
    }

    private static List<JavaSource> productionSources() throws IOException {
        Path sourceRoot = sourceRoot();
        try (var paths = Files.walk(sourceRoot)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(ServerArchitectureBoundaryTest::readSource)
                    .toList();
        }
    }

    private static JavaSource sourceEndingWith(Path suffix) throws IOException {
        return productionSources().stream()
                .filter(source -> source.path().endsWith(suffix))
                .findFirst()
                .orElseThrow();
    }

    private static JavaSource readSource(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            String packageName = lines.stream()
                    .filter(line -> line.startsWith("package "))
                    .findFirst()
                    .map(line -> line.replace("package ", "").replace(";", "").trim())
                    .orElse("");
            String text = String.join("\n", lines);
            List<String> imports = lines.stream()
                    .filter(line -> line.startsWith("import "))
                    .map(line -> line.replace("import ", "").replace("static ", "").replace(";", "").trim())
                    .toList();
            return new JavaSource(path, packageName, imports, text);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read Java source " + path, exception);
        }
    }

    private static Path sourceRoot() {
        Path moduleRoot = Path.of("src/main/java");
        if (Files.isDirectory(moduleRoot)) {
            return moduleRoot;
        }
        return Path.of("server/src/main/java");
    }

    private static boolean isDomainForbiddenImport(String importName) {
        return DOMAIN_FORBIDDEN_IMPORT_PREFIXES.stream().anyMatch(importName::startsWith);
    }

    private static boolean isProviderAdapterImport(String importName) {
        return PROVIDER_ADAPTER_IMPORT_PREFIXES.stream().anyMatch(importName::startsWith);
    }

    private static boolean isPublicDeliveryContract(JavaSource source) {
        return source.packageName().equals(BACKEND_PACKAGE + "controller")
                || source.packageName().startsWith(BACKEND_PACKAGE + "controller.")
                || source.packageName().equals(BACKEND_PACKAGE + "model")
                || source.packageName().startsWith(BACKEND_PACKAGE + "model.");
    }

    private static boolean isProtocolProjectionOrMcpSurface(JavaSource source) {
        String className = source.path().getFileName().toString();
        return !source.packageName().equals(BACKEND_PACKAGE + "config")
                && !source.packageName().startsWith(BACKEND_PACKAGE + "config.")
                && !source.packageName().startsWith(BACKEND_PACKAGE + "chat.provider.")
                && (className.contains("Mcp")
                || className.contains("WebDav")
                || className.contains("CalDav")
                || className.contains("CardDav")
                || className.contains("Matrix"));
    }

    private static boolean isMemberNativeOrMcpContract(JavaSource source) {
        String path = source.path().toString().replace('\\', '/');
        String className = source.path().getFileName().toString();
        return path.contains("/model/files/FileNative")
                || path.contains("/model/calendar/CalendarNative")
                || path.contains("/model/calendar/CalendarClientSetup")
                || path.contains("/model/calendar/CalendarExternalEndpoints")
                || path.contains("/model/calls/CallNative")
                || path.contains("/service/calendar/AppleMobileConfigProfileRenderer")
                || path.contains("/weaver/")
                || className.contains("Mcp");
    }

    private static boolean usesFileRuntimeStore(JavaSource source) {
        String text = source.text();
        return FILE_RUNTIME_AUTHORITY_MARKERS.stream().anyMatch(text::contains)
                && (text.contains(".json") || text.contains(".jsonl") || text.contains("storagePath"));
    }

    private static boolean isAllowedFileRuntimeAuthority(JavaSource source) {
        String path = source.path().toString().replace('\\', '/');
        return ACCEPTED_FILE_KEY_CUSTODY_ALLOWLIST.stream().anyMatch(path::endsWith);
    }

    private static List<String> forbiddenNativeOrMcpLiterals(JavaSource source) {
        String normalized = source.text().toLowerCase(Locale.ROOT);
        for (String exemption : NATIVE_OR_MCP_CONTRACT_EXEMPT_LITERALS) {
            normalized = normalized.replace(exemption, "");
        }
        return NATIVE_OR_MCP_CONTRACT_FORBIDDEN_LITERALS.stream()
                .filter(normalized::contains)
                .toList();
    }

    private static boolean isCanonicalDomainPackage(JavaSource source) {
        String packageName = source.packageName();
        return packageName.endsWith(".domain") || packageName.contains(".domain.");
    }

    private static boolean isDomainOrApplicationPackage(JavaSource source) {
        String packageName = source.packageName();
        return packageName.endsWith(".domain")
                || packageName.contains(".domain.")
                || packageName.endsWith(".application")
                || packageName.contains(".application.");
    }

    private static boolean isCanonicalCollaborationPort(JavaSource source) {
        String packageName = source.packageName();
        return packageName.equals(BACKEND_PACKAGE + "files.port")
                || packageName.equals(BACKEND_PACKAGE + "calendar.port")
                || packageName.equals(BACKEND_PACKAGE + "chat.port")
                || packageName.equals(BACKEND_PACKAGE + "boards.port");
    }

    private static String violation(JavaSource source, String importName) {
        return source.path() + " imports " + importName;
    }

    private record JavaSource(Path path, String packageName, List<String> imports, String text) {
    }
}
