package com.attendance.controller;

import com.attendance.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/service")
public class ServiceController {
    
    private static final Logger log = LoggerFactory.getLogger(ServiceController.class);

    private Process backendProcess;
    private Process frontendProcess;

    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("backend", backendProcess != null && isProcessRunning(backendProcess));
        status.put("frontend", frontendProcess != null && isProcessRunning(frontendProcess));
        status.put("timestamp", System.currentTimeMillis());
        return Result.success(status);
    }

    @PostMapping("/backend/start")
    public Result<Void> startBackend() {
        if (backendProcess != null && isProcessRunning(backendProcess)) {
            return Result.error("后端服务已运行");
        }

        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("mvn", "spring-boot:run");
            builder.directory(new java.io.File("./backend"));
            builder.redirectErrorStream(true);
            
            backendProcess = builder.start();
            
            logProcessOutput(backendProcess, "backend");
            
            Thread.sleep(3000);
            if (isProcessRunning(backendProcess)) {
                return Result.success(null, "后端服务启动成功");
            } else {
                return Result.error("后端服务启动失败");
            }
        } catch (Exception e) {
            log.error("启动后端服务失败", e);
            return Result.error("启动后端服务失败: " + e.getMessage());
        }
    }

    @PostMapping("/backend/stop")
    public Result<Void> stopBackend() {
        if (backendProcess == null) {
            return Result.error("后端服务未运行");
        }

        try {
            backendProcess.destroyForcibly();
            backendProcess.waitFor();
            backendProcess = null;
            return Result.success(null, "后端服务已停止");
        } catch (Exception e) {
            log.error("停止后端服务失败", e);
            return Result.error("停止后端服务失败");
        }
    }

    @PostMapping("/frontend/start")
    public Result<Void> startFrontend() {
        if (frontendProcess != null && isProcessRunning(frontendProcess)) {
            return Result.error("前端服务已运行");
        }

        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("npm", "run", "dev");
            builder.directory(new java.io.File("./frontend"));
            builder.redirectErrorStream(true);
            
            frontendProcess = builder.start();
            
            logProcessOutput(frontendProcess, "frontend");
            
            Thread.sleep(3000);
            if (isProcessRunning(frontendProcess)) {
                return Result.success(null, "前端服务启动成功");
            } else {
                return Result.error("前端服务启动失败");
            }
        } catch (Exception e) {
            log.error("启动前端服务失败", e);
            return Result.error("启动前端服务失败: " + e.getMessage());
        }
    }

    @PostMapping("/frontend/stop")
    public Result<Void> stopFrontend() {
        if (frontendProcess == null) {
            return Result.error("前端服务未运行");
        }

        try {
            frontendProcess.destroyForcibly();
            frontendProcess.waitFor();
            frontendProcess = null;
            return Result.success(null, "前端服务已停止");
        } catch (Exception e) {
            log.error("停止前端服务失败", e);
            return Result.error("停止前端服务失败");
        }
    }

    @PostMapping("/start-all")
    public Result<Void> startAll() {
        Result<Void> backendResult = startBackend();
        if (backendResult.getCode() != 200) {
            return backendResult;
        }

        try {
            Thread.sleep(2000);
            return startFrontend();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.error("启动服务失败");
        }
    }

    @PostMapping("/stop-all")
    public Result<Void> stopAll() {
        stopBackend();
        stopFrontend();
        return Result.success(null, "所有服务已停止");
    }

    private boolean isProcessRunning(Process process) {
        try {
            process.exitValue();
            return false;
        } catch (IllegalThreadStateException e) {
            return true;
        }
    }

    private void logProcessOutput(Process process, String name) {
        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[{}] {}", name, line);
                }
            } catch (IOException e) {
                log.error("读取进程输出失败", e);
            }
        }).start();
    }
}