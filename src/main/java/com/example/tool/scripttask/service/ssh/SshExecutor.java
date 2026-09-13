package com.example.tool.scripttask.service.ssh;

import com.example.tool.scripttask.entity.ScriptType;

/**
 * 纯 SSH 执行器，不认识 ScriptTask。
 */
public interface SshExecutor {

    /**
     * 在目标主机执行一段脚本（脚本通过 stdin 传入）。
     */
    SshResult executeScript(SshConfig config, ScriptType type, String script, long timeoutMs);

    /**
     * 在目标主机执行一条命令（用于关机等）。
     */
    SshResult executeCommand(SshConfig config, String command, long timeoutMs);

    /**
     * 在目标主机执行一条命令，并可向命令的 stdin 写入内容（例如 sudo 密码）。
     */
    SshResult executeCommand(SshConfig config, String command, String stdin, long timeoutMs);

    /**
     * 连通性测试。成功返回目标主机指纹，失败抛异常。
     */
    String testConnection(SshConfig config, long timeoutMs);
}
