package com.attendance.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.attendance.entity.Task;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRecordPayloadResolverTest {

    @Test
    void confirmedTask_mergesEditsWithFullRawRows() {
        Task task = new Task();
        task.setStatus("confirmed");
        task.setRawData("["
                + "{\"_rowKey\":\"a\",\"NO\":\"1\",\"NOM_PRENOM\":\"Raw A\"},"
                + "{\"_rowKey\":\"b\",\"NO\":\"2\",\"NOM_PRENOM\":\"Raw B\"},"
                + "{\"_rowKey\":\"c\",\"NO\":\"3\",\"NOM_PRENOM\":\"Raw C\"}"
                + "]");
        task.setConfirmedData("["
                + "{\"_rowKey\":\"a\",\"NO\":\"1\",\"NOM_PRENOM\":\"Edited A\"},"
                + "{\"_rowKey\":\"b\",\"NO\":\"2\",\"NOM_PRENOM\":\"Edited B\"}"
                + "]");

        JSONArray merged = JSON.parseArray(TaskRecordPayloadResolver.resolvePayload(task));
        assertEquals(3, merged.size());
        assertEquals("Edited A", merged.getJSONObject(0).getString("NOM_PRENOM"));
        assertEquals("Edited B", merged.getJSONObject(1).getString("NOM_PRENOM"));
        assertEquals("Raw C", merged.getJSONObject(2).getString("NOM_PRENOM"));
    }

    @Test
    void processedTask_usesRawData() {
        Task task = new Task();
        task.setStatus("processed");
        task.setRawData("[{\"NO\":\"1\"}]");
        task.setConfirmedData(null);
        assertEquals(task.getRawData(), TaskRecordPayloadResolver.resolvePayload(task));
    }

    @Test
    void confirmedTask_rawWithoutRowKey_confWithRowKey_mergesByIndexNotDuplicate() {
        Task task = new Task();
        task.setStatus("confirmed");
        task.setRawData("["
                + "{\"NO\":\"1\",\"NOM_PRENOM\":\"Raw A\"},"
                + "{\"NO\":\"2\",\"NOM_PRENOM\":\"Raw B\"}"
                + "]");
        task.setConfirmedData("["
                + "{\"_rowKey\":\"task-1-0\",\"NO\":\"1\",\"NOM_PRENOM\":\"Edited A\"},"
                + "{\"_rowKey\":\"task-1-1\",\"NO\":\"2\",\"NOM_PRENOM\":\"Edited B\"}"
                + "]");

        JSONArray merged = JSON.parseArray(TaskRecordPayloadResolver.resolvePayload(task));
        assertEquals(2, merged.size());
        assertEquals("Edited A", merged.getJSONObject(0).getString("NOM_PRENOM"));
        assertEquals("task-1-0", merged.getJSONObject(0).getString("_rowKey"));
        assertEquals("Edited B", merged.getJSONObject(1).getString("NOM_PRENOM"));
    }

    @Test
    void mergeByIndexWhenNoRowKeys() {
        JSONArray raw = JSON.parseArray("[{\"NO\":\"1\",\"Date\":\"\"},{\"NO\":\"2\"}]");
        JSONArray conf = JSON.parseArray("[{\"NO\":\"1\",\"Date\":\"2026-06-15\"},{\"NO\":\"2\",\"Date\":\"2026-06-16\"}]");
        JSONArray merged = TaskRecordPayloadResolver.mergeArrays(raw, conf);
        assertEquals("2026-06-15", merged.getJSONObject(0).getString("Date"));
        assertTrue(merged.getJSONObject(1).getString("Date").contains("2026-06-16"));
    }
}
