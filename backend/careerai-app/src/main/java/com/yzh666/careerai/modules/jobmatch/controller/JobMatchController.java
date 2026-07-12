package com.yzh666.careerai.modules.jobmatch.controller;

import com.yzh666.careerai.common.annotation.RateLimit;
import com.yzh666.careerai.common.result.Result;
import com.yzh666.careerai.modules.jobmatch.dto.CreateJobMatchRequest;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchReportDTO;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchTaskDTO;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchService;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchTaskService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JobMatchController {

    private final JobMatchService jobMatchService;
    private final JobMatchTaskService jobMatchTaskService;

    @GetMapping("/api/job-matches")
    public Result<List<JobMatchReportDTO>> listReports(@RequestParam(required = false) Long jobId) {
        return Result.success(jobMatchService.listReports(jobId));
    }

    @PostMapping("/api/job-matches")
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 5)
    public Result<JobMatchReportDTO> createReport(@Valid @RequestBody CreateJobMatchRequest request) {
        return Result.success(jobMatchService.createReport(request));
    }

    @PostMapping("/api/job-matches/tasks")
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 5)
    public Result<JobMatchTaskDTO> createReportTask(@Valid @RequestBody CreateJobMatchRequest request) {
        return Result.success(jobMatchTaskService.createTask(request));
    }

    @GetMapping("/api/job-matches/tasks/{taskId}")
    public Result<JobMatchTaskDTO> getReportTask(@PathVariable Long taskId) {
        return Result.success(jobMatchTaskService.getTask(taskId));
    }
}
