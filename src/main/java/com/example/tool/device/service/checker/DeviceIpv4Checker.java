package com.example.tool.device.service.checker;

import com.example.tool.device.exception.BusinessException;
import com.example.tool.device.util.NetworkStringCleansingUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Component("deviceIpv4Checker")
public class DeviceIpv4Checker implements DeviceIpChecker {

    @Value("${wol.ipv4.broadcast-address: 255.255.255.255}")
    private String broadcastAddress;

    private byte[] ipAddressBytes;

    @PostConstruct
    public void init() {
        try {
            InetAddress.getByName(broadcastAddress);
            log.info("IPv4 WOL 配置: broadcastAddress={}", broadcastAddress);
        } catch (UnknownHostException e) {
            log.error("非法的广播地址: {}", broadcastAddress, e);
            throw new BusinessException("wol.ipv4.broadcast-address 配置错误");
        }
    }

    @Override
    public Set<InetAddress> resolveDestinationAddress(String deviceIp) {
        Set<InetAddress> broadCasts = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp() || networkInterface.isVirtual()) {
                    continue;
                }
                String name = networkInterface.getName();
                if (name.startsWith("tun") || name.startsWith("veth") || name.startsWith("br-") || name.startsWith("virbr") || name.startsWith("tap")) {
                    continue;
                } else if (name.startsWith("docker")) {
                    log.warn("Docker environment detected. Please check whether Docker is configured with the host setting; otherwise, this WOL feature will not work.");
                    continue;
                }
                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast != null) {// 注意
                        broadCasts.add(broadcast);
                    }
                }
            }
            broadCasts.add(InetAddress.getByName(deviceIp));
            broadCasts.add(InetAddress.getByName(broadcastAddress));
        } catch (SocketException e) {
            log.error("Failed to discover broad casts", e);
        } catch (UnknownHostException e) {
            log.debug("IPV4 Environment variable settings are incorrect : {}",broadcastAddress, e);
        }
        return broadCasts;
    }

}
