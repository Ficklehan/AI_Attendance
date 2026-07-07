package com.attendance.controller;

import com.attendance.common.PageResult;
import com.attendance.common.Result;
import com.attendance.dto.response.EmployeeDTO;
import com.attendance.dto.response.WeeklyAttendanceDTO;
import com.attendance.security.AdminAuthService;
import com.attendance.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/employees")
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private AdminAuthService adminAuthService;

    @GetMapping
    public Result<PageResult<EmployeeDTO>> listEmployees(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String regionCodes,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String keyword) {
        return Result.success(employeeService.listEmployees(page, size, regionCodes, regionCode, keyword));
    }

    @GetMapping("/weekly")
    public Result<WeeklyAttendanceDTO> weeklyAttendance(
            @RequestParam(required = false) String isoWeek,
            @RequestParam(required = false) String regionCodes,
            @RequestParam(required = false) String regionCode) {
        return Result.success(employeeService.getWeeklyAttendance(isoWeek, regionCodes, regionCode));
    }

    @PostMapping("/backfill")
    public Result<Map<String, Object>> backfill() {
        adminAuthService.requireAdmin();
        return Result.success(employeeService.backfillConfirmedTasks());
    }
}
