package com.example.tool.wake.util;

import com.example.tool.wake.exception.MACAnalysisException;

public class MacUtils {
    public static final int MAC_SIZE = 6;

    public MacUtils() {
        throw  new UnsupportedOperationException("Mac utils class");
    }

    //解析mac
    public static byte[] parse(String mac) {
        checkMac(mac);
        
        String hex = mac.replaceAll("[^0-9A-Fa-f]", "");

        byte[] macBytes = new byte[MAC_SIZE];
        for (int i = 0; i < MAC_SIZE; i++) {
            int start = i * 2;
            macBytes[i] = (byte) Integer.parseInt(hex.substring(start, start + 2), 16);
        }
        return macBytes;
    }

    public static void checkMac(String mac) {
        if (mac == null || mac.isEmpty()) {
            throw new MACAnalysisException(1001, "MAC address cannot be null or empty");
        }
        if (mac.replaceAll("[^0-9A-Fa-f]", "").length() != 12) {
            throw new MACAnalysisException(1001, mac + " is not a valid MAC address");
        }
    }
}
