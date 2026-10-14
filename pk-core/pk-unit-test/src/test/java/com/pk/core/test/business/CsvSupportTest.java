package com.pk.core.test.business;

import com.pk.core.business.service.impl.CsvSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** CSV import/export (V24): trích dẫn, xuống dòng trong ô, BOM, chống CSV injection. */
class CsvSupportTest {

    @Test
    void quotesCommasQuotesAndNewlines() {
        String line = CsvSupport.line(List.of("a,b", "say \"hi\"", "x\ny", "plain"));
        assertEquals("\"a,b\",\"say \"\"hi\"\"\",\"x\ny\",plain", line);
    }

    @Test
    void parseRoundTripsQuotedCellsAndSkipsBlankLines() {
        String text = "﻿a,b,c\r\n\"1,5\",\"he said \"\"x\"\"\",\"l1\nl2\"\r\n\r\n,,\r\nlast,,";
        List<List<String>> rows = CsvSupport.parse(text);
        assertEquals(3, rows.size());
        assertEquals(List.of("a", "b", "c"), rows.get(0));
        assertEquals(List.of("1,5", "he said \"x\"", "l1\nl2"), rows.get(1));
        assertEquals(List.of("last", "", ""), rows.get(2));
    }

    @Test
    void formulaLikeCellsAreNeutralisedButNumbersAreNot() {
        assertEquals("'=SUM(A1)", CsvSupport.cell("=SUM(A1)"));
        assertEquals("'@cmd", CsvSupport.cell("@cmd"));
        assertEquals("-5", CsvSupport.cell("-5"));
        assertEquals("", CsvSupport.cell(null));
    }
}
