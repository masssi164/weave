package com.massimotter.weave.e2e;

import com.massimotter.weave.adminapi.model.ProviderSelectionRequest;
import com.massimotter.weave.adminapi.model.ProviderSelectionResponse;
import com.massimotter.weave.adminapi.model.MemberInvitationResponse;
import com.massimotter.weave.adminapi.model.OrganizationMemberResponse;
import com.massimotter.weave.userapi.model.ChatReadiness;
import com.massimotter.weave.userapi.model.IdentitySessionReconcileResponse;
import com.massimotter.weave.userapi.model.ProfileReadinessResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One-process Fresh product proof.
 *
 * <p>Human passwords, action links, OAuth tokens, and private JWKs are held only in this process.
 * The only durable output is an allowlisted support-safe evidence document.
 */
public final class FreshProductFlow {
  private static final Set<PosixFilePermission> OWNER_FILE_PERMISSIONS =
      PosixFilePermissions.fromString("rw-------");

  private final ProductFlowEnvironment environment;
  private final JsonHttpClient http;
  private final SecureRandom random = new SecureRandom();

  private FreshProductFlow(ProductFlowEnvironment environment) {
    this.environment = environment;
    this.http = new JsonHttpClient(environment.caCertificate());
  }

  public static void main(String[] arguments) {
    if (arguments.length != 0) {
      System.err.println("WEAVE_TEST_APP_ERROR command line arguments are not accepted");
      System.exit(2);
    }
    try {
      ProductFlowEnvironment environment = ProductFlowEnvironment.fromSystemProperties();
      System.setProperty("jdk.net.hosts.file", environment.hostsFile().toString());
      new FreshProductFlow(environment).run();
      System.out.println(
          "WEAVE_TEST_APP_RESULT status=passed activation=browser pkce=S256 "
              + "workload=private_key_jwt tools=files.search,calendar.agenda projection=user-api "
              + "userFiles=generated supportSafe=true");
    } catch (RuntimeException failure) {
      System.err.println(
          "WEAVE_TEST_APP_ERROR "
              + failure.getClass().getSimpleName()
              + " "
              + safeMessage(failure.getMessage()));
      System.exit(1);
    }
  }

