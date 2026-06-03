package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.config.FeishuProperties;
import com.attendance.dto.request.FeishuMiniprogramLoginRequest;
import com.attendance.dto.response.LoginResponse;
import com.attendance.entity.User;
import com.attendance.service.AuditLogService;
import com.attendance.service.FeishuService;
import com.attendance.service.UserService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.UUID;

@RestController
@RequestMapping("/feishu-auth")
public class FeishuAuthController {

    private static final Logger log = LoggerFactory.getLogger(FeishuAuthController.class);

    @Autowired
    private FeishuProperties feishuProperties;

    @Autowired
    private UserService userService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private FeishuService feishuService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    @GetMapping("/login")
    public void login(HttpServletResponse response) throws IOException {
        String state = UUID.randomUUID().toString();
        String redirectUri = URLEncoder.encode(feishuProperties.getRedirectUri(), "UTF-8");
        
        String url = String.format(
            "https://open.feishu.cn/open-apis/authen/v1/index?app_id=%s&redirect_uri=%s&state=%s",
            feishuProperties.getAppId(),
            redirectUri,
            state
        );
        
        response.sendRedirect(url);
    }

    @GetMapping("/callback")
    public void callback(@RequestParam("code") String code, @RequestParam("state") String state, HttpServletResponse response) throws IOException {
        log.info("飞书回调, code: {}", code);
        
        try {
            String token = getAccessToken(code);
            JSONObject userInfo = getUserInfo(token);
            
            log.info("飞书返回的完整用户信息: {}", userInfo.toJSONString());
            
            String feishuUserId = userInfo.getString("open_id");
            if (feishuUserId == null || feishuUserId.isEmpty()) {
                feishuUserId = userInfo.getString("union_id");
            }
            if (feishuUserId == null || feishuUserId.isEmpty()) {
                feishuUserId = userInfo.getString("user_id");
            }
            
            log.info("使用的飞书用户ID: {}", feishuUserId);
            
            String name = userInfo.getString("name");
            String email = userInfo.getString("email");
            
            if (email == null || email.trim().isEmpty()) {
                email = feishuUserId + "@feishu.user";
            }
            
            User user = userService.findByFeishuUserId(feishuUserId);
            
            LoginResponse loginResponse;
            if (user != null) {
                loginResponse = userService.loginByFeishu(user);
            } else {
                loginResponse = userService.registerByFeishu(feishuUserId, name, email);
            }
            
            auditLogService.log("USER_LOGIN", "user", loginResponse.getUserInfo().getId(), "飞书登录");
            
            String jsonData = JSON.toJSONString(loginResponse);
            String html = generateLoginSuccessHtml(jsonData);
            
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(html);
        } catch (RuntimeException e) {
            log.error("飞书登录失败", e);
            String errorMessage = "登录失败";
            if (e.getMessage() != null && e.getMessage().contains("code has been used")) {
                errorMessage = "授权码已过期，请返回重新登录";
            }
            String html = generateErrorHtml(errorMessage);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(html);
        }
    }
    
    private String generateLoginSuccessHtml(String jsonData) {
        return "<!DOCTYPE html>\n" +
               "<html lang=\"zh-CN\">\n" +
               "<head>\n" +
               "    <meta charset=\"UTF-8\">\n" +
               "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
               "    <title>登录成功</title>\n" +
               "    <style>\n" +
               "        body { margin: 0; padding: 0; background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%); display: flex; justify-content: center; align-items: center; height: 100vh; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }\n" +
               "        .container { text-align: center; color: white; }\n" +
               "        .success-icon { font-size: 64px; margin-bottom: 20px; }\n" +
               "        .title { font-size: 24px; margin-bottom: 10px; }\n" +
               "        .subtitle { color: #aaa; margin-bottom: 30px; }\n" +
               "        .spinner { width: 40px; height: 40px; border: 4px solid rgba(255,255,255,0.3); border-top-color: #5B8FF9; border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 20px; }\n" +
               "        @keyframes spin { to { transform: rotate(360deg); } }\n" +
               "    </style>\n" +
               "</head>\n" +
               "<body>\n" +
               "    <div class=\"container\">\n" +
               "        <div class=\"success-icon\">✓</div>\n" +
               "        <div class=\"title\">登录成功</div>\n" +
               "        <div class=\"subtitle\">正在跳转...</div>\n" +
               "        <div class=\"spinner\"></div>\n" +
               "    </div>\n" +
               "    <script>\n" +
               "        var data = " + jsonData + ";\n" +
               "        var params = new URLSearchParams();\n" +
               "        params.set('token', data.token);\n" +
               "        params.set('userInfo', JSON.stringify(data.userInfo));\n" +
               "        window.location.href = 'http://localhost:5175/feishu/callback?' + params.toString();\n" +
               "    </script>\n" +
               "</body>\n" +
               "</html>";
    }
    
