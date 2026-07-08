package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.common.BusinessException;
import com.attendance.common.PageResult;
import com.attendance.dto.response.EmployeeDTO;
import com.attendance.dto.response.WeeklyAttendanceDTO;
import com.attendance.entity.Employee;
import com.attendance.entity.Task;
import com.attendance.mapper.EmployeeMapper;
import com.attendance.mapper.TaskMapper;
import com.attendance.security.AdminAuthService;
import com.attendance.security.DataScopeContext;
import com.attendance.util.EmployeeMatchSupport;
import com.attendance.util.RecognizedDateNormalizer;
import com.attendance.util.RecordJsonSupport;
import com.attendance.util.TaskRecordExportSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);
    private static final Pattern ISO_WEEK = Pattern.compile("^(\\d{4})-W(\\d{2})$", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskRecordSyncService taskRecordSyncService;

    @Autowired
    private DataScopeService dataScopeService;

    @Autowired
    private AdminAuthService adminAuthService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private UserService userService;

    @Transactional
    public void assignEmployeeOnConfirm(Map<String, Object> record, String regionCode) {
        if (!EmployeeMatchSupport.eligibleForEmployeeAssignment(record)) {
            return;
        }
        if (EmployeeMatchSupport.hasSystemEmployeeId(record)) {
            return;
        }
        String region = EmployeeMatchSupport.normalizeRegionCode(regionCode);
        String agencyKey = EmployeeMatchSupport.resolveAgencyKey(record);
        String matchName = EmployeeMatchSupport.resolveMatchName(record);
        String displayName = firstNonBlank(record, "NOM_PRENOM", "NOM", "NAME", "Name");
        if (RecordJsonSupport.isBlank(displayName)) {
            displayName = matchName;
        }
        LocalDate attendanceDate = parseWorkDate(record);
        Employee employee = resolveOrCreate(region, agencyKey, matchName, displayName, attendanceDate);
        record.put("employeeId", employee.getId());
        record.put("employeeNo", employee.getEmpNo());
        record.put("EMPLOYEE_NO", employee.getEmpNo());
    }

    @Transactional
    public Employee resolveOrCreate(String regionCode, String agencyKey, String matchName,
                                    String displayName, LocalDate attendanceDate) {
        Employee existing = employeeMapper.selectByIdentity(regionCode, agencyKey, matchName);
        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            LocalDate lastDate = existing.getLastAttendanceDate();
            if (attendanceDate != null && (lastDate == null || attendanceDate.isAfter(lastDate))) {
                lastDate = attendanceDate;
            } else if (attendanceDate == null) {
                lastDate = existing.getLastAttendanceDate();
            }
            employeeMapper.updateLastSeen(existing.getId(), displayName, lastDate, now);
            existing.setDisplayName(displayName);
            existing.setLastAttendanceDate(lastDate);
            existing.setLastSeenAt(now);
            return existing;
        }

        String empNo = allocateEmpNo(regionCode);
        Employee created = new Employee();
        created.setEmpNo(empNo);
        created.setRegionCode(regionCode);
        created.setAgencyKey(agencyKey);
        created.setMatchName(matchName);
        created.setDisplayName(displayName);
        created.setStatus(1);
        created.setFirstCreatedAt(now);
        created.setLastAttendanceDate(attendanceDate);
        created.setLastSeenAt(now);
        try {
            employeeMapper.insertEmployee(created);
            return created;
        } catch (DuplicateKeyException ex) {
            Employee raced = employeeMapper.selectByIdentity(regionCode, agencyKey, matchName);
            if (raced != null) {
                employeeMapper.updateLastSeen(raced.getId(), displayName, attendanceDate, now);
                return raced;
            }
            throw ex;
        }
    }

    public PageResult<EmployeeDTO> listEmployees(int page, int size, String regionCodesParam,
                                                 String regionCode, String keyword) {
        requireEmployeesAccess();
        DataScopeContext scope = dataScopeService.resolveForCurrentUser();
        if (!canQueryEmployees(scope)) {
            long safePage = Math.max(page, 1);
            long safeSize = Math.min(Math.max(size, 1), 200);
            return PageResult.of(Collections.emptyList(), 0, safePage, safeSize);
        }
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(size, 1), 200);
        long offset = (safePage - 1) * safeSize;
        String kw = keyword != null ? keyword.trim() : null;
        List<String> regions = intersectRegionFilters(scope, normalizeRegionFilters(regionCodesParam, regionCode));
        if (regions != null && regions.isEmpty()) {
            return PageResult.of(Collections.emptyList(), 0, safePage, safeSize);
        }
        long total = employeeMapper.countEmployees(scope, regions, kw);
        List<Employee> rows = employeeMapper.selectEmployeePage(scope, regions, kw, offset, safeSize);
        List<EmployeeDTO> dtos = rows.stream().map(this::toDto).collect(Collectors.toList());
        return PageResult.of(dtos, total, safePage, safeSize);
    }

    public WeeklyAttendanceDTO getWeeklyAttendance(String isoWeek, String regionCodesParam, String regionCode,
                                                   String keyword) {
        requireEmployeesAccess();
        DataScopeContext scope = dataScopeService.resolveForCurrentUser();
        if (!canQueryEmployees(scope)) {
            return emptyWeeklyAttendance(isoWeek);
        }
        LocalDate[] range = parseIsoWeekRange(isoWeek);
        LocalDate start = range[0];
        LocalDate end = range[1];
        List<String> regions = intersectRegionFilters(scope, normalizeRegionFilters(regionCodesParam, regionCode));
        if (regions != null && regions.isEmpty()) {
            return emptyWeeklyAttendance(formatIsoWeek(start));
        }
        String kw = keyword != null ? keyword.trim() : null;
        if (kw != null && kw.isEmpty()) {
            kw = null;
        }

        WeeklyAttendanceDTO dto = new WeeklyAttendanceDTO();
        dto.setIsoWeek(formatIsoWeek(start));
        dto.setStartDate(start.format(DATE_FMT));
        dto.setEndDate(end.format(DATE_FMT));
        List<String> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            days.add(d.format(DATE_FMT));
        }
        dto.setDays(days);

        List<Map<String, Object>> rawRows = employeeMapper.selectWeeklyAttendanceRows(
                scope, regions, dto.getStartDate(), dto.getEndDate(), kw);
        Map<Long, WeeklyAttendanceDTO.WeeklyEmployeeRow> rowMap = new LinkedHashMap<>();
        for (Map<String, Object> raw : rawRows) {
            Long employeeId = toLong(raw.get("employeeId"));
            if (employeeId == null) {
                continue;
            }
            WeeklyAttendanceDTO.WeeklyEmployeeRow row = rowMap.computeIfAbsent(employeeId, id -> {
                WeeklyAttendanceDTO.WeeklyEmployeeRow r = new WeeklyAttendanceDTO.WeeklyEmployeeRow();
                r.setEmployeeId(id);
                r.setEmpNo(stringValue(raw.get("empNo")));
                r.setDisplayName(stringValue(raw.get("displayName")));
                r.setRegionCode(stringValue(raw.get("regionCode")));
                r.setAgencyKey(stringValue(raw.get("agencyKey")));
                return r;
            });
            String workDate = stringValue(raw.get("workDate"));
            if (workDate.isEmpty()) {
                continue;
            }
            WeeklyAttendanceDTO.WeeklyCell cell = new WeeklyAttendanceDTO.WeeklyCell();
            cell.setPresent(true);
            cell.setWorkHours(parseWorkHours(raw));
            row.getCells().put(workDate, cell);
        }
        dto.setRows(new ArrayList<>(rowMap.values()));
        return dto;
    }

    private void requireEmployeesAccess() {
        permissionService.requirePermission(userService.getCurrentUser(), PermissionService.EMPLOYEES);
    }

    private static boolean canQueryEmployees(DataScopeContext scope) {
        return scope != null && (scope.isAllUsers() || scope.isEmployeeDimensionFilter());
    }

    /**
     * 将 UI 地区筛选与角色数据范围取交集；null 表示不额外收窄（仍由 Mapper 按 scope 过滤）。
     */
    private static List<String> intersectRegionFilters(DataScopeContext scope, List<String> requested) {
        if (scope == null || scope.isAllUsers()) {
            return requested;
        }
        if (!scope.isEmployeeDimensionFilter()) {
            return Collections.emptyList();
        }
        if (requested == null || requested.isEmpty()) {
            return null;
        }
        Set<String> allowed = new LinkedHashSet<>();
        if (scope.getWorkRegions() != null) {
            allowed.addAll(scope.getWorkRegions());
        }
        if (scope.getCountryMatchTokens() != null) {
            allowed.addAll(scope.getCountryMatchTokens());
        }
        List<String> matched = new ArrayList<>();
        for (String region : requested) {
            if (region == null || region.trim().isEmpty()) {
                continue;
            }
            String norm = EmployeeMatchSupport.normalizeRegionCode(region);
            if (allowed.contains(norm) || allowed.contains(region.trim().toUpperCase(Locale.ROOT))) {
                matched.add(norm);
            }
        }
        return matched;
    }

    private WeeklyAttendanceDTO emptyWeeklyAttendance(String isoWeek) {
        LocalDate[] range;
        try {
            range = parseIsoWeekRange(isoWeek);
        } catch (BusinessException ex) {
            range = parseIsoWeekRange(null);
        }
        WeeklyAttendanceDTO dto = new WeeklyAttendanceDTO();
        dto.setIsoWeek(formatIsoWeek(range[0]));
        dto.setStartDate(range[0].format(DATE_FMT));
        dto.setEndDate(range[1].format(DATE_FMT));
        List<String> days = new ArrayList<>();
        for (LocalDate d = range[0]; !d.isAfter(range[1]); d = d.plusDays(1)) {
            days.add(d.format(DATE_FMT));
        }
        dto.setDays(days);
        dto.setRows(Collections.emptyList());
        return dto;
    }

    @Transactional
    public Map<String, Object> backfillConfirmedTasks() {
        adminAuthService.requireAdmin();
        List<Task> tasks = taskMapper.selectTasksByStatuses(Collections.singletonList("confirmed"));
        int taskCount = 0;
        int recordCount = 0;
        int recordSkipped = 0;
        for (Task task : tasks) {
            if (task.getConfirmedData() == null || task.getConfirmedData().trim().isEmpty()) {
                continue;
            }
            JSONArray rows;
            try {
                rows = JSON.parseArray(task.getConfirmedData());
            } catch (Exception e) {
                log.warn("回填跳过无效 JSON: taskId={}", task.getTaskId());
                continue;
            }
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            String regionCode = task.getPromptCountry();
            if (RecordJsonSupport.isBlank(regionCode)) {
                regionCode = "DEFAULT";
            }
            boolean changed = false;
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (row == null) {
                    continue;
                }
                Map<String, Object> map = row.getInnerMap();
                if (!EmployeeMatchSupport.eligibleForEmployeeAssignment(map)) {
                    continue;
                }
                if (EmployeeMatchSupport.hasSystemEmployeeId(map)) {
                    recordSkipped++;
                    continue;
                }
                assignEmployeeOnConfirm(map, regionCode);
                if (!EmployeeMatchSupport.hasSystemEmployeeId(map)) {
                    log.warn("回填未能为员工绑定系统 ID: taskId={}, row={}", task.getTaskId(), i);
                    continue;
                }
                recordCount++;
                changed = true;
            }
            if (changed) {
                taskMapper.updateTaskConfirmedData(task.getTaskId(), rows.toJSONString());
                taskRecordSyncService.syncFromTaskId(task.getTaskId());
                taskCount++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tasksUpdated", taskCount);
        result.put("recordsAssigned", recordCount);
        result.put("recordsSkipped", recordSkipped);
        return result;
    }

    private String allocateEmpNo(String regionCode) {
        employeeMapper.ensureSerialCounter(regionCode);
        employeeMapper.incrementSerialCounter(regionCode);
        Integer seq = employeeMapper.selectLastInsertId();
        if (seq == null || seq <= 0) {
            throw new BusinessException(500, "employee.serial.allocate_failed");
        }
        return EmployeeMatchSupport.formatEmpNo(regionCode, seq);
    }

    private EmployeeDTO toDto(Employee employee) {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setId(employee.getId());
        dto.setEmpNo(employee.getEmpNo());
        dto.setRegionCode(employee.getRegionCode());
        dto.setAgencyKey(employee.getAgencyKey());
        dto.setMatchName(employee.getMatchName());
        dto.setDisplayName(employee.getDisplayName());
        dto.setStatus(employee.getStatus());
        dto.setFirstCreatedAt(employee.getFirstCreatedAt());
        dto.setLastAttendanceDate(employee.getLastAttendanceDate());
        dto.setLastSeenAt(employee.getLastSeenAt());
        return dto;
    }

    private static LocalDate parseWorkDate(Map<String, Object> record) {
        String raw = firstNonBlank(record, "Date", "DATE", "WorkDate");
        if (RecordJsonSupport.isBlank(raw)) {
            return null;
        }
        String normalized = RecognizedDateNormalizer.normalizeDate(raw);
        if (RecordJsonSupport.isBlank(normalized)) {
            return null;
        }
        try {
            return LocalDate.parse(normalized, DATE_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Double parseWorkHours(Map<String, Object> raw) {
        JSONObject record = new JSONObject();
        record.put("ARRIVEE", raw.get("arrival"));
        record.put("DEPAR", raw.get("departure"));
        record.put("PAUSE", raw.get("pauseMinutes"));
        record.put("SmartMark", raw.get("smartMark"));
        record.put("isDeleted", raw.get("deleted"));
        String hours = TaskRecordExportSupport.formatWorkHours(record);
        if ("-".equals(hours) || RecordJsonSupport.isBlank(hours)) {
            return null;
        }
        try {
            return Double.parseDouble(hours);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate[] parseIsoWeekRange(String isoWeek) {
        if (isoWeek == null || isoWeek.trim().isEmpty()) {
            LocalDate today = LocalDate.now();
            int week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            int year = today.get(IsoFields.WEEK_BASED_YEAR);
            isoWeek = String.format(Locale.ROOT, "%d-W%02d", year, week);
        }
        Matcher matcher = ISO_WEEK.matcher(isoWeek.trim());
        if (!matcher.matches()) {
            throw new BusinessException(400, "employee.invalid_iso_week");
        }
        int year = Integer.parseInt(matcher.group(1));
        int week = Integer.parseInt(matcher.group(2));
        LocalDate monday = LocalDate.of(year, 1, 4)
                .with(WeekFields.ISO.weekBasedYear(), year)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), week)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new LocalDate[]{monday, monday.plusDays(6)};
    }

    private static String formatIsoWeek(LocalDate monday) {
        int week = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = monday.get(IsoFields.WEEK_BASED_YEAR);
        return String.format(Locale.ROOT, "%d-W%02d", year, week);
    }

    private static String normalizeRegionFilter(String regionCode) {
        if (regionCode == null || regionCode.trim().isEmpty()) {
            return null;
        }
        return EmployeeMatchSupport.normalizeRegionCode(regionCode);
    }

    private static List<String> normalizeRegionFilters(String regionCodesParam, String regionCode) {
        java.util.LinkedHashSet<String> regions = new java.util.LinkedHashSet<>();
        if (regionCodesParam != null && !regionCodesParam.trim().isEmpty()) {
            for (String part : regionCodesParam.split(",")) {
                String normalized = normalizeRegionFilter(part);
                if (normalized != null) {
                    regions.add(normalized);
                }
            }
        }
        String single = normalizeRegionFilter(regionCode);
        if (single != null) {
            regions.add(single);
        }
        return regions.isEmpty() ? null : new java.util.ArrayList<>(regions);
    }

    private static String firstNonBlank(Map<String, Object> record, String... keys) {
        for (String key : keys) {
            Object value = record.get(key);
            if (value != null) {
                String s = String.valueOf(value).trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        }
        return "";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
