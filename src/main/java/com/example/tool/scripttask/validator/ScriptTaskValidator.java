package com.example.tool.scripttask.validator;

import com.example.tool.device.exception.BusinessException;
import com.example.tool.scripttask.entity.ShutdownMode;
import com.example.tool.scripttask.entity.TriggerType;
import com.example.tool.scripttask.request.ScriptTaskRequest;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ScriptTaskValidator {

    public void validate(ScriptTaskRequest request, boolean isCreate) {
        if (request.getTriggerType() == TriggerType.ONCE) {
            if (request.getExecuteAt() == null) {
                throw new BusinessException("ONCE 模式必须设置执行时间");
            }
            if (isCreate && request.getExecuteAt().isBefore(LocalDateTime.now())) {
                throw new BusinessException("执行时间必须晚于当前时间");
            }
        } else if (request.getTriggerType() == TriggerType.CRON) {
            if (isBlank(request.getCronExpression())) {
                throw new BusinessException("CRON 模式必须填写 cron 表达式");
            }
            if (!CronExpression.isValidExpression(request.getCronExpression())) {
                throw new BusinessException("cron 表达式无效: " + request.getCronExpression());
            }
        }

        if (request.getShutdownMode() == ShutdownMode.DELAYED) {
            if (request.getShutdownDelaySeconds() == null || request.getShutdownDelaySeconds() < 1) {
                throw new BusinessException("延迟关机必须设置大于 0 的延迟秒数");
            }
        }

        if (isCreate && isBlank(request.getSshPrivateKey())) {
            throw new BusinessException("必须上传 SSH 私钥");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
