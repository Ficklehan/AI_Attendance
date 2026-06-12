package com.attendance.service;

import com.attendance.config.FeishuProperties;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class FeishuService {
    
    private static final Logger log = LoggerFactory.getLogger(FeishuService.class);

    @Autowired
    private FeishuProperties feishuProperties;

    @Autowired
    private OkHttpClient httpClient;

    private String accessToken;
    private long tokenExpireTime;
    private final Object tokenLock = new Object();

    private String appAccessToken;
    private long appTokenExpireTime;
    private final Object appTokenLock = new Object();

    /**
     * 小程序 code2session：tt.login 的 code 换用户凭证（官方 mina/v2/tokenLoginValidate）
     */
    public JSONObject exchangeMiniprogramLoginCode(String code) throws IOException {
        String appToken = getAppAccessToken();

        JSONObject body = new JSONObject();
        body.put("code", code);

        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/mina/v2/tokenLoginValidate")
                .header("Authorization", "Bearer " + appToken)
                .header("Content-Type", "application/json; charset=utf-8")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        Response response = httpClient.newCall(request).execute();
        String responseBody = response.body() != null ? response.body().string() : "";
        if (!response.isSuccessful()) {
            log.error("tokenLoginValidate HTTP {}: {}", response.code(), responseBody);
            throw new RuntimeException("小程序登录校验失败: HTTP " + response.code());
        }

        JSONObject result = JSON.parseObject(responseBody);
        assertFeishuApiSuccess(result, "小程序登录校验");
        return result.getJSONObject("data");
    }

    public String getAppAccessToken() throws IOException {
        synchronized (appTokenLock) {
            if (appAccessToken != null && System.currentTimeMillis() < appTokenExpireTime) {
                return appAccessToken;
            }

            JSONObject body = new JSONObject();
            body.put("app_id", feishuProperties.getAppId());
            body.put("app_secret", feishuProperties.getAppSecret());

            Request request = new Request.Builder()
                    .url("https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal")
                    .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                    .build();

            Response response = httpClient.newCall(request).execute();
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                log.error("app_access_token HTTP {}: {}", response.code(), responseBody);
                throw new RuntimeException("获取 app_access_token 失败: HTTP " + response.code());
            }

            JSONObject result = JSON.parseObject(responseBody);
            assertFeishuApiSuccess(result, "获取 app_access_token");

            appAccessToken = result.getString("app_access_token");
            long expire = result.getLong("expire");
            if (expire <= 0) {
                expire = result.getLongValue("expire_in");
            }
            appTokenExpireTime = System.currentTimeMillis() + (expire - 60) * 1000;
            log.info("飞书 app_access_token 获取成功");
            return appAccessToken;
        }
    }

    private void assertFeishuApiSuccess(JSONObject result, String action) {
        if (result == null || result.getInteger("code") == null || result.getInteger("code") != 0) {
            String msg = result != null ? result.getString("msg") : null;
            if (msg == null || msg.isEmpty()) {
                msg = result != null ? result.getString("message") : null;
            }
            Integer errCode = result != null ? result.getInteger("code") : null;
            log.error("飞书 {} 失败: {}", action, result != null ? result.toJSONString() : "null");
            throw new RuntimeException(action + "失败: " + (msg != null && !msg.isEmpty() ? msg : "errCode=" + errCode));
        }
    }

    private String getAccessToken() throws IOException {
        synchronized (tokenLock) {
            if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
                return accessToken;
            }

            JSONObject body = new JSONObject();
            body.put("app_id", feishuProperties.getAppId());
            body.put("app_secret", feishuProperties.getAppSecret());

            Request request = new Request.Builder()
                    .url("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")
                    .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                    .build();

            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException("获取飞书Token失败: " + response.code());
            }

            JSONObject result = JSON.parseObject(response.body().string());
            if (result.getInteger("code") != 0) {
                throw new RuntimeException("获取Token失败: " + result.getString("msg"));
            }

            accessToken = result.getString("tenant_access_token");
            tokenExpireTime = System.currentTimeMillis() + (result.getLong("expire") - 60) * 1000;
            log.info("飞书Token获取成功");
            
            return accessToken;
        }
    }

    public String getImageUrl(String fileKey) throws IOException {
        String token = getAccessToken();
        
        JSONObject body = new JSONObject();
        body.put("file_key", fileKey);

        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/im/v1/images/" + fileKey + "/download")
                .header("Authorization", "Bearer " + token)
                .get()
                .build();

        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException("获取图片URL失败: " + response.code());
        }

        JSONObject result = JSON.parseObject(response.body().string());
        if (result.getInteger("code") != 0) {
            throw new RuntimeException("获取图片URL失败: " + result.getString("msg"));
        }

        return result.getJSONObject("data").getString("url");
    }

    /**
     * 库中 feishu_user_id 来自飞书登录 open_id（ou_ 前缀），须与 receive_id_type 一致。
     */
    static String resolveReceiveIdType(String receiveId) {
        if (receiveId == null || receiveId.isEmpty()) {
            return "open_id";
        }
        if (receiveId.startsWith("ou_")) {
            return "open_id";
        }
        if (receiveId.startsWith("on_")) {
            return "union_id";
        }
        return "user_id";
    }

    private String sendImMessage(String receiveId, String msgType, String contentJson) throws IOException {
        String token = getAccessToken();
        String receiveIdType = resolveReceiveIdType(receiveId);

        JSONObject body = new JSONObject();
        body.put("receive_id", receiveId);
        body.put("msg_type", msgType);
        body.put("content", contentJson);

        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=" + receiveIdType)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        Response response = httpClient.newCall(request).execute();
        String responseBody = response.body() != null ? response.body().string() : "";
        if (!response.isSuccessful()) {
            log.warn("发送飞书消息失败: HTTP {} receive_id_type={} receive_id={} body={}",
                    response.code(), receiveIdType, receiveId, responseBody);
            throw new IOException("飞书消息发送失败 HTTP " + response.code() + ": " + responseBody);
        }
        JSONObject result = JSON.parseObject(responseBody);
        assertFeishuApiSuccess(result, "发送飞书消息");
        JSONObject data = result.getJSONObject("data");
        return data != null ? data.getString("message_id") : null;
    }

    public void sendTextMessage(String userId, String text) throws IOException {
        sendImMessage(userId, "text", JSONObject.toJSONString(Collections.singletonMap("text", text)));
    }

    public String sendCardMessage(String userId, JSONObject card) throws IOException {
        return sendImMessage(userId, "interactive", card.toJSONString());
    }

    public void recallMessage(String messageId) throws IOException {
        if (messageId == null || messageId.trim().isEmpty()) {
            return;
        }
        String token = getAccessToken();
        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/im/v1/messages/" + messageId.trim())
                .delete()
                .header("Authorization", "Bearer " + token)
                .build();

        Response response = httpClient.newCall(request).execute();
        String responseBody = response.body() != null ? response.body().string() : "";
        if (!response.isSuccessful()) {
            log.warn("撤回飞书消息失败: HTTP {} message_id={} body={}",
                    response.code(), messageId, responseBody);
            throw new IOException("飞书消息撤回失败 HTTP " + response.code() + ": " + responseBody);
        }
        JSONObject result = JSON.parseObject(responseBody);
        if (result != null && result.getInteger("code") != null && result.getInteger("code") != 0) {
            log.warn("撤回飞书消息失败: message_id={} body={}", messageId, responseBody);
            throw new IOException("飞书消息撤回失败: " + result.getString("msg"));
        }
    }

    public void deleteMessage(String messageId) throws IOException {
        recallMessage(messageId);
    }

    public void sendSuccessNotification(String userId, int recordCount) throws IOException {
        JSONObject card = new JSONObject();
        card.put("type", "template_card");

        JSONObject cardContent = new JSONObject();
        cardContent.put("card_type", "notification");
        cardContent.put("title", "考勤识别完成");

        JSONArray elements = new JSONArray();
        JSONObject element = new JSONObject();
        element.put("tag", "div");
        Map<String, String> textBody = new HashMap<>();
        textBody.put("content", "成功识别 " + recordCount + " 条考勤记录");
        textBody.put("tag", "lark_md");
        element.put("text", JSONObject.toJSONString(textBody));
        elements.add(element);

        cardContent.put("elements", elements);
        Map<String, String> footerInner = new HashMap<>();
        footerInner.put("content", "点击查看详情");
        footerInner.put("tag", "lark_md");
        Map<String, String> footerOuter = new HashMap<>();
        footerOuter.put("text", JSONObject.toJSONString(footerInner));
        cardContent.put("footer", JSONObject.toJSONString(footerOuter));

        card.put("card", cardContent);
        
        sendCardMessage(userId, card);
    }
}