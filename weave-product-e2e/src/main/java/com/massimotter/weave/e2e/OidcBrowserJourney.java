package com.massimotter.weave.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import tools.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

/** Real Chromium registration and Authorization Code + PKCE journey. */
final class OidcBrowserJourney implements AutoCloseable {
  private static final int MAX_BROWSER_STEPS = 12;
  private static final double BROWSER_TIMEOUT_MILLIS = 30_000;

  private final ProductFlowEnvironment environment;
  private final JsonHttpClient http;
  private final SecureRandom random = new SecureRandom();
  private final Playwright playwright;
  private final Browser browser;

  OidcBrowserJourney(ProductFlowEnvironment environment, JsonHttpClient http) {
    this.environment = environment;
    this.http = http;
    this.playwright = Playwright.create();
    this.browser =
        playwright
            .chromium()
            .launch(
                new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setArgs(
                        List.of(
                            "--host-resolver-rules=" + loopbackResolverRules(),
                            "--ignore-certificate-errors-spki-list=" + leafSpkiPin())));
  }

  private String leafSpkiPin() {
    try (var input = Files.newInputStream(environment.tlsLeafCertificate())) {
      Certificate certificate =
          CertificateFactory.getInstance("X.509").generateCertificate(input);
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(certificate.getPublicKey().getEncoded());
      return Base64.getEncoder().encodeToString(digest);
    } catch (GeneralSecurityException | IOException failure) {
      throw new ProductFlowException(
          "isolated browser TLS leaf pin could not be initialized", failure);
    }
  }

  private String loopbackResolverRules() {
    Set<String> hosts = new TreeSet<>();
    hosts.add(environment.apiOrigin().getHost());
    hosts.add(environment.issuer().getHost());
    hosts.add(environment.mcpEndpoint().getHost());
    return hosts.stream()
        .map(host -> "MAP " + host + " 127.0.0.1")
        .reduce((left, right) -> left + ", " + right)
        .orElseThrow(() -> new ProductFlowException("isolated host resolver is empty"));
  }

