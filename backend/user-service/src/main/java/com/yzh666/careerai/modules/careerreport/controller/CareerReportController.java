package com.yzh666.careerai.modules.careerreport.controller;

import com.yzh666.careerai.common.result.Result;
import com.yzh666.careerai.modules.careerreport.dto.CareerReportDTO;
import com.yzh666.careerai.modules.careerreport.service.CareerReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CareerReportController {

    private final CareerReportService careerReportService;

    @GetMapping("/api/career-reports/{matchReportId}")
    public Result<CareerReportDTO> getReport(@PathVariable Long matchReportId) {
        return Result.success(careerReportService.getReport(matchReportId));
    }

    @GetMapping("/api/career-reports/{matchReportId}/export")
    public ResponseEntity<byte[]> exportPdf(@PathVariable Long matchReportId) {
        var result = careerReportService.exportPdf(matchReportId);
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(result.filename(), java.nio.charset.StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .contentType(MediaType.APPLICATION_PDF)
            .body(result.pdfBytes());
    }
}
