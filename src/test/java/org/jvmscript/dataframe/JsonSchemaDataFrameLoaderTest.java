package org.jvmscript.dataframe;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the validate-then-coerce behavior of the FastCSV-based loader, focusing on the
 * silent-corruption cases the previous coerce-then-validate path let through, plus the
 * malformed-input ("torture") cases that matter for unreliable source files.
 */
class JsonSchemaDataFrameLoaderTest {

    @TempDir
    Path dir;

    private static final String SCHEMA = """
            $schema: "https://json-schema.org/draft/2020-12/schema"
            type: array
            items:
              type: object
              properties:
                clOrdId: { type: string, minLength: 1 }
                side: { type: string, pattern: "^[12]$" }
                lastQty: { type: number, minimum: 0 }
                lastPx: { type: [number, "null"], minimum: 0 }
                putOrCall: { type: [integer, "null"], enum: [0, 1, null] }
                strikePrice: { type: [number, "null"] }
                tradeDate: { type: string, x-date-format: "yyyyMMdd" }
              required: [clOrdId, side, lastQty]
              dependentRequired:
                strikePrice: [putOrCall]
              additionalProperties: false
            """;

    private static final String HEADER =
            "clOrdId,side,lastQty,lastPx,putOrCall,strikePrice,tradeDate\n";

    private LoadResult load(String csv) throws Exception {
        return loadWithSchema(SCHEMA, csv);
    }

    private LoadResult loadWithSchema(String schema, String csv) throws Exception {
        Path s = dir.resolve("schema.yaml");
        Path c = dir.resolve("data.csv");
        Files.writeString(s, schema);
        Files.writeString(c, csv);
        return DataFrameUtility.loadCsvWithYamlSchema(c.toString(), s.toString());
    }

    @Test
    void blankPutOrCallIsNullNotZero() throws Exception {
        // The old path coerced empty integer -> 0, which is enum-valid (= "Put"). Must now be null.
        LoadResult r = load(HEADER + "A1,1,100,10.5,,,20240115\n");
        assertEquals(1, r.validData.height());
        assertEquals(0, r.getTotalBadRows());
        assertNull(r.validData.getColumn("putOrCall").get(0));
        assertEquals(0, new BigDecimal("10.5")
                .compareTo((BigDecimal) r.validData.getColumn("lastPx").get(0)));
    }

    @Test
    void garbageNumberInNullableFieldIsRejectedNotSilentlyNulled() throws Exception {
        // The old path turned "N/A" -> null and (since lastPx is nullable) passed it silently.
        LoadResult r = load(HEADER + "A2,1,100,N/A,,,20240115\n");
        assertEquals(0, r.validData.height());
        assertEquals(1, r.validationErrors.size());
    }

    @Test
    void garbagePutOrCallRejected() throws Exception {
        // The old path coerced "X" -> 0 (= "Put") and passed. Must now be flagged.
        LoadResult r = load(HEADER + "A3,2,100,10,X,,20240115\n");
        assertEquals(0, r.validData.height());
        assertEquals(1, r.validationErrors.size());
    }

    @Test
    void groupedThousandsAcceptedMalformedCommaRejected() throws Exception {
        LoadResult good = load(HEADER + "A4,1,\"1,234\",10,,,20240115\n");
        assertEquals(1, good.validData.height());
        assertEquals(0, new BigDecimal("1234")
                .compareTo((BigDecimal) good.validData.getColumn("lastQty").get(0)));

        LoadResult bad = load(HEADER + "A5,1,\"1,2,3\",10,,,20240115\n");
        assertEquals(0, bad.validData.height());
        assertEquals(1, bad.validationErrors.size());
    }

    @Test
    void raggedRowIsParseErrorWithTrueLineNumber() throws Exception {
        LoadResult r = load(HEADER + "A6,1,100,10,,,20240115\nTOO,FEW\n");
        assertEquals(1, r.validData.height());
        assertEquals(1, r.parseErrors.size());
        assertEquals(3, r.parseErrors.get(0).rowNumber); // header=1, good row=2, ragged=3
    }

    @Test
    void impossibleDateRejected() throws Exception {
        // 20240230 matches an 8-digit pattern but is not a real date.
        LoadResult r = load(HEADER + "A7,1,100,10,,,20240230\n");
        assertEquals(0, r.validData.height());
        assertEquals(1, r.validationErrors.size());
    }

    @Test
    void dependentRequiredEnforced() throws Exception {
        // strikePrice present requires putOrCall.
        LoadResult r = load(HEADER + "A8,1,100,10,,55,20240115\n");
        assertEquals(0, r.validData.height());
        assertEquals(1, r.validationErrors.size());
    }

    @Test
    void additionalPropertiesRejectsUnknownColumn() throws Exception {
        LoadResult r = loadWithSchema(SCHEMA, "clOrdId,side,lastQty,junk\nA9,1,100,surprise\n");
        assertEquals(0, r.validData.height());
        assertFalse(r.validationErrors.isEmpty());
    }

    @Test
    void bomHeaderHandled() throws Exception {
        LoadResult r = load("﻿" + HEADER + "B1,1,100,10,,,20240115\n");
        assertEquals(1, r.validData.height());
        assertEquals(0, r.getTotalBadRows());
    }

    @Test
    void unterminatedQuoteIsCaughtNotSilentlyCorrupting() throws Exception {
        // C2 opens a quote that never closes; C1 must survive and the bad input must be reported.
        LoadResult r = load(HEADER
                + "C1,1,100,10,,,20240115\n"
                + "C2,1,\"oops,1,,,20240115\n");
        assertTrue(r.validData.height() >= 1);
        assertTrue(r.parseErrors.size() + r.validationErrors.size() >= 1);
    }

