package com.massimotter.weave.backend.controller.protocol;

import com.massimotter.weave.backend.service.files.FilePathCodec;
import com.massimotter.weave.backend.service.files.WebDavSyncRequest;
import com.massimotter.weave.backend.service.files.WebDavSyncRequest.SyncLevel;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

/** Strict, bounded parser for the Weave-supported RFC 6578 sync-collection request. */
public final class FilesWebDavSyncParser {

    private static final String DAV = "DAV:";
    private static final String XINCLUDE = "http://www.w3.org/2001/XInclude";
    private static final String XML_SCHEMA_INSTANCE = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String WEBDAV_PREFIX = "/dav/files";
    private static final int MAXIMUM_BODY_BYTES = 65_536;
    private static final Pattern EXTERNAL_DECLARATION = Pattern.compile(
            "(?is)<!DOCTYPE\\b[^>]*(?:SYSTEM|PUBLIC)|<!ENTITY\\b[^>]*(?:SYSTEM|PUBLIC)");
    private static final Pattern UNSIGNED_DECIMAL = Pattern.compile("[0-9]+");
    private static final Pattern ENCODED_SEPARATOR = Pattern.compile("(?i)%2f|%5c");

    private FilesWebDavSyncParser() {}

    /** Parses one request without resolving a collection, token, journal row, or provider. */
    public static WebDavSyncRequest parse(HttpServletRequest request) {
        Objects.requireNonNull(request, "request");
        requireDepthZero(request);
        Charset mediaCharset = validateMediaType(request.getContentType());
        byte[] body = readBoundedBody(request);
        rejectExternalDeclarations(body, mediaCharset);

        Document document = parseSafeXml(body);
        rejectForbiddenXmlFeatures(document);
        Element root = document.getDocumentElement();
        if (root == null || !isDav(root, "sync-collection")) {
            throw invalidStructure();
        }

        List<Element> semantic = children(root).stream()
                .filter(FilesWebDavSyncParser::recognizedRootChild)
                .toList();
        if (semantic.size() != 3 && semantic.size() != 4) {
            throw invalidStructure();
        }
        Element syncToken = semantic.get(0);
        Element syncLevel = semantic.get(1);
        boolean hasLimit = semantic.size() == 4;
        Element limit = hasLimit ? semantic.get(2) : null;
        Element prop = semantic.get(hasLimit ? 3 : 2);
        if (!isDav(syncToken, "sync-token")
                || !isDav(syncLevel, "sync-level")
                || (hasLimit && !isDav(limit, "limit"))
                || !isDav(prop, "prop")
                || !children(syncToken).isEmpty()
                || !children(syncLevel).isEmpty()) {
            throw invalidStructure();
        }

        String token = directText(syncToken).trim();
        if (token.length() > 4_096 || token.chars().anyMatch(FilesWebDavSyncParser::control)) {
            throw invalidStructure();
        }
        SyncLevel level = switch (directText(syncLevel).trim()) {
            case "1" -> SyncLevel.ONE;
            case "infinite" -> SyncLevel.INFINITE;
            default -> throw invalidStructure();
        };
        int resultLimit = hasLimit ? parseLimit(limit) : WebDavSyncRequest.MAXIMUM_LIMIT;
        List<QName> properties = parseProperties(prop);
        return new WebDavSyncRequest(
                requestProductPath(request),
                token,
                level,
                properties,
                resultLimit,
                hasLimit);
    }

    private static boolean recognizedRootChild(Element element) {
        return isDav(element, "sync-token")
                || isDav(element, "sync-level")
                || isDav(element, "limit")
                || isDav(element, "prop");
    }