  private void run() {
    Instant startedAt = Instant.now();
    String ownerPassword = randomPassword();
    String memberPassword = randomPassword();
    String outsiderPassword = randomPassword();
    String ownerEmail = environment.ownerEmail();
    String memberEmail = environment.memberEmail();
    String outsiderEmail = environment.outsiderEmail();
    OidcBrowserJourney.TokenSet memberSession = null;
    OidcBrowserJourney.TokenSet outsiderSession = null;
    OidcBrowserJourney.TokenSet adminSession = null;
    String personRef = null;
    JsonNode startedRuntime = null;
    WorkloadMcpJourney.McpProof mcpProof = null;
    PersistenceRestartJourney.RestartProof restartProof = null;
    boolean revocationDenied = false;
    boolean regrantRestored = false;
    boolean sameHumanSubjectAfterRegrant = false;
    boolean samePersonRefAfterRegrant = false;
    boolean spaceRevocationRestored = false;
    List<CollaborationJourney.PassProof> collaborationPasses = new java.util.ArrayList<>();

    try (OidcBrowserJourney browser = new OidcBrowserJourney(environment, http)) {
      MemberInvitationResponse ownerInvitation = bootstrapOwner(ownerEmail);
      String organizationId = ownerInvitation.getOrganizationId();
      if (organizationId == null || organizationId.isBlank()
          || !"owner".equals(ownerInvitation.getRequestedRole())
          || !ownerEmail.equalsIgnoreCase(ownerInvitation.getEmail())) {
        throw new ProductFlowException("Owner bootstrap returned an invalid invitation projection");
      }
      MailpitActivationInbox ownerInbox =
          new MailpitActivationInbox(
              http,
              environment.mailpitApi(),
              environment.issuer(),
              environment.convergenceTimeout());
      URI ownerAction =
          ownerInbox.awaitActivationLink(ownerEmail, startedAt.minusSeconds(5));
      Instant ownerRegistrationStartedAt = Instant.now();
      browser.activate(
          ownerAction,
          ownerEmail,
          ownerPassword,
          "Weave E2E Owner",
          () ->
              ownerInbox.awaitEmailVerificationLink(
                  ownerEmail, ownerRegistrationStartedAt.minusSeconds(5)));

      OidcBrowserJourney.TokenSet ownerSession =
          browser.authorize(
              "weave-app",
              URI.create("com.massimotter.weave:/oauthredirect"),
              List.of("openid", "profile", "email"),
              ownerEmail,
              ownerPassword,
              "owner-initial");
      validateHumanBootstrapToken(browser.jwtPayload(ownerSession.accessToken()), "weave-app");
      ownerSession =
          reconcileIdentitySession(
              browser, ownerSession, "owner", ownerEmail, ownerPassword);
      ownerSession = awaitAuthority(browser, ownerSession, "/owners", "owner");
      validateHumanWorkspaceToken(browser.jwtPayload(ownerSession.accessToken()), "weave-app");
      adminSession =
          browser.authorize(
              "weave-admin-console",
              environment.productOrigin().resolve("/admin-console/"),
              List.of("openid", "profile", "email", "agent-runtime.admin"),
              ownerEmail,
              ownerPassword,
              "owner-initial-admin");
      validateAdminToken(browser.jwtPayload(adminSession.accessToken()));
      assertSeparatedApiSessions(ownerSession.accessToken(), adminSession.accessToken());
      assertGeneratedAdminControlPlane(adminSession.accessToken(), organizationId);
      GeneratedSpacesJourney spaces = new GeneratedSpacesJourney(environment);
      spaces.provisionDefault(adminSession.accessToken(), ownerSession.accessToken());
      configureRequiredProviders(adminSession.accessToken());
      awaitChatReadiness(ownerSession.accessToken());

      Instant memberInvitedAt = Instant.now();
      inviteActor(
          organizationId,
          memberEmail,
          "Weave E2E Member",
          "member",
          adminSession.accessToken());
      MailpitActivationInbox memberInbox =
          new MailpitActivationInbox(
              http,
              environment.mailpitApi(),
              environment.issuer(),
              environment.convergenceTimeout());
      URI memberAction =
          memberInbox.awaitActivationLink(memberEmail, memberInvitedAt.minusSeconds(5));
      Instant memberRegistrationStartedAt = Instant.now();
      browser.activate(
          memberAction,
          memberEmail,
          memberPassword,
          "Weave E2E Member",
          () ->
              memberInbox.awaitEmailVerificationLink(
                  memberEmail, memberRegistrationStartedAt.minusSeconds(5)));
      memberSession =
          browser.authorize(
              "weave-app",
              URI.create("com.massimotter.weave:/oauthredirect"),
              List.of("openid", "profile", "email"),
              memberEmail,
              memberPassword,
              "member-initial");
      validateHumanBootstrapToken(browser.jwtPayload(memberSession.accessToken()), "weave-app");
      memberSession =
          reconcileIdentitySession(
              browser, memberSession, "member", memberEmail, memberPassword);
      setWeaverEntitlement(
          organizationId, memberEmail, adminSession.accessToken(), true, "initial");
      memberSession =
          awaitAuthority(
              browser, memberSession, "/capabilities/weaver", "agent-runtime.entitled");
      JsonNode memberClaims = browser.jwtPayload(memberSession.accessToken());
      validateHumanWorkspaceToken(memberClaims, "weave-app");
      spaces.verifyAbsent(memberSession.accessToken());
      spaces.grantEditor(adminSession.accessToken(), memberSession.accessToken(),
          accountId(environment.issuer().toString(), memberSession.subject()));
      String memberUsername = memberEmail.substring(0, memberEmail.indexOf('@'));
      if (!memberUsername.equals(memberClaims.path("preferred_username").asString())) {
        throw new ProductFlowException(
            "member token does not match the isolated Context principal");
      }
      proveMemberApi(memberSession.accessToken());

      Instant outsiderInvitedAt = Instant.now();
      inviteActor(
          organizationId,
          outsiderEmail,
          "Weave E2E Outsider",
          "guest",
          adminSession.accessToken());
      MailpitActivationInbox outsiderInbox =
          new MailpitActivationInbox(
              http,
              environment.mailpitApi(),
              environment.issuer(),
              environment.convergenceTimeout());
      URI outsiderAction =
          outsiderInbox.awaitActivationLink(outsiderEmail, outsiderInvitedAt.minusSeconds(5));
      Instant outsiderRegistrationStartedAt = Instant.now();
      browser.activate(
          outsiderAction,
          outsiderEmail,
          outsiderPassword,
          "Weave E2E Outsider",
          () ->
              outsiderInbox.awaitEmailVerificationLink(
                  outsiderEmail, outsiderRegistrationStartedAt.minusSeconds(5)));
      outsiderSession =
          browser.authorize(
              "weave-app",
              URI.create("com.massimotter.weave:/oauthredirect"),
              List.of("openid", "profile", "email"),
              outsiderEmail,
              outsiderPassword,
              "outsider-initial");
      validateHumanBootstrapToken(browser.jwtPayload(outsiderSession.accessToken()), "weave-app");
      outsiderSession =
          reconcileIdentitySession(
              browser, outsiderSession, "guest", outsiderEmail, outsiderPassword);
      outsiderSession = awaitAuthority(browser, outsiderSession, "/guests", "guest");
      validateHumanWorkspaceToken(browser.jwtPayload(outsiderSession.accessToken()), "weave-app");
      spaces.verifyAbsent(outsiderSession.accessToken());

      GeneratedFilesJourney generatedFiles = new GeneratedFilesJourney(environment);
      GeneratedFilesJourney.Proof generatedFilesProof =
          generatedFiles.createAndVerify(
              memberSession.accessToken(), outsiderSession.accessToken(), environment.runId());
      spaces.verifyOwnerOnlyFileRelation(memberSession.accessToken(), ownerSession.accessToken(),
          generatedFilesProof.fileId());

      String openClawMatrixMemberToken = null;
      if (Boolean.getBoolean("weave.e2e.release-mcp")) {
        OidcBrowserJourney.TokenSet openClawMatrixSession = browser.authorize(
            "weave-app",
            URI.create("com.massimotter.weave:/oauthredirect"),
            List.of("openid", "profile", "email"),
            memberEmail,
            memberPassword,
            "member-openclaw-matrix-device");
        JsonNode openClawClaims = browser.jwtPayload(openClawMatrixSession.accessToken());
        validateHumanWorkspaceToken(openClawClaims, "weave-app");
        JsonNode primaryClaims = browser.jwtPayload(memberSession.accessToken());
        String primarySessionId = primaryClaims.path("sid").asString();
        if (primarySessionId.isBlank()) {
          primarySessionId = primaryClaims.path("session_state").asString();
        }
        String openClawSessionId = openClawClaims.path("sid").asString();
        if (openClawSessionId.isBlank()) {
          openClawSessionId = openClawClaims.path("session_state").asString();
        }
        if (!memberSession.subject().equals(openClawMatrixSession.subject())
            || primarySessionId.isBlank() || openClawSessionId.isBlank()
            || primarySessionId.equals(openClawSessionId)) {
          throw new ProductFlowException(
              "OpenClaw Matrix needs a distinct normal member OIDC device session");
        }
        openClawMatrixMemberToken = openClawMatrixSession.accessToken();
      }

      CollaborationJourney collaboration = new CollaborationJourney(environment, http);
      collaborationPasses.add(
          collaboration.runPass(
              1,
              ownerSession,
              memberSession,
              outsiderSession,
              browser.jwtPayload(ownerSession.accessToken()),
              browser.jwtPayload(memberSession.accessToken()),
              browser.jwtPayload(outsiderSession.accessToken()),
              openClawMatrixMemberToken));
      collaboration.restartCollaborationServices();
      browser.awaitIssuerTransportAfterRestart();
      ownerSession =
          browser.authorize(
              "weave-app",
              URI.create("com.massimotter.weave:/oauthredirect"),
              List.of("openid", "profile", "email"),
              ownerEmail,
              ownerPassword,
              "owner-post-collaboration-restart");
      memberSession =
          browser.authorize(
              "weave-app",
              URI.create("com.massimotter.weave:/oauthredirect"),
              List.of("openid", "profile", "email"),
              memberEmail,
              memberPassword,
              "member-post-collaboration-restart");
      outsiderSession =
          browser.authorize(
              "weave-app",
              URI.create("com.massimotter.weave:/oauthredirect"),
              List.of("openid", "profile", "email"),
              outsiderEmail,
              outsiderPassword,
              "outsider-post-collaboration-restart");
      generatedFiles.verify(
          generatedFilesProof, memberSession.accessToken(), outsiderSession.accessToken());
      new GeneratedWorkspaceJourney(environment).verifyHome(memberSession.accessToken());
      collaborationPasses.add(
          collaboration.runPass(
              2,
              ownerSession,
              memberSession,
              outsiderSession,
              browser.jwtPayload(ownerSession.accessToken()),
              browser.jwtPayload(memberSession.accessToken()),
              browser.jwtPayload(outsiderSession.accessToken()),
              null));

      adminSession =
          browser.authorize(
              "weave-admin-console",
              environment.productOrigin().resolve("/admin-console/"),
              List.of("openid", "profile", "email", "agent-runtime.admin"),
              ownerEmail,
              ownerPassword,
              "owner-agent-runtime-admin");
      validateAdminToken(browser.jwtPayload(adminSession.accessToken()));

      personRef =
          accountId(environment.issuer().toString(), memberSession.subject());
      GeneratedCalendarJourney generatedCalendar = new GeneratedCalendarJourney(environment);
      GeneratedCalendarJourney.Proof mcpCalendarProof = generatedCalendar.createAndVerify(
          ownerSession.accessToken(), memberSession.accessToken(),
          outsiderSession.accessToken(), environment.runId() + "-mcp");
      GeneratedFilesJourney.Proof mcpTextProof =
          generatedFiles.createMcpTextFile(memberSession.accessToken(), environment.runId());
      if (Boolean.getBoolean("weave.e2e.release-mcp")) {
        ReleaseMcpJourney release = new ReleaseMcpJourney(environment, http);
        release.bind(memberSession.subject(), personRef, true);
        mcpProof = release.files(mcpTextProof);
        release.calendar(mcpCalendarProof);
        release.verifyCalendarWriteDenied(mcpCalendarProof.calendarId());
        String openClawVersion = release.proveOpenClawFiles(mcpTextProof);
        if (!openClawVersion.equals(release.proveOpenClawCalendar(mcpCalendarProof))) {
          throw new ProductFlowException("OpenClaw client version changed within one proof");
        }

        restartProof = new PersistenceRestartJourney(environment, http).restart();
        WorkloadMcpJourney.McpProof afterRestart = release.files(mcpTextProof);
        release.calendar(mcpCalendarProof);
        if (!mcpProof.equals(afterRestart)) {
          throw new ProductFlowException("release MCP binding changed across service restart");
        }

        String originalSubject = memberSession.subject();
        release.bind(originalSubject, personRef, false);
        if (!releaseMcpDenied(release, mcpTextProof)) {
          throw new ProductFlowException("revoked release binding retained Files access");
        }
        setWeaverEntitlement(
            organizationId, memberEmail, adminSession.accessToken(), false, "revoke");
        memberSession = awaitAuthorityAbsent(
            browser, memberSession, "/capabilities/weaver", "agent-runtime.entitled");
        release.bind(originalSubject, personRef, true);
        if (!releaseMcpDenied(release, mcpTextProof)) {
          throw new ProductFlowException("removed Weaver entitlement retained Files access");
        }
        revocationDenied = true;
        if (!releaseCalendarDenied(release, mcpCalendarProof)) {
          throw new ProductFlowException("removed Weaver entitlement retained Calendar access");
        }
        boolean calendarRevocationDenied = true;

        setWeaverEntitlement(
            organizationId, memberEmail, adminSession.accessToken(), true, "regrant");
        memberSession = awaitAuthority(
            browser, memberSession, "/capabilities/weaver", "agent-runtime.entitled");
        sameHumanSubjectAfterRegrant = originalSubject.equals(memberSession.subject());
        samePersonRefAfterRegrant = personRef.equals(
            accountId(environment.issuer().toString(), memberSession.subject()));
        if (!sameHumanSubjectAfterRegrant || !samePersonRefAfterRegrant) {
          throw new ProductFlowException("Weaver regrant replaced the immutable member identity");
        }
        regrantRestored = mcpProof.equals(release.files(mcpTextProof));
        release.calendar(mcpCalendarProof);
        if (!regrantRestored) {
          throw new ProductFlowException("release MCP access did not recover after regrant");
        }
        spaces.verifyRevocationAndVersionedRegrant(adminSession.accessToken(),
            memberSession.accessToken(), personRef, generatedFilesProof.fileId());
        spaceRevocationRestored = true;
        generatedCalendar.delete(mcpCalendarProof, ownerSession.accessToken());
        writeEvidence(startedAt, ownerEmail, memberEmail, outsiderEmail,
            release.bindingRef(), mcpProof, restartProof, revocationDenied,
            calendarRevocationDenied, regrantRestored, sameHumanSubjectAfterRegrant,
            samePersonRefAfterRegrant, spaceRevocationRestored, collaborationPasses,
            true, openClawVersion);
      } else {
        JsonNode provisioned = provisionRuntime(personRef, adminSession.accessToken());
        startedRuntime = startRuntime(personRef, adminSession.accessToken(), provisioned);
        WorkloadMcpJourney mcpJourney = new WorkloadMcpJourney(environment, http);
        String cellRef = requiredText(startedRuntime, "cellRef");
        mcpProof = mcpJourney.invokeFilesSearch(cellRef, mcpTextProof);
        mcpJourney.invokeCalendarAgenda(cellRef, mcpCalendarProof);
        mcpJourney.verifyCalendarWriteDeniedForMember(cellRef, mcpCalendarProof.calendarId());

        restartProof = new PersistenceRestartJourney(environment, http).restart();
        JsonNode persistedRuntime =
            getRuntime(personRef, adminSession.accessToken());
        requireSameRuntime(startedRuntime, persistedRuntime);
        WorkloadMcpJourney.McpProof postRestartMcpProof =
            new WorkloadMcpJourney(environment, http)
                .invokeFilesSearch(requiredText(startedRuntime, "cellRef"), mcpTextProof);
        new WorkloadMcpJourney(environment, http)
            .invokeCalendarAgenda(cellRef, mcpCalendarProof);
        if (!mcpProof.equals(postRestartMcpProof)) {
          throw new ProductFlowException(
              "the same Cell MCP projection did not persist across service restarts");
        }

        String originalSubject = memberSession.subject();
        setWeaverEntitlement(
            organizationId, memberEmail, adminSession.accessToken(), false, "revoke");
        memberSession =
            awaitAuthorityAbsent(
                browser, memberSession, "/capabilities/weaver", "agent-runtime.entitled");
        JsonNode revokedRuntime = reconcileRuntime(personRef, adminSession.accessToken(), "revoke");
        if (!"revoked".equals(revokedRuntime.path("entitlementState").asString())) {
          throw new ProductFlowException("ARC reconciliation did not revoke the unentitled cell");
        }
        try {
          new WorkloadMcpJourney(environment, http)
              .invokeFilesSearch(requiredText(startedRuntime, "cellRef"), mcpTextProof);
        } catch (ProductFlowException expectedDenial) {
          revocationDenied = true;
        }
        if (!revocationDenied) {
          throw new ProductFlowException("revoked cell remained able to invoke MCP");
        }
        boolean calendarRevocationDenied = false;
        try {
          new WorkloadMcpJourney(environment, http)
              .invokeCalendarAgenda(cellRef, mcpCalendarProof);
        } catch (ProductFlowException expectedDenial) {
          calendarRevocationDenied = true;
        }
        if (!calendarRevocationDenied) {
          throw new ProductFlowException("revoked cell remained able to read Calendar over MCP");
        }

        setWeaverEntitlement(
            organizationId, memberEmail, adminSession.accessToken(), true, "regrant");
        memberSession =
            awaitAuthority(
                browser, memberSession, "/capabilities/weaver", "agent-runtime.entitled");
        sameHumanSubjectAfterRegrant = originalSubject.equals(memberSession.subject());
        String regrantedPersonRef =
            accountId(environment.issuer().toString(), memberSession.subject());
        samePersonRefAfterRegrant = personRef.equals(regrantedPersonRef);
        if (!sameHumanSubjectAfterRegrant || !samePersonRefAfterRegrant) {
          throw new ProductFlowException("Weaver regrant replaced the immutable human identity");
        }
        JsonNode regrantedRuntime =
            provisionRuntime(regrantedPersonRef, adminSession.accessToken(), "regrant");
        JsonNode restartedRuntime =
            startRuntime(regrantedPersonRef, adminSession.accessToken(), regrantedRuntime, "regrant");
        requireSameRuntimeIdentity(startedRuntime, restartedRuntime);
        WorkloadMcpJourney.McpProof postRegrantMcpProof =
            new WorkloadMcpJourney(environment, http)
                .invokeFilesSearch(requiredText(restartedRuntime, "cellRef"), mcpTextProof);
        new WorkloadMcpJourney(environment, http)
            .invokeCalendarAgenda(cellRef, mcpCalendarProof);
        regrantRestored = mcpProof.equals(postRegrantMcpProof);
        if (!regrantRestored) {
          throw new ProductFlowException(
              "the regranted Cell did not restore the same MCP projection");
        }

        spaces.verifyRevocationAndVersionedRegrant(adminSession.accessToken(),
            memberSession.accessToken(), personRef, generatedFilesProof.fileId());
        spaceRevocationRestored = true;
        generatedCalendar.delete(mcpCalendarProof, ownerSession.accessToken());

        writeEvidence(
            startedAt,
            ownerEmail,
            memberEmail,
            outsiderEmail,
            requiredText(startedRuntime, "cellRef"),
            mcpProof,
            restartProof,
            revocationDenied,
            calendarRevocationDenied,
            regrantRestored,
            sameHumanSubjectAfterRegrant,
            samePersonRefAfterRegrant,
            spaceRevocationRestored,
            collaborationPasses,
            false,
            "");
      }
    } finally {
      // Avoid retaining references longer than the single bounded JVM run.
      ownerPassword = "";
      memberPassword = "";
      outsiderPassword = "";
      memberSession = null;
      outsiderSession = null;
      adminSession = null;
      personRef = null;
      startedRuntime = null;
      mcpProof = null;
      restartProof = null;
    }
  }

