package com.example.tool.scripttask.service.ssh;

import com.example.tool.scripttask.entity.ScriptType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinaSshExecutorCommandTest {

    private static final String PREFIX = "powershell -NoProfile -NonInteractive -Command ";

    @Test
    void bashReadsScriptFromStdin() {
        assertEquals("bash -s", MinaSshExecutor.scriptCommand(ScriptType.BASH));
    }

    @Test
    void powershellUsesUtf8Bootstrap() {
        String command = MinaSshExecutor.scriptCommand(ScriptType.POWERSHELL);
        assertTrue(command.startsWith(PREFIX));
        String bootstrap = command.substring(PREFIX.length());
        assertTrue(bootstrap.contains("UTF8Encoding"));
        assertTrue(bootstrap.endsWith("iex $src}"));
    }

    @Test
    void bootstrapAvoidsCmdMetacharacters() {
        String command = MinaSshExecutor.scriptCommand(ScriptType.POWERSHELL);
        String bootstrap = command.substring(PREFIX.length());
        for (char forbidden : new char[]{'"', '&', '|', '<', '>', '%', '^', '\n', '\r'}) {
            assertFalse(bootstrap.indexOf(forbidden) >= 0,
                    "bootstrap 不应包含会被 cmd.exe 解释的字符: " + forbidden);
        }
    }
}
