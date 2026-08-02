package com.massimotter.weave.e2e;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Semantic assertions for the canonical northbound iCalendar projection. */
final class IcalendarProjectionAssertions {
  private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
  private static final Pattern WORKSPACE_THREAD =
      Pattern.compile("meeting:workspace-default:[0-9a-f]{12}");
  private static final List<String> TEXT_PROPERTIES =
      List.of("UID", "SUMMARY", "DESCRIPTION", "LOCATION", "RRULE", "RDATE", "EXDATE");

  private IcalendarProjectionAssertions() {}

  static void requireWorkspaceProjection(String submitted, String projected, String operation) {
    Map<String, List<Property>> submittedProperties = eventProperties(submitted, operation);
    Map<String, List<Property>> projectedProperties = eventProperties(projected, operation);

    for (String name : TEXT_PROPERTIES) {
      if (!values(submittedProperties, name).equals(values(projectedProperties, name))) {
        fail(operation, "canonical " + name + " semantics changed");
      }
    }
    for (String name : List.of("DTSTART", "DTEND", "RECURRENCE-ID")) {
      if (!temporalValues(submittedProperties, name, operation)
          .equals(temporalValues(projectedProperties, name, operation))) {
        fail(operation, "canonical " + name + " semantics changed");
      }
    }
    if (!signatures(submittedProperties, "ATTENDEE")
        .equals(signatures(projectedProperties, "ATTENDEE"))) {
      fail(operation, "canonical attendee semantics changed");
    }
    requireSingleValue(projectedProperties, "X-WEAVE-CONTEXT-ID", "workspace-default", operation);
    String meetingThread = singleValue(projectedProperties, "X-WEAVE-MEETING-THREAD-ID", operation);
    if (!WORKSPACE_THREAD.matcher(meetingThread).matches()) {
      fail(operation, "canonical meeting-thread projection is invalid");
    }
    if (projectedProperties.containsKey("X-WEAVE-CHANNEL-ID")) {
      fail(operation, "workspace projection unexpectedly contains a channel identifier");
    }
  }

  private static Map<String, List<Property>> eventProperties(String calendar, String operation) {
    if (calendar == null || calendar.isBlank()) {
      fail(operation, "iCalendar body is empty");
    }
    List<String> unfolded = new ArrayList<>();
    for (String line : calendar.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
      if ((line.startsWith(" ") || line.startsWith("\t")) && !unfolded.isEmpty()) {
        int last = unfolded.size() - 1;
        unfolded.set(last, unfolded.get(last) + line.substring(1));
      } else {
        unfolded.add(line);
      }
    }
    boolean inEvent = false;
    Map<String, List<Property>> result = new LinkedHashMap<>();
    for (String line : unfolded) {
      if ("BEGIN:VEVENT".equalsIgnoreCase(line)) {
        inEvent = true;
        continue;
      }
      if ("END:VEVENT".equalsIgnoreCase(line)) {
        break;
      }
      if (!inEvent || line.isBlank()) {
        continue;
      }
      int separator = line.indexOf(':');
      if (separator <= 0 || separator == line.length() - 1) {
        fail(operation, "iCalendar property is malformed");
      }
      String declaration = line.substring(0, separator);
      String name = declaration.split(";", 2)[0].toUpperCase(Locale.ROOT);
      result.computeIfAbsent(name, ignored -> new ArrayList<>())
          .add(new Property(declaration, line.substring(separator + 1)));
    }
    if (!result.containsKey("UID") || !result.containsKey("DTSTART") || !result.containsKey("DTEND")) {
      fail(operation, "iCalendar event omitted required canonical properties");
    }
    return result;
  }

  private static List<String> values(Map<String, List<Property>> properties, String name) {
    return properties.getOrDefault(name, List.of()).stream().map(Property::value).sorted().toList();
  }

  private static List<String> signatures(Map<String, List<Property>> properties, String name) {
    return properties.getOrDefault(name, List.of()).stream()
        .map(Property::semanticSignature)
        .sorted()
        .toList();
  }

  private static List<String> temporalValues(
      Map<String, List<Property>> properties, String name, String operation) {
    return properties.getOrDefault(name, List.of()).stream()
        .map(property -> normalizeTemporal(property, operation))
        .sorted()
        .toList();
  }

  private static String normalizeTemporal(Property property, String operation) {
    String value = property.value();
    try {
      if (property.declaration().toUpperCase(Locale.ROOT).contains("VALUE=DATE")) {
        return "date:" + LocalDate.parse(value, DATE);
      }
      if (value.endsWith("Z")) {
        return "instant:"
            + LocalDateTime.parse(value.substring(0, value.length() - 1), DATE_TIME)
                .toInstant(ZoneOffset.UTC);
      }
      String timezone = parameter(property.declaration(), "TZID");
      if (timezone == null) {
        return "floating:" + LocalDateTime.parse(value, DATE_TIME);
      }
      Instant instant = LocalDateTime.parse(value, DATE_TIME).atZone(ZoneId.of(timezone)).toInstant();
      return "instant:" + instant;
    } catch (java.time.DateTimeException exception) {
      fail(operation, "iCalendar temporal property is invalid");
      throw new IllegalStateException("unreachable", exception);
    }
  }

  private static String parameter(String declaration, String name) {
    for (String part : declaration.split(";")) {
      int separator = part.indexOf('=');
      if (separator > 0 && name.equalsIgnoreCase(part.substring(0, separator))) {
        return part.substring(separator + 1);
      }
    }
    return null;
  }

  private static void requireSingleValue(
      Map<String, List<Property>> properties, String name, String expected, String operation) {
    if (!expected.equals(singleValue(properties, name, operation))) {
      fail(operation, "canonical " + name + " projection is invalid");
    }
  }

  private static String singleValue(
      Map<String, List<Property>> properties, String name, String operation) {
    List<String> values = values(properties, name);
    if (values.size() != 1) {
      fail(operation, "canonical " + name + " projection is not singular");
    }
    return values.getFirst();
  }

  private static void fail(String operation, String reason) {
    throw new ProductFlowException(operation + " failed: " + reason);
  }

  private record Property(String declaration, String value) {
    private String semanticSignature() {
      String[] parts = declaration.split(";");
      List<String> parameters = java.util.Arrays.stream(parts)
          .skip(1)
          .map(parameter -> parameter.toUpperCase(Locale.ROOT))
          .sorted()
          .toList();
      return parts[0].toUpperCase(Locale.ROOT) + ";" + String.join(";", parameters) + ":" + value;
    }
  }
}
