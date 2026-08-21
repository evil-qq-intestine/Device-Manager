package com.example.tool.wake.service.checker;

import com.example.tool.wake.entity.Device;

/**
 * 设备健康检查策略接口
 * 所有检查方式（Ping、心跳等）都必须实现这个接口
 */
public interface DeviceHealthChecker {

    /**
     * 检查设备是否存活
     * @param device 要检查的设备
     * @return true-在线，false-离线
     */
    boolean isAlive(Device device);
}
