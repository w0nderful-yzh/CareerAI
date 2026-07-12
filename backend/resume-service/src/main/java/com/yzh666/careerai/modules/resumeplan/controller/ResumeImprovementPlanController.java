package com.yzh666.careerai.modules.resumeplan.controller;

import com.yzh666.careerai.common.annotation.RateLimit;
import com.yzh666.careerai.common.result.Result;
import com.yzh666.careerai.modules.resumeplan.dto.CreateResumeImprovementPlanRequest;
import com.yzh666.careerai.modules.resumeplan.dto.ResumeImprovementPlanDTO;
import com.yzh666.careerai.modules.resumeplan.service.ResumeImprovementPlanService;
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
public class ResumeImprovementPlanController {

    private final ResumeImprovementPlanService planService;

    @GetMapping("/api/resume-improvement-plans")
    public Result<List<ResumeImprovementPlanDTO>> listPlans(@RequestParam(required = false) Long matchReportId) {
        return Result.success(planService.listPlans(matchReportId));
    }

    @GetMapping("/api/resume-improvement-plans/{id}")
    public Result<ResumeImprovementPlanDTO> getPlan(@PathVariable Long id) {
        return Result.success(planService.getPlan(id));
    }

    @PostMapping("/api/resume-improvement-plans")
    @RateLimit(dimension = RateLimit.Dimension.USER, count = 5)
    public Result<ResumeImprovementPlanDTO> createPlan(
        @Valid @RequestBody CreateResumeImprovementPlanRequest request
    ) {
        return Result.success(planService.createPlan(request));
    }
}
