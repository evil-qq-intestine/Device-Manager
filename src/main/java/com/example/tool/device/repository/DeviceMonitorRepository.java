package com.example.tool.device.repository;

import com.example.tool.device.entity.DeviceMonitor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceMonitorRepository extends JpaRepository<DeviceMonitor, Integer> {

    List<DeviceMonitor> findByDeviceUserId(Integer userId);

    Optional<DeviceMonitor> findByMonitorIdAndDeviceUserId(Integer deviceId, Integer userId);
}