  private MemberInvitationResponse bootstrapOwner(String email) {
    return new GeneratedAdminApi(environment.apiOrigin(), environment.caCertificate())
        .bootstrapOwner(readBootstrapToken(), "test-app-owner-" + runHash(), email);
  }

  private void inviteActor(
      String organizationId,
      String email,
      String displayName,
      String role,
      String accessToken) {
    MemberInvitationResponse invitation =
        new GeneratedAdminApi(environment.apiOrigin(), environment.caCertificate())
            .inviteMember(
                accessToken,
                organizationId,
                "test-app-" + role + "-" + runHash(),
                email,
                displayName,
                role);
    if (!role.equals(invitation.getRequestedRole())
        || !organizationId.equals(invitation.getOrganizationId())
        || !email.equalsIgnoreCase(invitation.getEmail())) {
      throw new ProductFlowException(role + " invitation projection is invalid");
    }
  }

  private void setWeaverEntitlement(
      String organizationId,
      String email,
      String accessToken,
      boolean entitled,
      String operation) {
    GeneratedAdminApi adminApi = new GeneratedAdminApi(environment.apiOrigin(), environment.caCertificate());
    var page = adminApi.listMembers(accessToken, organizationId);
    OrganizationMemberResponse member = null;
    for (OrganizationMemberResponse candidate : page.getItems()) {
      if (email.equalsIgnoreCase(candidate.getEmail())) {
        if (member != null) {
          throw new ProductFlowException("member projection is ambiguous");
        }
        member = candidate;
      }
    }
    if (member == null) {
      throw new ProductFlowException("activated member is missing");
    }
    if (member.getMemberHandle() == null || member.getMemberHandle().isBlank()
        || member.getVersion() == null || member.getVersion().isBlank()) {
      throw new ProductFlowException("member projection omitted stable handle or version");
    }
    OrganizationMemberResponse updated =
        adminApi.updateWeaverEntitlement(
            accessToken,
            organizationId,
            member.getMemberHandle(),
            member.getVersion(),
            "test-app-weaver-" + operation + "-" + runHash(),
            entitled);
    Set<String> expected = entitled ? Set.of("agent-runtime.entitled") : Set.of();
    if (updated.getCapabilities() == null
        || !Set.copyOf(updated.getCapabilities()).equals(expected)) {
      throw new ProductFlowException("native Weaver capability mutation did not converge");
    }
  }

