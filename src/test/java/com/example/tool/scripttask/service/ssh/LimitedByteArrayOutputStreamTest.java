package com.example.tool.scripttask.service.ssh;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitedByteArrayOutputStreamTest {

    @Test
    void decodesUtf8WithinLimit() {
        LimitedByteArrayOutputStream out = new LimitedByteArrayOutputStream(64, OutputCharset.fromConfig("AUTO"));
        byte[] bytes = "输出中文".getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
        assertEquals("输出中文", out.asString());
    }

    @Test
    void marksTruncation() {
        LimitedByteArrayOutputStream out = new LimitedByteArrayOutputStream(4, OutputCharset.fromConfig("AUTO"));
        byte[] bytes = "abcdefghij".getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
        String text = out.asString();
        assertTrue(text.startsWith("abcd"));
        assertTrue(text.endsWith("...[输出已截断]"));
    }

    @Test
    void doesNotMarkWhenUnderLimit() {
        LimitedByteArrayOutputStream out = new LimitedByteArrayOutputStream(64, OutputCharset.fromConfig("AUTO"));
        byte[] bytes = "ok".getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
        assertFalse(out.asString().contains("截断"));
    }
}
