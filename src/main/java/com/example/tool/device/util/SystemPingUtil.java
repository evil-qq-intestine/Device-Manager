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
            log.warn("IP 地址为空，无法 Ping");
            return false;
        }
        if (timeoutSeconds < 0) {
            log.warn("超时时间不合法");
            return false;
        }
        String os =  System.getProperty("os.name");
        String[] command;
        if (os.contains("Windows")) {
            timeoutSeconds = Math.max(timeoutSeconds * 1000, 1000);
            command = new String[]{"ping", "-n", "1", "-w", String.valueOf(timeoutSeconds), ip};
        } else {
            command = new String[]{"ping", "-c", "1", "-w", String.valueOf(timeoutSeconds), ip};
        }
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            StringBuilder output = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            boolean finished = process.waitFor(timeoutSeconds + 1, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("Ping进程超时，强制结束，IP：{}", ip);
            }
            int exitCode = process.exitValue();
            boolean reachable = (exitCode == 0);
            if (log.isDebugEnabled()){
                log.debug("Ping {} 结果：{}，退出码：{}，输出：{}",
                        ip, reachable ? "通" : "不通", exitCode, output.toString().trim());
            }
            return reachable;
        } catch (Exception e) {
            log.error("执行 Ping 命令异常，IP: {}", ip, e);
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
