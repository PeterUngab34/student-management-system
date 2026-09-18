package com.peterungab.sms.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvWriterTest {

    @Test
    void escapesSpecialCharacters() {
        assertEquals("plain", CsvWriter.escape("plain"));
        assertEquals("\"Dela Cruz, Juan\"", CsvWriter.escape("Dela Cruz, Juan"));
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""));
        assertEquals("", CsvWriter.escape(null));
    }

    @Test
    void writesHeaderAndRows() throws IOException {
        StringWriter out = new StringWriter();
        CsvWriter.write(out, List.of("No.", "Name"), List.of(List.of("2023-00123", "Santos, Maria")));
        assertEquals("No.,Name\r\n2023-00123,\"Santos, Maria\"\r\n", out.toString());
    }

    @Test
    void fileStartsWithUtf8Bom(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("students.csv");
        CsvWriter.write(file, List.of("Name"), List.of(List.of("Ibañez")));
        byte[] bytes = Files.readAllBytes(file);
        assertEquals((byte) 0xEF, bytes[0]);
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("Ibañez"));
    }
}
