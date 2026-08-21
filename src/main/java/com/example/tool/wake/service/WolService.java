package com.example.tool.wake.service;


import com.example.tool.wake.entity.Device;
import com.example.tool.wake.entity.DeviceStatus;
import com.example.tool.wake.exception.BusinessException;
import com.example.tool.wake.exception.IdNotDetectedException;
import com.example.tool.wake.repository.DeviceRepository;
import com.example.tool.wake.util.MacUtils;
import com.example.tool.wake.util.NetworkStringCleansingUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

@Service
@Slf4j
public class WolService {
    @Autowired
    private DeviceRepository deviceRepository;

    @Value("${wol.broadcast-address: 255.255.255.255}")
    private String broadcastAddress;

    private byte[] ipAddressBytes;

    @PostConstruct
    public void init() {
        this.ipAddressBytes = NetworkStringCleansingUtils.parseIpToBytes(broadcastAddress);
    }

    @Value("${wol.port: 9}")
    private int wolPort;

    public void wakeDevice(Integer deviceId) {
        if (deviceId == null) {
            throw new IdNotDetectedException("deviceId is null");
        }
        Device device = deviceRepository.findById(deviceId).orElseThrow(() -> new RuntimeException("device not found"));
        byte[] payload = MacUtils.parse(device.getMac());
        sendMagicPacket(payload);
        device.setStatus(DeviceStatus.PENDING);
        deviceRepository.save(device);
    }

    private void sendMagicPacket(byte[] macBytes) {
        try {
            byte[] payload = new byte[102];

            for (int i = 0; i < 6; i++) {
                payload[i] = (byte) 0xFF;
            }

            for (int i = 0; i < 16; i++) {
                System.arraycopy(macBytes, 0, payload, 6 + i * macBytes.length, macBytes.length);
            }

            InetAddress broadcast = InetAddress.getByAddress(ipAddressBytes);
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
