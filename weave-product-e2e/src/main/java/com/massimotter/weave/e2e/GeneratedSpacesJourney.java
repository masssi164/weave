package com.massimotter.weave.e2e;

import com.massimotter.weave.adminapi.api.SpacesAdminApi;
import com.massimotter.weave.adminapi.model.SpaceMemberChangeRequest;
import com.massimotter.weave.adminapi.model.SpaceMemberResponse;
import com.massimotter.weave.adminapi.model.SpaceProvisionRequest;
import com.massimotter.weave.adminapi.model.SpaceProvisionResponse;
import com.massimotter.weave.userapi.api.SpacesUserApi;
import com.massimotter.weave.userapi.model.SpaceRelationshipResponse;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Real Admin setup and member admission through the generated, separate HTTP clients. */
final class GeneratedSpacesJourney {
  private static final String DEFAULT_SPACE = "workspace-default";
  private final SpacesAdminApi admin;
  private final SpacesUserApi user;

  GeneratedSpacesJourney(ProductFlowEnvironment environment) {
    String base = environment.apiOrigin().getScheme() + "://"
        + environment.apiOrigin().getRawAuthority();
    var adminClient = new com.massimotter.weave.adminapi.invoker.ApiClient()
        .setHttpClientBuilder(HttpClient.newBuilder()
            .sslContext(JsonHttpClient.sslContext(environment.caCertificate()))
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER))
        .setReadTimeout(Duration.ofSeconds(30));
    adminClient.updateBaseUri(base);
    admin = new SpacesAdminApi(adminClient);
    var userClient = new com.massimotter.weave.userapi.invoker.ApiClient()
        .setHttpClientBuilder(HttpClient.newBuilder()
            .sslContext(JsonHttpClient.sslContext(environment.caCertificate()))
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER))
        .setReadTimeout(Duration.ofSeconds(30));
    userClient.updateBaseUri(base);
    user = new SpacesUserApi(userClient);
  }

  void provisionDefault(String adminToken, String ownerToken) {
    try {
      SpaceProvisionResponse created = admin.provisionSpace(
          new SpaceProvisionRequest().spaceRef(DEFAULT_SPACE).memberAccountRefs(List.of()),
          bearer(adminToken));
      if (created == null || !DEFAULT_SPACE.equals(created.getSpaceRef())
          || created.getResult() != SpaceProvisionResponse.ResultEnum.CREATED) {
        throw new ProductFlowException("Admin Space setup did not create the durable default Space");
      }
      assertVisible(ownerToken, true);
    } catch (com.massimotter.weave.adminapi.invoker.ApiException
             | com.massimotter.weave.userapi.invoker.ApiException failure) {
      throw new ProductFlowException("Generated Space provisioning failed: "
          + failure.getClass().getSimpleName());
    }
  }

  void grantEditor(String adminToken, String memberToken, String memberAccountRef) {
    try {
      SpaceMemberResponse granted = admin.putSpaceMember(DEFAULT_SPACE, memberAccountRef,
          new SpaceMemberChangeRequest()
              .permissionLevel(SpaceMemberChangeRequest.PermissionLevelEnum.EDIT),
          null, "*", bearer(adminToken));
      if (granted == null || !memberAccountRef.equals(granted.getAccountRef())
          || !granted.getPermissions().contains(SpaceMemberResponse.PermissionsEnum.VIEW)
          || !granted.getPermissions().contains(SpaceMemberResponse.PermissionsEnum.EDIT)
          || granted.getPermissions().contains(SpaceMemberResponse.PermissionsEnum.ADMIN)) {
        throw new ProductFlowException("Admin Space grant did not produce current editor rights");
      }
      assertVisible(memberToken, true);
    } catch (com.massimotter.weave.adminapi.invoker.ApiException
             | com.massimotter.weave.userapi.invoker.ApiException failure) {
      throw new ProductFlowException("Generated Space membership grant failed: "
          + failure.getClass().getSimpleName());
    }
  }

  void verifyAbsent(String token) {
    try {
      assertVisible(token, false);
    } catch (com.massimotter.weave.userapi.invoker.ApiException failure) {
      throw new ProductFlowException("Generated denied Space listing failed with HTTP "
          + failure.getCode());
    }
  }

  void verifyOwnerOnlyFileRelation(String memberToken, String ownerToken, String fileId) {
    try {
      boolean memberVisible = containsFile(memberToken, fileId);
      boolean ownerVisible = containsFile(ownerToken, fileId);
      if (!memberVisible || ownerVisible) {
        throw new ProductFlowException(
            "Direct Space File relation did not preserve current owner-only visibility"
                + " memberVisible=" + memberVisible + " ownerVisible=" + ownerVisible);
      }
    } catch (com.massimotter.weave.userapi.invoker.ApiException failure) {
      throw new ProductFlowException("Generated Space relation read failed with HTTP "
          + failure.getCode());
    }
  }

  private boolean containsFile(String token, String fileId)
      throws com.massimotter.weave.userapi.invoker.ApiException {
    String after = null;
    for (int page = 0; page < 10; page++) {
      var result = user.listSpaceRelationships(DEFAULT_SPACE, after, 100, bearer(token));
      if (result == null || result.getRelationships() == null) {
        throw new ProductFlowException("Generated Space relation page was unavailable");
      }
      if (result.getRelationships().stream().anyMatch(relation ->
          relation.getTargetKind() == SpaceRelationshipResponse.TargetKindEnum.FILE
              && fileId.equals(relation.getTargetRef())
              && relation.getRelationRef() != null
              && relation.getRelationRef().startsWith("relation:file:"))) {
        return true;
      }
      String next = result.getNextAfterRelationRef();
      if (next == null) {
        return false;
      }
      if (next.equals(after)) {
        throw new ProductFlowException("Generated Space relation cursor did not advance");
      }
      after = next;
    }
    throw new ProductFlowException("Generated Space relation scan exceeded the bounded proof window");
  }

  private void assertVisible(String token, boolean expected)
      throws com.massimotter.weave.userapi.invoker.ApiException {
    var page = user.listSpaces(null, 10, bearer(token));
    boolean visible = page != null && page.getSpaces() != null
        && page.getSpaces().stream().anyMatch(space -> DEFAULT_SPACE.equals(space.getSpaceRef()));
    if (visible != expected) {
      throw new ProductFlowException("Current Space visibility differs from the Admin grant");
    }
    if (expected && !DEFAULT_SPACE.equals(user.getSpace(DEFAULT_SPACE, bearer(token)).getSpaceRef())) {
      throw new ProductFlowException("Generated User Space read did not match its list reference");
    }
  }

  private static Map<String, String> bearer(String token) {
    return Map.of("Authorization", "Bearer " + token);
  }
}
