package com.attendance.controller;

import com.attendance.common.Result;
import com.attendance.dto.request.SignInSheetPrintJobRequest;
import com.attendance.dto.response.SignInSheetPrintDayDTO;
import com.attendance.dto.response.SignInSheetPrintDayDetailDTO;
import com.attendance.dto.response.SignInSheetPrintJobDTO;
import com.attendance.service.SignInSheetPrintService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/print-sheets")
@Validated
public class SignInSheetPrintController {

    @Autowired
    private SignInSheetPrintService signInSheetPrintService;

    @PostMapping("/jobs")
    public Result<SignInSheetPrintJobDTO> createJob(@Valid @RequestBody SignInSheetPrintJobRequest request) {
        return Result.success(signInSheetPrintService.createJob(request));
    }

    @GetMapping("/days")
    public Result<List<SignInSheetPrintDayDTO>> listDays(
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Result.success(signInSheetPrintService.listDays(countryCode, from, to));
    }

    @GetMapping("/days/detail")
    public Result<SignInSheetPrintDayDetailDTO> dayDetail(
            @RequestParam String countryCode,
            @RequestParam String workDate) {
        return Result.success(signInSheetPrintService.getDayDetail(countryCode, workDate));
    }
}
