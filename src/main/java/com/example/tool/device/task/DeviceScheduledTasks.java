package com.example.tool.device.task;

import com.example.tool.device.entity.Device;
import com.example.tool.device.entity.DeviceMonitorModeEnum;
import com.example.tool.device.entity.DeviceStatusEnum;
import com.example.tool.device.repository.DeviceMonitorRepository;
import com.example.tool.device.repository.DeviceRepository;
import com.example.tool.device.service.checker.DeviceHealthChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DeviceScheduledTasks {
    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private Map<String, DeviceHealthChecker> healthCheckerMap;

    private static final Map<Integer, Long> lastPingMap = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${ping.task-time:30}000")
    public void healthCheck() {
        List<Device> devices = deviceRepository.findAll();
        for (Device device : devices) {
            if (device.getMonitorMode() == DeviceMonitorModeEnum.PING) {
                Long lastPing = lastPingMap.get(device.getDeviceId());
                long now = System.currentTimeMillis();
                if (lastPing != null && (now - lastPing) < device.getPingInterval() * 1000L) {
                    continue;
                }

                healthQuestion(device, "ping");

                // 无论结果如何，都记录当前时间
                lastPingMap.put(device.getDeviceId(), now);
            } else if (device.getMonitorMode() == DeviceMonitorModeEnum.HEARTBEAT) {

                healthQuestion(device, "heartbeat");

            } else {
                throw new RuntimeException("unknown device monitor mode");
            }
        }
    }

    private void healthQuestion(Device device, String checkerType) {
        String key = checkerType + "HealthChecker";
        if (healthCheckerMap.get(key) == null){
            log.error("{} checkerMap is null, ID : {}", key, device.getDeviceId());
            return;
        }
        boolean alive = healthCheckerMap.get(key).isAlive(device);
        if (alive){
            device.setStatus(DeviceStatusEnum.ONLINE);
            if (device.getMonitorMode() == DeviceMonitorModeEnum.PING) {
                device.setLastOnlineTime(LocalDateTime.now());
            }
            deviceRepository.save(device);
        } else {
            if (device.getStatus() == DeviceStatusEnum.PROBE){
                if (device.getResponseTimeout() > Duration.between(device.getProbeStartTime(), LocalDateTime.now()).getSeconds()) {
                    device.setStatus(DeviceStatusEnum.OFFLINE);
                } else {
                    log.info("Device {} has been PROBE", device.getDeviceId());
                }
            } else {
                device.setStatus(DeviceStatusEnum.OFFLINE);
                deviceRepository.save(device);
                log.info("{} is dead, ID : {}", key, device.getDeviceId());
            }
        }
    }

    public static void removeLastPingMap(Collection<Device> devices) {
        for (Device key : devices){
            lastPingMap.remove(key.getDeviceId());
        }
    }
}
