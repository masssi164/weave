package com.massimotter.weave.backend.service.files;

import static com.massimotter.weave.backend.service.files.WebDavBasicSearchEvaluator.CANONICAL_ID;
import static com.massimotter.weave.backend.service.files.WebDavBasicSearchEvaluator.DISPLAY_NAME;
import static com.massimotter.weave.backend.service.files.WebDavBasicSearchEvaluator.GET_CONTENT_LENGTH;
import static com.massimotter.weave.backend.service.files.WebDavBasicSearchEvaluator.GET_CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesSearch.CandidatePage;
import com.massimotter.weave.backend.files.domain.FilesSearch.ScopeDepth;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebDavBasicSearchEvaluatorTest {

    private final WebDavBasicSearchEvaluator evaluator = new WebDavBasicSearchEvaluator();

    @Test
    void evaluatesThreeValuedPredicatesRfcLikeAndNumericComparison() {
        var predicate = new WebDavSearchRequest.LogicalPredicate(
                WebDavSearchRequest.LogicalOperator.AND,
                List.of(
                        new WebDavSearchRequest.ComparisonPredicate(
                                WebDavSearchRequest.ComparisonOperator.LIKE,
                                DISPLAY_NAME,
                                "read%_.md"),
                        new WebDavSearchRequest.ComparisonPredicate(
                                WebDavSearchRequest.ComparisonOperator.GTE,
                                GET_CONTENT_LENGTH,
                                "12"),
                        new WebDavSearchRequest.LogicalPredicate(
                                WebDavSearchRequest.LogicalOperator.OR,
                                List.of(
                                        new WebDavSearchRequest.IsDefinedPredicate(GET_CONTENT_TYPE),
                                        new WebDavSearchRequest.ComparisonPredicate(
                                                WebDavSearchRequest.ComparisonOperator.EQ,
                                                GET_CONTENT_TYPE,
                                                "missing/type")))));
        WebDavSearchResult result = evaluate(
                request(predicate, List.of(), 25),
                new CandidatePage(List.of(
                        file("files:b", "/Team/readme-one.md", 12, "text/markdown"),
                        file("files:a", "/Team/read.txt", 30, null),
                        folder("files:c", "/Team/readme-two.md")), false));

        assertThat(result.resources()).extracting(resource -> resource.item().id()).containsExactly("files:b");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void appliesRequestedOrderingThenAlwaysAscendingCanonicalTieBreakers() {
        var order = List.of(new WebDavSearchRequest.OrderClause(
                GET_CONTENT_TYPE,
                WebDavSearchRequest.OrderDirection.DESCENDING));
        WebDavSearchResult result = evaluate(
                request(new WebDavSearchRequest.TruePredicate(), order, 25),
                new CandidatePage(List.of(
                        file("files:z", "/z", 1, null),
                        file("files:b", "/b", 1, "text/plain"),
                        file("files:a", "/a", 1, "text/plain")), false));

        assertThat(result.resources()).extracting(resource -> resource.item().id())
                .containsExactly("files:a", "files:b", "files:z");
    }

    @Test
    void ordersContentLengthNumerically() {
        var order = List.of(new WebDavSearchRequest.OrderClause(
                GET_CONTENT_LENGTH,
                WebDavSearchRequest.OrderDirection.ASCENDING));
        WebDavSearchResult result = evaluate(
                request(new WebDavSearchRequest.TruePredicate(), order, 25),
                new CandidatePage(List.of(
                        file("files:ten", "/ten", 10, "text/plain"),
                        file("files:two", "/two", 2, "text/plain")), false));

        assertThat(result.resources()).extracting(resource -> resource.item().id())
                .containsExactly("files:two", "files:ten");

        WebDavSearchResult descending = evaluate(
                request(
                        new WebDavSearchRequest.TruePredicate(),
                        List.of(new WebDavSearchRequest.OrderClause(
                                GET_CONTENT_LENGTH,
                                WebDavSearchRequest.OrderDirection.DESCENDING)),
                        25),
                new CandidatePage(List.of(
                        folder("files:null", "/null"),
                        file("files:ten-b", "/b", 10, "text/plain"),
                        file("files:two", "/two", 2, "text/plain"),
                        file("files:ten-a", "/a", 10, "text/plain")), false));

        assertThat(descending.resources()).extracting(resource -> resource.item().id())
                .containsExactly("files:ten-a", "files:ten-b", "files:two", "files:null");
    }

    @Test
    void returnsOrderedPartialRowsAndExplicitTruncation() {
        WebDavSearchResult result = evaluate(
                request(new WebDavSearchRequest.TruePredicate(), List.of(), 2),
                new CandidatePage(List.of(
                        file("files:c", "/c", 1, "text/plain"),
                        file("files:a", "/a", 1, "text/plain"),
                        file("files:b", "/b", 1, "text/plain")), true));

        assertThat(result.resources()).extracting(resource -> resource.item().path())
                .containsExactly("/a", "/b");
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void exactCanonicalResolutionFailsClosedForIncompleteOrDuplicateCoverage() {
        var exact = request(new WebDavSearchRequest.ComparisonPredicate(
                WebDavSearchRequest.ComparisonOperator.EQ,
                CANONICAL_ID,
                "files:a"), List.of(), 2);

        assertThatThrownBy(() -> evaluate(
                exact,
                new CandidatePage(List.of(file("files:a", "/a", 1, "text/plain")), true)))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.code()).isEqualTo("files-search-incomplete"));
        assertThatThrownBy(() -> evaluate(
                exact,
                new CandidatePage(List.of(
                        file("files:a", "/a", 1, "text/plain"),
                        file("files:a", "/b", 1, "text/plain")), false)))
                .isInstanceOfSatisfying(ApiErrorException.class,
                        error -> assertThat(error.code()).isEqualTo("files-canonical-integrity-unavailable"));
    }

    @Test
    void utf8OrderingAndRfcLikeEscapesAreDeterministic() {
        assertThat(WebDavBasicSearchEvaluator.compareUtf8("z", "é")).isLessThan(0);
        assertThat(WebDavBasicSearchEvaluator.like("100%_safe\\name", "100\\%\\_safe\\\\name")).isTrue();
        assertThat(WebDavBasicSearchEvaluator.like("abc", "a_c")).isTrue();
        assertThat(WebDavBasicSearchEvaluator.like("abc", "a%")) .isTrue();
        assertThat(WebDavBasicSearchEvaluator.like("abc", "a\\%")) .isFalse();
    }

    private WebDavSearchResult evaluate(WebDavSearchRequest request, CandidatePage page) {
        return evaluator.evaluate(request, page, this::project);
    }

    private WebDavSearchRequest request(
            WebDavSearchRequest.Predicate predicate,
            List<WebDavSearchRequest.OrderClause> order,
            int limit) {
        return new WebDavSearchRequest(
                "/Team",
                "/Team",
                ScopeDepth.INFINITY,
                new WebDavSearchRequest.AllProperties(),
                predicate,
                order,
                limit);
    }

    private WebDavPropfindResource project(VersionedFile file) {
        FileObject item = file.item();
        return new WebDavPropfindResource(
                new FileItemResponse(
                        item.id().value(),
                        item.name(),
                        item.path().value(),
                        item.kind() == Kind.COLLECTION ? "folder" : "file",
                        item.mediaType(),
                        item.kind() == Kind.FILE ? item.size() : null,
                        item.modifiedAt() == null
                                ? null
                                : OffsetDateTime.ofInstant(item.modifiedAt(), ZoneOffset.UTC),
                        item.kind() == Kind.FILE),
                "\"" + file.version().value() + "\"");
    }

    private VersionedFile file(String id, String path, long size, String mediaType) {
        return new VersionedFile(
                new FileObject(
                        new FileId(id),
                        new FilePath(path),
                        Kind.FILE,
                        size,
                        mediaType,
                        Instant.parse("2026-08-20T12:00:00Z"),
                        false),
                new FileVersion("v1"));
    }

    private VersionedFile folder(String id, String path) {
        return new VersionedFile(
                new FileObject(
                        new FileId(id),
                        new FilePath(path),
                        Kind.COLLECTION,
                        0,
                        null,
                        Instant.parse("2026-08-20T12:00:00Z"),
                        false),
                new FileVersion("v1"));
    }
}
