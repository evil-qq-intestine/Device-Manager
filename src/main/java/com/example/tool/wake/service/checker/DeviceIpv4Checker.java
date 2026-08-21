package com.example.tool.wake.service.checker;

import com.example.tool.wake.entity.Device;
import com.example.tool.wake.util.NetworkStringCleansingUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.InetAddress;

@Slf4j
public class DeviceIpv4Checker implements DeviceIpChecker {

    @Value("${wol.ipv4-broadcast-address: 255.255.255.255}")
    private String broadcastAddress;

    private byte[] ipAddressBytes;

    @PostConstruct
    public void init() {
        this.ipAddressBytes = NetworkStringCleansingUtils.parseIpToBytes(broadcastAddress);
    }

    @Override
    public InetAddress resolveDestinationAddress(Device device) {
        InetAddress broadcast;
        try {
            broadcast = InetAddress.getByAddress(ipAddressBytes);
        } catch (IOException e) {
            log.error("构建InetAddress对象失败", e);
            throw new RuntimeException("Failed to construct object 'InetAddress'", e);
        }
        return broadcast;
    }
}
