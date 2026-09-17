package com.example.tool.device.service.component;

import com.example.tool.device.exception.BusinessException;
import com.example.tool.device.util.MacUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.*;
import java.util.Set;

@Slf4j
@Component
public class MagicPacketComponent {

    @Value("${wol.ports:9}")
    private int[] WOL_PORTS;

    @Value("${wol.retry:1}")
    private int WOL_RETRY;

    @Value("${wol.intervalMs:100}")
    private int WOL_INTERVAL_MS;

    public void sendMagicPacket(String mac, Set<InetAddress> broadcast) {
        byte[] macBytes = MacUtils.parse(mac);
        try {
            byte[] payload = new byte[102];

            for (int i = 0; i < 6; i++) {
                payload[i] = (byte) 0xFF;
            }

            for (int i = 0; i < 16; i++) {
                System.arraycopy(macBytes, 0, payload, 6 + i * macBytes.length, macBytes.length);
            }

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                int success = 0, fail = 0;
                for (InetAddress address : broadcast) {
                    for (int port : WOL_PORTS) {
                        for (int retry = 0; retry < WOL_RETRY; retry++) {
                            try {
                                socket.send(new DatagramPacket(payload, payload.length, address, port));
                                success++;
                            } catch (IOException e) {
                                fail++;
                            }
                            Thread.sleep(WOL_INTERVAL_MS);
                        }
                    }
                }
                log.info("WOL magic packet sent (102 bytes), target MAC: {}, success: {}, fail: {}", bytesToHex(macBytes), success, fail);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            log.error("Failed to send WOL packet", e);
            throw new BusinessException("WOL send failed", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
            if (sb.length() < 16) sb.append(":");
        }
        return sb.toString();
    }
}
