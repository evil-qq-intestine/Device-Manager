package com.example.tool.scripttask.service.ssh;

/**
 * SSH 执行结果。框架层错误（连接失败/超时/认证失败）时 exitCode 为 -1 且 errorMessage 非空。
 */
public record SshResult(int exitCode, String stdout, String stderr, String errorMessage) {

    public static SshResult error(String message) {
        return new SshResult(-1, "", "", message);
    }

    public boolean success() {
        return exitCode == 0 && errorMessage == null;
    }
}
