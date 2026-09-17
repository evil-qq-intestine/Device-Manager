package com.example.tool.device.service.checker;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "wol.ipv6")
public class WolIpv6Properties {

    /** 模式：multicast | relay | auto */
    private String mode = "auto";

    /** 组播 mode */
    private String multicastAddress = "ff02::1";

    /** 组播 mode ： 网卡列表，留空自探测 */
    private Set<String> networkCards = new LinkedHashSet<>();

    /** 中继 mode ： 中继设备的域名或 IPV6 地址 */
    private String relayHost;

    /** 中继 mode ： 中继 port */
    private int relayPort = 9;
}