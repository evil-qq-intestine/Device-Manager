package com.example.tool.scripttask.service;

import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.ScriptType;
import com.example.tool.scripttask.entity.ShutdownMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShutdownCommandsTest {

    private ScriptTask task(ScriptType type, ShutdownMode mode, Integer delay) {
        return ScriptTask.builder()
                .scriptType(type)
                .shutdownMode(mode)
                .shutdownDelaySeconds(delay)
                .build();
    }

    @Test
    void bashImmediateNoPasswordUsesSudoN() {
        assertEquals("sudo -n shutdown -h now", ShutdownCommands.command(ScriptType.BASH, true, 0, false));
        assertEquals("sudo -n shutdown -h now",
                ShutdownCommands.forTask(task(ScriptType.BASH, ShutdownMode.IMMEDIATE, null), false));
    }

    @Test
    void bashImmediateWithPasswordUsesSudoS() {
        String command = ShutdownCommands.command(ScriptType.BASH, true, 0, true);
        assertEquals("sudo -S -p '' shutdown -h now", command);
    }

    @Test
    void bashDelayedNoPasswordUsesSeconds() {
        String command = ShutdownCommands.forTask(task(ScriptType.BASH, ShutdownMode.DELAYED, 30), false);
        assertTrue(command.contains("sleep 30"));
        assertTrue(command.contains("sudo -n shutdown -h now"));
    }

    @Test
    void bashDelayedWithPasswordDoesNotEmbedPassword() {
        String command = ShutdownCommands.command(ScriptType.BASH, false, 30, true);
        assertTrue(command.contains("sleep 30"));
        assertTrue(command.contains("sudo -S"));
        // 密码通过 stdin 传入，不能出现在命令里
        assertFalse(command.contains("secret"));
    }

    @Test
    void powershellCommands() {
        assertEquals("Stop-Computer -Force", ShutdownCommands.immediate(ScriptType.POWERSHELL, false));
        assertEquals("shutdown /s /t 15 /f",
                ShutdownCommands.forTask(task(ScriptType.POWERSHELL, ShutdownMode.DELAYED, 15), false));
    }
}