  private OidcBrowserJourney.TokenSet awaitAuthority(
      OidcBrowserJourney browser,
      OidcBrowserJourney.TokenSet initial,
      String group,
      String role) {
    Instant deadline = Instant.now().plus(environment.convergenceTimeout());
    OidcBrowserJourney.TokenSet current = initial;
    Set<String> observedGroups = Set.of();
    Set<String> observedRoles = Set.of();
    Set<String> observedScopes = Set.of();
    while (Instant.now().isBefore(deadline)) {
      JsonNode claims = browser.jwtPayload(current.accessToken());
      observedGroups = organizationGroups(claims);
      observedRoles = organizationRoles(claims, "weave-app");
      observedScopes = tokenScopes(claims);
      if (observedGroups.contains(group) || observedRoles.contains(role)) {
        return current;
      }
      sleep();
      current = browser.refresh(current);
    }
    throw new ProductFlowException(
        "Keycloak role and capability projection did not converge"
            + " expectedGroup="
            + group
            + " expectedRole="
            + role
            + " observedGroups="
            + new java.util.TreeSet<>(observedGroups)
            + " observedRoles="
            + new java.util.TreeSet<>(observedRoles)
            + " observedScopes="
            + new java.util.TreeSet<>(observedScopes));
  }

  private OidcBrowserJourney.TokenSet awaitAuthorityAbsent(
      OidcBrowserJourney browser,
      OidcBrowserJourney.TokenSet initial,
      String group,
      String role) {
    Instant deadline = Instant.now().plus(environment.convergenceTimeout());
    OidcBrowserJourney.TokenSet current = initial;
    while (Instant.now().isBefore(deadline)) {
      current = browser.refresh(current);
      JsonNode claims = browser.jwtPayload(current.accessToken());
      if (!organizationGroups(claims).contains(group)
          && !organizationRoles(claims, "weave-app").contains(role)) {
        return current;
      }
      sleep();
    }
    throw new ProductFlowException("revoked Weaver authority remained in the human session");
  }

  private void proveMemberApi(String token) {
    new GeneratedWorkspaceJourney(environment).verifyHome(token);
    ProfileReadinessResponse readiness =
        new GeneratedUserApi(environment.apiOrigin(), environment.caCertificate())
            .profileReadiness(token);
    if (!Boolean.TRUE.equals(readiness.getSupportSafe())) {
      throw new ProductFlowException("profile readiness was not support-safe");
    }
  }

