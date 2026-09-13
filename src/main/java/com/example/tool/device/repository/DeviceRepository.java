package com.example.tool.device.repository;


import com.example.tool.device.entity.Device;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Integer> {

    @Override
    @EntityGraph(attributePaths = "monitor")
    List<Device> findAll();

    List<Device> findByUserId(Integer userId);

    Optional<Device> findByDeviceIdAndUserId(Integer deviceId, Integer userId);

    boolean existsByMac(String mac);

    Optional<Device> findByMacAndDeviceToken(String mac, String deviceToken);
}
