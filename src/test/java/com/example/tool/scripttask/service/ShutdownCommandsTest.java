package com.example.tool.scripttask.service;

import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.ScriptType;
import com.example.tool.scripttask.entity.ShutdownMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShutdownCommandsTest {

    private ScriptTask task(ShutdownMode mode, Integer delay) {
        return ScriptTask.builder()
                .shutdownMode(mode)
                .shutdownDelaySeconds(delay)
                .build();
    }

    @Test
    void bashImmediateNoPasswordUsesSudoN() {
        assertEquals("sudo -n shutdown -h now", ShutdownCommands.command(ScriptType.BASH, true, 0, false));
        assertEquals("sudo -n shutdown -h now",
                ShutdownCommands.forTask(task(ShutdownMode.IMMEDIATE, null), ScriptType.BASH, false));
    }

    @Test
    void bashImmediateWithPasswordUsesSudoS() {
        assertEquals("sudo -S -p '' shutdown -h now", ShutdownCommands.command(ScriptType.BASH, true, 0, true));
    }

    @Test
    void bashDelayedNoPasswordUsesSeconds() {
        String command = ShutdownCommands.forTask(task(ShutdownMode.DELAYED, 30), ScriptType.BASH, false);
        assertTrue(command.contains("sleep 30"));
        assertTrue(command.contains("sudo -n shutdown -h now"));
    }

    @Test
    void bashDelayedWithPasswordDoesNotEmbedPassword() {
        String command = ShutdownCommands.command(ScriptType.BASH, false, 30, true);
        assertTrue(command.contains("sleep 30"));
        assertTrue(command.contains("sudo -S"));
        assertFalse(command.contains("secret"));
    }

    @Test
    void powershellCommands() {
        assertEquals("Stop-Computer -Force", ShutdownCommands.immediate(ScriptType.POWERSHELL, false));
        assertEquals("shutdown /s /t 15 /f",
                ShutdownCommands.forTask(task(ShutdownMode.DELAYED, 15), ScriptType.POWERSHELL, false));
    }
}
