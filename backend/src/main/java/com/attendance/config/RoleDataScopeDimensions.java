package com.attendance.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 角色数据范围维度 — 单一来源，供 DataScopeService / RoleDataScopeService 共用 */
public final class RoleDataScopeDimensions {

    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            "owner_user", "country", "warehouse", "agency", "work_region"));

    private RoleDataScopeDimensions() {
    }
}
