package com.example.tool.scripttask.service;

import com.example.tool.scripttask.entity.ScriptTask;
import com.example.tool.scripttask.entity.ScriptType;
import com.example.tool.scripttask.entity.ShutdownMode;
import com.example.tool.scripttask.service.ssh.SshResult;
import org.springframework.stereotype.Component;

/**
 * 关机命令生成。bash 与 powershell 目标分别处理。
 * <p>
 * sudo 策略：
 * <ul>
 *   <li>无密码：{@code sudo -n}（要求目标机配置 NOPASSWD，或 SSH 用户为 root）</li>
 *   <li>有密码：{@code sudo -S -p ''}，密码通过 SSH stdin 传入，不出现在命令行里</li>
 * </ul>
 */
@Component
public final class ShutdownCommands {

    private ShutdownCommands() {
    }

    /** 按任务配置（IMMEDIATE / DELAYED）生成关机命令。 */
    public static String forTask(ScriptTask task, ScriptType type, boolean withPassword) {
        boolean immediate = task.getShutdownMode() == ShutdownMode.IMMEDIATE;
        int delay = task.getShutdownDelaySeconds() == null ? 0 : task.getShutdownDelaySeconds();
        return command(type, immediate, delay, withPassword);
    }

    /** 立即关机（忽略任务配置）。 */
    public static String immediate(ScriptType type, boolean withPassword) {
        return command(type, true, 0, withPassword);
    }

    public static String command(ScriptType type, boolean immediate, int delaySeconds, boolean withPassword) {
        if (type == ScriptType.POWERSHELL) {
            // Windows 需要 SSH 用户是管理员；/t 单位是秒。
            // 用 shutdown.exe 而不是 PowerShell cmdlet Stop-Computer：
            // Windows OpenSSH 默认 shell 是 cmd.exe，Stop-Computer 无法识别。
            return "shutdown /s /t " + (immediate ? 0 : delaySeconds) + " /f";
        }
        if (immediate) {
            return withPassword
                    ? "sudo -S -p '' shutdown -h now"
                    : "sudo -n shutdown -h now";
        }
        if (withPassword) {
            // sudo 认证后以 root 后台延迟关机，SSH 命令立即返回
            return "sudo -S -p '' bash -c 'nohup bash -c \"sleep " + delaySeconds + "; shutdown -h now\" >/dev/null 2>&1 &'";
        }
        return "nohup bash -c 'sleep " + delaySeconds + " && sudo -n shutdown -h now' >/dev/null 2>&1 &";
    }

    /**
     * 把执行结果翻译成对用户友好的错误信息。
     */
    public static String describeFailure(SshResult result) {
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            return result.errorMessage();
        }
        String stderr = result.stderr();
        if (stderr != null && !stderr.isBlank()) {
            String lower = stderr.toLowerCase();
            if (lower.contains("password is required")
                    || lower.contains("no tty")
                    || lower.contains("a terminal is required")) {
                return "目标机需要 sudo 密码：请配置免密 sudo（NOPASSWD），或为该配置填写 sudo 密码";
            }
            return stderr.trim();
        }
        return "关机命令退出码 " + result.exitCode();
    }
}
