package com.yzh666.careerai.modules.resumeplan.controller;

import com.yzh666.careerai.common.result.Result;
import com.yzh666.careerai.modules.resumeplan.dto.ResumeImprovementPlanDTO;
import com.yzh666.careerai.modules.resumeplan.service.ResumeImprovementPlanService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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

}
