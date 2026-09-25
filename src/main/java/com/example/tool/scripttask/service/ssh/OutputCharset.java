package com.example.tool.scripttask.service.ssh;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 远端命令输出的解码策略。
 *
 * <p>默认 {@code AUTO}：优先按严格 UTF-8 解码，失败时（例如 Windows PowerShell 5.1 默认
 * 以 CP936/GBK 输出）回退到 GB18030。若输出因超过上限被截断，还会尝试丢弃末尾 1–3 个
 * 字节（被切开的半个多字节字符）后再次按 UTF-8 解码。
 *
 * <p>也可显式指定 {@code UTF-8}、{@code GBK}、{@code GB18030}、{@code windows-1252}
 * 等任意 Java 支持的字符集名称。
 */
final class OutputCharset {

    private static final String AUTO_LABEL = "AUTO";
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final int MAX_TRAILING_BYTES = 3;

    private final String label;
    private final Charset charset;

    private OutputCharset(String label, Charset charset) {
        this.label = label;
        this.charset = charset;
    }

    static OutputCharset fromConfig(String value) {
        if (value == null || value.isBlank() || AUTO_LABEL.equalsIgnoreCase(value.trim())) {
            return new OutputCharset(AUTO_LABEL, null);
        }
        String name = value.trim();
        try {
            Charset cs = Charset.forName(name);
            return new OutputCharset(cs.name(), cs);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "不支持的输出字符集: " + value + "（可选 AUTO / UTF-8 / GBK / GB18030 / windows-1252）");
        }
    }

    String label() {
        return label;
    }

    String decode(byte[] bytes) {
        return decode(bytes, false);
    }

    String decode(byte[] bytes, boolean truncated) {
        if (charset != null) {
            return new String(bytes, charset);
        }
        int maxDrop = truncated ? Math.min(MAX_TRAILING_BYTES, bytes.length) : 0;
        for (int drop = 0; drop <= maxDrop; drop++) {
            String text = strictUtf8(bytes, bytes.length - drop);
            if (text != null) {
                return text;
            }
        }
        return new String(bytes, GB18030);
    }

    private static String strictUtf8(byte[] bytes, int length) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes, 0, length)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
