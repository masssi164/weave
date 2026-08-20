package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesDomain.VersionedFile;
import com.massimotter.weave.backend.files.domain.FilesSearch.CandidatePage;
import com.massimotter.weave.backend.model.files.FileItemResponse;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javax.xml.namespace.QName;
import org.springframework.http.HttpStatus;

/** Framework-light evaluator for the accepted bounded {@code DAV:basicsearch} profile. */
public final class WebDavBasicSearchEvaluator {

    public static final String DAV_NAMESPACE = "DAV:";
    public static final String WEAVE_FILES_NAMESPACE = "urn:weave:files";

    public static final QName DISPLAY_NAME = new QName(DAV_NAMESPACE, "displayname");
    public static final QName RESOURCE_TYPE = new QName(DAV_NAMESPACE, "resourcetype");
    public static final QName GET_ETAG = new QName(DAV_NAMESPACE, "getetag");
    public static final QName GET_CONTENT_TYPE = new QName(DAV_NAMESPACE, "getcontenttype");
    public static final QName GET_CONTENT_LENGTH = new QName(DAV_NAMESPACE, "getcontentlength");
    public static final QName GET_LAST_MODIFIED = new QName(DAV_NAMESPACE, "getlastmodified");
    public static final QName SUPPORTED_LOCK = new QName(DAV_NAMESPACE, "supportedlock");
    public static final QName LOCK_DISCOVERY = new QName(DAV_NAMESPACE, "lockdiscovery");
    public static final QName CANONICAL_ID = new QName(WEAVE_FILES_NAMESPACE, "canonical-id");

    public static final List<QName> ALL_PROPERTIES = List.of(
            DISPLAY_NAME,
            RESOURCE_TYPE,
            GET_ETAG,
            GET_CONTENT_TYPE,
            GET_CONTENT_LENGTH,
            GET_LAST_MODIFIED,
            SUPPORTED_LOCK,
            LOCK_DISCOVERY,
            CANONICAL_ID);

    /** Evaluates only the bounded candidate page supplied by the canonical Files query. */
    public WebDavSearchResult evaluate(
            WebDavSearchRequest request,
            CandidatePage page,
            Function<VersionedFile, WebDavPropfindResource> projection) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(projection, "projection");

        List<EvaluatedResource> matches = new ArrayList<>();
        for (VersionedFile candidate : page.candidates()) {
            WebDavPropfindResource resource = projection.apply(candidate);
            if (evaluate(request.predicate(), resource) == Truth.TRUE) {
                matches.add(new EvaluatedResource(resource));
            }
        }
        matches.sort(order(request.orderBy()));