  private void configureRequiredProviders(String ownerToken) {
    GeneratedAdminApi adminApi =
        new GeneratedAdminApi(environment.apiOrigin(), environment.caCertificate());
    List<ProviderSelection> requiredProviders =
        List.of(
            new ProviderSelection("chat", "weave-native"),
            new ProviderSelection("calendar", "weave-native"));
    for (ProviderSelection selection : requiredProviders) {
      ProviderSelectionResponse response =
          adminApi.selectProvider(
              ownerToken,
              new ProviderSelectionRequest()
                  .category(selection.category())
                  .providerKey(selection.providerKey())
                  .choiceModel("recommended_self_hosted_default")
                  .dryRun(false)
                  .lossyMappingNotes(List.of())
                  .reason("configure the isolated Fresh Stack product path"));
      if (!selection.category().equals(response.getCategory())
          || !selection.providerKey().equals(response.getProviderKey())
          || !"recommended_self_hosted_default".equals(response.getChoiceModel())
          || !Boolean.TRUE.equals(response.getApplied())
          || !Boolean.FALSE.equals(response.getDryRun())
          || !Boolean.TRUE.equals(response.getSupportSafe())) {
        throw new ProductFlowException(
            selection.category() + " provider selection did not converge");
      }
    }

    // The fresh stack starts with its native Files binding. An Admin selection
    // must not silently replace it until a real migration has verified data,
    // references, and effective permissions against both providers.
    ProviderSelectionResponse dryRun =
        adminApi.selectProvider(
            ownerToken,
            new ProviderSelectionRequest()
                .category("files")
                .providerKey("weave-native")
                .choiceModel("recommended_self_hosted_default")
                .dryRun(true)
                .lossyMappingNotes(List.of())
                .reason("verify Files activation remains fenced"));
    if (!"files".equals(dryRun.getCategory())
        || !Boolean.TRUE.equals(dryRun.getDryRun())
        || !Boolean.FALSE.equals(dryRun.getApplied())
        || !Boolean.TRUE.equals(dryRun.getSupportSafe())) {
      throw new ProductFlowException("Files selection dry-run was not support-safe and unapplied");
    }

    ObjectNode filesRequest = http.mapper().createObjectNode();
    filesRequest.put("category", "files");
    filesRequest.put("providerKey", "weave-native");
    filesRequest.put("choiceModel", "recommended_self_hosted_default");
    filesRequest.putArray("lossyMappingNotes");
    filesRequest.put("reason", "verify Files activation remains fenced");
    filesRequest.put("dryRun", false);
    JsonNode blocked =
        http.json(
            "reject unverified Files provider activation",
            "POST",
            environment.api("/api/admin/providers/selections"),
            bearer(ownerToken, Map.of()),
            filesRequest,
            Set.of(409));
    if (!"files-provider-activation-unverified".equals(blocked.path("code").asString())) {
      throw new ProductFlowException("unverified Files provider activation did not fail closed");
    }
  }

  private void awaitChatReadiness(String ownerToken) {
    Instant deadline = Instant.now().plus(environment.convergenceTimeout());
    String observedState = "unavailable";
    GeneratedUserApi userApi = new GeneratedUserApi(environment.apiOrigin(), environment.caCertificate());
    while (Instant.now().isBefore(deadline)) {
      ChatReadiness readiness = userApi.chatReadiness(ownerToken);
      observedState = readiness.getMemberState() == null
          ? "unavailable" : readiness.getMemberState().getValue();
      if (readiness.getMemberState() == ChatReadiness.MemberStateEnum.AVAILABLE
          && "chat".equals(readiness.getDomain())
          && Boolean.FALSE.equals(readiness.getFailClosed())
          && Boolean.TRUE.equals(readiness.getSupportSafe())) {
        return;
      }
      sleep();
    }
    throw new ProductFlowException(
        "Chat provider readiness did not converge observedState=" + observedState);
  }

  private OidcBrowserJourney.TokenSet reconcileIdentitySession(
      OidcBrowserJourney browser,
      OidcBrowserJourney.TokenSet session,
      String expectedRole,
      String email,
      String password) {
    IdentitySessionReconcileResponse response =
        new GeneratedUserApi(environment.apiOrigin(), environment.caCertificate())
            .reconcileIdentitySession(session.accessToken());
    if (response.getState() != IdentitySessionReconcileResponse.StateEnum.ACCESS_UPDATED
        || !Boolean.TRUE.equals(response.getReauthorizationRequired())) {
      throw new ProductFlowException(
          "identity session did not apply the pending " + expectedRole + " intent");
    }
    return browser.authorize(
        "weave-app",
        URI.create("com.massimotter.weave:/oauthredirect"),
        session.requestedScopes(),
        email,
        password,
        expectedRole + "-post-session-reconcile");
  }

  private void validateAdminToken(JsonNode claims) {
    String organizationViolation = NativeOrganizationClaims.violation(claims);
    if (organizationViolation != null) {
      throw new ProductFlowException("Admin token contract did not match " + organizationViolation);
    }
    Set<String> audiences = strings(claims.path("aud"));
    Set<String> scopes = tokenScopes(claims);
    if (!"weave-admin-console".equals(claims.path("azp").asString())
        || !audiences.equals(Set.of(environment.apiOrigin().resolve("/api").toString()))
        || !hasExactWorkspaceScope(scopes)
        || !scopes.contains("agent-runtime.admin")
        || !organizationGroups(claims).contains("/owners")
        || !organizationRoles(claims, "weave-app").contains("owner")) {
      throw new ProductFlowException("Agent Runtime admin token is not exact");
    }
  }

  private void assertSeparatedApiSessions(String userToken, String adminToken) {
    for (String path : List.of("/api/admin/workspace/capability-policy", "/api/admin/workspace/release-readiness")) {
      JsonNode denied = http.json("reject User session at Admin workspace diagnostics", "GET",
          environment.api(path), bearer(userToken, Map.of()), null, Set.of(401));
      if (!"unauthorized".equals(denied.path("code").asString())) {
        throw new ProductFlowException("Admin workspace diagnostics accepted a User session");
      }
    }
    JsonNode providerStatusDenied =
        http.json(
            "reject User session at Admin provider status",
            "GET",
            environment.api("/api/admin/providers/status"),
            bearer(userToken, Map.of()),
            null,
            Set.of(401));
    JsonNode adminDenied =
        http.json(
            "reject User session at Admin API",
            "GET",
            environment.api("/api/admin/control-plane"),
            bearer(userToken, Map.of()),
            null,
            Set.of(401));
    JsonNode userDenied =
        http.json(
            "reject Admin session at User API",
            "GET",
            environment.api("/api/me"),
            bearer(adminToken, Map.of()),
            null,
            Set.of(401));
    if (!"unauthorized".equals(adminDenied.path("code").asString())
        || !"unauthorized".equals(providerStatusDenied.path("code").asString())
        || !"unauthorized".equals(userDenied.path("code").asString())) {
      throw new ProductFlowException("User and Admin API sessions were not separated");
    }
  }

