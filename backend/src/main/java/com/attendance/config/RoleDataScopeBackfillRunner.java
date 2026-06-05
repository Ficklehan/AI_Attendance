package com.attendance.config;

import com.attendance.entity.SystemRole;
import com.attendance.mapper.RoleDataScopeMapper;
import com.attendance.mapper.SystemRoleMapper;
import com.attendance.security.DataScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(18)
public class RoleDataScopeBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleDataScopeBackfillRunner.class);

    private final RoleDataScopeMapper roleDataScopeMapper;
    private final SystemRoleMapper systemRoleMapper;

    public RoleDataScopeBackfillRunner(RoleDataScopeMapper roleDataScopeMapper,
                                       SystemRoleMapper systemRoleMapper) {
        this.roleDataScopeMapper = roleDataScopeMapper;
        this.systemRoleMapper = systemRoleMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            backfillRole("admin", "all", null);
            backfillRole("user", "restricted", DataScopeContext.SELF_TOKEN);
            for (SystemRole role : systemRoleMapper.selectAll()) {
                if ("admin".equals(role.getRoleKey()) || "user".equals(role.getRoleKey())) {
                    continue;
                }
                backfillRole(role.getRoleKey(), "restricted", DataScopeContext.SELF_TOKEN);
            }
            log.info("角色数据权限默认配置已就绪");
        } catch (Exception e) {
            log.warn("角色数据权限回填失败: {}", e.getMessage());
        }
    }

    private void backfillRole(String role, String scopeType, String selfRule) {
        String existingScopeType = roleDataScopeMapper.selectScopeType(role);
        if (existingScopeType == null) {
            roleDataScopeMapper.upsertScopeType(role, scopeType);
            if (selfRule != null) {
                roleDataScopeMapper.insertRule(role, "owner_user", selfRule);
            }
            log.info("回填角色数据权限: role={}, scopeType={}", role, scopeType);
            return;
        }
        if (selfRule != null && "restricted".equalsIgnoreCase(existingScopeType)) {
            ensureSelfOwnerRule(role, selfRule);
        }
    }

    private void ensureSelfOwnerRule(String role, String selfRule) {
        boolean hasOwnerRule = false;
        for (java.util.Map<String, String> row : roleDataScopeMapper.selectRulesByRole(role)) {
            if (row != null && "owner_user".equals(row.get("dimension"))) {
                hasOwnerRule = true;
                break;
            }
        }
        if (!hasOwnerRule) {
            roleDataScopeMapper.insertRule(role, "owner_user", selfRule);
            log.info("补全角色 owner_user 规则: role={}, value={}", role, selfRule);
        }
    }
}
