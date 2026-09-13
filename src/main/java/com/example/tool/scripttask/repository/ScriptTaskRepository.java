package com.example.tool.scripttask.repository;

import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.TriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptTaskRepository extends JpaRepository<ScriptTask, Long> {

    List<ScriptTask> findByOwner_Id(Integer userId);

    Optional<ScriptTask> findByIdAndOwner_Id(Long id, Integer userId);

    List<ScriptTask> findByTriggerTypeAndEnabledTrue(TriggerType triggerType);

    List<ScriptTask> findByTriggerTypeAndEnabledTrueAndTargets_DeviceId(TriggerType triggerType, Integer deviceId);

    List<ScriptTask> findByTargets_DeviceId(Integer deviceId);
}