  private void assertGeneratedAdminControlPlane(String adminToken, String organizationId) {
    var admin = new GeneratedAdminApi(environment.apiOrigin(), environment.caCertificate());
    admin.verifyWorkspaceDiagnostics(adminToken);
    var controlPlane = admin.controlPlane(adminToken);
    if (!organizationId.equals(controlPlane.getOrganizationId())
        || !Boolean.TRUE.equals(controlPlane.getSupportSafe())) {
      throw new ProductFlowException("Admin control plane identity or projection did not match");
    }
    var status = admin.providerStatus(adminToken);
    var binding = status.getFilesBinding();
    if (!environment.tenantId().equals(status.getOrganizationId())
        || !Boolean.TRUE.equals(status.getSupportSafe())
        || binding == null
        || binding.getBindingState()
            != com.massimotter.weave.adminapi.model.FilesBindingStatusResponse.BindingStateEnum.ACTIVE
        || binding.getBindingRevision() == null
        || binding.getBindingRevision() < 1
        || !"weave-native".equals(binding.getAdapterKey())
        || binding.getReadiness()
            != com.massimotter.weave.adminapi.model.FilesBindingStatusResponse.ReadinessEnum.CONFIGURED) {
      throw new ProductFlowException("Admin status did not match the isolated Files binding");
    }
  }

  private void validateHumanWorkspaceToken(JsonNode claims, String clientId) {
    validateHumanToken(claims, clientId, true);
  }

  private void validateHumanBootstrapToken(JsonNode claims, String clientId) {
    validateHumanToken(claims, clientId, false);
  }

  private void validateHumanToken(
      JsonNode claims, String clientId, boolean workspaceAccessExpected) {
    Set<String> scopes = tokenScopes(claims);
    Set<String> invalidClaims = new java.util.TreeSet<>();
    if (!clientId.equals(claims.path("azp").asString())) {
      invalidClaims.add("authorized-party");
    }
    String explicitClientId = claims.path("client_id").asString("");
    if (!explicitClientId.isBlank() && !clientId.equals(explicitClientId)) {
      invalidClaims.add("client-id");
    }
    if (!strings(claims.path("aud"))
        .contains(environment.apiOrigin().resolve("/api").toString())) {
      invalidClaims.add("audience");
    }
    if (claims.path("email").asString("").isBlank()) {
      invalidClaims.add("email");
    }
    if (!claims.path("email_verified").asBoolean(false)) {
      invalidClaims.add("email-verified");
    }
    if (!hasExactWorkspaceScope(scopes)) {
      invalidClaims.add("workspace-scope");
    }
    String organizationViolation = NativeOrganizationClaims.violation(claims);
    if (organizationViolation != null) {
      invalidClaims.add(organizationViolation);
    }
    Set<String> organizationRoles = organizationRoles(claims, "weave-app");
    Set<String> productRoles = Set.of("owner", "admin", "member", "guest");
    long productRoleCount = organizationRoles.stream().filter(productRoles::contains).count();
    if (workspaceAccessExpected && productRoleCount != 1) {
      invalidClaims.add("selected-organization-role");
    }
    if (!workspaceAccessExpected && productRoleCount != 0) {
      invalidClaims.add("premature-selected-organization-role");
    }
    if (strings(claims.path("resource_access").path("weave-app").path("roles"))
        .stream()
        .anyMatch(productRoles::contains)) {
      invalidClaims.add("top-level-product-role");
    }
    if (!invalidClaims.isEmpty()) {
      throw new ProductFlowException(
          (workspaceAccessExpected ? "human workspace token" : "human bootstrap token")
              + " contract did not match fields="
              + String.join(",", invalidClaims)
              + " observedScopes="
              + new java.util.TreeSet<>(scopes));
    }
  }

  private static boolean hasExactWorkspaceScope(Set<String> scopes) {
    return scopes.contains("weave:workspace") && !scopes.contains("weave-workspace");
  }

  private static Set<String> tokenScopes(JsonNode claims) {
    Set<String> result = new java.util.LinkedHashSet<>();
    for (String scope : claims.path("scope").asString("").trim().split("\\s+")) {
      if (!scope.isBlank()) {
        result.add(scope);
      }
    }
    return Set.copyOf(result);
  }

  private JsonNode provisionRuntime(String personRef, String token) {
    return provisionRuntime(personRef, token, "initial");
  }

  private JsonNode provisionRuntime(String personRef, String token, String operation) {
    JsonNode response =
        http.json(
            "provision Agent Runtime cell",
            "POST",
            runtimeUri(personRef, "/provision"),
            bearer(
                token,
                Map.of(
                    "Idempotency-Key",
                    "test-app-provision-" + operation + "-" + runHash())),
            null,
            Set.of(202));
    if (!personRef.equals(response.path("personRef").asString())
        || !"entitled".equals(response.path("entitlementState").asString())) {
      throw new ProductFlowException("ARC provision projection is not entitled");
    }
    return response;
  }

  private JsonNode startRuntime(String personRef, String token, JsonNode provisioned) {
    return startRuntime(personRef, token, provisioned, "initial");
  }

  private JsonNode startRuntime(
      String personRef, String token, JsonNode provisioned, String operation) {
    JsonNode response =
        http.json(
            "start Agent Runtime cell",
            "POST",
            runtimeUri(personRef, "/start"),
            bearer(
                token,
                Map.of(
                    "Idempotency-Key",
                    "test-app-start-" + operation + "-" + runHash())),
            null,
            Set.of(202));
    if (!requiredText(provisioned, "cellRef").equals(response.path("cellRef").asString())
        || response.path("runtimeProfileRef").asString("").isBlank()) {
      throw new ProductFlowException("ARC start did not issue the current RuntimeProfile");
    }
    return response;
  }

  private JsonNode reconcileRuntime(String personRef, String token, String operation) {
    return http.jsonRetryingDependencyUnavailable(
        "reconcile Agent Runtime entitlement",
        "POST",
        runtimeUri(personRef, "/reconcile"),
        bearer(
            token,
            Map.of(
                "Idempotency-Key",
                "test-app-reconcile-" + operation + "-" + runHash())),
        null,
        Set.of(202),
        6,
        Duration.ofSeconds(2));
  }

  private JsonNode getRuntime(String personRef, String token) {
    return http.json(
        "read persisted Agent Runtime cell after service restart",
        "GET",
        runtimeUri(personRef, ""),
        bearer(token, Map.of()),
        null,
        Set.of(200));
  }

  private static void requireSameRuntime(JsonNode before, JsonNode after) {
    for (String field :
        List.of(
            "personRef",
            "cellRef",
            "runtimeProfileRef",
            "entitlementRevision",
            "entitlementState",
            "desiredState")) {
      if (!requiredText(before, field).equals(requiredText(after, field))) {
        throw new ProductFlowException(
            "the JPA Cell projection changed across PostgreSQL restart field=" + field);
      }
    }
  }

