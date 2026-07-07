package com.attendance.service;

import com.attendance.config.CountryCatalog;
import com.attendance.dto.internal.AgencyBillingBundle;
import com.attendance.dto.internal.AgencyBillingRow;
import com.attendance.dto.request.AgencyBillingQuery;
import com.attendance.mapper.TaskRecordMapper;
import com.attendance.security.DataScopeContext;
import com.attendance.util.CountryResolver;
import com.attendance.util.RecordJsonSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgencyBillingCacheService {

    private static final long CACHE_TTL_MS = 3 * 60 * 1000L;
    private static final int MAX_CACHE_ENTRIES = 64;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    private TaskRecordMapper taskRecordMapper;

    public AgencyBillingBundle loadBundle(AgencyBillingQuery query, DataScopeContext scope) {
        DateRange range = parseRange(query);
        String cacheKey = buildCacheKey(query, scope, range);
        long now = System.currentTimeMillis();
        CacheEntry hit = cache.get(cacheKey);
        if (hit != null && hit.expiresAt > now) {
            return hit.bundle;
        }
        List<String> regions = parseRegionCodeList(query.getRegionCodes());
        List<String> warehouses = parseCodeList(query.getWarehouseKeys());
        List<AgencyBillingRow> rows = taskRecordMapper.selectAgencyBillingSlimRows(
                scope, range.start, range.end, regions, warehouses);
        AgencyBillingBundle bundle = AgencyBillingAggregator.buildBundle(
                range.start, range.end, range.dayList, rows);
        putCache(cacheKey, bundle, now);
        return bundle;
    }

    public void invalidate(AgencyBillingQuery query, DataScopeContext scope) {
        if (query == null) {
            return;
        }
        try {
            DateRange range = parseRange(query);
            cache.remove(buildCacheKey(query, scope, range));
        } catch (RuntimeException ignored) {
            // ignore invalid query
        }
    }

    private void putCache(String key, AgencyBillingBundle bundle, long now) {
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.entrySet().removeIf(e -> e.getValue().expiresAt <= now);
        }
        cache.put(key, new CacheEntry(bundle, now + CACHE_TTL_MS));
    }

    private static String buildCacheKey(AgencyBillingQuery query, DataScopeContext scope, DateRange range) {
        StringBuilder sb = new StringBuilder(160);
        sb.append(scope != null && scope.isAllUsers() ? "all" : "scoped");
        if (scope != null && scope.getViewerUserId() != null) {
            sb.append('|').append(scope.getViewerUserId());
        }
        if (scope != null && scope.getOwnerUserIds() != null) {
            sb.append('|').append(String.join(",", scope.getOwnerUserIds()));
        }
        if (scope != null && scope.getCountries() != null) {
            sb.append('|').append(String.join(",", scope.getCountries()));
        }
        if (scope != null && scope.getWarehouses() != null) {
            sb.append('|').append(String.join(",", scope.getWarehouses()));
        }
        if (scope != null && scope.getAgencies() != null) {
            sb.append('|').append(String.join(",", scope.getAgencies()));
        }
        sb.append('|').append(range.start).append('|').append(range.end);
        sb.append('|').append(safe(query.getRegionCodes()));
        sb.append('|').append(safe(query.getWarehouseKeys()));
        return sb.toString();
    }

    private static List<String> parseRegionCodeList(String raw) {
        List<String> codes = parseCatalogCodeList(raw);
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        return CountryCatalog.expandMatchTokens(codes);
    }

    private static List<String> parseCatalogCodeList(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ("default".equalsIgnoreCase(trimmed)) {
                set.add("DEFAULT");
            } else {
                String resolved = CountryCatalog.resolveCountryCodeFromPays(trimmed);
                set.add(resolved != null ? resolved : CountryResolver.normalize(trimmed));
            }
        }
        return set.isEmpty() ? null : new ArrayList<>(set);
    }

    private static List<String> parseCodeList(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if ("default".equalsIgnoreCase(trimmed)) {
                set.add("DEFAULT");
            } else {
                set.add(CountryResolver.normalize(trimmed));
            }
        }
        return set.isEmpty() ? null : new ArrayList<>(set);
    }

    private static DateRange parseRange(AgencyBillingQuery query) {
        if (query == null || RecordJsonSupport.isBlank(query.getStartDate()) || RecordJsonSupport.isBlank(query.getEndDate())) {
            throw new IllegalArgumentException("agency_billing.date_range_required");
        }
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(query.getStartDate().trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            end = LocalDate.parse(query.getEndDate().trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("agency_billing.invalid_date");
        }
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("agency_billing.invalid_date_range");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > 62) {
            throw new IllegalArgumentException("agency_billing.range_too_long");
        }
        List<String> dayList = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            dayList.add(d.format(DateTimeFormatter.ISO_LOCAL_DATE));
        }
        return new DateRange(start.format(DateTimeFormatter.ISO_LOCAL_DATE),
                end.format(DateTimeFormatter.ISO_LOCAL_DATE), dayList);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class CacheEntry {
        private final AgencyBillingBundle bundle;
        private final long expiresAt;

        private CacheEntry(AgencyBillingBundle bundle, long expiresAt) {
            this.bundle = bundle;
            this.expiresAt = expiresAt;
        }
    }

    private static final class DateRange {
        private final String start;
        private final String end;
        private final List<String> dayList;

        private DateRange(String start, String end, List<String> dayList) {
            this.start = start;
            this.end = end;
            this.dayList = dayList;
        }
    }
}
