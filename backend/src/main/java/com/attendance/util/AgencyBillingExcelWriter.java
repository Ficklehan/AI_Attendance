package com.attendance.util;

import com.attendance.dto.response.AgencyBillingDetailDTO;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** 欧洲线下 Relevé d'heures 版式 Excel 导出 */
public final class AgencyBillingExcelWriter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private AgencyBillingExcelWriter() {
    }

    public static long write(Path file, List<AgencyBillingDetailDTO> details, String locale) throws IOException {
        String resolvedLocale = ExportLocaleSupport.resolveLocale(locale);
        try (ExcelExportHelper.ExcelSheetWriter writer = ExcelExportHelper.open(file)) {
            if (details == null || details.isEmpty()) {
                writer.writeHeader(ExportLocaleSupport.text(resolvedLocale, "agencyBilling.noData"));
                writer.writeRow(ExportLocaleSupport.text(resolvedLocale, "agencyBilling.noData"));
                return writer.getDataRowCount();
            }
            long totalRows = 0;
            for (int i = 0; i < details.size(); i++) {
                if (i > 0) {
                    writer.writeBlankRow();
                }
                totalRows += writeReleve(writer, details.get(i), resolvedLocale);
            }
            return totalRows;
        }
    }

    private static long writeReleve(ExcelExportHelper.ExcelSheetWriter writer,
                                    AgencyBillingDetailDTO detail, String locale) {
        writer.writeRow(
                ExportLocaleSupport.text(locale, "agencyBilling.warehouse"),
                ExcelExportHelper.cell(detail.getWarehouseLabel()),
                ExportLocaleSupport.text(locale, "agencyBilling.period"),
                periodLabel(detail));
        writer.writeRow(
                ExportLocaleSupport.text(locale, "agencyBilling.agency"),
                ExcelExportHelper.cell(detail.getAgencyLabel()),
                ExportLocaleSupport.text(locale, "agencyBilling.country"),
                ExcelExportHelper.cell(detail.getCountryLabel()));
        writer.writeBlankRow();

        List<String> days = detail.getDays();
        String[] headers = buildDayHeaders(days, locale);
        String[] headerRow = new String[2 + days.size() + 1];
        headerRow[0] = ExportLocaleSupport.text(locale, "agencyBilling.colNo");
        headerRow[1] = ExportLocaleSupport.text(locale, "agencyBilling.colName");
        for (int i = 0; i < headers.length; i++) {
            headerRow[2 + i] = headers[i];
        }
        headerRow[headerRow.length - 1] = ExportLocaleSupport.text(locale, "agencyBilling.totalHours");
        writer.writeHeader(headerRow);

        long rows = 0;
        for (AgencyBillingDetailDTO.EmployeeRow employee : detail.getRows()) {
            String[] row = new String[headerRow.length];
            row[0] = ExcelExportHelper.cell(blankToDash(employee.getEmpNo()));
            row[1] = ExcelExportHelper.cell(employee.getDisplayName());
            int col = 2;
            for (String day : days) {
                AgencyBillingDetailDTO.DayCell cell = employee.getCells().get(day);
                if (cell != null && cell.isPresent()) {
                    row[col] = cell.getWorkHours() != null
                            ? formatHours(cell.getWorkHours())
                            : "✓";
                } else {
                    row[col] = "—";
                }
                col++;
            }
            row[col] = formatHours(employee.getTotalHours());
            writer.writeRow(row);
            rows++;
        }
        writer.writeBlankRow();
        writer.writeTotalRow(
                ExportLocaleSupport.text(locale, "agencyBilling.totalLabel"),
                detail.getTotalHeadcount() + " "
                        + ExportLocaleSupport.text(locale, "agencyBilling.workersUnit"),
                "",
                ExportLocaleSupport.text(locale, "agencyBilling.totalHours"),
                formatHours(detail.getTotalHours()));
        return rows;
    }

    private static String[] buildDayHeaders(List<String> days, String locale) {
        String[] headers = new String[days.size()];
        for (int i = 0; i < days.size(); i++) {
            String day = days.get(i);
            try {
                LocalDate date = LocalDate.parse(day, DATE_FMT);
                headers[i] = ExportLocaleSupport.dayHeader(locale, date.getDayOfWeek().getValue(), day.substring(5));
            } catch (Exception e) {
                headers[i] = day;
            }
        }
        return headers;
    }

    private static String periodLabel(AgencyBillingDetailDTO detail) {
        return detail.getStartDate() + " ~ " + detail.getEndDate();
    }

    private static String formatHours(Double value) {
        if (value == null) {
            return "—";
        }
        double rounded = Math.round(value * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.001) {
            return String.valueOf((long) Math.rint(rounded));
        }
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static String blankToDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }
}
