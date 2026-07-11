package com.yzh666.careerai.modules.jobmatch.controller;

import com.yzh666.careerai.common.annotation.RateLimit;
import com.yzh666.careerai.common.result.Result;
import com.yzh666.careerai.modules.jobmatch.dto.CreateJobMatchRequest;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchReportDTO;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JobMatchController {

    private final JobMatchService jobMatchService;

    @GetMapping("/api/job-matches")
    public Result<List<JobMatchReportDTO>> listReports(@RequestParam(required = false) Long jobId) {
        return Result.success(jobMatchService.listReports(jobId));
    }

    @PostMapping("/api/job-matches")
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 5)
    public Result<JobMatchReportDTO> createReport(@Valid @RequestBody CreateJobMatchRequest request) {
        return Result.success(jobMatchService.createReport(request));
    }
}
