package com.massimotter.weave.backend.controller.protocol;

import com.massimotter.weave.backend.files.domain.FilesSearch;
import com.massimotter.weave.backend.service.files.FilePathCodec;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.AllProperties;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.ComparisonOperator;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.ComparisonPredicate;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.IsCollectionPredicate;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.IsDefinedPredicate;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.LogicalOperator;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.LogicalPredicate;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.OrderClause;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.OrderDirection;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.Predicate;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.SelectedProperties;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.Selection;
import com.massimotter.weave.backend.service.files.WebDavSearchRequest.TruePredicate;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.util.UriUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/** Strict, bounded parser for the Weave-supported subset of RFC 5323 {@code DAV:basicsearch}. */
public final class FilesWebDavSearchParser {

    private static final String DAV = "DAV:";
    private static final String WEAVE_FILES = "urn:weave:files";
    private static final String XINCLUDE = "http://www.w3.org/2001/XInclude";
    private static final String XML_SCHEMA_INSTANCE = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String WEBDAV_PREFIX = "/dav/files";
    private static final int MAX_BODY_BYTES = 65_536;
    private static final int MAX_SELECTED_PROPERTIES = 16;
    private static final int MAX_ORDER_CLAUSES = 2;
    private static final int MAX_EXPRESSION_DEPTH = 8;
    private static final int MAX_OPERATORS = 32;
    private static final int MAX_LITERAL_CODE_POINTS = 200;
    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    private static final Pattern EXTERNAL_DECLARATION = Pattern.compile(
            "(?is)<!DOCTYPE\\b[^>]*(?:SYSTEM|PUBLIC)|<!ENTITY\\b[^>]*(?:SYSTEM|PUBLIC)");
    private static final Pattern UNSIGNED_DECIMAL = Pattern.compile("[0-9]+");
    private static final Pattern CANONICAL_UNSIGNED_DECIMAL = Pattern.compile("(?:0|[1-9][0-9]*)");
    private static final Pattern ENCODED_SEPARATOR = Pattern.compile("(?i)%2f|%5c");

    private static final QName DISPLAY_NAME = dav("displayname");
    private static final QName RESOURCE_TYPE = dav("resourcetype");
    private static final QName GET_ETAG = dav("getetag");
    private static final QName GET_CONTENT_TYPE = dav("getcontenttype");
    private static final QName GET_CONTENT_LENGTH = dav("getcontentlength");
    private static final QName GET_LAST_MODIFIED = dav("getlastmodified");
    private static final QName SUPPORTED_LOCK = dav("supportedlock");
    private static final QName LOCK_DISCOVERY = dav("lockdiscovery");
    private static final QName CANONICAL_ID = new QName(WEAVE_FILES, "canonical-id");

    private static final Set<QName> SELECTABLE_PROPERTIES = Set.of(
            DISPLAY_NAME,
            RESOURCE_TYPE,
            GET_ETAG,
            GET_CONTENT_TYPE,
            GET_CONTENT_LENGTH,
            GET_LAST_MODIFIED,
            SUPPORTED_LOCK,
            LOCK_DISCOVERY,
            CANONICAL_ID);
    private static final Set<QName> SCALAR_SEARCHABLE_PROPERTIES =
            Set.of(DISPLAY_NAME, GET_ETAG, GET_CONTENT_TYPE, GET_CONTENT_LENGTH, CANONICAL_ID);
    private static final Set<QName> LIKE_PROPERTIES = Set.of(DISPLAY_NAME, GET_CONTENT_TYPE);
    private static final Set<QName> ORDERABLE_PROPERTIES =
            Set.of(DISPLAY_NAME, GET_CONTENT_TYPE, GET_CONTENT_LENGTH, CANONICAL_ID);

    private FilesWebDavSearchParser() {}

