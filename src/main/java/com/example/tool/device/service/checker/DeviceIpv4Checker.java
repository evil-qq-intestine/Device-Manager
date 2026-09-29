package com.example.tool.device.service.checker;

import com.example.tool.device.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

@Slf4j
@Component("deviceIpv4Checker")
public class DeviceIpv4Checker implements DeviceIpChecker {

    @Value("${wol.ipv4.broadcast-address:255.255.255.255}")
    private String broadcastAddress;

    @PostConstruct
    public void init() {
        try {
            InetAddress.getByName(broadcastAddress);
        } catch (UnknownHostException e) {
            log.error("非法的广播地址: {}", broadcastAddress, e);
            throw new BusinessException("wol.ipv4.broadcast-address 配置错误");
        }
        Set<InetAddress> detected = interfaceBroadcasts();
        if (detected.isEmpty()) {
            log.info("IPv4 WOL 配置: broadcastAddress={}, 未探测到任何物理网卡广播（只会发这一个兜底地址）。"
                    + "若本服务跑在 Docker bridge 网络里，广播出不了容器，需 --network host 或直接在宿主机部署，"
                    + "否则局域网设备收不到魔术包。", broadcastAddress);
        } else {
            log.info("IPv4 WOL 配置: broadcastAddress={}, 现场探测到的网卡广播={}", broadcastAddress, detected);
        }
    }

    @Override
    public Set<InetAddress> resolveDestinationAddress(String deviceIp) {
        Set<InetAddress> broadCasts = new LinkedHashSet<>(interfaceBroadcasts());
        addIfResolvable(broadCasts, deviceIp);
        addIfResolvable(broadCasts, broadcastAddress);
        return broadCasts;
    }

    /**
     * 枚举所有物理网卡的广播地址。
     * 排除 loopback / 虚拟网卡 —— 含 {@code tun}（VPN）、{@code veth}/{@code br-}（Docker）、
     * {@code virbr}（libvirt）、{@code tap}，这些接口的广播发出去到不了局域网。
     */
    private Set<InetAddress> interfaceBroadcasts() {
        Set<InetAddress> broadCasts = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return broadCasts;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (networkInterface.isLoopback() || !networkInterface.isUp() || networkInterface.isVirtual()) {
                    continue;
                }
                String name = networkInterface.getName();
                if (name.startsWith("tun") || name.startsWith("veth") || name.startsWith("br-")
                        || name.startsWith("virbr") || name.startsWith("tap")) {
                    continue;
                }
                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast != null) {
                        broadCasts.add(broadcast);
                    }
                }
            }
        } catch (SocketException e) {
            log.error("枚举网卡广播地址失败", e);
        }
        return broadCasts;
    }

    private void addIfResolvable(Set<InetAddress> target, String host) {
        try {
            target.add(InetAddress.getByName(host));
        } catch (UnknownHostException e) {
            log.debug("无法解析地址，跳过: {}", host, e);
        }
    }
}
