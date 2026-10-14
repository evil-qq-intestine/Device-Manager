package com.example.tool.security.mcp;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * {@code allowedIp} 匹配：支持逗号分隔的多条规则，每条是精确 IP 或 CIDR。
 *
 * <p>例：{@code 192.168.1.10}、{@code 10.0.0.0/8}、{@code 2001:db8::/32}、
 * {@code 192.168.1.10, 10.0.0.0/8}。空字符串表示不限制。
 *
 * <p>只接受 IP 字面量，绝不触发 DNS（见 {@link #parse}），避免来源 IP 被伪造成主机名时
 * 反向解析。
 */
public final class McpIpMatcher {

    private McpIpMatcher() {
    }

    /** {@code allowedIp} 为空 = 放行；否则 {@code clientIp} 命中任意一条规则才放行。 */
    public static boolean matches(String allowedIp, String clientIp) {
        if (allowedIp == null || allowedIp.isBlank()) {
            return true;
        }
        byte[] client = parse(clientIp);
        if (client == null) {
            return false;
        }
        for (String rule : allowedIp.split(",")) {
            if (matchesRule(rule.trim(), client)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesRule(String rule, byte[] client) {
        if (rule.isEmpty()) {
            return false;
        }
        int slash = rule.indexOf('/');
        if (slash < 0) {
            byte[] single = parse(rule);
            return single != null && Arrays.equals(single, client);
        }
        byte[] network = parse(rule.substring(0, slash));
        int prefix = parsePrefix(rule.substring(slash + 1));
        return network != null && network.length == client.length
                && prefix >= 0 && prefix <= network.length * 8
                && prefixMatches(network, client, prefix);
    }

    /** 解析 IP 字面量为字节；非字面量或非法返回 null（不会做 DNS 解析）。 */
    private static byte[] parse(String ip) {
        if (ip == null || ip.isBlank() || !looksLikeIp(ip)) {
            return null;
        }
        try {
            return InetAddress.getByName(stripBrackets(ip)).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    private static boolean looksLikeIp(String ip) {
        if (ip.indexOf(':') >= 0) {
            return true;                 // IPv6 字面量
        }
        return ip.matches("[0-9.]+");    // IPv4 字面量（纯数字点，排除主机名）
    }

    private static String stripBrackets(String ip) {
        if (ip.startsWith("[") && ip.endsWith("]")) {
            return ip.substring(1, ip.length() - 1);
        }
        return ip;
    }

    private static int parsePrefix(String s) {
        try {
            int prefix = Integer.parseInt(s.trim());
            return (prefix >= 0 && prefix <= 128) ? prefix : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean prefixMatches(byte[] network, byte[] client, int prefix) {
        int whole = prefix / 8;
        for (int i = 0; i < whole; i++) {
            if (network[i] != client[i]) {
                return false;
            }
        }
        int rest = prefix % 8;
        if (rest == 0) {
            return true;
        }
        int mask = (0xFF << (8 - rest)) & 0xFF;
        return (network[whole] & mask) == (client[whole] & mask);
    }
}
