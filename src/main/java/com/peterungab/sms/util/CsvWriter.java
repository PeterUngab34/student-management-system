package com.peterungab.sms.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes RFC 4180 CSV. Files start with a UTF-8 BOM so Excel shows names like "Ibañez" correctly. */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static void write(Path file, List<String> header, List<List<String>> rows) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write('﻿');
            write(w, header, rows);
        }
    }

    public static void write(Writer w, List<String> header, List<List<String>> rows) throws IOException {
        writeLine(w, header);
        for (List<String> row : rows) {
            writeLine(w, row);
        }
    }

    private static void writeLine(Writer w, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                w.write(',');
            }
            w.write(escape(values.get(i)));
        }
        w.write("\r\n");
    }

    /** Quotes a value when it contains a comma, quote or line break; doubles embedded quotes. */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        return needsQuotes ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