    /** Parses and validates one request without invoking a provider or resolving any file content. */
    public static WebDavSearchRequest parse(HttpServletRequest request) {
        Objects.requireNonNull(request, "request");
        byte[] body = readBoundedBody(request);
        Charset mediaCharset = validateMediaType(request.getContentType());
        rejectExternalDeclarations(body, mediaCharset);

        Document document = parseSafeXml(body);
        rejectForbiddenXmlFeatures(document);
        Element root = document.getDocumentElement();
        if (root == null) {
            throw invalidStructure();
        }
        if (isDav(root, "query-schema-discovery")) {
            throw forbidden(DavCondition.SEARCH_GRAMMAR_DISCOVERY_SUPPORTED);
        }
        if (!isDav(root, "searchrequest")) {
            throw invalidStructure();
        }

        Element grammar = singleGrammar(root);
        Element select = singleRecognizedChild(grammar, "select", true);
        Element from = singleRecognizedChild(grammar, "from", true);
        Element where = singleRecognizedChild(grammar, "where", false);
        Element orderBy = singleRecognizedChild(grammar, "orderby", false);
        Element limit = singleRecognizedChild(grammar, "limit", false);

        String arbiterPath = requestProductPath(request);
        Scope scope = parseScope(from, request, arbiterPath);
        Selection selection = parseSelection(select);
        Predicate predicate = where == null ? new TruePredicate() : parseWhere(where);
        List<OrderClause> orders = orderBy == null ? List.of() : parseOrderBy(orderBy);
        int resultLimit = limit == null ? DEFAULT_LIMIT : parseLimit(limit);
        return new WebDavSearchRequest(
                arbiterPath,
                scope.path(),
                scope.depth(),
                selection,
                predicate,
                orders,
                resultLimit);
    }

