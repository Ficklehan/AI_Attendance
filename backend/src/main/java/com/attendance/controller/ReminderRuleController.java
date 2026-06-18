package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.dto.request.ReminderRuleRequest;
import com.attendance.dto.response.ReminderRuleDTO;
import com.attendance.service.ReminderRuleService;
import com.attendance.service.ReminderLocaleSupport;
import com.attendance.service.ReminderSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reminder-rules")
@Validated
public class ReminderRuleController {

    @Autowired
    private ReminderRuleService reminderRuleService;

    @GetMapping
    public Result<List<ReminderRuleDTO>> list() {
        return Result.success(reminderRuleService.listRules());
    }

    @GetMapping("/{id}")
    public Result<ReminderRuleDTO> get(@PathVariable String id) {
        return Result.success(reminderRuleService.getRule(id));
    }

    @PostMapping
    public Result<ReminderRuleDTO> create(@Valid @RequestBody ReminderRuleRequest request) {
        return Result.success(reminderRuleService.createRule(request));
    }

    @PostMapping("/{id}/update")
    public Result<ReminderRuleDTO> update(@PathVariable String id,
                                          @Valid @RequestBody ReminderRuleRequest request) {
        return Result.success(reminderRuleService.updateRule(id, request));
    }

    @PostMapping("/{id}/enabled")
    public Result<Void> setEnabled(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        applyEnabled(id, body);
        return Result.success(null);
    }

    private void applyEnabled(String id, Map<String, Boolean> body) {
        Boolean enabled = body != null ? body.get("enabled") : null;
        if (enabled == null) {
            enabled = body != null ? body.get("value") : null;
        }
        reminderRuleService.setEnabled(id, Boolean.TRUE.equals(enabled));
    }

    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable String id) {
        reminderRuleService.deleteRule(id);
        return Result.success(null);
    }

    @GetMapping("/default-template")
    public Result<Map<String, Object>> defaultTemplate() {
        Map<String, Object> body = new HashMap<>();
        body.put("template", ReminderLocaleSupport.defaultOperatorTemplate(ReminderLocaleSupport.DEFAULT_LOCALE));
        body.put("supervisorTemplate",
                ReminderLocaleSupport.defaultSupervisorTemplate(ReminderLocaleSupport.DEFAULT_LOCALE));
        body.put("operatorTemplates", ReminderLocaleSupport.allDefaultOperatorTemplates());
        body.put("supervisorTemplates", ReminderLocaleSupport.allDefaultSupervisorTemplates());
        body.put("supportedLocales", ReminderLocaleSupport.supportedLocales());
        body.put("countryLocale", ReminderLocaleSupport.countryLocaleMap());
        return Result.success(body);
    }
}
