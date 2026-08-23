package com.example.tool.device.util;

import com.example.tool.device.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class NetworkStringCleansingUtils {

    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
                    "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
                    "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
                    "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
    );

    public static byte[] parseIpToBytes(String ip) {
        if (ip == null) {
            log.error("IP address is null");
            throw new BusinessException("IP address cannot be null");
        }

        Matcher matcher = IP_PATTERN.matcher(ip);

        if (matcher.find()) {
            return new byte[]{
                    (byte) Integer.parseInt(matcher.group(1)),
                    (byte) Integer.parseInt(matcher.group(2)),
                    (byte) Integer.parseInt(matcher.group(3)),
                    (byte) Integer.parseInt(matcher.group(4))
            };
        }

        log.error("Invalid IP address format: {}", ip);
        throw new BusinessException("Invalid IP address: " + ip);
    }
}