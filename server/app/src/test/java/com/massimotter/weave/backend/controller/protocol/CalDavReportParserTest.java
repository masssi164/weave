package com.massimotter.weave.backend.controller.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalDavReportParserTest {

    @Test
    void parsesNamespacedReportPropertiesWithoutDependingOnPrefixesOrAttributeOrder() {
        var report = CalDavReportParser.parse("""
                <calendar-query xmlns="urn:ietf:params:xml:ns:caldav" xmlns:d="DAV:">
                  <filter>
                    <comp-filter name="VCALENDAR">
                      <comp-filter name="VEVENT">
                        <time-range end="20261102T000000Z" start="20261030T000000Z"/>
                      </comp-filter>
                    </comp-filter>
                  </filter>
                  <d:href>/caldav/workspace/dst-event.ics</d:href>
                </calendar-query>
                """);

        assertThat(report.kind()).isEqualTo(CalDavReportParser.Kind.CALENDAR_QUERY);
        assertThat(report.rangeStart()).isEqualTo("20261030T000000Z");
        assertThat(report.rangeEnd()).isEqualTo("20261102T000000Z");
        assertThat(report.hrefs()).containsExactly("/caldav/workspace/dst-event.ics");
    }

    @Test
    void parsesSyncTokenAndMultigetHrefsAsStructuredXml() {
        var sync = CalDavReportParser.parse("""
                <x:sync-collection xmlns:x="DAV:">
                  <x:sync-token>weave-sync-7</x:sync-token>
                  <x:sync-level>1</x:sync-level>
                </x:sync-collection>
                """);
        var multiget = CalDavReportParser.parse("""
                <c:calendar-multiget xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:d="DAV:">
                  <d:href>/caldav/workspace/one.ics</d:href>
                  <d:href>/caldav/workspace/two.ics</d:href>
                </c:calendar-multiget>
                """);

        assertThat(sync.kind()).isEqualTo(CalDavReportParser.Kind.SYNC_COLLECTION);
        assertThat(sync.syncToken()).isEqualTo("weave-sync-7");
        assertThat(multiget.hrefs()).containsExactly(
                "/caldav/workspace/one.ics",
                "/caldav/workspace/two.ics");
    }

    @Test
    void rejectsDoctypeAndExternalEntityPayloads() {
        assertThatThrownBy(() -> CalDavReportParser.parse("""
                <!DOCTYPE report [<!ENTITY provider SYSTEM "file:///etc/passwd">]>
                <c:calendar-multiget xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:d="DAV:">
                  <d:href>&provider;</d:href>
                </c:calendar-multiget>
                """))
                .isInstanceOf(CalDavReportParser.InvalidCalDavReportException.class)
                .hasMessageContaining("safe XML");
    }
}
