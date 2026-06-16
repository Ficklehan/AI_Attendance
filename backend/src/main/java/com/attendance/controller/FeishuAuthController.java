package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.config.FeishuCredentialsStartupValidator;
import com.attendance.config.FeishuProperties;
import com.attendance.dto.request.FeishuLoginExchangeRequest;
import com.attendance.dto.request.FeishuMiniprogramLoginRequest;
import com.attendance.dto.response.LoginResponse;
import com.attendance.entity.User;
import com.attendance.service.AuditLogService;
import com.attendance.service.FeishuService;
import com.attendance.service.ReminderSupport;
import com.attendance.service.UserService;
import com.attendance.security.LoginExchangeService;
import com.attendance.security.OAuthStateService;
import com.alibaba.fastjson.JSONObject;
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
import java.util.HashMap;
import java.util.Map;

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

    @Autowired
    private OAuthStateService oauthStateService;

    @Autowired
    private LoginExchangeService loginExchangeService;

    @Autowired
    private FeishuCredentialsStartupValidator feishuCredentialsStartupValidator;

    @GetMapping("/readiness")
    public Result<Map<String, Object>> readiness() {
        Map<String, Object> body = new HashMap<>();
        body.put("feishuConfigured", feishuCredentialsStartupValidator.isConfigured());
        if (feishuProperties.getAppId() != null && !feishuProperties.getAppId().trim().isEmpty()) {
            body.put("appId", FeishuCredentialsStartupValidator.maskAppId(feishuProperties.getAppId()));
        }
        return Result.success(body);
    }

    @GetMapping("/login")
    public void login(@RequestParam(value = "redirect", required = false) String redirect,
                      HttpServletResponse response) throws IOException {
        String safeRedirect = ReminderSupport.sanitizePostLoginRedirect(redirect);
        String state = oauthStateService.createState(safeRedirect);
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
        log.debug("飞书回调收到授权码, len={}", code != null ? code.length() : 0);

        try {
            oauthStateService.validateState(state);
            String postLoginRedirect = ReminderSupport.sanitizePostLoginRedirect(
                    oauthStateService.extractRedirect(state));
            String token = feishuService.exchangeWebOAuthCode(code);
            JSONObject userInfo = feishuService.fetchAuthenUserInfo(token);
            
            log.debug("飞书返回的用户信息: open_id={}", userInfo.getString("open_id"));
            
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

            String exchangeCode = loginExchangeService.issue(loginResponse);
            String html = generateLoginSuccessHtml(exchangeCode, postLoginRedirect);

            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(html);
        } catch (IllegalArgumentException e) {
            log.error("飞书登录失败", e);
            String html = generateErrorHtml("登录状态无效，请返回重新登录");
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(html);
        } catch (IOException e) {
            log.error("飞书登录失败：无法连接飞书开放平台", e);
            String errorMessage = "无法连接飞书服务器，请检查本机网络/DNS 或代理设置后重试";
            if (e.getCause() instanceof java.net.UnknownHostException
                    || e instanceof java.net.UnknownHostException) {
                errorMessage = "无法解析飞书域名 open.feishu.cn，请检查网络或 DNS 后重试";
            }
            String html = generateErrorHtml(errorMessage);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(html);
        } catch (RuntimeException e) {
            log.error("飞书登录失败", e);
            String errorMessage = "登录失败";
            if (e.getMessage() != null && e.getMessage().contains("code has been used")) {
                errorMessage = "授权码已过期，请返回重新登录";
            } else if (e.getMessage() != null && e.getMessage().contains("OAuth state")) {
                errorMessage = "登录状态无效，请返回重新登录";
            }
            String html = generateErrorHtml(errorMessage);
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(html);
        }
    }
    
    @PostMapping("/exchange")
    public Result<LoginResponse> exchangeLogin(@Valid @RequestBody FeishuLoginExchangeRequest request) {
        LoginResponse loginResponse = loginExchangeService.consume(request.getCode());
        if (loginResponse == null) {
            return Result.error(401, "登录凭证无效或已过期，请重新登录");
        }
        return Result.success(loginResponse);
    }

    private String generateLoginSuccessHtml(String exchangeCode, String redirectPath)
            throws java.io.UnsupportedEncodingException {
        String callbackUrl = feishuProperties.getFrontendCallbackUrl();
        String encodedCode = URLEncoder.encode(exchangeCode, "UTF-8");
        String redirectQuery = "";
        if (redirectPath != null && !redirectPath.isEmpty()) {
            redirectQuery = "&redirect=" + URLEncoder.encode(redirectPath, "UTF-8");
        }
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
               "        window.location.href = '" + callbackUrl + "?exchange=" + encodedCode + redirectQuery + "';\n" +
               "    </script>\n" +
               "</body>\n" +
               "</html>";
    }
    
    private String generateErrorHtml(String message) {
        String loginUrl = feishuProperties.getFrontendLoginUrl();
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
               "        <button class=\"btn\" onclick=\"window.location.href='" + loginUrl + "'\">返回登录页</button>\n" +
               "    </div>\n" +
               "</body>\n" +
               "</html>";
    }

    @PostMapping("/miniprogram/login")
    public Result<LoginResponse> miniprogramLogin(@Valid @RequestBody FeishuMiniprogramLoginRequest request) {
        String code = request.getCode();
        log.debug("飞书小程序登录, codeLen={}", code != null ? code.length() : 0);

        if (feishuProperties.getAppId() == null || feishuProperties.getAppId().isEmpty()
                || feishuProperties.getAppSecret() == null || feishuProperties.getAppSecret().isEmpty()) {
            return Result.error("飞书应用凭证未配置，请在 UAT 服务器环境变量或 backend/.env 中设置 FEISHU_APP_ID 和 FEISHU_APP_SECRET 后重启服务");
        }

        try {
            JSONObject tokenData = feishuService.exchangeMiniprogramLoginCode(code);
            String accessToken = tokenData.getString("access_token");
            String openId = tokenData.getString("open_id");
            String unionId = tokenData.getString("union_id");
            String userId = tokenData.getString("user_id");
            String employeeId = tokenData.getString("employee_id");
            
            JSONObject userInfo = feishuService.fetchAuthenUserInfo(accessToken);
            log.debug("飞书小程序用户信息: open_id={}", userInfo.getString("open_id"));

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
}
