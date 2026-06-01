package com.attendance.service;

import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class SimulatedRecognitionService {
    
    private static final Logger log = LoggerFactory.getLogger(SimulatedRecognitionService.class);
    
    private final Random random = new Random();
    
    private static final String[] FIRST_NAMES = {"张三", "李四", "王五", "赵六", "钱七", "孙八", "周九", "吴十"};
    private static final String[] LAST_NAMES = {"A", "B", "C", "D", "E", "F", "G", "H"};
    private static final String[] AGENCIES = {"中介A", "中介B", "中介C", "中介D"};
    private static final String[] SHIFTS = {"白班", "夜班", "早班", "晚班"};
    
    public interface SimCallback {
        void onRecord(JSONObject record);
        void onComplete(int totalCount);
        void onError(Exception e);
    }
    
    public void simulateRecognition(SimCallback callback) {
        log.info("使用模拟识别服务（同步）");
        try {
            int recordCount = 5 + random.nextInt(6);
            List<JSONObject> records = generateMockRecords(recordCount);
            for (JSONObject record : records) {
                callback.onRecord(record);
            }
            callback.onComplete(recordCount);
        } catch (Exception e) {
            callback.onError(e);
        }
    }
    
    private List<JSONObject> generateMockRecords(int count) {
        List<JSONObject> records = new ArrayList<>();
        
        for (int i = 1; i <= count; i++) {
            JSONObject record = new JSONObject();
            
            record.put("NO", String.format("%04d", i));
            record.put("Pays", "Netherlands");
            record.put("Entrepot", "AMS");
            record.put("NOM_PRENOM", FIRST_NAMES[random.nextInt(FIRST_NAMES.length)] + LAST_NAMES[random.nextInt(LAST_NAMES.length)]);
            record.put("AGENCE_INTERIMAIRE", AGENCIES[random.nextInt(AGENCIES.length)]);
            record.put("HORAIRES_DU_TRAVAIL", SHIFTS[random.nextInt(SHIFTS.length)]);
            
            String date = "2026-05-" + String.format("%02d", 15 + random.nextInt(6));
            record.put("Date", date);
            
            int arriveHour = 6 + random.nextInt(6);
            int arriveMin = random.nextInt(60);
            record.put("ARRIVEE", String.format("%02d:%02d", arriveHour, arriveMin));
            
            int departHour = arriveHour + 7 + random.nextInt(3);
            int departMin = random.nextInt(60);
            record.put("DEPAR", String.format("%02d:%02d", departHour, departMin));
            
            record.put("PAUSE", 30 + random.nextInt(31));
            record.put("SIGNATURE", "员工签名" + (char)('A' + random.nextInt(5)));
            record.put("CHECKER", record.getString("SIGNATURE"));
            record.put("Observations", "");
            
            String[] marks = {"正常", "手写", "模糊", "正常;夜班", "手写;夜班"};
            record.put("Mark", marks[random.nextInt(marks.length)]);
            
            record.put("isDeleted", false);
            record.put("SmartMark", record.getString("Mark"));
            
            String[] riskLevels = {"none", "none", "none", "medium", "high"};
            record.put("riskLevel", riskLevels[random.nextInt(riskLevels.length)]);
            
            records.add(record);
        }
        
        return records;
    }
}