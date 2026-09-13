package com.example.tool.scripttask.repository;

import com.example.tool.scripttask.entity.ScriptTaskLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ScriptTaskLogRepository extends JpaRepository<ScriptTaskLog, Long> {

    Page<ScriptTaskLog> findByTaskIdOrderByStartedAtDesc(Long taskId, Pageable pageable);

    Optional<ScriptTaskLog> findByIdAndTaskId(Long id, Long taskId);

    void deleteByTaskId(Long taskId);

    void deleteByDeviceId(Integer deviceId);
}