  void activate(URI actionLink, String email, String password, String displayName) {
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions().setLocale("en-US"))) {
      Page page = context.newPage();
      navigate(page, actionLink, "activation");
      boolean submitted = false;
      for (int step = 0; step < MAX_BROWSER_STEPS; step++) {
        fillIfVisible(page, "input[name='username']", username(email));
        fillIfVisible(page, "input[name='email']", email);
        fillIfVisible(page, "input[name='firstName']", firstName(displayName));
        fillIfVisible(page, "input[name='lastName']", lastName(displayName));
        fillIfVisible(page, "input[name='password']", password);
        fillIfVisible(page, "input[name='password-new']", password);
        fillIfVisible(page, "input[name='password-confirm']", password);
        checkVisibleCheckboxes(page);

        boolean actionPage = page.url().contains("/login-actions/");
        boolean credentialAction =
            visible(page, "input[name='password-new']")
                || visible(page, "input[name='password-confirm']")
                || visible(page, "input[name='firstName']")
                || visible(page, "input[name='lastName']");
        if (submitted && !actionPage && !credentialAction) {
          return;
        }
        Locator submit =
            page.locator(
                "form button[type='submit'], form input[type='submit'], #kc-form-buttons button");
        Locator visibleSubmit = firstVisible(submit);
        if (visibleSubmit == null) {
          if (submitted && !credentialAction) {
            return;
          }
          throw new ProductFlowException(
              "Keycloak activation did not expose the expected registration action");
        }
        try {
          visibleSubmit.click(
              new Locator.ClickOptions().setTimeout(BROWSER_TIMEOUT_MILLIS));
        } catch (PlaywrightException failure) {
          throw sanitized("Keycloak activation browser action failed", failure);
        }
        submitted = true;
        waitForPage(page);
        if (hasVisibleError(page)) {
          throw new ProductFlowException("Keycloak activation rejected the registration");
        }
      }
      throw new ProductFlowException("Keycloak activation exceeded the bounded browser steps");
    }
  }

  TokenSet authorize(
      String clientId,
      URI redirectUri,
      List<String> scopes,
      String email,
      String password) {
    String verifier = randomUrlSafe(64);
    String state = randomUrlSafe(32);
    String nonce = randomUrlSafe(32);
    URI authorization =
        uriWithQuery(
            environment.oidc("/protocol/openid-connect/auth"),
            Map.of(
                "client_id", clientId,
                "redirect_uri", redirectUri.toString(),
                "response_type", "code",
                "scope", String.join(" ", scopes),
                "state", state,
                "nonce", nonce,
                "code_challenge", sha256UrlSafe(verifier),
                "code_challenge_method", "S256"));

    URI callback;
    try (BrowserContext context =
        browser.newContext(
            new Browser.NewContextOptions().setLocale("en-US"))) {
      Page page = context.newPage();
      AtomicReference<String> observedCallback = new AtomicReference<>();
      page.onRequest(
          request -> captureCallback(request.url(), redirectUri, observedCallback));
      page.onFrameNavigated(
          frame -> captureCallback(frame.url(), redirectUri, observedCallback));
      navigate(page, authorization, "oidc");
      callback =
          authenticateAndAwaitCallback(
              page, redirectUri, observedCallback, email, password);
    }

    Map<String, String> callbackParameters = query(callback);
    if (!state.equals(callbackParameters.get("state"))) {
      throw new ProductFlowException("OIDC callback state did not match");
    }
    String code = callbackParameters.getOrDefault("code", "");
    if (code.isBlank()) {
      throw new ProductFlowException("OIDC callback did not contain an authorization code");
    }
    JsonNode token =
        http.form(
            "exchange OIDC authorization code",
            environment.oidc("/protocol/openid-connect/token"),
            Map.of(
                "grant_type", "authorization_code",
                "client_id", clientId,
                "code", code,
                "redirect_uri", redirectUri.toString(),
                "code_verifier", verifier),
            Set.of(200));
    TokenSet result = tokenSet(token, clientId, scopes, "");
    JsonNode idClaims = jwtPayload(result.idToken());
    if (!nonce.equals(idClaims.path("nonce").asString())) {
      throw new ProductFlowException("OIDC ID token nonce did not match");
    }
    return result;
  }

  TokenSet refresh(TokenSet current) {
    if (current.refreshToken().isBlank()) {
      throw new ProductFlowException("OIDC session did not contain a refresh token");
    }
    JsonNode token =
        http.form(
            "refresh OIDC session",
            environment.oidc("/protocol/openid-connect/token"),
            Map.of(
                "grant_type", "refresh_token",
                "client_id", current.clientId(),
                "refresh_token", current.refreshToken()),
            Set.of(200));
    return tokenSet(
        token,
        current.clientId(),
        current.requestedScopes(),
        current.idToken());
  }

  JsonNode jwtPayload(String token) {
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      throw new ProductFlowException("OIDC endpoint returned a non-JWT token");
    }
    try {
      return http.mapper().readTree(Base64.getUrlDecoder().decode(parts[1]));
    } catch (RuntimeException failure) {
      throw new ProductFlowException("OIDC endpoint returned an invalid JWT", failure);
    }
  }

  private TokenSet tokenSet(
      JsonNode token,
      String clientId,
      List<String> requestedScopes,
      String previousIdToken) {
    String accessToken = requiredText(token, "access_token");
    String idToken = token.path("id_token").asString(previousIdToken);
    if (idToken.isBlank()) {
      throw new ProductFlowException("OIDC endpoint omitted the required initial id_token");
    }
    String refreshToken = token.path("refresh_token").asString("");
    JsonNode claims = jwtPayload(accessToken);
    if (!environment.issuer().toString().equals(claims.path("iss").asString())
        || !clientId.equals(claims.path("azp").asString())
        || claims.path("sub").asString("").isBlank()
        || claims.path("exp").asLong(0) <= Instant.now().getEpochSecond()) {
      throw new ProductFlowException("OIDC access token claims did not match the request");
    }
    return new TokenSet(
        clientId,
        accessToken,
        refreshToken,
        idToken,
        claims.path("sub").asString(),
        List.copyOf(requestedScopes));
  }

  private URI awaitCallback(
      Page page, URI redirectUri, AtomicReference<String> observedCallback) {
    for (int step = 0; step < MAX_BROWSER_STEPS * 4; step++) {
      captureCallback(page.url(), redirectUri, observedCallback);
      String value = observedCallback.get();
      if (value != null) {
        return URI.create(value);
      }
      Locator submit = firstVisible(page.locator("form button[type='submit'], form input[type='submit']"));
      if (submit != null && !visible(page, "input[name='username']")) {
        try {
          submit.click(new Locator.ClickOptions().setTimeout(3_000));
        } catch (PlaywrightException ignored) {
          // Callback capture remains authoritative.
        }
      }
      page.waitForTimeout(250);
    }
    throw new ProductFlowException("OIDC browser did not reach the registered callback");
  }

  private URI authenticateAndAwaitCallback(
      Page page,
      URI redirectUri,
      AtomicReference<String> observedCallback,
      String email,
      String password) {
    boolean passwordSubmitted = false;
    for (int step = 0; step < MAX_BROWSER_STEPS; step++) {
      captureCallback(page.url(), redirectUri, observedCallback);
      String callback = observedCallback.get();
      if (callback != null) {
        return URI.create(callback);
      }

      Locator username = firstVisible(page.locator("input[name='username']"));
      Locator passwordInput = firstVisible(page.locator("input[name='password']"));
      if (username != null) {
        username.fill(email);
      }
      if (passwordInput != null) {
        passwordInput.fill(password);
      }
      if (username == null && passwordInput == null) {
        if (passwordSubmitted) {
          return awaitCallback(page, redirectUri, observedCallback);
        }
        throw new ProductFlowException("OIDC login did not expose a credential action");
      }

      Locator login = firstVisible(page.locator("#kc-login, form button[type='submit']"));
      if (login == null) {
        throw new ProductFlowException("OIDC login did not expose a submit action");
      }
      passwordSubmitted = passwordSubmitted || passwordInput != null;
      try {
        login.click(new Locator.ClickOptions().setTimeout(BROWSER_TIMEOUT_MILLIS));
      } catch (PlaywrightException ignored) {
        // A custom-scheme redirect can make Chromium report an unsupported navigation after
        // the request was already observed. The exact callback is still verified below.
      }
      waitForPage(page);
      if (hasVisibleError(page)) {
        throw new ProductFlowException("OIDC login rejected the credentials");
      }
      if (passwordSubmitted) {
        return awaitCallback(page, redirectUri, observedCallback);
      }
    }
    throw new ProductFlowException("OIDC login exceeded the bounded browser steps");
  }

  private static void captureCallback(
      String candidate, URI redirectUri, AtomicReference<String> target) {
    if (candidate != null && matchesRedirect(candidate, redirectUri)) {
      target.compareAndSet(null, candidate);
    }
  }

  private static boolean matchesRedirect(String candidate, URI expected) {
    try {
      URI value = URI.create(candidate);
      return value.getScheme().equalsIgnoreCase(expected.getScheme())
          && java.util.Objects.equals(value.getHost(), expected.getHost())
          && java.util.Objects.equals(value.getPath(), expected.getPath())
          && effectivePort(value) == effectivePort(expected);
    } catch (RuntimeException invalid) {
      return false;
    }
  }

  private static int effectivePort(URI uri) {
    if (uri.getPort() >= 0) {
      return uri.getPort();
    }
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }

  private void navigate(Page page, URI target, String operation) {
    AtomicReference<Integer> issuerResponseStatus = new AtomicReference<>();
    AtomicReference<String> redirectTarget = new AtomicReference<>("none");
    page.onResponse(
        response -> {
          if (isIssuerPage(response.url(), environment.issuer())) {
            issuerResponseStatus.set(response.status());
            if (response.status() >= 300 && response.status() < 400) {
              redirectTarget.set(
                  redirectTargetClass(response.url(), response.headers().get("location")));
            }
          }
        });
    try {
      page.navigate(
          target.toString(),
          new Page.NavigateOptions()
              .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
              .setTimeout(BROWSER_TIMEOUT_MILLIS));
    } catch (PlaywrightException failure) {
      String category = browserFailureCategory(failure.getMessage());
      String pageState = abortedPageState(page);
      if ("navigation-aborted".equals(category) && "issuer-form".equals(pageState)) {
        return;
      }
      throw sanitized(
          "browser navigation failed"
              + " operation="
              + operation
              + " category="
              + category
              + " pageState="
              + pageState
              + " issuerResponse="
              + statusClass(issuerResponseStatus.get())
              + " redirectTarget="
              + redirectTarget.get(),
          failure,
          false);
    }
  }

  private String abortedPageState(Page page) {
    waitForPage(page);
    try {
      String rawUrl = page.url();
      if ("about:blank".equals(rawUrl)) {
        return "blank";
      }
      URI candidate = URI.create(rawUrl);
      if (!"https".equalsIgnoreCase(candidate.getScheme())) {
        return "custom-scheme";
      }
      if (!isIssuerPage(rawUrl)) {
        return "other-origin";
      }
      return firstVisible(page.locator("form")) == null ? "issuer-no-form" : "issuer-form";
    } catch (PlaywrightException unstablePage) {
      return "unstable";
    } catch (RuntimeException invalidPageUrl) {
      return "invalid";
    }
  }

  boolean isIssuerPage(String rawUrl) {
    return isIssuerPage(rawUrl, environment.issuer());
  }

  static boolean isIssuerPage(String rawUrl, URI issuer) {
    try {
      URI candidate = URI.create(rawUrl);
      String issuerPath = issuer.getPath().replaceAll("/+$", "");
      return "https".equalsIgnoreCase(candidate.getScheme())
          && candidate.getHost() != null
          && candidate.getHost().equalsIgnoreCase(issuer.getHost())
          && effectivePort(candidate) == effectivePort(issuer)
          && candidate.getPath() != null
          && candidate.getPath().startsWith(issuerPath + "/")
          && candidate.getUserInfo() == null;
    } catch (RuntimeException invalid) {
      return false;
    }
  }

  private static void waitForPage(Page page) {
    try {
      page.waitForTimeout(350);
      page.waitForLoadState();
    } catch (PlaywrightException ignored) {
      // The next bounded DOM inspection determines convergence.
    }
  }

  private static void fillIfVisible(Page page, String selector, String value) {
    Locator locator = firstVisible(page.locator(selector));
    if (locator != null) {
      locator.fill(value);
    }
  }

  private static void checkVisibleCheckboxes(Page page) {
    Locator checkboxes = page.locator("form input[type='checkbox']");
    for (int index = 0; index < checkboxes.count(); index++) {
      Locator checkbox = checkboxes.nth(index);
      if (checkbox.isVisible() && !checkbox.isChecked()) {
        checkbox.check();
      }
    }
  }

  private static boolean visible(Page page, String selector) {
    return firstVisible(page.locator(selector)) != null;
  }

  private static Locator firstVisible(Locator candidates) {
    for (int index = 0; index < candidates.count(); index++) {
      Locator candidate = candidates.nth(index);
      if (candidate.isVisible()) {
        return candidate;
      }
    }
    return null;
  }

  private static boolean hasVisibleError(Page page) {
    return firstVisible(
            page.locator(
                "[role='alert'], .alert-error, .pf-m-danger, .kc-feedback-text"))
        != null;
  }

  private String randomUrlSafe(int bytes) {
    byte[] value = new byte[bytes];
    random.nextBytes(value);
    try {
      return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    } finally {
      java.util.Arrays.fill(value, (byte) 0);
    }
  }

  private static String sha256UrlSafe(String value) {
    try {
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static URI uriWithQuery(URI base, Map<String, String> parameters) {
    String query =
        parameters.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .reduce((left, right) -> left + "&" + right)
            .orElse("");
    return URI.create(base.toString() + "?" + query);
  }

  private static Map<String, String> query(URI uri) {
    Map<String, String> result = new LinkedHashMap<>();
    String raw = uri.getRawQuery();
    if (raw == null) {
      return Map.of();
    }
    for (String pair : raw.split("&")) {
      String[] parts = pair.split("=", 2);
      result.put(
          java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
          parts.length == 1
              ? ""
              : java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
    }
    return Map.copyOf(result);
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8)
        .replace("+", "%20");
  }

  private static String requiredText(JsonNode node, String field) {
    String value = node.path(field).asString("").trim();
    if (value.isEmpty()) {
      throw new ProductFlowException("OIDC token response omitted " + field);
    }
    return value;
  }

  private static String username(String email) {
    return email.substring(0, email.indexOf('@'));
  }

  private static String firstName(String displayName) {
    String[] parts = displayName.trim().split("\\s+", 2);
    return parts.length == 0 || parts[0].isBlank() ? "Weave" : parts[0];
  }

  private static String lastName(String displayName) {
    String[] parts = displayName.trim().split("\\s+", 2);
    return parts.length < 2 || parts[1].isBlank() ? "E2E" : parts[1];
  }

  private static ProductFlowException sanitized(String message, PlaywrightException cause) {
    return sanitized(message, cause, true);
  }

  private static ProductFlowException sanitized(
      String message, PlaywrightException cause, boolean appendCategory) {
    return new ProductFlowException(
        message
            + (appendCategory
                ? " category=" + browserFailureCategory(cause.getMessage())
                : ""),
        new IllegalStateException(cause.getClass().getName()));
  }

  static String statusClass(Integer status) {
    if (status == null) {
      return "none";
    }
    if (status >= 200 && status < 300) {
      return "2xx";
    }
    if (status >= 300 && status < 400) {
      return "3xx";
    }
    if (status >= 400 && status < 500) {
      return "4xx";
    }
    if (status >= 500 && status < 600) {
      return "5xx";
    }
    return "other";
  }

  static String redirectTargetClass(String responseUrl, String location) {
    if (location == null || location.isBlank()) {
      return "missing";
    }
    try {
      URI raw = URI.create(location);
      if (!raw.isAbsolute()) {
        return "issuer-relative";
      }
      URI issuer = URI.create(responseUrl);
      URI target = issuer.resolve(raw);
      if ("https".equalsIgnoreCase(target.getScheme())
          && target.getHost() != null
          && target.getHost().equalsIgnoreCase(issuer.getHost())
          && effectivePort(target) == effectivePort(issuer)) {
        return "issuer";
      }
      if ("https".equalsIgnoreCase(target.getScheme())) {
        return "other-https";
      }
      return "custom-scheme";
    } catch (RuntimeException invalid) {
      return "invalid";
    }
  }

  static String browserFailureCategory(String message) {
    if (message == null) {
      return "playwright";
    }
    if (message.contains("ERR_CERT_")) {
      return "tls";
    }
    if (message.contains("ERR_NAME_NOT_RESOLVED")) {
      return "dns";
    }
    if (message.contains("ERR_CONNECTION_REFUSED")) {
      return "connect-refused";
    }
    if (message.contains("ERR_CONNECTION_RESET")) {
      return "connection-reset";
    }
    if (message.contains("ERR_INVALID_URL")) {
      return "invalid-url";
    }
    if (message.contains("ERR_TOO_MANY_REDIRECTS")) {
      return "redirect-loop";
    }
    if (message.contains("ERR_ABORTED")) {
      return "navigation-aborted";
    }
    if (message.toLowerCase(java.util.Locale.ROOT).contains("timeout")) {
      return "timeout";
    }
    return "playwright";
  }

  @Override
  public void close() {
    browser.close();
    playwright.close();
  }

  record TokenSet(
      String clientId,
      String accessToken,
      String refreshToken,
      String idToken,
      String subject,
      List<String> requestedScopes) {
    TokenSet {
      requestedScopes = List.copyOf(requestedScopes);
    }
  }
}