        boolean exactCanonicalResolution = isExactCanonicalResolution(request.predicate());
        boolean resultTruncated = matches.size() > request.limit();
        if (exactCanonicalResolution && matches.size() > 1) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "files-canonical-integrity-unavailable",
                    "Canonical Files identity integrity is unavailable.",
                    Map.of("module", "files", "operation", "webdav-search", "diagnosticsRedacted", true));
        }
        if (exactCanonicalResolution && (page.truncated() || resultTruncated)) {
            throw new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "files-search-incomplete",
                    "Canonical Files search could not prove complete exact-match coverage.",
                    Map.of("module", "files", "operation", "webdav-search", "diagnosticsRedacted", true));
        }
        int returned = Math.min(request.limit(), matches.size());
        List<WebDavPropfindResource> resources = matches.subList(0, returned).stream()
                .map(EvaluatedResource::resource)
                .toList();
        return new WebDavSearchResult(resources, page.truncated() || resultTruncated);
    }

    private Truth evaluate(WebDavSearchRequest.Predicate predicate, WebDavPropfindResource resource) {
        if (predicate instanceof WebDavSearchRequest.TruePredicate) {
            return Truth.TRUE;
        }
        if (predicate instanceof WebDavSearchRequest.IsCollectionPredicate) {
            return "folder".equals(resource.item().type()) ? Truth.TRUE : Truth.FALSE;
        }
        if (predicate instanceof WebDavSearchRequest.IsDefinedPredicate defined) {
            return property(resource, defined.property()) == null ? Truth.FALSE : Truth.TRUE;
        }
        if (predicate instanceof WebDavSearchRequest.ComparisonPredicate comparison) {
            return compare(comparison, resource);
        }
        WebDavSearchRequest.LogicalPredicate logical = (WebDavSearchRequest.LogicalPredicate) predicate;
        List<Truth> values = logical.operands().stream()
                .map(operand -> evaluate(operand, resource))
                .toList();
        return switch (logical.operator()) {
            case NOT -> values.getFirst().negate();
            case AND -> values.contains(Truth.FALSE)
                    ? Truth.FALSE
                    : values.contains(Truth.UNKNOWN) ? Truth.UNKNOWN : Truth.TRUE;
            case OR -> values.contains(Truth.TRUE)
                    ? Truth.TRUE
                    : values.contains(Truth.UNKNOWN) ? Truth.UNKNOWN : Truth.FALSE;
        };
    }

    private Truth compare(
            WebDavSearchRequest.ComparisonPredicate comparison,
            WebDavPropfindResource resource) {
        String actual = property(resource, comparison.property());
        if (actual == null) {
            return Truth.UNKNOWN;
        }
        if (comparison.operator() == WebDavSearchRequest.ComparisonOperator.LIKE) {
            return like(actual, comparison.literal()) ? Truth.TRUE : Truth.FALSE;
        }

        int result;
        if (GET_CONTENT_LENGTH.equals(comparison.property())) {
            try {
                result = new BigInteger(actual).compareTo(new BigInteger(comparison.literal()));
            } catch (NumberFormatException invalid) {
                return Truth.UNKNOWN;
            }
        } else {
            result = compareUtf8(actual, comparison.literal());
        }
        return switch (comparison.operator()) {
            case EQ -> truth(result == 0);
            case LT -> truth(result < 0);
            case LTE -> truth(result <= 0);
            case GT -> truth(result > 0);
            case GTE -> truth(result >= 0);
            case LIKE -> throw new IllegalStateException("LIKE handled before scalar comparison");
        };
    }

    private Comparator<EvaluatedResource> order(List<WebDavSearchRequest.OrderClause> clauses) {
        Comparator<EvaluatedResource> comparator = (left, right) -> 0;
        for (WebDavSearchRequest.OrderClause clause : clauses) {
            comparator = comparator.thenComparing((left, right) -> compareNullable(
                    property(left.resource(), clause.property()),
                    property(right.resource(), clause.property()),
                    clause.property(),
                    clause.direction()));
        }
        return comparator
                .thenComparing(resource -> resource.resource().item().path(), WebDavBasicSearchEvaluator::compareUtf8)
                .thenComparing(resource -> resource.resource().item().id(), WebDavBasicSearchEvaluator::compareUtf8);
    }

    private static int compareNullable(
            String left,
            String right,
            QName property,
            WebDavSearchRequest.OrderDirection direction) {
        int result;
        if (left == null && right == null) {
            result = 0;
        } else if (left == null) {
            result = -1;
        } else if (right == null) {
            result = 1;
        } else if (GET_CONTENT_LENGTH.equals(property)) {
            result = new BigInteger(left).compareTo(new BigInteger(right));
        } else {
            result = compareUtf8(left, right);
        }
        return direction == WebDavSearchRequest.OrderDirection.DESCENDING ? -result : result;
    }

    private static String property(WebDavPropfindResource resource, QName property) {
        FileItemResponse item = resource.item();
        if (DISPLAY_NAME.equals(property)) {
            return item.name();
        }
        if (RESOURCE_TYPE.equals(property)) {
            return item.type();
        }
        if (GET_ETAG.equals(property)) {
            return resource.etag();
        }
        if (GET_CONTENT_TYPE.equals(property)) {
            return "file".equals(item.type()) ? item.mimeType() : null;
        }
        if (GET_CONTENT_LENGTH.equals(property)) {
            return "file".equals(item.type()) && item.size() != null ? item.size().toString() : null;
        }
        if (GET_LAST_MODIFIED.equals(property)) {
            return item.modifiedAt() == null
                    ? null
                    : item.modifiedAt().withOffsetSameInstant(ZoneOffset.UTC).toString();
        }
        if (SUPPORTED_LOCK.equals(property) || LOCK_DISCOVERY.equals(property)) {
            return "defined";
        }
        if (CANONICAL_ID.equals(property)) {
            return item.id();
        }
        return null;
    }

    private static boolean isExactCanonicalResolution(WebDavSearchRequest.Predicate predicate) {
        return predicate instanceof WebDavSearchRequest.ComparisonPredicate comparison
                && comparison.operator() == WebDavSearchRequest.ComparisonOperator.EQ
                && CANONICAL_ID.equals(comparison.property());
    }

    static int compareUtf8(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        int common = Math.min(leftBytes.length, rightBytes.length);
        for (int index = 0; index < common; index++) {
            int compared = Integer.compare(Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
    }

    static boolean like(String value, String pattern) {
        int[] input = value.codePoints().toArray();
        List<PatternToken> tokens = parsePattern(pattern);
        boolean[][] matched = new boolean[tokens.size() + 1][input.length + 1];
        matched[0][0] = true;
        for (int tokenIndex = 0; tokenIndex < tokens.size(); tokenIndex++) {
            PatternToken token = tokens.get(tokenIndex);
            for (int inputIndex = 0; inputIndex <= input.length; inputIndex++) {
                if (!matched[tokenIndex][inputIndex]) {
                    continue;
                }
                if (token.wildcardMany()) {
                    for (int end = inputIndex; end <= input.length; end++) {
                        matched[tokenIndex + 1][end] = true;
                    }
                } else if (inputIndex < input.length
                        && (token.wildcardOne() || token.codePoint() == input[inputIndex])) {
                    matched[tokenIndex + 1][inputIndex + 1] = true;
                }
            }
        }
        return matched[tokens.size()][input.length];
    }

    private static List<PatternToken> parsePattern(String pattern) {
        int[] points = pattern.codePoints().toArray();
        List<PatternToken> tokens = new ArrayList<>();
        boolean escaped = false;
        for (int point : points) {
            if (escaped) {
                tokens.add(PatternToken.literal(point));
                escaped = false;
            } else if (point == '\\') {
                escaped = true;
            } else if (point == '%') {
                tokens.add(PatternToken.many());
            } else if (point == '_') {
                tokens.add(PatternToken.one());
            } else {
                tokens.add(PatternToken.literal(point));
            }
        }
        if (escaped) {
            throw new IllegalArgumentException("DAV:like pattern has an incomplete escape");
        }
        return List.copyOf(tokens);
    }

    private static Truth truth(boolean value) {
        return value ? Truth.TRUE : Truth.FALSE;
    }

    private enum Truth {
        TRUE,
        FALSE,
        UNKNOWN;

        private Truth negate() {
            return this == TRUE ? FALSE : this == FALSE ? TRUE : UNKNOWN;
        }
    }

    private record EvaluatedResource(WebDavPropfindResource resource) {}

    private record PatternToken(int codePoint, boolean wildcardOne, boolean wildcardMany) {
        private static PatternToken literal(int codePoint) {
            return new PatternToken(codePoint, false, false);
        }

        private static PatternToken one() {
            return new PatternToken(-1, true, false);
        }

        private static PatternToken many() {
            return new PatternToken(-1, false, true);
        }
    }
}