    private static void requireDepthZero(HttpServletRequest request) {
        List<String> depthValues = Collections.list(request.getHeaders("Depth"));
        if (depthValues.isEmpty()) {
            return;
        }
        if (depthValues.size() != 1 || !"0".equals(depthValues.getFirst().trim())) {
            throw invalidDepth();
        }
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
        } catch (SyncRequestException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unsupportedMediaType();
        }
    }

    private static byte[] readBoundedBody(HttpServletRequest request) {
        if (request.getContentLengthLong() > MAXIMUM_BODY_BYTES) {
            throw bodyTooLarge();
        }
        try {
            byte[] body = request.getInputStream().readNBytes(MAXIMUM_BODY_BYTES + 1);
            if (body.length > MAXIMUM_BODY_BYTES) {
                throw bodyTooLarge();
            }
            if (body.length == 0) {
                throw invalidStructure();
            }
            return body;
        } catch (SyncRequestException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidStructure();
        }
    }

    private static List<QName> parseProperties(Element prop) {
        List<QName> properties = children(prop).stream()
                .map(FilesWebDavSyncParser::qName)
                .toList();
        if (properties.size() > WebDavSyncRequest.MAXIMUM_PROPERTIES) {
            throw unsupportedExpression();
        }
        return properties;
    }

    private static int parseLimit(Element limit) {
        List<Element> nresults = children(limit).stream()
                .filter(child -> isDav(child, "nresults"))
                .toList();
        if (nresults.size() != 1 || !children(nresults.getFirst()).isEmpty()) {
            throw invalidStructure();
        }
        String value = directText(nresults.getFirst()).trim();
        if (!UNSIGNED_DECIMAL.matcher(value).matches()) {
            throw invalidStructure();
        }
        BigInteger parsed = new BigInteger(value);
        return parsed.compareTo(BigInteger.valueOf(WebDavSyncRequest.MAXIMUM_LIMIT)) > 0
                ? WebDavSyncRequest.MAXIMUM_LIMIT
                : parsed.intValue();
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
        } catch (SyncRequestException exception) {
            throw exception;
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

    private static String requestProductPath(HttpServletRequest request) {
        String rawPath = request.getRequestURI();
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        validateRawPath(rawPath);
        String decoded;
        try {
            decoded = UriUtils.decode(rawPath, StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw invalidTarget();
        }
        if (!contextPath.isEmpty()) {
            if (!(decoded.equals(contextPath) || decoded.startsWith(contextPath + "/"))) {
                throw invalidTarget();
            }
            decoded = decoded.substring(contextPath.length());
        }
        if (!(decoded.equals(WEBDAV_PREFIX) || decoded.startsWith(WEBDAV_PREFIX + "/"))) {
            throw invalidTarget();
        }
        String suffix = decoded.substring(WEBDAV_PREFIX.length());
        try {
            return FilePathCodec.normalizeProductPath(suffix.isEmpty() ? "/" : suffix);
        } catch (RuntimeException exception) {
            throw invalidTarget();
        }
    }

    private static void validateRawPath(String rawPath) {
        if (rawPath == null || rawPath.indexOf('\\') >= 0 || ENCODED_SEPARATOR.matcher(rawPath).find()) {
            throw invalidTarget();
        }
        String decoded;
        try {
            decoded = UriUtils.decode(rawPath, StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw invalidTarget();
        }
        if (decoded.indexOf('\0') >= 0 || decoded.indexOf('\\') >= 0) {
            throw invalidTarget();
        }
        for (String segment : decoded.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                throw invalidTarget();
            }
        }
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

    private static boolean control(int character) {
        return character < 0x20 && character != '\t' && character != '\n' && character != '\r';
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

    private static SyncRequestException invalidStructure() {
        return new SyncRequestException(
                HttpStatus.BAD_REQUEST,
                null,
                "The WebDAV synchronization request is invalid.");
    }

    private static SyncRequestException invalidDepth() {
        return new SyncRequestException(
                HttpStatus.BAD_REQUEST,
                null,
                "The WebDAV synchronization Depth header must be zero.");
    }

    private static SyncRequestException invalidTarget() {
        return new SyncRequestException(
                HttpStatus.CONFLICT,
                null,
                "The WebDAV synchronization collection is invalid.");
    }

    private static SyncRequestException unsupportedMediaType() {
        return new SyncRequestException(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                null,
                "The WebDAV synchronization media type is unsupported.");
    }

    private static SyncRequestException bodyTooLarge() {
        return new SyncRequestException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                null,
                "The WebDAV synchronization request body exceeds the supported limit.");
    }

    private static SyncRequestException unsupportedExpression() {
        return new SyncRequestException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                null,
                "The WebDAV synchronization request exceeds the supported profile.");
    }

    private static SyncRequestException forbidden(DavCondition condition) {
        return new SyncRequestException(
                HttpStatus.FORBIDDEN,
                condition,
                "The requested WebDAV synchronization capability is unsupported.");
    }

    public enum DavCondition {
        NO_EXTERNAL_ENTITIES("no-external-entities"),
        VALID_SYNC_TOKEN("valid-sync-token"),
        NUMBER_OF_MATCHES_WITHIN_LIMITS("number-of-matches-within-limits"),
        SYNC_TRAVERSAL_SUPPORTED("sync-traversal-supported");

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

    public static final class SyncRequestException extends RuntimeException {
        private final HttpStatus status;
        private final DavCondition davCondition;

        private SyncRequestException(HttpStatus status, DavCondition davCondition, String message) {
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