  private static void requireSameRuntimeIdentity(JsonNode before, JsonNode after) {
    for (String field : List.of("personRef", "cellRef")) {
      if (!requiredText(before, field).equals(requiredText(after, field))) {
        throw new ProductFlowException(
            "the Weaver regrant replaced the immutable Runtime Cell field=" + field);
      }
    }
  }

  private URI runtimeUri(String personRef, String operation) {
    return environment.api(
        "/api/admin/agent-runtimes/" + encodeSegment(personRef) + operation);
  }

  private static boolean releaseMcpDenied(
      ReleaseMcpJourney release, GeneratedFilesJourney.Proof proof) {
    try {
      release.files(proof);
      return false;
    } catch (ProductFlowException denied) {
      if (!isExpectedReleaseDenial(denied)) {
        throw denied;
      }
      return true;
    }
  }

  private static boolean releaseCalendarDenied(
      ReleaseMcpJourney release, GeneratedCalendarJourney.Proof proof) {
    try {
      release.calendar(proof);
      return false;
    } catch (ProductFlowException denied) {
      if (!isExpectedReleaseDenial(denied)) {
        throw denied;
      }
      return true;
    }
  }

  private static boolean isExpectedReleaseDenial(ProductFlowException denied) {
    String message = denied.getMessage();
    return message != null && (message.contains("http-403")
        || message.contains("HTTP 403"));
  }

  private void writeEvidence(
      Instant startedAt,
      String ownerEmail,
      String memberEmail,
      String outsiderEmail,
      String cellRef,
      WorkloadMcpJourney.McpProof mcpProof,
      PersistenceRestartJourney.RestartProof restartProof,
      boolean revocationDenied,
      boolean calendarRevocationDenied,
      boolean regrantRestored,
      boolean sameHumanSubjectAfterRegrant,
      boolean samePersonRefAfterRegrant,
      boolean spaceRevocationRestored,
      List<CollaborationJourney.PassProof> collaborationPasses,
      boolean releaseMcp,
      String openClawVersion) {
    ObjectNode evidence = http.mapper().createObjectNode();
    evidence.put("schemaVersion", releaseMcp
        ? "weave.test-app-product-flow/v3-release" : "weave.test-app-product-flow/v2");
    evidence.put("startedAt", startedAt.toString());
    evidence.put("completedAt", Instant.now().toString());
    evidence.put("candidateCommit", environment.candidateCommit());
    evidence.put("sourceCandidateCommit", environment.sourceCandidateCommit());
    evidence.put("specificationCommit", environment.specificationCommit());
    evidence.put("candidateManifestDigest", environment.candidateManifestDigest());
    evidence.put("composeProject", environment.composeProject());
    evidence.put("runIdSha256", Hashing.sha256(environment.runId()));
    evidence.put("ownerEmailSha256", Hashing.sha256(ownerEmail));
    evidence.put("memberEmailSha256", Hashing.sha256(memberEmail));
    evidence.put("outsiderEmailSha256", Hashing.sha256(outsiderEmail));
    if (releaseMcp) {
      evidence.put("releaseBindingRefSha256", Hashing.sha256(cellRef));
      evidence.put("arcCellCreated", false);
      evidence.put("sameReleaseBindingAfterRestart", true);
      evidence.put("openClawClientVersion", openClawVersion);
      evidence.put("openClawFilesInvoked", true);
      evidence.put("openClawCalendarInvoked", true);
    } else {
      evidence.put("cellRefSha256", Hashing.sha256(cellRef));
    }
    evidence.put("activation", "keycloak-required-actions-real-chromium");
    evidence.put("humanOAuth", "authorization_code_pkce_s256");
    evidence.put("workloadOAuth", "client_credentials_private_key_jwt");
    evidence.put("mcpTool", mcpProof.toolName());
    evidence.put("calendarMcpAgenda", true);
    evidence.put("calendarMcpWrongScopeDenied", true);
    evidence.put("calendarMcpRevocationDenied", calendarRevocationDenied);
    evidence.put("serverProjection", mcpProof.serverProjection());
    evidence.put("canonicalResourceSeen", mcpProof.canonicalResourceSeen());
    evidence.put("postgresRestartObserved", restartProof.postgresRestartObserved());
    evidence.put(
        "runtimeStateRestartObserved", restartProof.runtimeStateRestartObserved());
    evidence.put(
        "runtimeStateFixtureRestored", restartProof.runtimeStateFixtureRestored());
    if (!releaseMcp) {
      evidence.put("sameJpaCellAfterRestart", true);
      evidence.put("sameMcpCellAfterRestart", true);
    }
    evidence.put(
        "persistenceRestartEvidenceSha256",
        "sha256:" + restartProof.evidenceSha256());
    evidence.put("revocationDenied", revocationDenied);
    evidence.put("regrantRestored", regrantRestored);
    evidence.put("sameHumanSubjectAfterRegrant", sameHumanSubjectAfterRegrant);
    evidence.put("samePersonRefAfterRegrant", samePersonRefAfterRegrant);
    evidence.put("spaceRevocationRestored", spaceRevocationRestored);
    if (collaborationPasses.size() != 2
        || collaborationPasses.get(0).pass() != 1
        || collaborationPasses.get(1).pass() != 2
        || !collaborationPasses.get(0).authorIdentityRefHash()
            .equals(collaborationPasses.get(1).authorIdentityRefHash())
        || !collaborationPasses.get(0).collaboratorIdentityRefHash()
            .equals(collaborationPasses.get(1).collaboratorIdentityRefHash())
        || !collaborationPasses.get(0).outsiderIdentityRefHash()
            .equals(collaborationPasses.get(1).outsiderIdentityRefHash())) {
      throw new ProductFlowException("two-pass collaboration identity binding is incomplete");
    }
    ObjectNode collaboration = evidence.putObject("collaboration");
    collaboration.put("repeatCount", 2);
    ObjectNode selectedProviders = collaboration.putObject("selectedProviders");
    selectedProviders.put("chat", "weave-native");
    selectedProviders.put("files", "weave-native");
    selectedProviders.put("calendar", "weave-native");
    ObjectNode northboundContracts = collaboration.putObject("northboundContracts");
    northboundContracts.put("matrix", "matrix-client-server");
    northboundContracts.put("files", "weave-user-api");
    northboundContracts.put("calendar", "weave-user-api");
    collaboration.put("southboundProviderDependencyObserved", false);
    ObjectNode identityHashes = collaboration.putObject("identityRefHashes");
    identityHashes.put("author", collaborationPasses.get(0).authorIdentityRefHash());
    identityHashes.put(
        "collaborator", collaborationPasses.get(0).collaboratorIdentityRefHash());
    identityHashes.put("outsider", collaborationPasses.get(0).outsiderIdentityRefHash());
    var passes = collaboration.putArray("passes");
    for (CollaborationJourney.PassProof pass : collaborationPasses) {
      ObjectNode item = passes.addObject();
      item.put("pass", pass.pass());
      item.put("freshAuthorizationCodePkce", pass.freshAuthorizationCodePkce());
      item.put("chatPassed", pass.chatPassed());
      item.put("filesPassed", pass.filesPassed());
      item.put("calendarPassed", pass.calendarPassed());
      item.put("homePassed", pass.homePassed());
      item.put("profilePassed", pass.profilePassed());
      item.put("outsiderDenied", pass.outsiderDenied());
      item.put("canonicalJpaVerified", pass.canonicalJpaVerified());
      item.put("nativePersistenceVerified", pass.nativePersistenceVerified());
      item.put("idempotencyVerified", pass.idempotencyVerified());
      item.put(
          "southboundProviderDependencyObserved",
          pass.southboundProviderDependencyObserved());
      item.put("restartContinuityVerified", pass.restartContinuityVerified());
      item.put("cleanupComplete", pass.cleanupComplete());
      item.put(
          "nativeRevisionHash", "sha256:" + pass.nativeRevisionHash());
    }
    evidence.put("credentialsIncluded", false);
    evidence.put("actionLinksIncluded", false);
    evidence.put("supportSafe", true);
    String serialized;
    try {
      serialized =
          http.mapper()
              .writerWithDefaultPrettyPrinter()
              .writeValueAsString(evidence)
              + "\n";
    } catch (JacksonException failure) {
      throw new ProductFlowException("testApp evidence encoding failed", failure);
    }
    if (serialized.contains("/protocol/openid-connect/registrations")
        || serialized.contains(ownerEmail)
        || serialized.contains(memberEmail)
        || serialized.contains(outsiderEmail)
        || serialized.contains(mcpProof.clientId())) {
      throw new ProductFlowException("testApp evidence failed secret-safe validation");
    }
    Path target = environment.evidenceFile();
    Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
    try {
      Files.createDirectories(target.getParent());
      Files.writeString(temporary, serialized, StandardCharsets.UTF_8);
      setPrivatePermissions(temporary);
      Files.move(
          temporary,
          target,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
      setPrivatePermissions(target);
    } catch (IOException | UnsupportedOperationException failure) {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
        // The incomplete file contains support-safe hashes only.
      }
      throw new ProductFlowException("testApp evidence could not be persisted", failure);
    }
  }

