package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.dto.request.RoleDataScopeUpdateRequest;
import com.attendance.dto.response.DimensionOptionDTO;
import com.attendance.dto.response.RoleDataScopeDTO;
import com.attendance.security.DataScopeContext;
import com.attendance.service.DataScopeService;
import com.attendance.service.RoleDataScopeService;
import com.attendance.service.UserService;
import com.attendance.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/data-scope")
@Validated
public class RoleDataScopeController {

    @Autowired
    private RoleDataScopeService roleDataScopeService;

    @Autowired
    private DataScopeService dataScopeService;

    @Autowired
    private UserService userService;

    @GetMapping("/roles")
    public Result<Map<String, RoleDataScopeDTO>> listRoleScopes() {
        return Result.success(roleDataScopeService.getAllRoleScopes());
    }

    @PutMapping("/roles/{role}")
    public Result<RoleDataScopeDTO> updateRoleScope(
            @PathVariable String role,
            @RequestBody RoleDataScopeUpdateRequest request) {
        return Result.success(roleDataScopeService.updateRoleScope(role, request));
    }

    @GetMapping("/dimension-options")
    public Result<Map<String, List<DimensionOptionDTO>>> dimensionOptions() {
        return Result.success(roleDataScopeService.getDimensionOptions());
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> myDataScope() {
        User user = userService.getCurrentUser();
        DataScopeContext ctx = dataScopeService.resolveForUserId(user.getId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", user.getRole());
        body.put("allUsers", ctx.isAllUsers());
        body.put("ownerUserIds", ctx.getOwnerUserIds());
        body.put("countries", ctx.getCountries());
        body.put("warehouses", ctx.getWarehouses());
        body.put("agencies", ctx.getAgencies());
        return Result.success(body);
    }
}
