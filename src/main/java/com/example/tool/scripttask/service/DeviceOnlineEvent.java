package com.example.tool.scripttask.service;

/**
 * 设备由非在线变为在线时发布，用于触发 ON_BOOT 任务。
 */
public record DeviceOnlineEvent(Integer deviceId) {
}