  private String readBootstrapToken() {
    Path path = environment.bootstrapOwnerToken();
    try {
      if (Files.isSymbolicLink(path)
          || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
        throw new ProductFlowException("owner bootstrap SecretRef is unavailable");
      }
      requirePrivatePermissions(path);
      String token = Files.readString(path, StandardCharsets.UTF_8).strip();
      if (token.getBytes(StandardCharsets.UTF_8).length < 32
          || token.getBytes(StandardCharsets.UTF_8).length > 512) {
        throw new ProductFlowException("owner bootstrap SecretRef has an invalid size");
      }
      return token;
    } catch (IOException failure) {
      throw new ProductFlowException("owner bootstrap SecretRef could not be read", failure);
    }
  }

  private String randomPassword() {
    byte[] value = new byte[32];
    random.nextBytes(value);
    try {
      return "Wv!7" + Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    } finally {
      java.util.Arrays.fill(value, (byte) 0);
    }
  }

  private String runHash() {
    return Hashing.sha256(environment.runId()).substring(0, 24);
  }

  private static String accountId(String issuer, String subject) {
    String identityKey = "issuer+subject:" + issuer + "#" + subject;
    return "acct_" + Hashing.sha256(identityKey).substring(0, 32);
  }

  private static Map<String, String> bearer(
      String token, Map<String, String> additional) {
    Map<String, String> headers = new java.util.LinkedHashMap<>();
    headers.put("Authorization", "Bearer " + token);
    headers.putAll(additional);
    return Map.copyOf(headers);
  }

  private static Set<String> strings(JsonNode node) {
    if (node.isString()) {
      return Set.of(node.asString());
    }
    if (!node.isArray()) {
      return Set.of();
    }
    Set<String> result = new java.util.LinkedHashSet<>();
    node.forEach(value -> result.add(value.asString()));
    return Set.copyOf(result);
  }

  private static Set<String> organizationGroups(JsonNode claims) {
    JsonNode selected = selectedOrganization(claims);
    return selected == null ? Set.of() : strings(selected.path("groups"));
  }

  private static Set<String> organizationRoles(JsonNode claims, String clientId) {
    JsonNode selected = selectedOrganization(claims);
    return selected == null
        ? Set.of()
        : strings(selected.path("resource_access").path(clientId).path("roles"));
  }

  private static JsonNode selectedOrganization(JsonNode claims) {
    JsonNode organizations = claims.path("organization");
    if (!organizations.isObject() || organizations.size() != 1) {
      return null;
    }
    for (JsonNode value : organizations.values()) {
      return value;
    }
    return null;
  }

  private static String requiredText(JsonNode node, String field) {
    String value = node.path(field).asString("").trim();
    if (value.isEmpty()) {
      throw new ProductFlowException("product response omitted " + field);
    }
    return value;
  }

  private static String encodeSegment(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static void requirePrivatePermissions(Path path) throws IOException {
    try {
      Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
      Set<PosixFilePermission> forbidden =
          EnumSet.of(
              PosixFilePermission.GROUP_READ,
              PosixFilePermission.GROUP_WRITE,
              PosixFilePermission.GROUP_EXECUTE,
              PosixFilePermission.OTHERS_READ,
              PosixFilePermission.OTHERS_WRITE,
              PosixFilePermission.OTHERS_EXECUTE);
      if (!java.util.Collections.disjoint(permissions, forbidden)) {
        throw new ProductFlowException("SecretRef permissions are too broad");
      }
    } catch (UnsupportedOperationException ignored) {
      // Regular-file and no-symlink checks remain binding on non-POSIX file systems.
    }
  }

  private static void setPrivatePermissions(Path path) throws IOException {
    try {
      Files.setPosixFilePermissions(path, OWNER_FILE_PERMISSIONS);
    } catch (UnsupportedOperationException ignored) {
      // The test runner still writes into its private isolated evidence directory.
    }
  }

  private static void sleep() {
    try {
      Thread.sleep(1_000);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new ProductFlowException("identity convergence wait was interrupted", interrupted);
    }
  }

  private static String safeMessage(String message) {
    if (message == null || message.isBlank()) {
      return "unspecified-failure";
    }
    return message
        .replaceAll("https?://\\S+", "[uri-redacted]")
        .replaceAll("(?i)bearer\\s+\\S+", "bearer [redacted]")
        .replaceAll("(?i)(token|password|assertion)=\\S+", "$1=[redacted]");
  }

  private record ProviderSelection(String category, String providerKey) {}
}
