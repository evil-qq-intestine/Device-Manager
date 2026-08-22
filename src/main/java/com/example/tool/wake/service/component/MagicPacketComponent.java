package com.example.tool.wake.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

@Slf4j
public class MagicPacketUtils {

//    @Value("${spring.wol.port: 9}")
//    private static int wolPort;

    private static int wolPort;

    @Value("${spring.wol.port:9}")
    public void setWolPort(int port) {
        wolPort = port;
    }

    public static void sendMagicPacket(byte[] macBytes, InetAddress broadcast) {
        try {
            byte[] payload = new byte[102];

            for (int i = 0; i < 6; i++) {
                payload[i] = (byte) 0xFF;
            }

            for (int i = 0; i < 16; i++) {
                System.arraycopy(macBytes, 0, payload, 6 + i * macBytes.length, macBytes.length);
            }

            DatagramPacket packet = new DatagramPacket(payload, payload.length, broadcast, wolPort);
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.send(packet);
                log.info("WOL 魔术包已发送（102 字节），目标 MAC: {}", bytesToHex(macBytes));
            }
        } catch (IOException e) {
            log.error("发送 WOL 包失败", e);
            throw new RuntimeException("WOL send failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
            if (sb.length() < 12) sb.append(":");
        }
        return sb.toString();
    }
}
