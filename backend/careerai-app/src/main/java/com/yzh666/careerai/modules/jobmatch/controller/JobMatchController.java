package com.yzh666.careerai.modules.jobmatch.controller;

import com.yzh666.careerai.common.result.Result;
import com.yzh666.careerai.modules.jobmatch.dto.JobMatchReportDTO;
import com.yzh666.careerai.modules.jobmatch.service.JobMatchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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

}
