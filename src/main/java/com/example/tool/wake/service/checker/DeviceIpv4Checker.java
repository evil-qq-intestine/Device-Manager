package com.example.tool.wake.service.checker;

import com.example.tool.wake.entity.Device;
import com.example.tool.wake.util.NetworkStringCleansingUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;

@Slf4j
@Component("deviceIpv4Checker")
public class DeviceIpv4Checker implements DeviceIpChecker {

    @Value("${spring.wol.ipv4-broadcast-address: 255.255.255.255}")
    private String broadcastAddress;

    private byte[] ipAddressBytes;

    @PostConstruct
    public void init() {
        this.ipAddressBytes = NetworkStringCleansingUtils.parseIpToBytes(broadcastAddress);
    }

    @Override
    public InetAddress resolveDestinationAddress(Integer deviceId) {
        InetAddress broadcast;
        try {
            broadcast = InetAddress.getByAddress(ipAddressBytes);
        } catch (IOException e) {
            log.error("构建InetAddress对象失败，ID：{}", deviceId, e);
            throw new RuntimeException("Failed to construct object 'InetAddress'", e);
        }
        return broadcast;
    }
}
