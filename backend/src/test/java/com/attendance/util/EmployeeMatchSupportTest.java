package com.attendance.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeMatchSupportTest {

    @Test
    void resolveMatchName_keepsSerialByDefault() {
        Map<String, Object> record = new HashMap<>();
        record.put("NOM_PRENOM", "张三 01");
        record.put("AGENCE_INTERIMAIRE", "ABC");
        assertEquals("张三 01", EmployeeMatchSupport.resolveMatchName(record));
    }

    @Test
    void resolveMatchName_stripsSerialWhenDuplicateConfirmed() {
        Map<String, Object> record = new HashMap<>();
        record.put("NOM_PRENOM", "张三 01");
        record.put("_duplicateConfirmedUnique", true);
        assertEquals("张三", EmployeeMatchSupport.resolveMatchName(record));
    }

    @Test
    void formatEmpNo_usesRegionPrefix() {
        assertEquals("FR00001", EmployeeMatchSupport.formatEmpNo("FR", 1));
        assertEquals("DEFAULT00042", EmployeeMatchSupport.formatEmpNo("default", 42));
    }

    @Test
    void hasSystemEmployeeId_detectsBoundRows() {
        Map<String, Object> empty = new HashMap<>();
        assertFalse(EmployeeMatchSupport.hasSystemEmployeeId(empty));

        Map<String, Object> withId = new HashMap<>();
        withId.put("employeeId", 42L);
        assertTrue(EmployeeMatchSupport.hasSystemEmployeeId(withId));

        Map<String, Object> withTextId = new HashMap<>();
        withTextId.put("employeeId", "99");
        assertTrue(EmployeeMatchSupport.hasSystemEmployeeId(withTextId));

        Map<String, Object> withEmpNoOnly = new HashMap<>();
        withEmpNoOnly.put("employeeNo", "FR00001");
        assertFalse(EmployeeMatchSupport.hasSystemEmployeeId(withEmpNoOnly));
    }

    @Test
    void eligible_skipsDeletedAndAbsent() {
        Map<String, Object> deleted = new HashMap<>();
        deleted.put("deleted", true);
        deleted.put("NOM_PRENOM", "A");
        deleted.put("AGENCE_INTERIMAIRE", "X");
        assertFalse(EmployeeMatchSupport.eligibleForEmployeeAssignment(deleted));

        Map<String, Object> absent = new HashMap<>();
        absent.put("SmartMark", "未出勤");
        absent.put("NOM_PRENOM", "A");
        absent.put("AGENCE_INTERIMAIRE", "X");
        assertFalse(EmployeeMatchSupport.eligibleForEmployeeAssignment(absent));
    }
}
