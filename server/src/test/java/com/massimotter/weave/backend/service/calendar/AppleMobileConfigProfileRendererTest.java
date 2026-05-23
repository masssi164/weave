package com.massimotter.weave.backend.service.calendar;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AppleMobileConfigProfileRendererTest {

    @Test
    void rendersNoSecretAppleCaldavProfileMetadata() {
        AppleMobileConfigProfileRenderer renderer = new AppleMobileConfigProfileRenderer("https://files.weave.local");

        AppleMobileConfigProfile profile = renderer.renderUnsignedNoSecretProfile(
                new CalendarPrincipal("subject-123", "maria"));

        String plist = new String(profile.content(), StandardCharsets.UTF_8);
        assertThat(profile.filename()).isEqualTo("weave-calendar-maria.mobileconfig");
        assertThat(plist)
                .contains("<string>com.apple.caldav.account</string>")
                .contains("<key>CalDAVHostName</key>")
                .contains("<string>files.weave.local</string>")
                .contains("<key>CalDAVPort</key>")
                .contains("<integer>443</integer>")
                .contains("<key>CalDAVUseSSL</key>")
                .contains("<true/>")
                .contains("<key>CalDAVPrincipalURL</key>")
                .contains("<string>/remote.php/dav/principals/users/maria/</string>")
                .contains("<key>CalDAVUsername</key>")
                .contains("<string>maria</string>");
    }

    @Test
    void doesNotRenderPasswordBearerTokenOrBackendActorSecret() {
        AppleMobileConfigProfileRenderer renderer = new AppleMobileConfigProfileRenderer("https://files.weave.local");

        AppleMobileConfigProfile profile = renderer.renderUnsignedNoSecretProfile(
                new CalendarPrincipal("user-with-backend-secret-sentinel", "alice"));

        String plist = new String(profile.content(), StandardCharsets.UTF_8);
        assertThat(plist)
                .doesNotContain("CalDAVPassword")
                .doesNotContain("Password")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("backend-actor-token")
                .doesNotContain("WEAVE_CALDAV_BACKEND_TOKEN")
                .doesNotContain("user-with-backend-secret-sentinel");
    }
}