    private static final String TS_SCHEMA = """
            $schema: "https://json-schema.org/draft/2020-12/schema"
            type: array
            items:
              type: object
              properties:
                ts: { type: string, x-date-format: "yyyyMMdd-HH:mm:ss[.SSS]" }
              required: [ts]
              additionalProperties: false
            """;

    @Test
    void datetimeWithOptionalMillisNotFalselyRejected() throws Exception {
        // The optional [.SSS] section must parse whether or not millis are present.
        LoadResult noMillis = loadWithSchema(TS_SCHEMA, "ts\n20240115-10:30:00\n");
        assertEquals(1, noMillis.validData.height());
        assertEquals(0, noMillis.getTotalBadRows());

        LoadResult withMillis = loadWithSchema(TS_SCHEMA, "ts\n20240115-10:30:00.123\n");
        assertEquals(1, withMillis.validData.height());
    }

    @Test
    void datetimeImpossibleValueRejected() throws Exception {
        LoadResult badHour = loadWithSchema(TS_SCHEMA, "ts\n20240115-25:30:00\n"); // hour 25
        assertEquals(0, badHour.validData.height());
        assertEquals(1, badHour.validationErrors.size());
    }

    // --- IMP-2: x-not-future (@PastOrPresent port) -------------------------------------------------
    // Comparison zone is America/New_York; strict (no tolerance); the clock is injected so "now" is fixed.

    private static final String NF_DATE_SCHEMA = """
            $schema: "https://json-schema.org/draft/2020-12/schema"
            type: array
            items:
              type: object
              properties:
                d: { type: string, x-date-format: "yyyyMMdd", x-not-future: true }
              required: [d]
              additionalProperties: false
            """;

    private static final String NF_TS_SCHEMA = """
            $schema: "https://json-schema.org/draft/2020-12/schema"
            type: array
            items:
              type: object
              properties:
                ts: { type: string, x-date-format: "yyyyMMdd-HH:mm:ss", x-not-future: true }
              required: [ts]
              additionalProperties: false
            """;

    private LoadResult loadWithClock(String schema, String csv, java.time.Clock clock) throws Exception {
        Path s = dir.resolve("nf-schema.yaml");
        Path c = dir.resolve("nf-data.csv");
        Files.writeString(s, schema);
        Files.writeString(c, csv);
        return YamlSchemaDataFrameLoader.loadCsvWithYamlSchema(c.toString(), s.toString(),
                new JsonSchemaDataFrameLoader.LoadOptions().verbose(false).clock(clock));
    }

    private static java.time.Clock at(String instant) {
        return java.time.Clock.fixed(java.time.Instant.parse(instant), java.time.ZoneOffset.UTC);
    }

    @Test
    void notFutureRejectsFutureAcceptsToday() throws Exception {
        // 2026-07-03 12:00 UTC = 08:00 New York -> "today" in NY is 2026-07-03.
        java.time.Clock clock = at("2026-07-03T12:00:00Z");
        LoadResult r = loadWithClock(NF_DATE_SCHEMA, "d\n20260703\n20260704\n", clock);
        assertEquals(1, r.validData.height(), "today passes, tomorrow (future) rejected");
        assertEquals(1, r.validationErrors.size());
        assertEquals(java.time.LocalDate.of(2026, 7, 3), r.validData.getColumn("d").get(0));
    }

    @Test
    void notFutureUsesNewYorkZoneAtUtcBoundary() throws Exception {
        // 2026-07-04 02:00 UTC = 2026-07-03 22:00 New York (EDT, UTC-4): it is ALREADY 2026-07-04 in
        // UTC but STILL 2026-07-03 in NY. A UTC-based check would wrongly accept 20260704.
        java.time.Clock clock = at("2026-07-04T02:00:00Z");
        LoadResult r = loadWithClock(NF_DATE_SCHEMA, "d\n20260703\n20260704\n", clock);
        assertEquals(1, r.validData.height(), "20260703 (today in NY) passes; 20260704 (tomorrow in NY) rejected");
        assertEquals(1, r.validationErrors.size());
        assertEquals(java.time.LocalDate.of(2026, 7, 3), r.validData.getColumn("d").get(0));
    }

    @Test
    void notFutureIsStrictNoToleranceOnDatetime() throws Exception {
        // 2026-07-03 13:30:00 UTC = 09:30:00 New York -> now(NY) == 2026-07-03T09:30:00.
        java.time.Clock clock = at("2026-07-03T13:30:00Z");
        LoadResult r = loadWithClock(NF_TS_SCHEMA,
                "ts\n20260703-09:30:00\n20260703-09:30:01\n", clock); // equal-to-now, now+1s
        assertEquals(1, r.validData.height(), "equal-to-now passes, one second later is future -> rejected");
        assertEquals(1, r.validationErrors.size());
        assertEquals(java.time.LocalDateTime.of(2026, 7, 3, 9, 30, 0), r.validData.getColumn("ts").get(0));
    }

    @Test
    void notFutureInertWithoutTheKeyword() throws Exception {
        // Same future date, but the control schema omits x-not-future -> the future check must not fire.
        String control = NF_DATE_SCHEMA.replace(", x-not-future: true", "");
        LoadResult r = loadWithClock(control, "d\n20260704\n", at("2026-07-03T12:00:00Z"));
        assertEquals(1, r.validData.height(), "no x-not-future -> a future date is accepted");
        assertEquals(0, r.getTotalBadRows());
    }
}
