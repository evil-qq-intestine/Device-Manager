package com.example.tool.scripttask.service.ssh;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutputCharsetTest {

    @Test
    void autoDecodesUtf8() {
        assertEquals("输出中文测试",
                OutputCharset.fromConfig("AUTO").decode("输出中文测试".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void autoFallsBackToGb18030() {
        byte[] gbk = "无法将 opencodexyz 识别为命令".getBytes(Charset.forName("GBK"));
        assertEquals("无法将 opencodexyz 识别为命令", OutputCharset.fromConfig("AUTO").decode(gbk));
    }

    @Test
    void autoRepairsTruncatedUtf8() {
        byte[] utf8 = "输出中文测试".getBytes(StandardCharsets.UTF_8);
        byte[] cut = Arrays.copyOf(utf8, utf8.length - 2);
        assertEquals("输出中文测", OutputCharset.fromConfig("AUTO").decode(cut, true));
    }

    @Test
    void autoKeepsCompleteUtf8WhenTruncated() {
        byte[] utf8 = "输出中文".getBytes(StandardCharsets.UTF_8);
        assertEquals("输出中文", OutputCharset.fromConfig("AUTO").decode(utf8, true));
    }

    @Test
    void autoDoesNotRepairWhenNotTruncated() {
        byte[] utf8 = "输出中文测试".getBytes(StandardCharsets.UTF_8);
        byte[] cut = Arrays.copyOf(utf8, utf8.length - 2);
        assertEquals(new String(cut, Charset.forName("GB18030")),
                OutputCharset.fromConfig("AUTO").decode(cut));
    }

    @Test
    void explicitCharsetsAreUsed() {
        assertEquals("输出",
                OutputCharset.fromConfig("UTF-8").decode("输出".getBytes(StandardCharsets.UTF_8)));
        byte[] gbk = "输出".getBytes(Charset.forName("GBK"));
        assertEquals("输出", OutputCharset.fromConfig("gbk").decode(gbk));
    }

    @Test
    void blankConfigMeansAuto() {
        assertEquals("AUTO", OutputCharset.fromConfig(null).label());
        assertEquals("AUTO", OutputCharset.fromConfig("  ").label());
    }

    @Test
    void invalidCharsetRejected() {
        assertThrows(IllegalArgumentException.class, () -> OutputCharset.fromConfig("NOT-A-CHARSET"));
    }
}
