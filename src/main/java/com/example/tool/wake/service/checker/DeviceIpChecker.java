package com.example.tool.wake.service.checker;

import com.example.tool.wake.entity.Device;

import java.net.InetAddress;

/**
 * 设备IP策略检查接口，
 * 用做PING的策略检测，
 * IPV4和IPV6策略都必须实现这个接口
 */
public interface DeviceIpChecker {
    /**
     * 根据设备IP策略发出魔包的地址
     * @param device 要唤醒的设备
     * @return InetAddress对象，工具类拿到直接发包
     */
    InetAddress resolveDestinationAddress(Device device);
}
