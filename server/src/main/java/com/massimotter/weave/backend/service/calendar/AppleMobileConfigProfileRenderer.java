package com.massimotter.weave.backend.service.calendar;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class AppleMobileConfigProfileRenderer {

    private final String nextcloudBaseUrl;

    public AppleMobileConfigProfileRenderer(String nextcloudBaseUrl) {
        this.nextcloudBaseUrl = nextcloudBaseUrl == null || nextcloudBaseUrl.isBlank()
                ? "https://files.weave.local"
                : nextcloudBaseUrl.trim();
    }

    public AppleMobileConfigProfile renderUnsignedNoSecretProfile(CalendarPrincipal principal) {
        URI baseUri = URI.create(nextcloudBaseUrl);
        String host = firstNonBlank(baseUri.getHost(), "files.weave.local");
        boolean ssl = !"http".equalsIgnoreCase(firstNonBlank(baseUri.getScheme(), "https"));
        int port = baseUri.getPort() > 0 ? baseUri.getPort() : (ssl ? 443 : 80);
        String username = firstNonBlank(principal.nextcloudUserId(), principal.subject());
        String principalPath = "/remote.php/dav/principals/users/" + pathSegment(username) + "/";
        String safeUsername = sanitizeFilename(username);

        String profileUuid = stableUuid("weave-calendar-profile:" + principal.subject());
        String payloadUuid = stableUuid("weave-calendar-caldav:" + principal.subject() + ":" + username);

        String plist = """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">
                <plist version=\"1.0\">
                <dict>
                    <key>PayloadContent</key>
                    <array>
                        <dict>
                            <key>PayloadType</key>
                            <string>com.apple.caldav.account</string>
                            <key>PayloadVersion</key>
                            <integer>1</integer>
                            <key>PayloadIdentifier</key>
                            <string>local.weave.calendar.caldav.%s</string>
                            <key>PayloadUUID</key>
                            <string>%s</string>
                            <key>PayloadDisplayName</key>
                            <string>Weave Calendar</string>
                            <key>PayloadDescription</key>
                            <string>Secret-free CalDAV account metadata. The password is intentionally omitted; use a revocable per-client Nextcloud app password or login-flow credential.</string>
                            <key>CalDAVAccountDescription</key>
                            <string>Weave workspace calendar</string>
                            <key>CalDAVHostName</key>
                            <string>%s</string>
                            <key>CalDAVPort</key>
                            <integer>%d</integer>
                            <key>CalDAVUseSSL</key>
                            <%s/>
                            <key>CalDAVPrincipalURL</key>
                            <string>%s</string>
                            <key>CalDAVUsername</key>
                            <string>%s</string>
                        </dict>
                    </array>
                    <key>PayloadType</key>
                    <string>Configuration</string>
                    <key>PayloadVersion</key>
                    <integer>1</integer>
                    <key>PayloadIdentifier</key>
                    <string>local.weave.calendar.%s</string>
                    <key>PayloadUUID</key>
                    <string>%s</string>
                    <key>PayloadDisplayName</key>
                    <string>Weave Calendar</string>
                    <key>PayloadDescription</key>
                    <string>Weave Calendar CalDAV setup profile. Contains no password, bearer token, backend actor credential, or other static secret.</string>
                    <key>PayloadOrganization</key>
                    <string>Weave</string>
                    <key>PayloadRemovalDisallowed</key>
                    <false/>
                </dict>
                </plist>
                """.formatted(
                xml(safeUsername),
                payloadUuid,
                xml(host),
                port,
                ssl ? "true" : "false",
                xml(principalPath),
                xml(username),
                xml(safeUsername),
                profileUuid);

        return new AppleMobileConfigProfile(
                "weave-calendar-" + safeUsername + ".mobileconfig",
                plist.getBytes(StandardCharsets.UTF_8));
    }

    private String stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    private String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String sanitizeFilename(String value) {
        String sanitized = value == null ? "user" : value.replaceAll("[^A-Za-z0-9._-]", "-");
        return sanitized.isBlank() ? "user" : sanitized;
    }

    private String xml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
