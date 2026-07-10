package com.massimotter.weave.backend.controller.protocol;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public final class CalDavReportParser {

    private static final int MAX_REPORT_CHARS = 256 * 1024;
    private static final String DAV = "DAV:";
    private static final String CALDAV = "urn:ietf:params:xml:ns:caldav";

    private CalDavReportParser() {
    }

    public static Report parse(String xml) {
        if (xml == null || xml.isBlank() || xml.length() > MAX_REPORT_CHARS) {
            throw new InvalidCalDavReportException("CalDAV REPORT body is missing or too large.");
        }
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
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            Element root = document.getDocumentElement();
            Kind kind = Kind.from(root.getNamespaceURI(), root.getLocalName());
            List<String> hrefs = elementTexts(document, DAV, "href");
            String syncToken = firstElementText(document, DAV, "sync-token");
            Element timeRange = firstElement(document, CALDAV, "time-range");
            return new Report(
                    kind,
                    hrefs,
                    syncToken,
                    timeRange == null ? null : blankToNull(timeRange.getAttribute("start")),
                    timeRange == null ? null : blankToNull(timeRange.getAttribute("end")));
        } catch (InvalidCalDavReportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidCalDavReportException("CalDAV REPORT body is not valid safe XML.");
        }
    }

    private static List<String> elementTexts(Document document, String namespace, String localName) {
        NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
        List<String> values = new ArrayList<>();
        for (int index = 0; index < nodes.getLength(); index++) {
            String value = blankToNull(nodes.item(index).getTextContent());
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static String firstElementText(Document document, String namespace, String localName) {
        NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
        return nodes.getLength() == 0 ? null : blankToNull(nodes.item(0).getTextContent());
    }

    private static Element firstElement(Document document, String namespace, String localName) {
        NodeList nodes = document.getElementsByTagNameNS(namespace, localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public enum Kind {
        CALENDAR_QUERY,
        CALENDAR_MULTIGET,
        SYNC_COLLECTION,
        FREE_BUSY_QUERY;

        private static Kind from(String namespace, String localName) {
            if (CALDAV.equals(namespace) && "calendar-query".equals(localName)) {
                return CALENDAR_QUERY;
            }
            if (CALDAV.equals(namespace) && "calendar-multiget".equals(localName)) {
                return CALENDAR_MULTIGET;
            }
            if (DAV.equals(namespace) && "sync-collection".equals(localName)) {
                return SYNC_COLLECTION;
            }
            if (CALDAV.equals(namespace) && "free-busy-query".equals(localName)) {
                return FREE_BUSY_QUERY;
            }
            throw new InvalidCalDavReportException("CalDAV REPORT type is unsupported.");
        }
    }

    public record Report(
            Kind kind,
            List<String> hrefs,
            String syncToken,
            String rangeStart,
            String rangeEnd) {

        public Report {
            hrefs = List.copyOf(hrefs == null ? List.of() : hrefs);
        }
    }

    public static final class InvalidCalDavReportException extends IllegalArgumentException {
        public InvalidCalDavReportException(String message) {
            super(message);
        }
    }
}
