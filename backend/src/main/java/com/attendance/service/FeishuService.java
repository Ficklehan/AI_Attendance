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
import java.util.Map;

@Service
public class FeishuService {
    
    private static final Logger log = LoggerFactory.getLogger(FeishuService.class);

    @Autowired
    private FeishuProperties feishuProperties;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private String accessToken;
    private long tokenExpireTime;
    private final Object tokenLock = new Object();

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

    public void sendTextMessage(String userId, String text) throws IOException {
        String token = getAccessToken();
        
        JSONObject body = new JSONObject();
        body.put("receive_id", userId);
        body.put("msg_type", "text");
        body.put("content", JSONObject.toJSONString(Map.of("text", text)));

        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=user_id")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            log.warn("发送消息失败: {}", response.code());
        }
    }

    public void sendCardMessage(String userId, JSONObject card) throws IOException {
        String token = getAccessToken();
        
        JSONObject body = new JSONObject();
        body.put("receive_id", userId);
        body.put("msg_type", "interactive");
        body.put("content", card.toJSONString());

        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=user_id")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            log.warn("发送卡片消息失败: {}", response.code());
        }
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
        element.put("text", JSONObject.toJSONString(Map.of("content", "成功识别 " + recordCount + " 条考勤记录", "tag", "lark_md")));
        elements.add(element);
        
        cardContent.put("elements", elements);
        cardContent.put("footer", JSONObject.toJSONString(Map.of(
            "text", JSONObject.toJSONString(Map.of("content", "点击查看详情", "tag", "lark_md"))
        )));
        
        card.put("card", cardContent);
        
        sendCardMessage(userId, card);
    }
}