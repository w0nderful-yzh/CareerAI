package com.yzh666.careerai.modules.interview.controller;

import com.yzh666.careerai.common.result.Result;
import com.yzh666.careerai.modules.interview.model.InterviewDetailDTO;
import com.yzh666.careerai.modules.interview.model.InterviewClosureDTO;
import com.yzh666.careerai.modules.interview.model.InterviewSessionDTO;
import com.yzh666.careerai.modules.interview.model.SessionListItemDTO;
import com.yzh666.careerai.modules.interview.service.InterviewHistoryService;
import com.yzh666.careerai.modules.interview.service.InterviewPersistenceService;
import com.yzh666.careerai.modules.interview.service.InterviewSessionService;
import com.yzh666.careerai.modules.interview.service.InterviewClosureService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 面试控制器
 * 提供模拟面试相关的API接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "模拟面试", description = "面试会话创建、问答交互与报告生成")
public class InterviewController {
    
    private final InterviewSessionService sessionService;
    private final InterviewHistoryService historyService;
    private final InterviewPersistenceService persistenceService;
    private final InterviewClosureService interviewClosureService;

    /** 查询报告生成后幂等落库的结束总结和改进任务。 */
    @GetMapping("/api/interview/sessions/{sessionId}/closure")
    public Result<InterviewClosureDTO> getClosure(@PathVariable String sessionId) {
        return Result.success(interviewClosureService.getClosure(sessionId));
    }
    
    /**
     * 列出所有面试会话（用于面试记录页）
     */
    @GetMapping("/api/interview/sessions")
    public Result<List<SessionListItemDTO>> listSessions(@RequestParam(required = false) Long jobId) {
        List<SessionListItemDTO> items = (jobId == null
            ? persistenceService.findAll()
            : persistenceService.findByJobId(jobId)).stream()
            .map(SessionListItemDTO::from)
            .toList();
        return Result.success(items);
    }

    /**
     * 获取会话信息
     */
    @GetMapping("/api/interview/sessions/{sessionId}")
    public Result<InterviewSessionDTO> getSession(@PathVariable String sessionId) {
        InterviewSessionDTO session = sessionService.getSession(sessionId);
        return Result.success(session);
    }
    
    /**
     * 获取面试会话详情
     * GET /api/interview/sessions/{sessionId}/details
     */
    @GetMapping("/api/interview/sessions/{sessionId}/details")
    public Result<InterviewDetailDTO> getInterviewDetail(@PathVariable String sessionId) {
        InterviewDetailDTO detail = historyService.getInterviewDetail(sessionId);
        return Result.success(detail);
    }
    
    /**
     * 导出面试报告为PDF
     */
    @GetMapping("/api/interview/sessions/{sessionId}/export")
    public ResponseEntity<byte[]> exportInterviewPdf(@PathVariable String sessionId) {
        try {
            byte[] pdfBytes = historyService.exportInterviewPdf(sessionId);
            String filename = URLEncoder.encode("模拟面试报告_" + sessionId + ".pdf", 
                StandardCharsets.UTF_8);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
        } catch (Exception e) {
            log.error("导出PDF失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 删除面试会话
     */
    @DeleteMapping("/api/interview/sessions/{sessionId}")
    public Result<Void> deleteInterview(@PathVariable String sessionId) {
        log.info("删除面试会话: {}", sessionId);
        persistenceService.deleteSessionBySessionId(sessionId);
        return Result.success(null);
    }
}
