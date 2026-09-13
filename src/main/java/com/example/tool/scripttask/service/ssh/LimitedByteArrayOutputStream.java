package com.example.tool.scripttask.service.ssh;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 限制最大缓存字节数，避免远端输出过大导致内存问题。超出后继续丢弃但标记被截断。
 */
class LimitedByteArrayOutputStream extends ByteArrayOutputStream {

    private final int limit;
    private boolean truncated;

    LimitedByteArrayOutputStream(int limit) {
        this.limit = limit;
    }

    @Override
    public synchronized void write(int b) {
        if (count < limit) {
            super.write(b);
        } else {
            truncated = true;
        }
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        if (count >= limit) {
            truncated = true;
            return;
        }
        int allowed = Math.min(len, limit - count);
        super.write(b, off, allowed);
        if (allowed < len) {
            truncated = true;
        }
    }

    String asString() {
        String text = new String(toByteArray(), StandardCharsets.UTF_8);
        return truncated ? text + "\n...[输出已截断]" : text;
    }
}
