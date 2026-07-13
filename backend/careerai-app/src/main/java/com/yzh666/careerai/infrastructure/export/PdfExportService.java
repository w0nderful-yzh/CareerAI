package com.yzh666.careerai.infrastructure.export;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.careerreport.dto.CareerReportDTO;
import com.yzh666.careerai.modules.interview.model.InterviewAnswerEntity;
import com.yzh666.careerai.modules.interview.model.InterviewSessionEntity;
import com.yzh666.careerai.modules.interview.model.ResumeAnalysisResponse;
import com.yzh666.careerai.modules.resume.model.ResumeEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PDF导出服务
 * PDF Export Service for resume analysis and interview reports
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DeviceRgb HEADER_COLOR = new DeviceRgb(41, 128, 185);
    private static final DeviceRgb SECTION_COLOR = new DeviceRgb(52, 73, 94);
    
    private final ObjectMapper objectMapper;
    
    /**
     * 创建支持中文的字体
     */
    private PdfFont createChineseFont() {
        try (var fontStream = getClass().getClassLoader().getResourceAsStream("fonts/ZhuqueFangsong-Regular.ttf")) {
            if (fontStream != null) {
                byte[] fontBytes = fontStream.readAllBytes();
                log.debug("使用项目内嵌字体: fonts/ZhuqueFangsong-Regular.ttf");
                return PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H, EmbeddingStrategy.FORCE_EMBEDDED);
            }

            log.error("未找到字体文件: fonts/ZhuqueFangsong-Regular.ttf");
            throw new BusinessException(ErrorCode.EXPORT_PDF_FAILED, "字体文件缺失，请联系管理员");
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("创建中文字体失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.EXPORT_PDF_FAILED, "创建字体失败: " + e.getMessage());
        }
    }
    
    /**
     * 清理文本中可能导致字体问题的字符
     */
    private String sanitizeText(String text) {
        if (text == null) return "";
        // 移除可能导致问题的特殊字符（如 emoji）
        return text.replaceAll("[\\p{So}\\p{Cs}]", "").trim();
    }
    
    /**
     * 导出简历分析报告为PDF
     */
    public byte[] exportResumeAnalysis(ResumeEntity resume, ResumeAnalysisResponse analysis) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        
        // 使用支持中文的字体
        PdfFont font = createChineseFont();
        document.setFont(font);
        
        // 标题
        Paragraph title = new Paragraph("简历分析报告")
            .setFontSize(24)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(HEADER_COLOR);
        document.add(title);
        
        // 基本信息
        document.add(new Paragraph("\n"));
        document.add(createSectionTitle("基本信息"));
        document.add(new Paragraph("文件名: " + resume.getOriginalFilename()));
        document.add(new Paragraph("上传时间: " + 
            (resume.getUploadedAt() != null ? DATE_FORMAT.format(resume.getUploadedAt()) : "未知")));
        
        // 总分
        document.add(new Paragraph("\n"));
        document.add(createSectionTitle("综合评分"));
        Paragraph scoreP = new Paragraph("总分: " + analysis.overallScore() + " / 100")
            .setFontSize(18)
            .setBold()
            .setFontColor(getScoreColor(analysis.overallScore()));
        document.add(scoreP);
        
        // 各维度评分
        if (analysis.scoreDetail() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("各维度评分"));
            
            Table scoreTable = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                .useAllAvailableWidth();
            addScoreRow(scoreTable, "项目经验", analysis.scoreDetail().projectScore(), 40);
            addScoreRow(scoreTable, "技能匹配度", analysis.scoreDetail().skillMatchScore(), 20);
            addScoreRow(scoreTable, "内容完整性", analysis.scoreDetail().contentScore(), 15);
            addScoreRow(scoreTable, "结构清晰度", analysis.scoreDetail().structureScore(), 15);
            addScoreRow(scoreTable, "表达专业性", analysis.scoreDetail().expressionScore(), 10);
            document.add(scoreTable);
        }
        
        // 简历摘要
        if (analysis.summary() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("简历摘要"));
            document.add(new Paragraph(sanitizeText(analysis.summary())));
        }
        
        // 优势亮点
        if (analysis.strengths() != null && !analysis.strengths().isEmpty()) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("优势亮点"));
            for (String strength : analysis.strengths()) {
                document.add(new Paragraph("• " + sanitizeText(strength)));
            }
        }
        
        // 改进建议
        if (analysis.suggestions() != null && !analysis.suggestions().isEmpty()) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("改进建议"));
            for (ResumeAnalysisResponse.Suggestion suggestion : analysis.suggestions()) {
                document.add(new Paragraph("【" + suggestion.priority() + "】" + sanitizeText(suggestion.category()))
                    .setBold());
                document.add(new Paragraph("问题: " + sanitizeText(suggestion.issue())));
                document.add(new Paragraph("建议: " + sanitizeText(suggestion.recommendation())));
                document.add(new Paragraph("\n"));
            }
        }
        
        document.close();
        return baos.toByteArray();
    }

    /**
     * 导出求职综合报告为PDF
     */
    public byte[] exportCareerReport(CareerReportDTO report) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);

        PdfFont font = createChineseFont();
        document.setFont(font);

        Paragraph title = new Paragraph("CareerAI 求职综合报告")
            .setFontSize(24)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(HEADER_COLOR);
        document.add(title);

        document.add(new Paragraph("\n"));
        document.add(createSectionTitle("目标岗位"));
        document.add(new Paragraph("岗位: " + sanitizeText(report.job().title())));
        document.add(new Paragraph("公司: " + sanitizeText(defaultText(report.job().company(), "未填写"))));
        document.add(new Paragraph("地点: " + sanitizeText(defaultText(report.job().location(), "未填写"))));
        document.add(new Paragraph("简历: " + sanitizeText(report.resume().filename())));

        document.add(new Paragraph("\n"));
        document.add(createSectionTitle("简历-岗位匹配"));
        Table scoreTable = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
            .useAllAvailableWidth();
        addScoreRow(scoreTable, "总体匹配度", report.match().overallScore(), 100);
        addScoreRow(scoreTable, "技能匹配", report.match().skillScore(), 100);
        addScoreRow(scoreTable, "项目支撑", report.match().projectScore(), 100);
        addScoreRow(scoreTable, "关键词覆盖", report.match().keywordScore(), 100);
        document.add(scoreTable);
        document.add(new Paragraph("匹配结论: " + sanitizeText(report.match().summary())));
        addList(document, "匹配亮点", report.match().matchedHighlights());
        addList(document, "主要差距", report.match().gaps());
        addList(document, "优先行动", report.match().actionItems());

        if (report.latestInterview() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("岗位模拟面试复盘"));
            if (report.latestInterview().overallScore() != null) {
                Paragraph score = new Paragraph("面试得分: " + report.latestInterview().overallScore() + " / 100")
                    .setFontSize(16)
                    .setBold()
                    .setFontColor(getScoreColor(report.latestInterview().overallScore()));
                document.add(score);
            }
            document.add(new Paragraph("总体反馈: " + sanitizeText(defaultText(report.latestInterview().overallFeedback(), "暂无"))));
            var jobEvaluation = report.latestInterview().jobEvaluation();
            if (jobEvaluation != null) {
                document.add(new Paragraph("岗位结论: " + sanitizeText(defaultText(jobEvaluation.conclusion(), "暂无"))));
                document.add(new Paragraph("JD覆盖: " + jobEvaluation.jdCoverageScore() + " / 100")
                    .setFontColor(getScoreColor(jobEvaluation.jdCoverageScore())));
                addList(document, "已覆盖能力", jobEvaluation.jdCoverage());
                addList(document, "暴露短板", jobEvaluation.exposedGaps());
                addList(document, "简历表达建议", jobEvaluation.resumeRewriteSuggestions());
                addList(document, "下一步行动", jobEvaluation.nextActions());
            }
        } else {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("岗位模拟面试复盘"));
            document.add(new Paragraph("暂无岗位模拟面试复盘。完成一次岗位面试后，报告会自动补充这一部分。"));
        }

        if (report.improvementPlan() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("简历改进计划"));
            Paragraph readiness = new Paragraph("准备度: " + report.improvementPlan().readinessScore() + " / 100")
                .setFontSize(16)
                .setBold()
                .setFontColor(getScoreColor(report.improvementPlan().readinessScore()));
            document.add(readiness);
            document.add(new Paragraph("计划摘要: " + sanitizeText(report.improvementPlan().summary())));
            addList(document, "优先修改", report.improvementPlan().priorityFixes());
            addList(document, "可写入简历的表达", report.improvementPlan().resumeRewriteBullets());
            addList(document, "项目补强", report.improvementPlan().projectUpgradeTasks());
            addList(document, "面试练习", report.improvementPlan().interviewPracticeTasks());
            addList(document, "学习任务", report.improvementPlan().learningTasks());
        } else {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("简历改进计划"));
            document.add(new Paragraph("暂无简历改进计划。请先在岗位中心生成计划。"));
        }

        document.add(new Paragraph("\n"));
        document.add(createSectionTitle("建议使用方式"));
        document.add(new Paragraph("1. 先按优先修改清单更新简历。"));
        document.add(new Paragraph("2. 将可写入简历的表达改成自己的真实经历和数据。"));
        document.add(new Paragraph("3. 完成项目补强后重新生成匹配报告。"));
        document.add(new Paragraph("4. 再做一次岗位模拟面试，比较复盘结果变化。"));

        document.close();
        return baos.toByteArray();
    }
    
    /**
     * 导出面试报告为PDF
     */
    public byte[] exportInterviewReport(InterviewSessionEntity session) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        
        // 使用支持中文的字体
        PdfFont font = createChineseFont();
        document.setFont(font);
        
        // 标题
        Paragraph title = new Paragraph("模拟面试报告")
            .setFontSize(24)
            .setBold()
            .setTextAlignment(TextAlignment.CENTER)
            .setFontColor(HEADER_COLOR);
        document.add(title);
        
        // 基本信息
        document.add(new Paragraph("\n"));
        document.add(createSectionTitle("面试信息"));
        document.add(new Paragraph("会话ID: " + session.getSessionId()));
        document.add(new Paragraph("题目数量: " + session.getTotalQuestions()));
        document.add(new Paragraph("面试状态: " + getStatusText(session.getStatus())));
        document.add(new Paragraph("开始时间: " + 
            (session.getCreatedAt() != null ? DATE_FORMAT.format(session.getCreatedAt()) : "未知")));
        if (session.getCompletedAt() != null) {
            document.add(new Paragraph("完成时间: " + DATE_FORMAT.format(session.getCompletedAt())));
        }
        
        // 总分
        if (session.getOverallScore() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("综合评分"));
            Paragraph scoreP = new Paragraph("总分: " + session.getOverallScore() + " / 100")
                .setFontSize(18)
                .setBold()
                .setFontColor(getScoreColor(session.getOverallScore()));
            document.add(scoreP);
        }
        
        // 总体评价
        if (session.getOverallFeedback() != null) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("总体评价"));
            document.add(new Paragraph(sanitizeText(session.getOverallFeedback())));
        }
        
        // 优势
        if (session.getStrengthsJson() != null) {
            try {
                List<String> strengths = objectMapper.readValue(session.getStrengthsJson(),
                        new TypeReference<>() {
                        });
                if (!strengths.isEmpty()) {
                    document.add(new Paragraph("\n"));
                    document.add(createSectionTitle("表现优势"));
                    for (String s : strengths) {
                        document.add(new Paragraph("• " + sanitizeText(s)));
                    }
                }
            } catch (Exception e) {
                log.error("解析优势JSON失败: sessionId={}", session.getSessionId(), e);
            }
        }
        
        // 改进建议
        if (session.getImprovementsJson() != null) {
            try {
                List<String> improvements = objectMapper.readValue(session.getImprovementsJson(),
                        new TypeReference<>() {
                        });
                if (!improvements.isEmpty()) {
                    document.add(new Paragraph("\n"));
                    document.add(createSectionTitle("改进建议"));
                    for (String s : improvements) {
                        document.add(new Paragraph("• " + sanitizeText(s)));
                    }
                }
            } catch (Exception e) {
                log.error("解析改进建议JSON失败: sessionId={}", session.getSessionId(), e);
            }
        }
        
        // 问答详情
        List<InterviewAnswerEntity> answers = session.getAnswers();
        if (answers != null && !answers.isEmpty()) {
            document.add(new Paragraph("\n"));
            document.add(createSectionTitle("问答详情"));
            
            for (InterviewAnswerEntity answer : answers) {
                document.add(new Paragraph("\n"));
                document.add(new Paragraph("问题 " + (answer.getQuestionIndex() + 1) + 
                    " [" + (answer.getCategory() != null ? answer.getCategory() : "综合") + "]")
                    .setBold()
                    .setFontSize(12));
                document.add(new Paragraph("Q: " + sanitizeText(answer.getQuestion())));
                document.add(new Paragraph("A: " + sanitizeText(answer.getUserAnswer() != null ? answer.getUserAnswer() : "未回答")));
                document.add(new Paragraph("得分: " + answer.getScore() + "/100")
                    .setFontColor(getScoreColor(answer.getScore())));
                if (answer.getFeedback() != null) {
                    document.add(new Paragraph("评价: " + sanitizeText(answer.getFeedback()))
                        .setItalic());
                }
                if (answer.getReferenceAnswer() != null) {
                    document.add(new Paragraph("参考答案: " + sanitizeText(answer.getReferenceAnswer()))
                        .setFontColor(new DeviceRgb(39, 174, 96)));
                }
            }
        }
        
        document.close();
        return baos.toByteArray();
    }
    
    private Paragraph createSectionTitle(String title) {
        return new Paragraph(title)
            .setFontSize(14)
            .setBold()
            .setFontColor(SECTION_COLOR)
            .setMarginTop(10);
    }
    
    private void addScoreRow(Table table, String dimension, int score, int maxScore) {
        table.addCell(new Cell().add(new Paragraph(dimension)));
        table.addCell(new Cell().add(new Paragraph(score + " / " + maxScore)
            .setFontColor(getScoreColor(score * 100 / maxScore))));
    }

    private void addList(Document document, String title, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        document.add(new Paragraph(title).setBold().setMarginTop(8));
        for (String item : items) {
            document.add(new Paragraph("• " + sanitizeText(item)));
        }
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
    
    private DeviceRgb getScoreColor(int score) {
        if (score >= 80) return new DeviceRgb(39, 174, 96);   // 绿色
        if (score >= 60) return new DeviceRgb(241, 196, 15);  // 黄色
        return new DeviceRgb(231, 76, 60);                    // 红色
    }
    
    private String getStatusText(InterviewSessionEntity.SessionStatus status) {
        return switch (status) {
            case CREATED -> "已创建";
            case IN_PROGRESS -> "进行中";
            case COMPLETED -> "已完成";
            case EVALUATED -> "已评估";
        };
    }
}
