package com.example.tool.device.service.checker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
@Component("deviceIpv6Checker")
public class DeviceIpv6Checker implements DeviceIpChecker {

    @Value("${wol.ipv6.multicast-address: ff02::1}")
    private String multicastAddress;

    @Value("${wol.ipv6.networkCard:}")
    private String networkCard;

    @Override
    public InetAddress resolveDestinationAddress(Integer deviceId) {
        if (networkCard == null) {
            log.warn("To enable IPv6, you must configure the network interface used for sending data in the environment variables");
            throw new RuntimeException("To enable IPv6, you must configure the network interface used for sending data in the environment variables");
        }
        InetAddress multicast;
        try {
            multicast = InetAddress.getByName(multicastAddress + "%" + networkCard);
        } catch (UnknownHostException e) {
            log.error("构建InetAddress对象失败，ID：{}", deviceId, e);
            throw new RuntimeException("Failed to construct object 'InetAddress'", e);
        }
        return multicast;
    }
}
