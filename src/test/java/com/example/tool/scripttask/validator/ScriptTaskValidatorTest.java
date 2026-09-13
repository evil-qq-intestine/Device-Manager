package com.example.tool.scripttask.validator;

import com.example.tool.device.exception.BusinessException;
import com.example.tool.scripttask.entity.ScriptType;
import com.example.tool.scripttask.entity.ShutdownMode;
import com.example.tool.scripttask.entity.TriggerType;
import com.example.tool.scripttask.request.ScriptTaskRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScriptTaskValidatorTest {

    private final ScriptTaskValidator validator = new ScriptTaskValidator();

    private ScriptTaskRequest base() {
        ScriptTaskRequest r = new ScriptTaskRequest();
        r.setName("t");
        r.setScriptContent("echo hi");
        r.setScriptType(ScriptType.BASH);
        r.setTriggerType(TriggerType.ON_BOOT);
        r.setSshHost("127.0.0.1");
        r.setSshPort(22);
        r.setSshUser("root");
        r.setShutdownMode(ShutdownMode.NONE);
        return r;
    }

    @Test
    void onceRequiresFutureTime() {
        ScriptTaskRequest r = base();
        r.setTriggerType(TriggerType.ONCE);
        r.setSshPrivateKey("key");
        assertThrows(BusinessException.class, () -> validator.validate(r, true));

        r.setExecuteAt(LocalDateTime.now().minusMinutes(1));
        assertThrows(BusinessException.class, () -> validator.validate(r, true));

        r.setExecuteAt(LocalDateTime.now().plusMinutes(5));
        assertDoesNotThrow(() -> validator.validate(r, true));
    }

    @Test
    void cronMustBeValid() {
        ScriptTaskRequest r = base();
        r.setTriggerType(TriggerType.CRON);
        r.setSshPrivateKey("key");
        r.setCronExpression("not a cron");
        assertThrows(BusinessException.class, () -> validator.validate(r, true));

        r.setCronExpression("0 0 3 * * *");
        assertDoesNotThrow(() -> validator.validate(r, true));
    }

    @Test
    void delayedShutdownRequiresPositiveDelay() {
        ScriptTaskRequest r = base();
        r.setSshPrivateKey("key");
        r.setShutdownMode(ShutdownMode.DELAYED);
        assertThrows(BusinessException.class, () -> validator.validate(r, true));

        r.setShutdownDelaySeconds(30);
        assertDoesNotThrow(() -> validator.validate(r, true));
    }

    @Test
    void createRequiresPrivateKey() {
        ScriptTaskRequest r = base();
        assertThrows(BusinessException.class, () -> validator.validate(r, true));

        r.setSshPrivateKey("key");
        assertDoesNotThrow(() -> validator.validate(r, true));
        // 更新时私钥可空
        assertDoesNotThrow(() -> validator.validate(r, false));
    }
}
