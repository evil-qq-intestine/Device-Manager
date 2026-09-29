package com.example.tool.scripttask.repository;

import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.TriggerType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptTaskRepository extends JpaRepository<ScriptTask, Long> {

    List<ScriptTask> findByOwner_Id(Integer userId);

    Optional<ScriptTask> findByIdAndOwner_Id(Long id, Integer userId);

    /**
     * 连同 targets 一起抓取。给「事务外需要遍历目标设备」的调用方用
     * （例如手动关机：SSH 不能在事务里做，否则会长时间占住唯一的数据库连接）。
     */
    @EntityGraph(attributePaths = "targets")
    Optional<ScriptTask> findWithTargetsByIdAndOwner_Id(Long id, Integer userId);

    List<ScriptTask> findByTriggerTypeAndEnabledTrue(TriggerType triggerType);

    List<ScriptTask> findByTriggerTypeAndEnabledTrueAndTargets_DeviceId(TriggerType triggerType, Integer deviceId);

    List<ScriptTask> findByTargets_DeviceId(Integer deviceId);
}
