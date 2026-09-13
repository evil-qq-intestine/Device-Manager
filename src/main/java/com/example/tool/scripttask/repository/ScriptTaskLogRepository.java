package com.example.tool.scripttask.repository;

import com.example.tool.scripttask.entity.ScriptTaskLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptTaskLogRepository extends JpaRepository<ScriptTaskLog, Long> {

    Page<ScriptTaskLog> findByTaskIdOrderByStartedAtDesc(Long taskId, Pageable pageable);

    Optional<ScriptTaskLog> findByIdAndTaskId(Long id, Long taskId);

    Optional<ScriptTaskLog> findFirstByTaskIdAndDeviceIdOrderByStartedAtDesc(Long taskId, Integer deviceId);

    /** 每个 (任务, 设备) 只取最近一条日志（id 自增，取 max 即最新）。 */
    @Query("select l from ScriptTaskLog l where l.id in "
            + "(select max(l2.id) from ScriptTaskLog l2 where l2.taskId in :taskIds group by l2.taskId, l2.deviceId)")
    List<ScriptTaskLog> findLatestPerDeviceByTaskIds(@Param("taskIds") Collection<Long> taskIds);

    void deleteByTaskId(Long taskId);

    void deleteByDeviceId(Integer deviceId);
}
