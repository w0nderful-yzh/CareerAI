package com.yzh666.careerai.modules.job.controller;

import com.yzh666.careerai.common.annotation.RateLimit;
import com.yzh666.careerai.common.result.Result;
import com.yzh666.careerai.modules.job.dto.CreateJobRequest;
import com.yzh666.careerai.modules.job.dto.JobDTO;
import com.yzh666.careerai.modules.job.dto.JobParseRequest;
import com.yzh666.careerai.modules.job.dto.JobParseResponse;
import com.yzh666.careerai.modules.job.dto.UpdateJobStatusRequest;
import com.yzh666.careerai.modules.job.model.JobStatus;
import com.yzh666.careerai.modules.job.service.JobService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @GetMapping("/api/jobs")
    public Result<List<JobDTO>> listJobs(@RequestParam(required = false) JobStatus status) {
        return Result.success(jobService.listJobs(status));
    }

    @GetMapping("/api/jobs/{id}")
    public Result<JobDTO> getJob(@PathVariable Long id) {
        return Result.success(jobService.getJob(id));
    }

    @PostMapping("/api/jobs/parse-jd")
    @RateLimit(dimension = RateLimit.Dimension.IP, count = 5)
    public Result<JobParseResponse> parseJd(@Valid @RequestBody JobParseRequest request) {
        return Result.success(jobService.parseJd(request.jdText()));
    }

    @PostMapping("/api/jobs")
    public Result<JobDTO> createJob(@Valid @RequestBody CreateJobRequest request) {
        return Result.success(jobService.createJob(request));
    }

    @PatchMapping("/api/jobs/{id}/status")
    public Result<JobDTO> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UpdateJobStatusRequest request
    ) {
        return Result.success(jobService.updateStatus(id, request.status()));
    }

    @DeleteMapping("/api/jobs/{id}")
    public Result<Void> deleteJob(@PathVariable Long id) {
        jobService.deleteJob(id);
        return Result.success(null);
    }
}
