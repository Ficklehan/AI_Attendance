package com.attendance;

import com.attendance.common.BusinessException;
import com.attendance.util.UploadPathSecurity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadPathSecurityTest {

    @Test
    void acceptsValidUploadKey() {
        assertDoesNotThrow(() -> UploadPathSecurity.validateFileKey("20260603_0001.jpg"));
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(BusinessException.class,
                () -> UploadPathSecurity.validateFileKey("../../../etc/passwd"));
        assertThrows(BusinessException.class,
                () -> UploadPathSecurity.validateFileKey("20260603_0001/../../../etc/passwd"));
    }

    @Test
    void rejectsUnexpectedKeyFormat() {
        assertThrows(BusinessException.class,
                () -> UploadPathSecurity.validateFileKey("evil.exe"));
    }
}
