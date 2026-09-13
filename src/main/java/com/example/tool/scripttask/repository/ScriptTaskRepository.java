package com.example.tool.scripttask.repository;

import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.TriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptTaskRepository extends JpaRepository<ScriptTask, Long> {

    List<ScriptTask> findByDevice_DeviceIdAndDevice_UserId(Integer deviceId, Integer userId);

    Optional<ScriptTask> findByIdAndDevice_UserId(Long id, Integer userId);

    List<ScriptTask> findByTriggerTypeAndEnabledTrue(TriggerType triggerType);

    List<ScriptTask> findByDevice_DeviceIdAndTriggerTypeAndEnabledTrue(Integer deviceId, TriggerType triggerType);

    List<ScriptTask> findByDevice_DeviceId(Integer deviceId);
}
