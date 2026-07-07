package com.attendance.service;

import com.attendance.dto.internal.AgencyBillingBundle;
import com.attendance.dto.internal.AgencyBillingRow;
import com.attendance.dto.response.AgencyBillingDetailDTO;
import com.attendance.dto.response.AgencyBillingSummaryDTO;
import com.attendance.util.AgencyBillingHoursSupport;
import com.attendance.util.BillingDisplaySupport;
import com.attendance.util.RecordJsonSupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class AgencyBillingAggregator {

    private AgencyBillingAggregator() {
    }

    static AgencyBillingBundle buildBundle(String startDate, String endDate, List<String> dayList,
                                           List<AgencyBillingRow> rawRows) {
        Map<String, BlockAccumulator> blockMap = new LinkedHashMap<>();
        Map<String, List<AgencyBillingRow>> rowsByBlock = new LinkedHashMap<>();

        for (AgencyBillingRow row : rawRows) {
            if (!AgencyBillingHoursSupport.isBillable(row)) {
                continue;
            }
            String key = AgencyBillingBundle.blockKey(row.getAgencyKey(), row.getWarehouseKey(), row.getCountryKey());
            blockMap.computeIfAbsent(key, k -> new BlockAccumulator(row)).accept(row);
            rowsByBlock.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }

        AgencyBillingSummaryDTO summary = new AgencyBillingSummaryDTO();
        summary.setStartDate(startDate);
        summary.setEndDate(endDate);
        List<AgencyBillingSummaryDTO.AgencyBlock> blocks = new ArrayList<>();
        for (BlockAccumulator acc : blockMap.values()) {
            blocks.add(acc.toSummary());
        }
        blocks.sort((a, b) -> {
            int c = safe(a.getAgencyLabel()).compareToIgnoreCase(safe(b.getAgencyLabel()));
            if (c != 0) {
                return c;
            }
            c = safe(a.getWarehouseLabel()).compareToIgnoreCase(safe(b.getWarehouseLabel()));
            if (c != 0) {
                return c;
            }
            return safe(a.getCountryLabel()).compareToIgnoreCase(safe(b.getCountryLabel()));
        });
        summary.setBlocks(blocks);
        return new AgencyBillingBundle(startDate, endDate, dayList, summary, rowsByBlock);
    }

    static List<AgencyBillingDetailDTO> allDetails(AgencyBillingBundle bundle) {
        List<AgencyBillingDetailDTO> details = new ArrayList<>();
        for (AgencyBillingSummaryDTO.AgencyBlock block : bundle.getSummary().getBlocks()) {
            details.add(buildDetail(bundle, block));
        }
        return details;
    }

    static AgencyBillingDetailDTO buildDetail(AgencyBillingBundle bundle,
                                              AgencyBillingSummaryDTO.AgencyBlock block) {
        return buildDetail(
                bundle.getStartDate(), bundle.getEndDate(), bundle.getDayList(), block,
                bundle.rowsForBlock(block.getAgencyKey(), block.getWarehouseKey(), block.getCountryKey()));
    }

    static AgencyBillingDetailDTO buildDetail(AgencyBillingBundle bundle,
                                              String agencyKey, String warehouseKey, String countryKey) {
        for (AgencyBillingSummaryDTO.AgencyBlock block : bundle.getSummary().getBlocks()) {
            if (matchesBlock(block, agencyKey, warehouseKey, countryKey)) {
                return buildDetail(bundle, block);
            }
        }
        return buildDetail(
                bundle.getStartDate(), bundle.getEndDate(), bundle.getDayList(),
                agencyKey, warehouseKey, countryKey,
                bundle.rowsForBlock(agencyKey, warehouseKey, countryKey));
    }

    private static boolean matchesBlock(AgencyBillingSummaryDTO.AgencyBlock block,
                                        String agencyKey, String warehouseKey, String countryKey) {
        return safe(block.getAgencyKey()).equalsIgnoreCase(safe(agencyKey))
                && safe(block.getWarehouseKey()).equalsIgnoreCase(safe(warehouseKey))
                && safe(block.getCountryKey()).equalsIgnoreCase(safe(countryKey));
    }

    static AgencyBillingDetailDTO buildDetail(String startDate, String endDate, List<String> dayList,
                                              AgencyBillingSummaryDTO.AgencyBlock block,
                                              List<AgencyBillingRow> rows) {
        return buildDetail(startDate, endDate, dayList,
                block.getAgencyKey(), block.getWarehouseKey(), block.getCountryKey(), rows);
    }

    static AgencyBillingDetailDTO buildDetail(String startDate, String endDate, List<String> dayList,
                                              String agencyKey, String warehouseKey, String countryKey,
                                              List<AgencyBillingRow> rows) {
        AgencyBillingDetailDTO dto = new AgencyBillingDetailDTO();
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);
        dto.setAgencyKey(agencyKey);
        dto.setWarehouseKey(warehouseKey != null ? warehouseKey : "");
        dto.setCountryKey(countryKey != null ? countryKey : "");
        dto.setDays(dayList);

        String agencyLabel = "";
        String warehouseLabel = "";
        String countryLabel = "";
        Map<String, EmployeeAccumulator> employees = new TreeMap<>();

        for (AgencyBillingRow row : rows) {
            if (!agencyKey.equalsIgnoreCase(safe(row.getAgencyKey()))) {
                continue;
            }
            if (warehouseKey != null && !warehouseKey.isEmpty()
                    && !warehouseKey.equalsIgnoreCase(safe(row.getWarehouseKey()))) {
                continue;
            }
            if (countryKey != null && !countryKey.isEmpty()
                    && !countryKey.equalsIgnoreCase(safe(row.getCountryKey()))) {
                continue;
            }
            agencyLabel = BillingDisplaySupport.mergeTextLabel(agencyLabel, agencyKey, row.getAgency(), row.getAgencyKey());
            warehouseLabel = BillingDisplaySupport.mergeTextLabel(warehouseLabel, warehouseKey, row.getWarehouse(), row.getWarehouseKey());
            countryLabel = BillingDisplaySupport.mergeCountryLabel(countryLabel, countryKey, row.getCountry(), row.getCountryKey());
            String empKey = employeeKey(row);
            employees.computeIfAbsent(empKey, k -> new EmployeeAccumulator(row)).accept(row);
        }

        dto.setAgencyLabel(BillingDisplaySupport.resolveTextLabel(agencyLabel, agencyKey));
        dto.setWarehouseLabel(BillingDisplaySupport.resolveTextLabel(warehouseLabel, warehouseKey));
        dto.setCountryLabel(BillingDisplaySupport.resolveCountryLabel(countryLabel, countryKey));

        double totalHours = 0;
        List<AgencyBillingDetailDTO.EmployeeRow> employeeRows = new ArrayList<>();
        int seq = 0;
        for (EmployeeAccumulator emp : employees.values()) {
            AgencyBillingDetailDTO.EmployeeRow row = emp.toRow(dayList);
            row.setEmployeeKey(buildUniqueEmployeeKey(row, seq++));
            employeeRows.add(row);
            totalHours += row.getTotalHours();
        }
        dto.setRows(employeeRows);
        dto.setTotalHeadcount(employeeRows.size());
        dto.setTotalHours(roundHours(totalHours));
        return dto;
    }

    private static String employeeKey(AgencyBillingRow row) {
        if (!RecordJsonSupport.isBlank(row.getEmployeeNo())) {
            return "no:" + row.getEmployeeNo().trim().toUpperCase(Locale.ROOT);
        }
        return "name:" + safe(row.getAgencyKey()) + ":" + safe(row.getEmpName()).toUpperCase(Locale.ROOT);
    }

    private static String buildUniqueEmployeeKey(AgencyBillingDetailDTO.EmployeeRow row, int seq) {
        String base;
        if (!RecordJsonSupport.isBlank(row.getEmpNo())) {
            base = "no:" + row.getEmpNo().trim().toUpperCase(Locale.ROOT);
        } else {
            base = "name:" + safe(row.getDisplayName()).toUpperCase(Locale.ROOT);
        }
        return base + "#" + seq;
    }

    private static String pickLabel(String current, String candidate) {
        if (!RecordJsonSupport.isBlank(candidate)) {
            return candidate.trim();
        }
        return current != null ? current : "";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static double roundHours(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static final class BlockAccumulator {
        private String agencyKey;
        private String agencyLabel;
        private String warehouseKey;
        private String warehouseLabel;
        private String countryKey;
        private String countryLabel;
        private final Set<String> employees = new LinkedHashSet<>();
        private final Set<String> employeeDays = new LinkedHashSet<>();
        private double totalHours;

        private BlockAccumulator(AgencyBillingRow seed) {
            this.agencyKey = safe(seed.getAgencyKey());
            this.agencyLabel = BillingDisplaySupport.resolveTextLabel(seed.getAgency(), seed.getAgencyKey());
            this.warehouseKey = safe(seed.getWarehouseKey());
            this.warehouseLabel = BillingDisplaySupport.resolveTextLabel(seed.getWarehouse(), seed.getWarehouseKey());
            this.countryKey = safe(seed.getCountryKey());
            this.countryLabel = BillingDisplaySupport.resolveCountryLabel(seed.getCountry(), seed.getCountryKey());
        }

        private void accept(AgencyBillingRow row) {
            agencyLabel = BillingDisplaySupport.mergeTextLabel(agencyLabel, agencyKey, row.getAgency(), row.getAgencyKey());
            warehouseLabel = BillingDisplaySupport.mergeTextLabel(warehouseLabel, warehouseKey, row.getWarehouse(), row.getWarehouseKey());
            countryLabel = BillingDisplaySupport.mergeCountryLabel(countryLabel, countryKey, row.getCountry(), row.getCountryKey());
            String emp = employeeKey(row);
            employees.add(emp);
            employeeDays.add(emp + "|" + safe(row.getWorkDate()));
            Double hours = AgencyBillingHoursSupport.parseWorkHours(row);
            if (hours != null) {
                totalHours += hours;
            }
        }

        private AgencyBillingSummaryDTO.AgencyBlock toSummary() {
            AgencyBillingSummaryDTO.AgencyBlock block = new AgencyBillingSummaryDTO.AgencyBlock();
            block.setAgencyKey(agencyKey);
            block.setAgencyLabel(BillingDisplaySupport.resolveTextLabel(agencyLabel, agencyKey));
            block.setWarehouseKey(warehouseKey);
            block.setWarehouseLabel(BillingDisplaySupport.resolveTextLabel(warehouseLabel, warehouseKey));
            block.setCountryKey(countryKey);
            block.setCountryLabel(BillingDisplaySupport.resolveCountryLabel(countryLabel, countryKey));
            block.setHeadcount(employees.size());
            block.setAttendanceDays(employeeDays.size());
            block.setTotalHours(roundHours(totalHours));
            return block;
        }
    }

    private static final class EmployeeAccumulator {
        private String empNo;
        private String displayName;
        private final Map<String, Double> hoursByDay = new LinkedHashMap<>();
        private final List<AgencyBillingDetailDTO.LineItem> lines = new ArrayList<>();

        private EmployeeAccumulator(AgencyBillingRow seed) {
            this.empNo = safe(seed.getEmployeeNo());
            this.displayName = pickLabel("", seed.getEmpName());
        }

        private void accept(AgencyBillingRow row) {
            displayName = pickLabel(displayName, row.getEmpName());
            if (RecordJsonSupport.isBlank(empNo) && !RecordJsonSupport.isBlank(row.getEmployeeNo())) {
                empNo = row.getEmployeeNo().trim();
            }
            String day = safe(row.getWorkDate());
            Double hours = AgencyBillingHoursSupport.parseWorkHours(row);
            if (hours != null) {
                hoursByDay.merge(day, hours, Double::sum);
            } else if (!hoursByDay.containsKey(day)) {
                hoursByDay.put(day, null);
            }
            AgencyBillingDetailDTO.LineItem line = new AgencyBillingDetailDTO.LineItem();
            line.setWorkDate(day);
            line.setShift(safe(row.getShift()));
            line.setArrival(safe(row.getArrival()));
            line.setDeparture(safe(row.getDeparture()));
            line.setPauseMinutes(safe(row.getPauseMinutes()));
            line.setWorkHours(hours);
            line.setObservations(safe(row.getObservations()));
            lines.add(line);
        }

        private AgencyBillingDetailDTO.EmployeeRow toRow(List<String> days) {
            AgencyBillingDetailDTO.EmployeeRow row = new AgencyBillingDetailDTO.EmployeeRow();
            row.setEmployeeKey(empNo != null && !empNo.isEmpty() ? empNo : displayName);
            row.setEmpNo(empNo);
            row.setDisplayName(displayName);
            double total = 0;
            for (String day : days) {
                AgencyBillingDetailDTO.DayCell cell = new AgencyBillingDetailDTO.DayCell();
                if (hoursByDay.containsKey(day)) {
                    cell.setPresent(true);
                    Double hours = hoursByDay.get(day);
                    cell.setWorkHours(hours);
                    if (hours != null) {
                        total += hours;
                    }
                }
                row.getCells().put(day, cell);
            }
            row.setTotalHours(roundHours(total));
            row.setLines(lines);
            return row;
        }
    }
}
