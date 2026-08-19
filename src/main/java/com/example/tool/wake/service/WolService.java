package com.example.tool.wake.service;


import com.example.tool.wake.entity.Device;
import com.example.tool.wake.repository.DeviceRepository;
import com.example.tool.wake.util.MacUtils;
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

    @Value("${wol.broadcast-address:255.255.255.255}")
    private String broadcastAddress;

    @Value("${wol.port:9}")
    private int wolPort;

    public void wakeDevice(Integer deviceId) {
        Device device = deviceRepository.findById(deviceId).orElseThrow(() -> new RuntimeException("device not found"));
        byte[] payload = MacUtils.parse(device.getMac());
        sendMagicPacket(payload);
    }
    private void sendMagicPacket(byte[] payload) {
        try {
            InetAddress address = InetAddress.getByName(broadcastAddress);
            DatagramPacket packet = new DatagramPacket(payload, payload.length, address, wolPort);
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.send(packet);
                log.info("sent magic packet");
            }
        } catch (IOException e) {
            log.error("send magic packet error", e);
        }
    }
}
