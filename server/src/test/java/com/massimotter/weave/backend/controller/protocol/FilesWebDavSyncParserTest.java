package com.massimotter.weave.backend.controller.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.massimotter.weave.backend.controller.protocol.FilesWebDavSyncParser.DavCondition;
import com.massimotter.weave.backend.controller.protocol.FilesWebDavSyncParser.SyncRequestException;
import com.massimotter.weave.backend.service.files.WebDavSyncRequest;
import com.massimotter.weave.backend.service.files.WebDavSyncRequest.SyncLevel;
import java.nio.charset.StandardCharsets;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class FilesWebDavSyncParserTest {

    @Test
    void parsesInitialRequestAndPreservesPortableAndExtensionPropertyQNames() {
        MockHttpServletRequest request = request("/dav/files/Team", """
                <d:sync-collection xmlns:d="DAV:" xmlns:x="urn:extension" x:mode="ignored">
                  <x:before/>
                  <d:sync-token/>
                  <d:sync-level>1</d:sync-level>
                  <x:between/>
                  <d:limit><d:nresults>999999999999999999999</d:nresults></d:limit>
                  <d:prop><d:getetag/><x:future-property/></d:prop>
                </d:sync-collection>
                """);
        request.addHeader("Depth", "0");

        WebDavSyncRequest parsed = FilesWebDavSyncParser.parse(request);

        assertThat(parsed.collectionPath()).isEqualTo("/Team");
        assertThat(parsed.initial()).isTrue();
        assertThat(parsed.syncLevel()).isEqualTo(SyncLevel.ONE);
        assertThat(parsed.properties()).containsExactly(
                new QName("DAV:", "getetag"),
                new QName("urn:extension", "future-property"));
        assertThat(parsed.limit()).isEqualTo(100);
        assertThat(parsed.clientLimitSupplied()).isTrue();
    }

    @Test
    void parsesIncrementalInfiniteRequestWithDefaultLimitAndDefaultDepthZero() {
        WebDavSyncRequest parsed = FilesWebDavSyncParser.parse(request("/dav/files", """
                <s:sync-collection xmlns:s="DAV:">
                  <s:sync-token>urn:weave:files:sync:v1:opaque.signature</s:sync-token>
                  <s:sync-level>infinite</s:sync-level>
                  <s:prop/>
                </s:sync-collection>
                """));

        assertThat(parsed.collectionPath()).isEqualTo("/");
        assertThat(parsed.syncToken()).isEqualTo("urn:weave:files:sync:v1:opaque.signature");
        assertThat(parsed.initial()).isFalse();
        assertThat(parsed.syncLevel()).isEqualTo(SyncLevel.INFINITE);
        assertThat(parsed.properties()).isEmpty();
        assertThat(parsed.limit()).isEqualTo(100);
        assertThat(parsed.clientLimitSupplied()).isFalse();
    }

    @Test
    void rejectsAnotherDepthBeforeXmlOrStateAccess() {
        MockHttpServletRequest request = request("/dav/files", validBody("", "1", ""));
        request.addHeader("Depth", "1");

        SyncRequestException failure = failure(request);

        assertThat(failure.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(failure.davCondition()).isEmpty();
    }

    @Test
    void enforcesTheRfcDefinedSemanticElementOrderAndCardinality() {
        for (String body : new String[] {
            """
            <d:sync-collection xmlns:d="DAV:">
              <d:sync-level>1</d:sync-level><d:sync-token/><d:prop/>
            </d:sync-collection>
            """,
            """
            <d:sync-collection xmlns:d="DAV:">
              <d:sync-token/><d:sync-token/><d:sync-level>1</d:sync-level><d:prop/>
            </d:sync-collection>
            """,
            """
            <d:sync-collection xmlns:d="DAV:">
              <d:sync-token/><d:sync-level>1</d:sync-level>
            </d:sync-collection>
            """,
            """
            <d:sync-collection xmlns:d="DAV:">
              <d:sync-token><d:href>nested</d:href></d:sync-token>
              <d:sync-level>1</d:sync-level><d:prop/>
            </d:sync-collection>
            """
        }) {
            assertThat(failure(request("/dav/files", body)).status())
                    .as(body)
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    void rejectsUnsupportedSyncLevelAndInvalidLimits() {
        assertThat(failure(request("/dav/files", validBody("", "0", ""))).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(failure(request("/dav/files", validBody("", "2", ""))).status())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        for (String limit : new String[] {"", "-1", "+1", "1.0"}) {
            SyncRequestException failure = failure(request(
                    "/dav/files",
                    validBody("", "1", "<d:limit><d:nresults>" + limit + "</d:nresults></d:limit>")));
            assertThat(failure.status()).as(limit).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        WebDavSyncRequest zero = FilesWebDavSyncParser.parse(request(
                "/dav/files",
                validBody("", "1", "<d:limit><d:nresults>0</d:nresults></d:limit>")));
        assertThat(zero.limit()).isZero();
    }

    @Test
    void enforcesMediaTypeAndBodyByteCeilingsBeforeXmlParsing() {
        MockHttpServletRequest missingMedia = request("/dav/files", validBody("", "1", ""));
        missingMedia.setContentType(null);
        assertThat(failure(missingMedia).status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        MockHttpServletRequest json = request("/dav/files", "{}");
        json.setContentType("application/json");
        assertThat(failure(json).status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        MockHttpServletRequest unsupportedParameter = request("/dav/files", "<not-xml");
        unsupportedParameter.setContentType("application/xml; profile=provider");
        assertThat(failure(unsupportedParameter).status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        MockHttpServletRequest tooLarge = request("/dav/files", "x".repeat(65_537));
        assertThat(failure(tooLarge).status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);

        MockHttpServletRequest textXml = request("/dav/files", validBody("", "1", ""));
        textXml.setContentType("text/xml; charset=UTF-8");
        assertThat(FilesWebDavSyncParser.parse(textXml).syncLevel()).isEqualTo(SyncLevel.ONE);
    }

    @Test
    void rejectsExternalEntitiesXincludeAndExternalSchemaHintsWithoutLeakingTargets() {
        String entityBody = """
                <!DOCTYPE sync-collection [<!ENTITY provider SYSTEM "file:///private/sync-secret">]>
                <d:sync-collection xmlns:d="DAV:">
                  <d:sync-token>&provider;</d:sync-token><d:sync-level>1</d:sync-level><d:prop/>
                </d:sync-collection>
                """;
        SyncRequestException entity = failure(request("/dav/files", entityBody));
        assertThat(entity.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(entity.davCondition()).contains(DavCondition.NO_EXTERNAL_ENTITIES);
        assertThat(entity).hasMessageNotContaining("private").hasMessageNotContaining("sync-secret");

        SyncRequestException xinclude = failure(request("/dav/files", """
                <d:sync-collection xmlns:d="DAV:" xmlns:xi="http://www.w3.org/2001/XInclude">
                  <d:sync-token/><d:sync-level>1</d:sync-level><d:prop/>
                  <xi:include href="https://provider.invalid/private.xml"/>
                </d:sync-collection>
                """));
        assertThat(xinclude.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(xinclude).hasMessageNotContaining("provider.invalid");

        SyncRequestException schema = failure(request("/dav/files", """
                <d:sync-collection xmlns:d="DAV:"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="DAV: https://provider.invalid/sync.xsd">
                  <d:sync-token/><d:sync-level>1</d:sync-level><d:prop/>
                </d:sync-collection>
                """));
        assertThat(schema.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(schema).hasMessageNotContaining("provider.invalid");
    }

    @Test
    void boundsRequestedPropertiesAndRejectsEscapingFacadePaths() {
        String properties = "<d:property/>".repeat(17);
        SyncRequestException tooManyProperties = failure(request("/dav/files", """
                <d:sync-collection xmlns:d="DAV:">
                  <d:sync-token/><d:sync-level>1</d:sync-level><d:prop>%s</d:prop>
                </d:sync-collection>
                """.formatted(properties)));
        assertThat(tooManyProperties.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        for (String path : new String[] {
            "/dav/files/Team%2FSecret",
            "/dav/files/Team/%2e%2e/Sibling",
            "/dav/files-private/Team",
            "/not-dav/Team"
        }) {
            assertThat(failure(request(path, validBody("", "1", ""))).status())
                    .as(path)
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    private static String validBody(String token, String level, String optionalLimit) {
        return """
                <d:sync-collection xmlns:d="DAV:">
                  <d:sync-token>%s</d:sync-token>
                  <d:sync-level>%s</d:sync-level>
                  %s
                  <d:prop><d:getetag/></d:prop>
                </d:sync-collection>
                """.formatted(token, level, optionalLimit);
    }

    private static MockHttpServletRequest request(String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("REPORT", path);
        request.setScheme("https");
        request.setServerName("api.weave.test");
        request.setServerPort(443);
        request.setSecure(true);
        request.setContentType("application/xml");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static SyncRequestException failure(MockHttpServletRequest request) {
        return catchThrowableOfType(
                SyncRequestException.class,
                () -> FilesWebDavSyncParser.parse(request));
    }
}