    private static Charset validateMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw unsupportedMediaType();
        }
        try {
            MediaType mediaType = MediaType.parseMediaType(contentType);
            if (!(MediaType.APPLICATION_XML.equalsTypeAndSubtype(mediaType)
                    || MediaType.TEXT_XML.equalsTypeAndSubtype(mediaType))) {
                throw unsupportedMediaType();
            }
            if (mediaType.getParameters().keySet().stream()
                    .anyMatch(parameter -> !"charset".equalsIgnoreCase(parameter))) {
                throw unsupportedMediaType();
            }
            return mediaType.getCharset();
        } catch (SearchRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unsupportedMediaType();
        }
    }

    private static byte[] readBoundedBody(HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            throw bodyTooLarge();
        }
        try {
            byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                throw bodyTooLarge();
            }
            if (body.length == 0) {
                throw invalidStructure();
            }
            return body;
        } catch (SearchRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidStructure();
        }
    }

    private static void rejectExternalDeclarations(byte[] body, Charset mediaCharset) {
        String declarationView = declarationView(body, mediaCharset);
        if (EXTERNAL_DECLARATION.matcher(declarationView).find()) {
            throw forbidden(DavCondition.NO_EXTERNAL_ENTITIES);
        }
    }

    private static String declarationView(byte[] body, Charset mediaCharset) {
        if (startsWith(body, (byte) 0x00, (byte) 0x00, (byte) 0xfe, (byte) 0xff)
                || startsWith(body, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x3c)) {
            return new String(body, Charset.forName("UTF-32BE"));
        }
        if (startsWith(body, (byte) 0xff, (byte) 0xfe, (byte) 0x00, (byte) 0x00)
                || startsWith(body, (byte) 0x3c, (byte) 0x00, (byte) 0x00, (byte) 0x00)) {
            return new String(body, Charset.forName("UTF-32LE"));
        }
        if (startsWith(body, (byte) 0xfe, (byte) 0xff)
                || startsWith(body, (byte) 0x00, (byte) 0x3c, (byte) 0x00, (byte) 0x3f)) {
            return new String(body, StandardCharsets.UTF_16BE);
        }
        if (startsWith(body, (byte) 0xff, (byte) 0xfe)
                || startsWith(body, (byte) 0x3c, (byte) 0x00, (byte) 0x3f, (byte) 0x00)) {
            return new String(body, StandardCharsets.UTF_16LE);
        }
        return new String(body, mediaCharset == null ? StandardCharsets.ISO_8859_1 : mediaCharset);
    }

    private static boolean startsWith(byte[] value, byte... prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static Document parseSafeXml(byte[] body) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External entities are disabled");
            });
            builder.setErrorHandler(new SilentErrorHandler());
            return builder.parse(new ByteArrayInputStream(body));
        } catch (Exception exception) {
            throw invalidStructure();
        }
    }

    private static void rejectForbiddenXmlFeatures(Document document) {
        for (Node node = document; node != null; node = nextNode(document, node)) {
            if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE
                    && "xml-model".equalsIgnoreCase(node.getNodeName())) {
                throw invalidStructure();
            }
            if (!(node instanceof Element element)) {
                continue;
            }
            if (XINCLUDE.equals(element.getNamespaceURI())) {
                throw invalidStructure();
            }
            NamedNodeMap attributes = element.getAttributes();
            for (int index = 0; index < attributes.getLength(); index++) {
                Node attribute = attributes.item(index);
                if (XML_SCHEMA_INSTANCE.equals(attribute.getNamespaceURI())
                        && ("schemaLocation".equals(attribute.getLocalName())
                                || "noNamespaceSchemaLocation".equals(attribute.getLocalName()))) {
                    throw invalidStructure();
                }
            }
        }
    }

    private static Node nextNode(Document document, Node current) {
        if (current.getFirstChild() != null) {
            return current.getFirstChild();
        }
        Node cursor = current;
        while (cursor != null && cursor != document) {
            if (cursor.getNextSibling() != null) {
                return cursor.getNextSibling();
            }
            cursor = cursor.getParentNode();
        }
        return null;
    }

    private static Element singleGrammar(Element root) {
        List<Element> grammars = children(root);
        List<Element> basicSearch = grammars.stream()
                .filter(child -> isDav(child, "basicsearch"))
                .toList();
        if (basicSearch.size() > 1) {
            throw invalidStructure();
        }
        if (basicSearch.isEmpty()) {
            if (!grammars.isEmpty()) {
                throw forbidden(DavCondition.SEARCH_GRAMMAR_SUPPORTED);
            }
            throw invalidStructure();
        }
        return basicSearch.getFirst();
    }

    private static Element singleRecognizedChild(Element parent, String localName, boolean required) {
        List<Element> matches = children(parent).stream()
                .filter(child -> isDav(child, localName))
                .toList();
        if (matches.size() > 1 || (required && matches.isEmpty())) {
            throw invalidStructure();
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private static Selection parseSelection(Element select) {
        List<Element> candidates = children(select).stream()
                .filter(child -> isDav(child, "allprop") || isDav(child, "prop"))
                .toList();
        if (candidates.size() != 1) {
            throw invalidStructure();
        }
        Element candidate = candidates.getFirst();
        if (isDav(candidate, "allprop")) {
            return new AllProperties();
        }
        List<QName> properties = children(candidate).stream().map(FilesWebDavSearchParser::qName).toList();
        if (properties.size() > MAX_SELECTED_PROPERTIES) {
            throw unsupportedExpression();
        }
        return new SelectedProperties(properties);
    }

    private static Scope parseScope(Element from, HttpServletRequest request, String arbiterPath) {
        List<Element> scopes = children(from).stream()
                .filter(child -> isDav(child, "scope"))
                .toList();
        if (scopes.size() > 1) {
            throw forbidden(DavCondition.SEARCH_MULTIPLE_SCOPE_SUPPORTED);
        }
        if (scopes.isEmpty()) {
            throw invalidStructure();
        }
        Element scope = scopes.getFirst();
        if (children(scope).stream().anyMatch(child -> isDav(child, "include-versions"))) {
            throw invalidScope();
        }
        Element href = exactlyOneDavChild(scope, "href");
        Element depth = exactlyOneDavChild(scope, "depth");
        String hrefValue = directText(href).trim();
        if (hrefValue.isEmpty()) {
            throw invalidStructure();
        }
        String scopePath = resolveScopePath(request, hrefValue, arbiterPath);
        FilesSearch.ScopeDepth scopeDepth = switch (directText(depth).trim()) {
            case "0" -> FilesSearch.ScopeDepth.ZERO;
            case "1" -> FilesSearch.ScopeDepth.ONE;
            case "infinity" -> FilesSearch.ScopeDepth.INFINITY;
            default -> throw invalidScope();
        };
        return new Scope(scopePath, scopeDepth);
    }

    private static Predicate parseWhere(Element where) {
        List<Element> operators = children(where);
        if (operators.stream().anyMatch(operator -> !supportedOperator(operator))) {
            throw unsupportedExpression();
        }
        if (operators.size() != 1) {
            throw invalidStructure();
        }
        return parsePredicate(operators.getFirst(), 1, new ParseBudget());
    }

    private static Predicate parsePredicate(Element operator, int depth, ParseBudget budget) {
        budget.enter(depth);
        if (!DAV.equals(namespace(operator))) {
            throw unsupportedExpression();
        }
        return switch (localName(operator)) {
            case "and" -> parseLogical(operator, LogicalOperator.AND, depth, budget);
            case "or" -> parseLogical(operator, LogicalOperator.OR, depth, budget);
            case "not" -> parseLogical(operator, LogicalOperator.NOT, depth, budget);
            case "eq" -> parseComparison(operator, ComparisonOperator.EQ, budget);
            case "lt" -> parseComparison(operator, ComparisonOperator.LT, budget);
            case "lte" -> parseComparison(operator, ComparisonOperator.LTE, budget);
            case "gt" -> parseComparison(operator, ComparisonOperator.GT, budget);
            case "gte" -> parseComparison(operator, ComparisonOperator.GTE, budget);
            case "like" -> parseComparison(operator, ComparisonOperator.LIKE, budget);
            case "is-collection" -> parseIsCollection(operator);
            case "is-defined" -> parseIsDefined(operator);
            default -> throw unsupportedExpression();
        };
    }

    private static Predicate parseLogical(
            Element element, LogicalOperator operator, int depth, ParseBudget budget) {
        List<Element> children = children(element);
        if (children.stream().anyMatch(child -> !supportedOperator(child))) {
            throw unsupportedExpression();
        }
        if ((operator == LogicalOperator.NOT && children.size() != 1)
                || (operator != LogicalOperator.NOT && children.isEmpty())) {
            throw invalidStructure();
        }
        List<Predicate> operands = new ArrayList<>(children.size());
        for (Element child : children) {
            operands.add(parsePredicate(child, depth + 1, budget));
        }
        return new LogicalPredicate(operator, operands);
    }

    private static boolean supportedOperator(Element element) {
        if (!DAV.equals(namespace(element))) {
            return false;
        }
        return switch (localName(element)) {
            case "and", "or", "not", "eq", "lt", "lte", "gt", "gte", "like",
                    "is-collection", "is-defined" -> true;
            default -> false;
        };
    }

    private static Predicate parseComparison(
            Element element, ComparisonOperator operator, ParseBudget budget) {
        rejectCaseless(element);
        if (children(element).stream().anyMatch(child -> isDav(child, "typed-literal"))) {
            throw unsupportedExpression();
        }
        Element prop = exactlyOneDavChild(element, "prop");
        Element literalElement = exactlyOneDavChild(element, "literal");
        QName property = propertyName(prop);
        String literal = directText(literalElement);
        budget.literal(literal);
        if (!SCALAR_SEARCHABLE_PROPERTIES.contains(property)) {
            throw unsupportedExpression();
        }
        if (operator == ComparisonOperator.LIKE) {
            if (!LIKE_PROPERTIES.contains(property) || !validLikeLiteral(literal)) {
                throw unsupportedExpression();
            }
        }
        if (GET_CONTENT_LENGTH.equals(property)
                && !CANONICAL_UNSIGNED_DECIMAL.matcher(literal).matches()) {
            throw unsupportedExpression();
        }
        return new ComparisonPredicate(operator, property, literal);
    }

    private static Predicate parseIsDefined(Element element) {
        QName property = propertyName(exactlyOneDavChild(element, "prop"));
        if (!SELECTABLE_PROPERTIES.contains(property)) {
            throw unsupportedExpression();
        }
        return new IsDefinedPredicate(property);
    }

    private static Predicate parseIsCollection(Element element) {
        if (children(element).stream().anyMatch(child -> isDav(child, "prop"))) {
            throw unsupportedExpression();
        }
        return new IsCollectionPredicate();
    }

    private static List<OrderClause> parseOrderBy(Element orderBy) {
        List<Element> orders = children(orderBy).stream()
                .filter(child -> isDav(child, "order"))
                .toList();
        if (orders.isEmpty()) {
            throw invalidStructure();
        }
        if (orders.size() > MAX_ORDER_CLAUSES) {
            throw unsupportedExpression();
        }
        return orders.stream().map(FilesWebDavSearchParser::parseOrder).toList();
    }

    private static OrderClause parseOrder(Element order) {
        rejectCaseless(order);
        if (children(order).stream().anyMatch(child -> isDav(child, "score"))) {
            throw unsupportedExpression();
        }
        QName property = propertyName(exactlyOneDavChild(order, "prop"));
        if (!ORDERABLE_PROPERTIES.contains(property)) {
            throw unsupportedExpression();
        }
        List<Element> directions = children(order).stream()
                .filter(child -> isDav(child, "ascending") || isDav(child, "descending"))
                .toList();
        if (directions.size() > 1) {
            throw invalidStructure();
        }
        OrderDirection direction = directions.isEmpty() || isDav(directions.getFirst(), "ascending")
                ? OrderDirection.ASCENDING
                : OrderDirection.DESCENDING;
        return new OrderClause(property, direction);
    }

    private static int parseLimit(Element limit) {
        Element nResults = exactlyOneDavChild(limit, "nresults");
        String value = directText(nResults).trim();
        if (!UNSIGNED_DECIMAL.matcher(value).matches()) {
            throw invalidStructure();
        }
        BigInteger parsed = new BigInteger(value);
        return parsed.compareTo(BigInteger.valueOf(MAX_LIMIT)) > 0 ? MAX_LIMIT : parsed.intValue();
    }

    private static QName propertyName(Element prop) {
        List<Element> properties = children(prop);
        if (properties.size() != 1) {
            throw invalidStructure();
        }
        return qName(properties.getFirst());
    }

    private static Element exactlyOneDavChild(Element parent, String childName) {
        List<Element> matches = children(parent).stream()
                .filter(child -> isDav(child, childName))
                .toList();
        if (matches.size() != 1) {
            throw invalidStructure();
        }
        return matches.getFirst();
    }

    private static void rejectCaseless(Element element) {
        if (element.hasAttributeNS(null, "caseless")) {
            throw unsupportedExpression();
        }
    }

    private static boolean validLikeLiteral(String literal) {
        for (int index = 0; index < literal.length(); index++) {
            if (literal.charAt(index) != '\\') {
                continue;
            }
            if (++index >= literal.length()) {
                return false;
            }
            char escaped = literal.charAt(index);
            if (escaped != '%' && escaped != '_' && escaped != '\\') {
                return false;
            }
        }
        return true;
    }

    private static String requestProductPath(HttpServletRequest request) {
        URI requestUri = requestUri(request);
        return productPath(requestUri.getRawPath(), request.getContextPath());
    }

    private static String resolveScopePath(HttpServletRequest request, String href, String arbiterPath) {
        try {
            URI requestUri = requestUri(request);
            URI reference = new URI(href);
            rejectInvalidReference(reference);
            if ((reference.isAbsolute() || reference.getRawAuthority() != null)
                    && !sameOrigin(requestUri, reference)) {
                throw invalidScope();
            }
            URI resolved = requestUri.resolve(reference);
            if (!sameOrigin(requestUri, resolved)) {
                throw invalidScope();
            }
            String scopePath = productPath(resolved.getRawPath(), request.getContextPath());
            if (!("/".equals(arbiterPath)
                    || scopePath.equals(arbiterPath)
                    || scopePath.startsWith(arbiterPath + "/"))) {
                throw invalidScope();
            }
            return scopePath;
        } catch (SearchRequestException exception) {
            throw exception;
        } catch (RuntimeException | URISyntaxException exception) {
            throw invalidScope();
        }
    }

    private static URI requestUri(HttpServletRequest request) {
        try {
            return new URI(request.getRequestURL().toString());
        } catch (URISyntaxException exception) {
            throw invalidScope();
        }
    }

    private static void rejectInvalidReference(URI reference) {
        if (reference.getRawUserInfo() != null
                || reference.getRawQuery() != null
                || reference.getRawFragment() != null
                || reference.isOpaque()
                || reference.getRawPath() == null) {
            throw invalidScope();
        }
        validateRawPath(reference.getRawPath());
    }

    private static String productPath(String rawPath, String contextPath) {
        validateRawPath(rawPath);
        String decoded;
        try {
            decoded = UriUtils.decode(rawPath, StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw invalidScope();
        }
        String context = contextPath == null ? "" : contextPath;
        if (!context.isEmpty()) {
            if (!(decoded.equals(context) || decoded.startsWith(context + "/"))) {
                throw invalidScope();
            }
            decoded = decoded.substring(context.length());
        }
        if (!(decoded.equals(WEBDAV_PREFIX) || decoded.startsWith(WEBDAV_PREFIX + "/"))) {
            throw invalidScope();
        }
        String suffix = decoded.substring(WEBDAV_PREFIX.length());
        try {
            return FilePathCodec.normalizeProductPath(suffix.isEmpty() ? "/" : suffix);
        } catch (RuntimeException exception) {
            throw invalidScope();
        }
    }

    private static void validateRawPath(String rawPath) {
        if (rawPath == null || rawPath.indexOf('\\') >= 0 || ENCODED_SEPARATOR.matcher(rawPath).find()) {
            throw invalidScope();
        }
        String decoded;
        try {
            decoded = UriUtils.decode(rawPath, StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw invalidScope();
        }
        if (decoded.indexOf('\0') >= 0 || decoded.indexOf('\\') >= 0) {
            throw invalidScope();
        }
        for (String segment : decoded.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw invalidScope();
            }
        }
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme() != null
                && right.getScheme() != null
                && left.getHost() != null
                && right.getHost() != null
                && left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return switch (uri.getScheme().toLowerCase(Locale.ROOT)) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
    }

    private static List<Element> children(Element parent) {
        List<Element> children = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                children.add(element);
            }
        }
        return List.copyOf(children);
    }

    private static String directText(Element element) {
        StringBuilder text = new StringBuilder();
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(node.getNodeValue());
            }
        }
        return text.toString();
    }

    private static boolean isDav(Element element, String localName) {
        return DAV.equals(namespace(element)) && localName.equals(localName(element));
    }

    private static String namespace(Element element) {
        return element.getNamespaceURI() == null ? "" : element.getNamespaceURI();
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getTagName() : element.getLocalName();
    }

    private static QName qName(Element element) {
        return new QName(namespace(element), localName(element));
    }

    private static QName dav(String localName) {
        return new QName(DAV, localName);
    }

    private static SearchRequestException invalidStructure() {
        return new SearchRequestException(
                HttpStatus.BAD_REQUEST,
                null,
                "The WebDAV SEARCH request is invalid.");
    }

    private static SearchRequestException unsupportedMediaType() {
        return new SearchRequestException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                null,
                "The WebDAV SEARCH media type is unsupported.");
    }

    private static SearchRequestException bodyTooLarge() {
        return new SearchRequestException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                null,
                "The WebDAV SEARCH request body exceeds the supported limit.");
    }

    private static SearchRequestException unsupportedExpression() {
        return new SearchRequestException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                null,
                "The WebDAV SEARCH expression is unsupported.");
    }

    private static SearchRequestException invalidScope() {
        return new SearchRequestException(
                HttpStatus.CONFLICT,
                DavCondition.SEARCH_SCOPE_VALID,
                "The WebDAV SEARCH scope is invalid.");
    }

    private static SearchRequestException forbidden(DavCondition condition) {
        return new SearchRequestException(
                HttpStatus.FORBIDDEN,
                condition,
                "The requested WebDAV SEARCH capability is unsupported.");
    }

    public enum DavCondition {
        NO_EXTERNAL_ENTITIES("no-external-entities"),
        SEARCH_GRAMMAR_SUPPORTED("search-grammar-supported"),
        SEARCH_GRAMMAR_DISCOVERY_SUPPORTED("search-grammar-discovery-supported"),
        SEARCH_MULTIPLE_SCOPE_SUPPORTED("search-multiple-scope-supported"),
        SEARCH_SCOPE_VALID("search-scope-valid");

        private final String localName;

        DavCondition(String localName) {
            this.localName = localName;
        }

        public String localName() {
            return localName;
        }

        public QName qName() {
            return new QName(DAV, localName);
        }
    }

    public static final class SearchRequestException extends RuntimeException {
        private final HttpStatus status;
        private final DavCondition davCondition;

        private SearchRequestException(HttpStatus status, DavCondition davCondition, String message) {
            super(message);
            this.status = Objects.requireNonNull(status, "status");
            this.davCondition = davCondition;
        }

        public HttpStatus status() {
            return status;
        }

        public Optional<DavCondition> davCondition() {
            return Optional.ofNullable(davCondition);
        }
    }

    private record Scope(String path, FilesSearch.ScopeDepth depth) {}

    private static final class ParseBudget {
        private int operators;

        void enter(int depth) {
            operators++;
            if (depth > MAX_EXPRESSION_DEPTH || operators > MAX_OPERATORS) {
                throw unsupportedExpression();
            }
        }

        void literal(String literal) {
            if (literal.codePointCount(0, literal.length()) > MAX_LITERAL_CODE_POINTS) {
                throw unsupportedExpression();
            }
        }
    }

    private static final class SilentErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
