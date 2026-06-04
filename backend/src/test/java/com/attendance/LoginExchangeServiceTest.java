package com.attendance;

import com.attendance.dto.response.LoginResponse;
import com.attendance.security.LoginExchangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoginExchangeServiceTest {

    private LoginExchangeService service;

    @BeforeEach
    void setUp() {
        service = new LoginExchangeService();
    }

    @Test
    void exchangeCodeIsSingleUse() {
        LoginResponse response = new LoginResponse();
        response.setToken("jwt-token");
        String code = service.issue(response);

        assertNotNull(service.consume(code));
        assertNull(service.consume(code));
    }
}
