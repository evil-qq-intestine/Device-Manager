package com.example.tool.wake.repository;


import com.example.tool.wake.entity.Device;
import org.hibernate.internal.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import java.util.List;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Integer> {
//    普通不继承接口的写法：
//    private static final Logger log = LoggerFactory.getLogger(DeviceRepository.class);
//
//    private final JdbcClient jdbcClient;
//    public DeviceRepository(JdbcClient jdbcClient) {
//        this.jdbcClient = jdbcClient;
//    }
//
//    public List<Device> findAll() {
//        return jdbcClient.sql("")
//                .query(Device.class)
//                .list();
//    }
    boolean existsByMac(String mac);
    Optional<Device> findByMac(String mac);
}
