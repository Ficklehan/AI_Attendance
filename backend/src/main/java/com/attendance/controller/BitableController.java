package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.service.BitableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/bitable")
public class BitableController {

    private static final Logger log = LoggerFactory.getLogger(BitableController.class);

    @Autowired
    private BitableService bitableService;

    @GetMapping("/validate")
    public Result validate(@RequestParam String appToken, @RequestParam String tableId) {
        log.info("验证飞书多维表连接: appToken={}, tableId={}", appToken, tableId);

        try {
            boolean isValid = bitableService.validateConnection(appToken, tableId);

            if (isValid) {
                Map<String, Object> result = new HashMap<>();
                result.put("appToken", appToken);
                result.put("tableId", tableId);
                result.put("message", "连接验证成功");
                return Result.success(result);
            } else {
                return Result.error("连接验证失败，请检查App Token和Table ID是否正确");
            }
        } catch (Exception e) {
            log.error("验证飞书多维表连接失败", e);
            return Result.error("验证失败: " + e.getMessage());
        }
    }

    @PostMapping("/parse-url")
    public Result parseUrl(@RequestBody Map<String, String> request) {
        String url = request.get("url");

        if (url == null || url.trim().isEmpty()) {
            return Result.error("URL不能为空");
        }

        log.info("解析飞书多维表链接: {}", url);

        try {
            // 解析URL
            java.net.URL urlObj = new java.net.URL(url);
            String hostname = urlObj.getHost();

            // 检查是否是飞书域名
            if (!hostname.contains("feishu.cn") && !hostname.contains("larksuite.com")) {
                return Result.error("不是飞书链接");
            }

            // 检查路径是否包含 /base/
            String[] pathParts = urlObj.getPath().split("/");
            int baseIndex = -1;
            for (int i = 0; i < pathParts.length; i++) {
                if ("base".equals(pathParts[i])) {
                    baseIndex = i;
                    break;
                }
            }

            if (baseIndex == -1 || pathParts.length <= baseIndex + 1) {
                return Result.error("链接格式不正确，缺少App Token");
            }

            // 提取 App Token
            String appToken = pathParts[baseIndex + 1];

            if (appToken == null || appToken.length() < 10) {
                return Result.error("App Token格式不正确");
            }

            // 从URL参数中提取 Table ID
            String query = urlObj.getQuery();
            String tableId = null;

            if (query != null && !query.isEmpty()) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("table=")) {
                        tableId = param.substring(6);
                        break;
                    }
                }
            }

            if (tableId == null || tableId.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("appToken", appToken);
                result.put("tableId", null);
                result.put("message", "未找到Table ID，请在链接中添加 ?table=xxx 参数");
                return Result.success(result);
            }

            // 验证 Table ID 格式
            if (!tableId.startsWith("tbl")) {
                return Result.error("Table ID格式不正确，应以tbl开头");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("appToken", appToken);
            result.put("tableId", tableId);
            result.put("message", "解析成功");

            log.info("链接解析成功: appToken={}, tableId={}", appToken, tableId);
            return Result.success(result);

        } catch (Exception e) {
            log.error("解析飞书多维表链接失败", e);
            return Result.error("解析失败: " + e.getMessage());
        }
    }
}
