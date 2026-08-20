package com.massimotter.weave.backend.controller.protocol;

import com.massimotter.weave.backend.controller.protocol.FilesWebDavSearchParser.DavCondition;
import com.massimotter.weave.backend.controller.protocol.FilesWebDavSearchParser.SearchRequestException;
import com.massimotter.weave.backend.files.domain.FilesSearch;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.ComparisonOperator;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.ComparisonPredicate;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.IsDefinedPredicate;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.LogicalOperator;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.LogicalPredicate;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.OrderDirection;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.SelectedProperties;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.TruePredicate;
import java.nio.charset.StandardCharsets;
import javax.xml.namespace.QName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FilesWebDavSearchParserTest {

    private static final QName DISPLAY_NAME = new QName("DAV:", "displayname");
    private static final QName CONTENT_LENGTH = new QName("DAV:", "getcontentlength");
    private static final QName SUPPORTED_LOCK = new QName("DAV:", "supportedlock");

    @Test
    void parsesClosedTypedRequestAndPreservesUnknownSelectedQNames() {
        WebDavSearchRequest parsed = parse("/dav/files/Team", """
                <d:searchrequest xmlns:d="DAV:" xmlns:w="urn:weave:files" xmlns:x="urn:extension">
                  <d:basicsearch x:attribute="ignored">
                    <x:extension><x:anything/></x:extension>
                    <d:select>
                      <d:prop>
                        <d:displayname/>
                        <x:future-property/>
                      </d:prop>
                    </d:select>
                    <d:from>
                      <d:scope>
                        <d:href>https://api.weave.test:443/dav/files/Team/Reports</d:href>
                        <d:depth>infinity</d:depth>
                      </d:scope>
                    </d:from>
                    <d:where>
                      <d:and>
                        <d:like x:mode="ignored">
                          <d:prop><d:displayname/></d:prop>
                          <d:literal>%road\\_%</d:literal>
                        </d:like>
                        <d:is-defined><d:prop><d:supportedlock/></d:prop></d:is-defined>
                      </d:and>
                    </d:where>
                    <d:orderby>
                      <d:order>
                        <d:prop><d:getcontentlength/></d:prop>
                        <d:descending/>
                      </d:order>
                    </d:orderby>
                    <d:limit><d:nresults>999999999999999999999999</d:nresults></d:limit>
                  </d:basicsearch>
                </d:searchrequest>
                """);

        assertThat(parsed.arbiterPath()).isEqualTo("/Team");
        assertThat(parsed.scopePath()).isEqualTo("/Team/Reports");
        assertThat(parsed.scopeDepth()).isEqualTo(FilesSearch.ScopeDepth.INFINITY);
        assertThat(((SelectedProperties) parsed.selection()).properties())
                .containsExactly(DISPLAY_NAME, new QName("urn:extension", "future-property"));
        LogicalPredicate predicate = (LogicalPredicate) parsed.predicate();
        assertThat(predicate.operator()).isEqualTo(LogicalOperator.AND);
        assertThat(predicate.operands()).hasSize(2);
        assertThat((ComparisonPredicate) predicate.operands().getFirst())
                .isEqualTo(new ComparisonPredicate(ComparisonOperator.LIKE, DISPLAY_NAME, "%road\\_%"));
        assertThat(predicate.operands().get(1)).isEqualTo(new IsDefinedPredicate(SUPPORTED_LOCK));
        assertThat(parsed.orderBy()).singleElement().satisfies(order -> {
            assertThat(order.property()).isEqualTo(CONTENT_LENGTH);
            assertThat(order.direction()).isEqualTo(OrderDirection.DESCENDING);
        });
        assertThat(parsed.limit()).isEqualTo(100);
    }

    @Test
    void suppliesDefaultsAndAcceptsAllpropWithZeroDepth() {
        MockHttpServletRequest request = request("/dav/files", """
                <s:searchrequest xmlns:s="DAV:">
                  <s:basicsearch>
                    <s:from><s:scope><s:href>/dav/files</s:href><s:depth>0</s:depth></s:scope></s:from>
                    <s:select><s:allprop/></s:select>
                  </s:basicsearch>
                </s:searchrequest>
                """);
        request.addHeader("X-Weave-Search-Limit", "1");

        WebDavSearchRequest parsed = FilesWebDavSearchParser.parse(request);

        assertThat(parsed.arbiterPath()).isEqualTo("/");
        assertThat(parsed.scopePath()).isEqualTo("/");
        assertThat(parsed.scopeDepth()).isEqualTo(FilesSearch.ScopeDepth.ZERO);
        assertThat(parsed.selection()).isInstanceOf(WebDavSearchRequest.AllProperties.class);
        assertThat(parsed.predicate()).isEqualTo(new TruePredicate());
        assertThat(parsed.orderBy()).isEmpty();
        assertThat(parsed.limit()).isEqualTo(25);
    }

    @Test
    void resolvesContextRelativeScopeToNormalizedProductPaths() {
        MockHttpServletRequest request = request("/weave/dav/files/Team", basicSearch(
                "<d:allprop/>",
                "https://api.weave.test/weave/dav/files/Team/Reports",
                "1",
                ""));
        request.setContextPath("/weave");

        WebDavSearchRequest parsed = FilesWebDavSearchParser.parse(request);

        assertThat(parsed.arbiterPath()).isEqualTo("/Team");
        assertThat(parsed.scopePath()).isEqualTo("/Team/Reports");
        assertThat(parsed.scopeDepth()).isEqualTo(FilesSearch.ScopeDepth.ONE);
    }

    @Test
    void rejectsScopesOutsideTheEffectiveOriginArbiterOrFacadePath() {
        for (String invalidHref : new String[] {
            "https://other.weave.test/dav/files/Team",
            "https://user@api.weave.test/dav/files/Team",
            "/dav/files/Team?query=true",
            "/dav/files/Team#fragment",
            "/dav/files/Sibling",
            "/files/Team",
            "/dav/files/Team%2FSecret",
            "/dav/files/Team/%2e%2e/Sibling"
        }) {
            SearchRequestException exception = failure(
                    "/dav/files/Team", basicSearch("<d:allprop/>", invalidHref, "1", ""));
            assertThat(exception.status()).as(invalidHref).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.davCondition()).as(invalidHref).contains(DavCondition.SEARCH_SCOPE_VALID);
        }
    }

    @Test
    void mapsGrammarDiscoveryUnknownGrammarAndMultipleScopesToNamedForbiddenConditions() {
        assertFailure(
                "<d:query-schema-discovery xmlns:d=\"DAV:\"/>",
                HttpStatus.FORBIDDEN,
                DavCondition.SEARCH_GRAMMAR_DISCOVERY_SUPPORTED);
        assertFailure(
                "<d:searchrequest xmlns:d=\"DAV:\" xmlns:x=\"urn:future\"><x:future/></d:searchrequest>",
                HttpStatus.FORBIDDEN,
                DavCondition.SEARCH_GRAMMAR_SUPPORTED);

        SearchRequestException multipleScopes = failure("/dav/files", """
                <d:searchrequest xmlns:d="DAV:"><d:basicsearch>
                  <d:select><d:allprop/></d:select>
                  <d:from>
                    <d:scope><d:href>/dav/files</d:href><d:depth>0</d:depth></d:scope>
                    <d:scope><d:href>/dav/files/Other</d:href><d:depth>0</d:depth></d:scope>
                  </d:from>
                </d:basicsearch></d:searchrequest>
                """);
        assertThat(multipleScopes.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(multipleScopes.davCondition()).contains(DavCondition.SEARCH_MULTIPLE_SCOPE_SUPPORTED);
    }

    @Test
    void rejectsExternalEntityDeclarationsWithoutLeakingTheirTargets() {
        String body = """
                <!DOCTYPE searchrequest [<!ENTITY provider SYSTEM "file:///private/support-secret">]>
                <d:searchrequest xmlns:d="DAV:"><d:basicsearch>
                  <d:select><d:allprop/></d:select>
                  <d:from><d:scope><d:href>/dav/files</d:href><d:depth>0</d:depth></d:scope></d:from>
                  <d:where><d:eq><d:prop><d:displayname/></d:prop><d:literal>&provider;</d:literal></d:eq></d:where>
                </d:basicsearch></d:searchrequest>
                """;
        SearchRequestException exception = failure("/dav/files", body);

        assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.davCondition()).contains(DavCondition.NO_EXTERNAL_ENTITIES);
        assertThat(exception)
                .hasMessageNotContaining("private")
                .hasMessageNotContaining("support-secret")
                .hasMessageNotContaining("file:");

        MockHttpServletRequest utf16 = request("/dav/files", "");
        utf16.setContentType("application/xml; charset=UTF-16LE");
        utf16.setContent(body.getBytes(StandardCharsets.UTF_16LE));
        assertThat(failure(utf16).davCondition()).contains(DavCondition.NO_EXTERNAL_ENTITIES);
    }

    @Test
    void enforcesMediaTypeAndBodyByteLimitBeforeXmlParsing() {
        MockHttpServletRequest emptyWithoutMedia = request("/dav/files", "");
        emptyWithoutMedia.setContentType(null);
        assertThat(failure(emptyWithoutMedia).status()).isEqualTo(HttpStatus.BAD_REQUEST);

        MockHttpServletRequest emptyWithMedia = request("/dav/files", "");
        assertThat(failure(emptyWithMedia).status()).isEqualTo(HttpStatus.BAD_REQUEST);

        MockHttpServletRequest noMedia = request("/dav/files", "<not-xml");
        noMedia.setContentType(null);
        assertThat(failure(noMedia).status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        MockHttpServletRequest json = request("/dav/files", "{}");
        json.setContentType("application/json");
        assertThat(failure(json).status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        MockHttpServletRequest unsupportedParameter = request("/dav/files", "<not-xml");
        unsupportedParameter.setContentType("application/xml; profile=provider-details");
        assertThat(failure(unsupportedParameter).status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        MockHttpServletRequest tooLarge = request("/dav/files", "x".repeat(65_537));
        assertThat(failure(tooLarge).status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);

        MockHttpServletRequest textXml = request("/dav/files", basicSearch("<d:allprop/>", "/dav/files", "0", ""));
        textXml.setContentType("text/xml; charset=UTF-8");
        assertThat(FilesWebDavSearchParser.parse(textXml).limit()).isEqualTo(25);
    }

    @Test
    void rejectsXincludeAndExternalSchemaHintsBeforeGrammarEvaluation() {
        SearchRequestException xinclude = failure("/dav/files", """
                <d:searchrequest xmlns:d="DAV:" xmlns:xi="http://www.w3.org/2001/XInclude">
                  <d:basicsearch>
                    <d:select><d:allprop/></d:select>
                    <d:from><d:scope><d:href>/dav/files</d:href><d:depth>0</d:depth></d:scope></d:from>
                    <xi:include href="https://provider.invalid/private.xml"/>
                  </d:basicsearch>
                </d:searchrequest>
                """);
        assertThat(xinclude.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(xinclude).hasMessageNotContaining("provider.invalid");

        SearchRequestException schema = failure("/dav/files", """
                <d:searchrequest xmlns:d="DAV:"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="DAV: https://provider.invalid/search.xsd">
                  <d:basicsearch>
                    <d:select><d:allprop/></d:select>
                    <d:from><d:scope><d:href>/dav/files</d:href><d:depth>0</d:depth></d:scope></d:from>
                  </d:basicsearch>
                </d:searchrequest>
                """);
        assertThat(schema.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(schema).hasMessageNotContaining("provider.invalid");
    }

    @Test
    void distinguishesInvalidStructureFromUnsupportedOperatorSlots() {
        SearchRequestException duplicateSelect = failure("/dav/files", """
                <d:searchrequest xmlns:d="DAV:"><d:basicsearch>
                  <d:select><d:allprop/></d:select><d:select><d:allprop/></d:select>
                  <d:from><d:scope><d:href>/dav/files</d:href><d:depth>0</d:depth></d:scope></d:from>
                </d:basicsearch></d:searchrequest>
                """);
        assertThat(duplicateSelect.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(duplicateSelect.davCondition()).isEmpty();

        SearchRequestException unknownOperator = failure("/dav/files", basicSearch(
                "<d:allprop/>",
                "/dav/files",
                "0",
                "<d:where><future:operator xmlns:future=\"urn:future\"/></d:where>"));
        assertThat(unknownOperator.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(unknownOperator.davCondition()).isEmpty();

        SearchRequestException mixedWhere = failure("/dav/files", basicSearch(
                "<d:allprop/>",
                "/dav/files",
                "0",
                """
                <d:where>
                  <d:is-collection/>
                  <future:operator xmlns:future="urn:future"/>
                </d:where>
                """));
        assertThat(mixedWhere.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        SearchRequestException mixedNot = failure("/dav/files", basicSearch(
                "<d:allprop/>",
                "/dav/files",
                "0",
                """
                <d:where><d:not>
                  <d:is-collection/>
                  <future:operator xmlns:future="urn:future"/>
                </d:not></d:where>
                """));
        assertThat(mixedNot.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void enforcesSelectionOrderExpressionAndLiteralBounds() {
        String selected = "<d:property/>".repeat(17);
        assertThat(failure("/dav/files", basicSearch(
                        "<d:prop>" + selected + "</d:prop>", "/dav/files", "0", "")).status())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        String order = """
                <d:order><d:prop><d:displayname/></d:prop></d:order>
                """;
        assertThat(failure("/dav/files", basicSearch(
                        "<d:allprop/>", "/dav/files", "0", "<d:orderby>" + order.repeat(3) + "</d:orderby>")).status())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        String nested = "<d:eq><d:prop><d:displayname/></d:prop><d:literal>x</d:literal></d:eq>";
        for (int index = 0; index < 8; index++) {
            nested = "<d:not>" + nested + "</d:not>";
        }
        assertThat(failure("/dav/files", basicSearch(
                        "<d:allprop/>", "/dav/files", "0", "<d:where>" + nested + "</d:where>")).status())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        String flatOperators = """
                <d:eq><d:prop><d:displayname/></d:prop><d:literal>x</d:literal></d:eq>
                """.repeat(32);
        assertThat(failure("/dav/files", basicSearch(
                        "<d:allprop/>",
                        "/dav/files",
                        "0",
                        "<d:where><d:and>" + flatOperators + "</d:and></d:where>")).status())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        String longLiteral = "🦊".repeat(201);
        assertThat(failure("/dav/files", basicSearch(
                        "<d:allprop/>",
                        "/dav/files",
                        "0",
                        comparison("eq", "displayname", longLiteral))).status())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void validatesPredicateAndOrderingPropertyClassesAndLexicalForms() {
        assertUnprocessable(comparison("eq", "resourcetype", "collection"));
        assertUnprocessable(comparison("eq", "getcontentlength", "01"));
        assertUnprocessable(comparison("like", "displayname", "bad\\escape"));
        assertUnprocessable("""
                <d:where><d:eq caseless="yes">
                  <d:prop><d:displayname/></d:prop><d:literal>name</d:literal>
                </d:eq></d:where>
                """);
        assertUnprocessable("""
                <d:where><d:eq>
                  <d:prop><d:displayname/></d:prop><d:typed-literal>name</d:typed-literal>
                </d:eq></d:where>
                """);
        assertUnprocessable("""
                <d:where><d:is-collection><d:prop><d:resourcetype/></d:prop></d:is-collection></d:where>
                """);

        String etagOrder = """
                <d:orderby><d:order><d:prop><d:getetag/></d:prop></d:order></d:orderby>
                """;
        assertUnprocessable(etagOrder);

        String unknownPredicate = """
                <d:where><d:is-defined><d:prop><x:unknown xmlns:x="urn:unknown"/></d:prop></d:is-defined></d:where>
                """;
        assertUnprocessable(unknownPredicate);
    }

    @Test
    void validatesLimitAndAcceptsZeroAsAnUnsignedBound() {
        WebDavSearchRequest zero = parse("/dav/files", basicSearch(
                "<d:allprop/>", "/dav/files", "0", "<d:limit><d:nresults>0</d:nresults></d:limit>"));
        assertThat(zero.limit()).isZero();

        for (String invalid : new String[] {"", "-1", "+1", "1.0"}) {
            SearchRequestException exception = failure("/dav/files", basicSearch(
                    "<d:allprop/>",
                    "/dav/files",
                    "0",
                    "<d:limit><d:nresults>" + invalid + "</d:nresults></d:limit>"));
            assertThat(exception.status()).as(invalid).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private static void assertUnprocessable(String basicSearchSuffix) {
        SearchRequestException exception = failure(
                "/dav/files", basicSearch("<d:allprop/>", "/dav/files", "0", basicSearchSuffix));
        assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(exception.davCondition()).isEmpty();
    }

    private static void assertFailure(String xml, HttpStatus status, DavCondition condition) {
        SearchRequestException exception = failure("/dav/files", xml);
        assertThat(exception.status()).isEqualTo(status);
        assertThat(exception.davCondition()).contains(condition);
    }

    private static WebDavSearchRequest parse(String path, String body) {
        return FilesWebDavSearchParser.parse(request(path, body));
    }

    private static SearchRequestException failure(String path, String body) {
        return failure(request(path, body));
    }

    private static SearchRequestException failure(MockHttpServletRequest request) {
        return catchThrowableOfType(
                SearchRequestException.class,
                () -> FilesWebDavSearchParser.parse(request));
    }

    private static MockHttpServletRequest request(String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("SEARCH", path);
        request.setScheme("https");
        request.setServerName("api.weave.test");
        request.setServerPort(443);
        request.setSecure(true);
        request.setContentType("application/xml");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static String basicSearch(String selection, String href, String depth, String suffix) {
        return """
                <d:searchrequest xmlns:d="DAV:">
                  <d:basicsearch>
                    <d:select>%s</d:select>
                    <d:from><d:scope><d:href>%s</d:href><d:depth>%s</d:depth></d:scope></d:from>
                    %s
                  </d:basicsearch>
                </d:searchrequest>
                """.formatted(selection, href, depth, suffix);
    }

    private static String comparison(String operator, String property, String literal) {
        return """
                <d:where><d:%s>
                  <d:prop><d:%s/></d:prop><d:literal>%s</d:literal>
                </d:%s></d:where>
                """.formatted(operator, property, literal, operator);
    }
}
