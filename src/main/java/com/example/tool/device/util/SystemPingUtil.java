package com.example.tool.device.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SystemPingUtil {
    /**
     * 对目标 IP 执行一次系统 Ping 探测
     * @param ip 目标 IPv4 地址，如 "192.168.1.100"
     * @param timeoutSeconds 超时秒数（建议 3-5 秒）
     * @return true 表示 Ping 通（设备在线），false 表示不通或超时
     */
    public static boolean ping(String ip, int timeoutSeconds) {
        if (ip == null || ip.isBlank()) {
            log.warn("IP is empty, cannot ping");
            return false;
        }
        if (timeoutSeconds <= 0) {
            log.warn("Invalid timeout");
            return false;
        }

        boolean windows = System.getProperty("os.name", "").contains("Windows");
        long timeoutMillis;
        String[] command;
        if (windows) {
            timeoutMillis = Math.max(timeoutSeconds * 1000L, 1000L);
            command = new String[]{"ping", "-n", "1", "-w", String.valueOf(timeoutMillis), ip};
        } else {
            timeoutMillis = timeoutSeconds * 1000L;
            command = new String[]{"ping", "-c", "1", "-w", String.valueOf(timeoutSeconds), ip};
        }

        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();

            // 先带超时地等待进程结束，避免读取输出时被永久阻塞
            boolean finished = process.waitFor(timeoutMillis + 2000, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                log.error("Ping process timed out, forcefully terminated, IP: {}", ip);
                return false;
            }

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.exitValue();
            boolean reachable = (exitCode == 0);
            if (log.isDebugEnabled()) {
                log.debug("Ping {} result: {}, exit code: {}, output: {}",
                        ip, reachable ? "reachable" : "unreachable", exitCode, output.toString().trim());
            }
            return reachable;
        } catch (Exception e) {
            log.error("Failed to execute ping command, IP: {}", ip, e);
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
