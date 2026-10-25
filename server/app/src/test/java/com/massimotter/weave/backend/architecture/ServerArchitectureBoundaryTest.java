package com.massimotter.weave.backend.architecture;

import com.massimotter.weave.backend.identity.profile.persistence.FileProductProfileOverrideRepository;



import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServerArchitectureBoundaryTest {

    private static final String ENTERPRISE_TARGET_BOUNDARY_GATE = "ENTERPRISE_TARGET_BOUNDARY_GATE";
    private static final String BACKEND_PACKAGE = "com.massimotter.weave.backend.";
    private static final Path SPACE_CREATION_FENCE_SOURCE = Path.of(
            "server/core/src/main/java/com/massimotter/weave/backend/spaces/adapter/SpacePersistence.java");
    // WEAVE-DOMAIN-SPACES names this sole fixed statement under ADR 0023. The rest of its file remains guarded.
    private static final String SPACE_CREATION_FENCE_DECLARATION =
            "@Modifying(flushAutomatically = true) @Query(value = \"\"\" "
                    + "insert into weave_spaces (organization_ref, space_ref, display_name, lifecycle_state, "
                    + "created_at_utc, updated_at_utc, version) "
                    + "values (:organizationRef, :spaceRef, :displayName, 'ACTIVE', :now, :now, 0) "
                    + "on conflict do nothing \"\"\", nativeQuery = true) "
                    + "int insertIfAbsent(@Param(\"organizationRef\") String organizationRef, "
                    + "@Param(\"spaceRef\") String spaceRef, @Param(\"displayName\") String displayName, "
                    + "@Param(\"now\") OffsetDateTime now);";

    private static final Path MATRIX_AGGREGATE_FENCE_SOURCE = Path.of(
            "server/domains/chat/persistence/src/main/java/com/massimotter/weave/backend/chat/persistence/MatrixE2eeRelationalStore.java");
    // Absent aggregate rows cannot be row-locked; this statement serializes only their writers.
    private static final String MATRIX_AGGREGATE_FENCE_DECLARATION =
            "entityManager.createNativeQuery(\"select pg_advisory_xact_lock(hashtext(?1),hashtext(?2))\", Object.class) "
                    + ".setParameter(1, tenantId).setParameter(2, \"matrix:\" + aggregate).getSingleResult();";

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
            BACKEND_PACKAGE + "boards.local.");
    private static final List<String> PROVIDER_ADAPTER_IMPORT_PREFIXES = List.of(
            BACKEND_PACKAGE + "chat.provider.",
            BACKEND_PACKAGE + "boards.openproject.",
            BACKEND_PACKAGE + "boards.vikunja.",
            BACKEND_PACKAGE + "boards.deck.",
            BACKEND_PACKAGE + "boards.local.",
            BACKEND_PACKAGE + "files.provider.NextcloudFilesAdapter",
            BACKEND_PACKAGE + "calendar.provider.",
            BACKEND_PACKAGE + "calendar.persistence.NativeCalendarProviderAdapter");
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
            // Internal Spring filter type, not a member-facing bearer-token field.
            "bearertokenauthenticationfilter",
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
    private static final List<String> LEGACY_FILE_RUNTIME_STORE_ALLOWLIST = List.of(
            "/audit/FileAuditEventPublisher.java",
            "/provider/FileProviderSelectionRepository.java",
            "/service/FileOrganizationBootstrapRepository.java",
            "/service/FileProductProfileOverrideRepository.java",
            "/service/migration/FileMigrationRunEvidenceRepository.java");
    private static final List<String> ACCEPTED_FILE_KEY_CUSTODY_ALLOWLIST = List.of(
            "/agentruntime/adapter/FileRuntimeProfileSigningKeyStore.java",
            "/agentruntime/adapter/FileRuntimeStateKeyWrapper.java",
            "/schema/SchemaAuthorityInitializer.java",
            "/schema/SchemaReceiptVerifier.java");
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
    void securityFilterTypeDoesNotHideAnExposedBearerTokenField() {
        JavaSource frameworkFilter = new JavaSource(
                Path.of("CalendarMcpSecurityConfiguration.java"), "", List.of(),
                "BearerTokenAuthenticationFilter.class");
        JavaSource exposedField = new JavaSource(
                Path.of("CalendarMcpResponse.java"), "", List.of(),
                "record CalendarMcpResponse(String bearerToken) {}");

        assertThat(forbiddenNativeOrMcpLiterals(frameworkFilter)).isEmpty();
        assertThat(forbiddenNativeOrMcpLiterals(exposedField)).contains("bearertoken");
    }

    @Test
    void strategicJsonAndFileRuntimeAuthorityDoesNotExpandBeyondFencedDebt() throws IOException {
        List<String> violations = productionSources().stream()
                .filter(ServerArchitectureBoundaryTest::usesFileRuntimeStore)
                .filter(source -> !isAllowedFileRuntimeAuthority(source))
                .map(source -> source.path()
                        + " uses file-backed runtime persistence outside the explicit #1019/#1011 debt fence")
                .sorted()
                .toList();

        assertThat(violations)
                .as("Runtime truth must remain relational; only canonical operator-mounted cryptographic key custody and fenced legacy debt may use files.")
                .isEmpty();
    }

    @Test
    void filesMemberDataPlaneIsServerOwnedAndCodeFirst() throws IOException {
        JavaSource filesController = productionSources().stream()
                .filter(source -> source.path().endsWith(Path.of("files", "api", "FilesController.java")))
                .findFirst()
                .orElseThrow();
        JavaSource userController = productionSources().stream()
                .filter(source -> source.path().endsWith(Path.of("files", "api", "FilesUserItemsController.java")))
                .findFirst()
                .orElseThrow();

        assertThat(filesController.text())
                .doesNotContain("@GetMapping(\"/api/files\")")
                .doesNotContain("@PostMapping(\"/api/files/upload\")")
                .doesNotContain("@PostMapping(\"/api/files/folders\")")
                .doesNotContain("@DeleteMapping(\"/api/files/{id}\")");
        assertThat(userController.text())
                .contains("@GetMapping(value = \"/api/files/items\"")
                .contains("@GetMapping(value = \"/api/files/items/{fileId}\"")
                .contains("@GetMapping(\"/api/files/items/{fileId}/content\")")
                .contains("@PostMapping(value = \"/api/files/items/folders\"")
                .contains("@PostMapping(value = \"/api/files/items/uploads\"")
                .contains("@PutMapping(value = \"/api/files/items/{fileId}/content\"");
    }

    @Test
    void retiredApplicationCompatibilityRoutesAndConfigurationFallbacksStayAbsent()
            throws IOException {
        assertThat(sourceEndingWith(Path.of("controller", "WorkspaceController.java")).text())
                .doesNotContain("/api/v1/organization")
                .doesNotContain("/api/v1/workspace");
        assertThat(sourceEndingWith(Path.of("controller", "ChatController.java")).text())
                .doesNotContain("/api/v1/chat")
                .doesNotContain("/api/v1/admin/chat");
        assertThat(sourceEndingWith(Path.of("controller", "AdminControlPlaneController.java")).text())
                .doesNotContain("/api/v1/admin");
        assertThat(sourceEndingWith(
                Path.of("controller", "ProviderCapabilityHealthController.java")).text())
                .doesNotContain("/api/v1/admin");
    }

    @Test
    void filesWebDavWriteMethodsRouteThroughFacadeUseCases() throws IOException {
        JavaSource filesWebDavController = productionSources().stream()
                .filter(source -> source.path().endsWith(Path.of("files", "api", "FilesWebDavController.java")))
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
                .filter(source -> source.path().endsWith(Path.of("calendar", "api", "dav", "CalDavCalendarController.java")))
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
                // The bounded Matrix history path uses canonical paged timelines.
                .contains("chatDomainFacadeService.timelinePage(")
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
    void nativeChatOwnsCanonicalDefaultAndSynapseRemainsAPrivateSouthboundAdapter() throws IOException {
        JavaSource persistenceConfiguration = sourceEndingWith(
                Path.of("config", "CanonicalChatPersistenceConfiguration.java"));
        JavaSource runtimeConfiguration = sourceEndingWith(
                Path.of("config", "ChatRuntimeConfiguration.java"));
        JavaSource nativeAdapter = sourceEndingWith(
                Path.of("chat", "provider", "weave", "NativeChatProviderAdapter.java"));
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

        assertThat(persistenceConfiguration.text())
                .contains("CanonicalChatStore")
                .contains("JpaCanonicalChatStore")
                .contains("CanonicalChatJpaAuthority")
                .doesNotContain("MatrixSynapseChatSouthboundAdapter")
                .doesNotContain("MatrixApplicationServiceSecrets");
        assertThat(runtimeConfiguration.text())
                .contains("NativeChatProviderAdapter")
                .contains("MatrixSynapseChatSouthboundAdapter")
                .contains("SynapseBackedCanonicalChatAdapter")
                .doesNotContain("WEAVE_CHAT_STORAGE_MODE")
                .doesNotContain("JdbcTemplate")
                .doesNotContain("JdbcCanonicalChatStore");
        assertThat(nativeAdapter.text())
                .contains("implements ChatProviderPort")
                .contains("store.commitConversation(")
                .contains("store.commitEvent(")
                .doesNotContain("ChatSouthboundProvider")
                .doesNotContain("acknowledgeMapping(")
                .doesNotContain("providerEventRef");
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
    void organizationIdentityResolverExclusivelyOwnsHumanTenantClaimPrecedence()
            throws IOException {
        List<String> owners = productionSources().stream()
                .filter(source -> source.text().contains(".tenantClaim()")
                        || source.text().contains(".tenantFallbackClaim()"))
                .map(source -> source.path().getFileName().toString())
                .sorted()
                .toList();

        assertThat(owners)
                .as("Human product services must consume the canonical Keycloak organization identity context.")
                .containsExactly("OrganizationIdentityContextResolver.java");

        List<String> literalOwners = productionSources().stream()
                .filter(source -> source.text().contains("\"weave_tenant_id\"")
                        || source.text().replaceAll("@(?:Column|UniqueConstraint)\\([^)]*\\)", "").contains("\"tenant_id\""))
                .map(source -> source.path().getFileName().toString())
                .distinct()
                .sorted()
                .toList();

        assertThat(literalOwners)
                .as("Tenant aliases are configuration-owned; only path-bound device credentials may mint compatibility claims.")
                .containsExactly(
                        "ContextAuthorizationProperties.java",
                        "DeviceCredentialAuthenticationFilter.java");
    }

    @Test
    void serverCompositionContainsNoJpaEntityOrSpringDataRepositoryDeclaration()
            throws IOException {
        assertThat(productionSources().stream()
                .filter(source -> source.path().toAbsolutePath().normalize()
                        .startsWith(sourceRoot().toAbsolutePath().normalize())).toList())
                .isNotEmpty()
                .allSatisfy(source -> assertThat(source.text())
                        .as(source.path().toString())
                        .doesNotContain("@Entity\n", "@Entity\r", "@Entity(")
                        .doesNotContain("extends JpaRepository<"));
    }

    @Test
    void productionDataAccessUsesJpaWithoutNativeSqlOrJdbcEscapeHatches()
            throws IOException {
        List<JavaSource> persistence = persistenceSources();
        List<JavaSource> dataAccessSources = persistence;
        assertThat(dataAccessSources.stream()
                        .filter(source -> source.text().contains("JdbcTemplate")
                                || source.text().contains("org.springframework.jdbc.core"))
                        .map(source -> source.path().getFileName().toString())
                        .collect(java.util.stream.Collectors.toSet()))
                .as("ordinary product CRUD has no handwritten JDBC authority")
                .isEmpty();

        assertThat(dataAccessSources.stream()
                        .toList())
                .allSatisfy(ServerArchitectureBoundaryTest::assertNoUnreviewedNativeDataAccess);
        assertThat(dataAccessSources.stream()
                        .filter(source -> !source.packageName().equals(BACKEND_PACKAGE + "schema"))
                        .toList())
                .allSatisfy(source -> assertThat(source.text())
                        .as(source.path().toString())
                        .doesNotContain(
                                "java.sql.",
                                "DriverManager.getConnection(",
                                ".prepareStatement(",
                                ".createStatement("));
        assertThat(productionSources().stream()
                        .filter(source -> source.packageName().equals(BACKEND_PACKAGE + "schema"))
                        .filter(source -> source.text().contains("java.sql."))
                        .map(source -> source.path().getFileName().toString())
                        .toList())
                .containsExactlyInAnyOrder(
                        "SchemaAuthorityInitializer.java",
                        "SchemaCatalogFingerprint.java",
                        "MigrationCatalogReference.java",
                        "ServingSchemaAuthorityGuard.java");
    }

    @Test
    void spaceCreationFenceGuardRejectsChangedSqlOrParameterSignature() throws IOException {
        JavaSource source = spaceCreationFenceSource();
        for (String changed : List.of(
                source.text().replace("on conflict do nothing", "on conflict do update set lifecycle_state = 'ACTIVE'"),
                source.text().replace(":organizationRef, :spaceRef", "'fixed-org', :spaceRef"),
                source.text().replace(":displayName", "'Unreviewed fixed name'"),
                source.text().replace("int insertIfAbsent(", "int executeArbitrarySql("))) {
            assertThatThrownBy(() -> assertNoUnreviewedNativeDataAccess(withText(source, changed)))
                    .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void spaceCreationFenceGuardRejectsAdditionalNativeQueriesAndJdbcInTheSameFile() throws IOException {
        JavaSource source = spaceCreationFenceSource();
        for (String added : List.of(
                "@Query(value = \"select 1\", nativeQuery = true) int extraQuery();",
                "@Query(value = \"select 1\", nativeQuery=   true) int extraQuery();",
                "@Query(value = \"select 1\", nativeQuery = (true)) int extraQuery();",
                "@NativeQuery(\"select 1\") int extraQuery();",
                "@Query(value = \"select 1\", nativeQuery = false || true) int extraQuery();",
                "@org.springframework.data.jpa.repository.NativeQuery(\"select 1\") int extraQuery();",
                "entityManager.createNativeQuery(\"select 1\");",
                "JdbcTemplate anotherExecutor;")) {
            assertThatThrownBy(() -> assertNoUnreviewedNativeDataAccess(withText(source, source.text() + added)))
                    .isInstanceOf(AssertionError.class);
        }
        assertThatThrownBy(() -> assertNoUnreviewedNativeDataAccess(
                withText(source, source.text() + SPACE_CREATION_FENCE_DECLARATION)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void spaceCreationFenceGuardCannotExemptAnotherSourceOrModule() throws IOException {
        JavaSource source = spaceCreationFenceSource();
        for (Path path : List.of(source.path().resolveSibling("AnotherSpacePersistence.java"),
                Path.of("server/app/src/main/java/com/massimotter/weave/backend/spaces/adapter/SpacePersistence.java"))) {
            JavaSource copied = new JavaSource(path, source.packageName(), source.imports(), source.text());
            assertThatThrownBy(() -> assertNoUnreviewedNativeDataAccess(copied)).isInstanceOf(AssertionError.class);
        }
        JavaSource wrongPackage = new JavaSource(source.path(), BACKEND_PACKAGE + "other", source.imports(), source.text());
        assertThatThrownBy(() -> assertNoUnreviewedNativeDataAccess(wrongPackage)).isInstanceOf(AssertionError.class);
    }

    @Test
    void matrixAggregateFenceIsExactAndCannotHideAnotherNativeDataAccess() throws IOException {
        JavaSource source = persistenceSources().stream()
                .filter(ServerArchitectureBoundaryTest::ownsMatrixAggregateFence).findFirst().orElseThrow();
        assertNoUnreviewedNativeDataAccess(source);
        for (String changed : List.of(
                source.text().replace("pg_advisory_xact_lock", "pg_advisory_lock"),
                source.text().replace(".setParameter(1, tenantId)", ".setParameter(1, \"fixed\")"),
                source.text().replace("\"matrix:\" + aggregate", "aggregate"),
                source.text() + "entityManager.createNativeQuery(\"select 1\");",
                source.text() + "JdbcTemplate anotherExecutor;",
                source.text() + MATRIX_AGGREGATE_FENCE_DECLARATION)) {
            assertThatThrownBy(() -> assertNoUnreviewedNativeDataAccess(withText(source, changed)))
                    .isInstanceOf(AssertionError.class);
        }
        for (Path path : List.of(source.path().resolveSibling("AnotherStore.java"),
                Path.of("server/app/src/main/java/com/massimotter/weave/backend/chat/persistence/MatrixE2eeRelationalStore.java"))) {
            JavaSource copied = new JavaSource(path, source.packageName(), source.imports(), source.text());
            assertThatThrownBy(() -> assertNoUnreviewedNativeDataAccess(copied)).isInstanceOf(AssertionError.class);
        }
        assertThatThrownBy(() -> assertNoUnreviewedNativeDataAccess(new JavaSource(source.path(),
                BACKEND_PACKAGE + "other", source.imports(), source.text()))).isInstanceOf(AssertionError.class);
    }

    private static boolean ownsMatrixAggregateFence(JavaSource source) {
        return source.path().normalize().endsWith(MATRIX_AGGREGATE_FENCE_SOURCE)
                && source.packageName().equals(BACKEND_PACKAGE + "chat.persistence");
    }

    private static JavaSource withText(JavaSource source, String text) {
        return new JavaSource(source.path(), source.packageName(), source.imports(), text);
    }

    private static JavaSource spaceCreationFenceSource() throws IOException {
        return persistenceSources().stream().filter(ServerArchitectureBoundaryTest::ownsSpaceCreationFence)
                .findFirst().orElseThrow();
    }

    private static boolean ownsSpaceCreationFence(JavaSource source) {
        return source.path().normalize().endsWith(SPACE_CREATION_FENCE_SOURCE)
                && source.packageName().equals(BACKEND_PACKAGE + "spaces.adapter");
    }

    private static void assertNoUnreviewedNativeDataAccess(JavaSource source) {
        String guarded = source.text().replaceAll("\\s+", " ").trim();
        if (ownsSpaceCreationFence(source)) {
            assertThat(guarded).as("exact reviewed Space creation-fence declaration")
                    .containsOnlyOnce(SPACE_CREATION_FENCE_DECLARATION);
            guarded = guarded.replace(SPACE_CREATION_FENCE_DECLARATION, "");
        }
        if (ownsMatrixAggregateFence(source)) {
            assertThat(guarded).as("exact reviewed Matrix aggregate creation fence")
                    .containsOnlyOnce(MATRIX_AGGREGATE_FENCE_DECLARATION);
            guarded = guarded.replace(MATRIX_AGGREGATE_FENCE_DECLARATION, "");
        }
        assertThat(guarded).as(source.path().toString())
                .doesNotContainPattern("createNativeQuery\\s*\\(")
                .doesNotContainPattern("nativeQuery\\s*=")
                .doesNotContainPattern("\\b(?:NativeQuery|NamedNativeQuery|NamedNativeQueries)\\b")
                .doesNotContain("JdbcTemplate", "NamedParameterJdbcTemplate", "org.springframework.jdbc.core");
    }

    @Test
    void workloadRegistrationAuthorityRemainsAnAdapterPrivateSecretRefProtocol()
            throws IOException {
        List<JavaSource> providerAdapters =
                moduleProductionSources("server/domains/agent-runtime/provider");
        JavaSource transport = providerAdapters.stream()
                .filter(source -> source.path().endsWith(
                        Path.of("agentruntime", "adapter",
                                "KeycloakClientRegistrationTransport.java")))
                .findFirst()
                .orElseThrow();
        JavaSource fileStore = providerAdapters.stream()
                .filter(source -> source.path().endsWith(
                        Path.of("agentruntime", "adapter",
                                "FileRuntimeWorkloadCredentialStore.java")))
                .findFirst()
                .orElseThrow();

        assertThat(transport.text())
                .contains(
                        "JsonNode create(\n"
                                + "            JsonNode metadata,\n"
                                + "            String administrationAccessToken,\n"
                                + "            RegistrationHandoffProof handoff)")
                .contains(
                        "JsonNode update(\n"
                                + "            String clientId,\n"
                                + "            URI registrationUri,\n"
                                + "            JsonNode metadata,\n"
                                + "            byte[] registrationAccessToken,\n"
                                + "            RegistrationHandoffProof handoff)")
                .contains("JsonNode recover(")
                .contains("FinalizeResult finalizeHandoff(")
                .doesNotContain(
                        "RegistrationRecovery",
                        "default JsonNode",
                        "default FinalizeResult",
                        "default void");
        assertThat(fileStore.text())
                .containsOnlyOnce("root.resolve(\"weave/agent-runtime/cells\")")
                .containsOnlyOnce(
                        "root.resolve(\"weave/agent-runtime/registration-handoffs\")")
                .containsOnlyOnce(
                        "root.resolve(\"weave/agent-runtime/registration-lifecycle-locks\")")
                .containsOnlyOnce(
                        "root.resolve(\"weave/agent-runtime/registration-deletions\")")
                .doesNotContain("RegistrationRecovery");
        assertThat(moduleProductionSources("server/domains/agent-runtime/model"))
                .allSatisfy(source -> assertThat(source.text())
                        .as(source.path().toString())
                        .doesNotContain(
                                "RegistrationAccessToken",
                                "registrationAccessToken",
                                "KeycloakClientRegistrationTransport",
                                "RegistrationHandoffProof"));
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
                .contains("weave_matrix_protocol");
    }

    private static List<JavaSource> productionSources() throws IOException {
        return persistenceSources();
    }

    private static JavaSource sourceEndingWith(Path suffix) throws IOException {
        return productionSources().stream()
                .filter(source -> source.path().endsWith(suffix))
                .findFirst()
                .orElseThrow();
    }

    private static List<JavaSource> persistenceSources() throws IOException {
        Path serverRoot = (Files.isDirectory(Path.of("src/main/java")) ? Path.of("..") : Path.of("server"))
                .toAbsolutePath().normalize();
        try (var paths = Files.walk(serverRoot)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/build/"))
                    .map(ServerArchitectureBoundaryTest::readSource).toList();
        }
    }

    private static List<JavaSource> moduleProductionSources(String module)
            throws IOException {
        Path moduleRoot = Files.isDirectory(Path.of("src/main/java"))
                ? Path.of("../..", module, "src/main/java")
                : Path.of(module, "src/main/java");
        try (var paths = Files.walk(moduleRoot)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(ServerArchitectureBoundaryTest::readSource)
                    .toList();
        }
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
        return Path.of("server/app/src/main/java");
    }

    private static boolean isDomainForbiddenImport(String importName) {
        return DOMAIN_FORBIDDEN_IMPORT_PREFIXES.stream().anyMatch(importName::startsWith);
    }

    private static boolean isProviderAdapterImport(String importName) {
        return PROVIDER_ADAPTER_IMPORT_PREFIXES.stream().anyMatch(importName::startsWith);
    }

    private static boolean isPublicDeliveryContract(JavaSource source) {
        return source.packageName().endsWith(".api")
                || source.packageName().contains(".api.")
                || source.packageName().equals(BACKEND_PACKAGE + "controller")
                || source.packageName().startsWith(BACKEND_PACKAGE + "controller.")
                || source.packageName().equals(BACKEND_PACKAGE + "model")
                || source.packageName().startsWith(BACKEND_PACKAGE + "model.");
    }

    private static boolean isProtocolProjectionOrMcpSurface(JavaSource source) {
        String className = source.path().getFileName().toString();
        return !source.packageName().equals(BACKEND_PACKAGE + "config")
                && !source.packageName().startsWith(BACKEND_PACKAGE + "config.")
                && !source.packageName().startsWith(BACKEND_PACKAGE + "chat.provider.")
                && !source.packageName().startsWith(BACKEND_PACKAGE + "calendar.provider.")
                && !source.packageName().startsWith(BACKEND_PACKAGE + "calendar.config")
                && (className.contains("Mcp")
                || className.contains("WebDav")
                || className.contains("CalDav")
                || className.contains("CardDav")
                || className.contains("Matrix"));
    }

    private static boolean isMemberNativeOrMcpContract(JavaSource source) {
        String path = source.path().toString().replace('\\', '/');
        String className = source.path().getFileName().toString();
        return path.contains("/files/api/FileNative")
                || path.contains("/calendar/api/CalendarNative")
                || path.contains("/calendar/api/CalendarClientSetup")
                || path.contains("/calendar/api/CalendarExternalEndpoints")
                || path.contains("/model/calls/CallNative")
                || path.contains("/calendar/provider/caldav/AppleMobileConfigProfileRenderer")
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
        return LEGACY_FILE_RUNTIME_STORE_ALLOWLIST.stream().anyMatch(path::endsWith)
                || ACCEPTED_FILE_KEY_CUSTODY_ALLOWLIST.stream().anyMatch(path::endsWith);
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
