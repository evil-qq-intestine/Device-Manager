package com.example.tool.wake.repository;


import com.example.tool.wake.entity.Device;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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
//        return jdbcClient.sql("")
//                .query(Device.class)
//                .list();
//    }
    boolean existsByMac(String mac);
    Optional<Device> findByMac(String mac);
}
