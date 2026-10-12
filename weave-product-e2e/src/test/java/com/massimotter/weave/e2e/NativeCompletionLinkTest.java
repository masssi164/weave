package com.massimotter.weave.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

final class NativeCompletionLinkTest {
  private static final URI ORIGIN = URI.create("https://weave.localhost:48083");
  private static final String LINK = "weave://join?organization_origin=https%3A%2F%2Fweave.localhost%3A48083";

  @Test
  void acceptsOnlyTheActualCanonicalCompletionAnchor() {
    String page = "<a id=\"open-weave\" href=\"" + LINK + "\">Open Weave</a>";
    assertEquals(LINK, FreshProductFlow.nativeCompletionLinkFromPage(page, ORIGIN));
    assertEquals(LINK, FreshProductFlow.nativeCompletionLinkFromPage(page, URI.create(ORIGIN + "/")));
    String rootSlashLink = LINK + "%2F";
    assertEquals(rootSlashLink, FreshProductFlow.nativeCompletionLinkFromPage(
        page.replace(LINK, rootSlashLink), ORIGIN));
    for (String invalid : new String[] {
        "<a href=\"" + LINK + "\">Open Weave</a>",
        page + page,
        page.replace("weave.localhost%3A48083", "foreign.example"),
        page.replace("weave://join", "https://auth.weave.localhost"),
        page.replace(LINK, LINK + "&amp;private_token=credential"),
        page.replace(LINK, LINK + "%26private_token%3Dcredential"),
        page.replace(LINK, LINK + "%2Fprivate"),
        page.replace(LINK, LINK + "%3Fprivate_token%3Dcredential"),
        page.replace(LINK, LINK + "%23fragment"),
        page.replace("https%3A", "http%3A"),
        page.replace("%3A48083", "%3A48084")
    }) {
      assertThrows(ProductFlowException.class,
          () -> FreshProductFlow.nativeCompletionLinkFromPage(invalid, ORIGIN));
    }
  }
}
