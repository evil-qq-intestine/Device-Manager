package com.example.tool.device.service.checker;

import java.net.InetAddress;
import java.util.Set;

/**
 * 设备IP策略检查接口，
 * 用做PING的策略检测，
 * IPV4和IPV6策略都必须实现这个接口
 */
public interface DeviceIpChecker {
    /**
     * 根据设备IP策略发出魔包的地址
     * @param deviceId 要唤醒的设备
     * @return InetAddress对象，工具类拿到直接发包
     */
    Set<InetAddress> resolveDestinationAddress(Integer deviceId);
}
