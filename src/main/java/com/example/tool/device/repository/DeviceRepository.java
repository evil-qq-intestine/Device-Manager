package com.example.tool.device.repository;


import com.example.tool.device.entity.Device;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
//        return jdbcClient.sql("~~~")
//                .query(Device.class)
//                .list();
//    }

    @Override
    @Query("SELECT device FROM Device device JOIN FETCH device.monitor")
    List<Device> findAll();

    List<Device> findByUserId(Integer userId);

    Optional<Device> findByDeviceIdAndUserId(Integer deviceId, Integer userId);



    boolean existsByMac(String mac);
    Optional<Device> findByMacAndUserId(String mac, Integer userId);
}