    private String generateErrorHtml(String message) {
        return "<!DOCTYPE html>\n" +
               "<html lang=\"zh-CN\">\n" +
               "<head>\n" +
               "    <meta charset=\"UTF-8\">\n" +
               "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
               "    <title>登录失败</title>\n" +
               "    <style>\n" +
               "        body { margin: 0; padding: 0; background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%); display: flex; justify-content: center; align-items: center; height: 100vh; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }\n" +
               "        .container { text-align: center; color: white; }\n" +
               "        .error-icon { font-size: 64px; color: #ff6b6b; margin-bottom: 20px; }\n" +
               "        .title { font-size: 24px; margin-bottom: 10px; }\n" +
               "        .message { color: #ff6b6b; margin-bottom: 30px; }\n" +
               "        .btn { padding: 12px 30px; background: #5B8FF9; border: none; border-radius: 8px; color: white; cursor: pointer; font-size: 14px; }\n" +
               "        .btn:hover { background: #4a7de9; }\n" +
               "    </style>\n" +
               "</head>\n" +
               "<body>\n" +
               "    <div class=\"container\">\n" +
               "        <div class=\"error-icon\">✗</div>\n" +
               "        <div class=\"title\">登录失败</div>\n" +
               "        <div class=\"message\">" + message + "</div>\n" +
               "        <button class=\"btn\" onclick=\"window.location.href='http://localhost:5175'\">返回登录页</button>\n" +
               "    </div>\n" +
               "</body>\n" +
               "</html>";
    }

    private String getAccessToken(String code) throws IOException {
        JSONObject body = new JSONObject();
        body.put("app_id", feishuProperties.getAppId());
        body.put("app_secret", feishuProperties.getAppSecret());
        body.put("code", code);
        body.put("grant_type", "authorization_code");

        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/authen/v1/access_token")
                .header("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException("获取飞书access_token失败: " + response.code());
        }

        JSONObject result = JSON.parseObject(response.body().string());
        if (result.getInteger("code") != 0) {
            throw new RuntimeException("获取access_token失败: " + result.getString("msg"));
        }

        return result.getJSONObject("data").getString("access_token");
    }

    private JSONObject getUserInfo(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/authen/v1/user_info")
                .header("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException("获取飞书用户信息失败: " + response.code());
        }

        JSONObject result = JSON.parseObject(response.body().string());
        if (result.getInteger("code") != 0) {
            throw new RuntimeException("获取用户信息失败: " + result.getString("msg"));
        }

        return result.getJSONObject("data");
    }

    @PostMapping("/miniprogram/login")
    public Result<LoginResponse> miniprogramLogin(@Valid @RequestBody FeishuMiniprogramLoginRequest request) {
        String code = request.getCode();
        log.info("飞书小程序登录, code: {}", code);

        if (feishuProperties.getAppId() == null || feishuProperties.getAppId().isEmpty()
                || feishuProperties.getAppSecret() == null || feishuProperties.getAppSecret().isEmpty()) {
            return Result.error("未配置飞书应用凭证，请在 backend/.env 设置 FEISHU_APP_ID 和 FEISHU_APP_SECRET");
        }

        try {
            JSONObject tokenData = feishuService.exchangeMiniprogramLoginCode(code);
            String accessToken = tokenData.getString("access_token");
            String openId = tokenData.getString("open_id");
            String unionId = tokenData.getString("union_id");
            String userId = tokenData.getString("user_id");
            String employeeId = tokenData.getString("employee_id");
            
            JSONObject userInfo = getMiniprogramUserInfo(accessToken);
            log.info("飞书小程序返回的完整用户信息: {}", userInfo.toJSONString());

            String feishuUserId = openId;
            if (feishuUserId == null || feishuUserId.isEmpty()) {
                feishuUserId = unionId;
            }
            if (feishuUserId == null || feishuUserId.isEmpty()) {
                feishuUserId = userId;
            }
            if (feishuUserId == null || feishuUserId.isEmpty()) {
                feishuUserId = employeeId;
            }

            if (feishuUserId == null || feishuUserId.isEmpty()) {
                feishuUserId = userInfo.getString("open_id");
            }
            if (feishuUserId == null || feishuUserId.isEmpty()) {
                feishuUserId = userInfo.getString("union_id");
            }
            if (feishuUserId == null || feishuUserId.isEmpty()) {
                feishuUserId = userInfo.getString("user_id");
            }

            log.info("使用的飞书用户ID: {}", feishuUserId);

            String name = userInfo.getString("name");
            String email = userInfo.getString("email");

            if (email == null || email.trim().isEmpty()) {
                email = feishuUserId + "@feishu.user";
            }

            User user = userService.findByFeishuUserId(feishuUserId);

            LoginResponse loginResponse;
            if (user != null) {
                loginResponse = userService.loginByFeishu(user);
            } else {
                loginResponse = userService.registerByFeishu(feishuUserId, name, email);
            }

            auditLogService.log("USER_LOGIN", "user", loginResponse.getUserInfo().getId(), "飞书小程序登录");

            return Result.success(loginResponse);
        } catch (Exception e) {
            log.error("飞书小程序登录失败", e);
            return Result.error("登录失败: " + e.getMessage());
        }
    }
    
    private JSONObject getMiniprogramUserInfo(String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/authen/v1/user_info")
                .header("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException("获取飞书用户信息失败: " + response.code());
        }

        JSONObject result = JSON.parseObject(response.body().string());
        if (result.getInteger("code") != 0) {
            throw new RuntimeException("获取用户信息失败: " + result.getString("msg"));
        }

        return result.getJSONObject("data");
    }
}
