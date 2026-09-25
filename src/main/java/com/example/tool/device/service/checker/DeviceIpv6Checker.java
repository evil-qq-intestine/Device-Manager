package com.example.tool.device.service.checker;

import ch.qos.logback.core.util.StringUtil;
import com.example.tool.device.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.*;
import java.util.*;

@Slf4j
@Component("deviceIpv6Checker")
@RequiredArgsConstructor
public class DeviceIpv6Checker implements DeviceIpChecker {

    private final WolIpv6Properties wolIpv6Properties;

    @Override
    public Set<InetAddress> resolveDestinationAddress(String deviceIp){
        try {
            if (useRelay()){
                return resolveRelay(deviceIp);
            } else if (!useRelay()){
                return resolveMulticast(deviceIp);
            }
        } catch (UnknownHostException e) {
            log.error("IPV6 Address resolution failed");
            throw new BusinessException(e.getMessage());
        }
        return null;
    }

    private boolean useRelay() {
        if ("relay".equalsIgnoreCase(wolIpv6Properties.getMode())) {
            return true;
        }
        if ("multicast".equalsIgnoreCase(wolIpv6Properties.getMode())) {
            return false;
        }
        return StringUtils.hasText(wolIpv6Properties.getRelayHost());
    }

    //中继
    private Set<InetAddress> resolveRelay(String deviceIp) throws UnknownHostException {
        InetAddress[] addressesList = InetAddress.getAllByName(wolIpv6Properties.getRelayHost());
        log.info("IPV6 relayHost mode, device IP:{}, relay:{}, number of address:{}", deviceIp, wolIpv6Properties.getRelayHost(), addressesList.length);
        Set<InetAddress> addresses = new LinkedHashSet<>(Arrays.asList(addressesList));
        addresses.add(InetAddress.getByName(deviceIp));
        return addresses;
    }

    //组播
    private Set<InetAddress> resolveMulticast(String deviceIp) throws UnknownHostException {
        Set<String> cards = wolIpv6Properties.getNetworkCards();
        if (cards == null || cards.isEmpty()) {
            cards = detectIpv6NetworkCards();
            log.info("IPV6 multicast mode, automatically detects network cards:{}", cards);
        }
        if (cards.isEmpty()) {
            log.warn("No available network interface cards detected for IPV6; WOL may not function");
            Set<InetAddress> addresses = new LinkedHashSet<>();
            addresses.add(InetAddress.getByName(wolIpv6Properties.getMulticastAddress()));
            addresses.add(InetAddress.getByName(deviceIp));
            return addresses;
        }
        Set<InetAddress> addresses = new HashSet<>(cards.size());
        for (String card : cards) {
            String addr = wolIpv6Properties.getMulticastAddress() + "%" + card;
            addresses.add(InetAddress.getByName(addr));
        }
        log.info("IPV6 multicast mode, device IP:{}", deviceIp);
        return addresses;
    }

    //探测网卡
    private Set<String> detectIpv6NetworkCards(){
        Set<String> cards = new LinkedHashSet<>();
        try {
            Enumeration<NetworkInterface> ifaces =
                    NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }
                String name = iface.getName();
                if (name.startsWith("tun") || name.startsWith("tap")) {
                    continue;
                }
                boolean hasIpv6 = false;
                for (InterfaceAddress ia : iface.getInterfaceAddresses()) {
                    if (ia.getAddress() instanceof Inet6Address) {
                        hasIpv6 = true;
                        break;
                    }
                }
                if (hasIpv6) cards.add(name);
            }
        } catch (SocketException e) {
            log.warn("枚举网卡失败", e);
        }
        return cards;
    }
}
