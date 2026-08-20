package com.example.tool.wake.task;

import com.example.tool.wake.entity.Device;
import com.example.tool.wake.entity.DeviceStatus;
import com.example.tool.wake.repository.DeviceRepository;
import com.example.tool.wake.service.DeviceHealthChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DeviceScheduledTasks {
    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private Map<String, DeviceHealthChecker> checkerMap;

    private static final Map<Integer, Long> lastPingMap = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 30000)
    public void healthCheck() {
        List<Device> devices = deviceRepository.findAll();
        for (Device device : devices) {
            if ("PING".equals(device.getMonitorMode())){
                Long lastPing = lastPingMap.get(device.getId());
                long now = System.currentTimeMillis();
                if (lastPing != null && (now - lastPing) < device.getPingInterval() * 1000L) {
                    continue;
                }
                if (checkerMap.get("pingHealthChecker") == null){
                    log.error("pingHealthChecker checkerMap is null, ID : {}", device.getId());
                    continue;
                }
                boolean alive = checkerMap.get("pingHealthChecker").isAlive(device);
                if (alive){
                    device.setStatus(DeviceStatus.ONLINE);
                    device.setLastOnlineTime(LocalDateTime.now());
                    deviceRepository.save(device);
                } else {
                    if (device.getStatus() == DeviceStatus.PENDING){
                        log.info("Device {} has been pending", device.getId());
                        continue;
                    } else {
                        device.setStatus(DeviceStatus.OFFLINE);
                        deviceRepository.save(device);
                        log.info("pingHealthChecker is dead, ID : {}", device.getId());
                    }
                }
                // 无论结果如何，都记录当前时间
                lastPingMap.put(device.getId(), now);
            } else if ("HEALTH".equals(device.getMonitorMode())){
                if (checkerMap.get("heartbeatChecker") == null) {
                    log.error("heartbeatChecker checkerMap is null, ID : {}", device.getId());
                    continue;
                }
                boolean alive = checkerMap.get("heartbeatChecker").isAlive(device);
                if (alive) {
                    device.setStatus(DeviceStatus.ONLINE);
                    deviceRepository.save(device);
                    log.info("heartbeatChecker is alive, ID : {}", device.getId());
                } else {
                    if (device.getStatus() == DeviceStatus.PENDING){
                        log.info("Device {} has been pending", device.getId());
                    } else {
                        device.setStatus(DeviceStatus.OFFLINE);
                        deviceRepository.save(device);
                        log.info("heartbeatChecker is dead, ID : {}", device.getId());
                    }
                }
            }
        }
    }

    public static void removeLastPingMap(Integer id) {
        lastPingMap.remove(id);
    }
}
