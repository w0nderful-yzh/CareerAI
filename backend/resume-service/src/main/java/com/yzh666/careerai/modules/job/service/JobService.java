package com.yzh666.careerai.modules.job.service;

import com.yzh666.careerai.common.exception.BusinessException;
import com.yzh666.careerai.common.exception.ErrorCode;
import com.yzh666.careerai.modules.interview.skill.InterviewSkillService;
import com.yzh666.careerai.modules.interview.skill.InterviewSkillService.CategoryDTO;
import com.yzh666.careerai.modules.job.dto.CreateJobRequest;
import com.yzh666.careerai.modules.job.dto.JobDTO;
import com.yzh666.careerai.modules.job.dto.JobParseResponse;
import com.yzh666.careerai.modules.job.model.JobEntity;
import com.yzh666.careerai.modules.job.model.JobStatus;
import com.yzh666.careerai.modules.job.repository.JobRepository;
import com.yzh666.careerai.modules.user.service.CurrentUserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final CurrentUserService currentUserService;
    private final InterviewSkillService interviewSkillService;
    private final ObjectMapper objectMapper;

    public JobParseResponse parseJd(String jdText) {
        List<CategoryDTO> categories = interviewSkillService.parseJd(jdText);
        return new JobParseResponse(inferTitle(jdText), categories);
    }

    @Transactional
    public JobDTO createJob(CreateJobRequest request) {
        Long userId = currentUserService.currentUserId();
        List<CategoryDTO> categories = request.parsedCategories();
        if (categories == null || categories.isEmpty()) {
            categories = interviewSkillService.parseJd(request.jdText());
        }

        JobEntity entity = new JobEntity();
        entity.setUserId(userId);
        entity.setTitle(normalizeTitle(request.title(), request.jdText()));
        entity.setCompany(trimToNull(request.company()));
        entity.setLocation(trimToNull(request.location()));
        entity.setSourceUrl(trimToNull(request.sourceUrl()));
        entity.setJdText(request.jdText().trim());
        entity.setParsedCategoriesJson(writeCategories(categories));

        JobEntity saved = jobRepository.save(entity);
        log.info("岗位已保存: jobId={}, userId={}, title={}", saved.getId(), userId, saved.getTitle());
        return toDTO(saved);
    }

    public List<JobDTO> listJobs(JobStatus status) {
        Long userId = currentUserService.currentUserId();
        List<JobEntity> jobs = status == null
            ? jobRepository.findByUserIdOrderByUpdatedAtDesc(userId)
            : jobRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, status);
        return jobs.stream().map(this::toDTO).toList();
    }

    public JobDTO getJob(Long id) {
        return toDTO(loadUserJob(id));
    }

    @Transactional
    public JobDTO updateStatus(Long id, JobStatus status) {
        JobEntity job = loadUserJob(id);
        job.setStatus(status);
        return toDTO(jobRepository.save(job));
    }

    @Transactional
    public void deleteJob(Long id) {
        JobEntity job = loadUserJob(id);
        jobRepository.delete(job);
    }

    private JobEntity loadUserJob(Long id) {
        return jobRepository.findByIdAndUserId(id, currentUserService.currentUserId())
            .orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
    }

    private JobDTO toDTO(JobEntity entity) {
        return new JobDTO(
            entity.getId(),
            entity.getTitle(),
            entity.getCompany(),
            entity.getLocation(),
            entity.getSourceUrl(),
            entity.getStatus(),
            entity.getJdText(),
            readCategories(entity.getParsedCategoriesJson()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private String writeCategories(List<CategoryDTO> categories) {
        try {
            return objectMapper.writeValueAsString(categories == null ? List.of() : categories);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.JOB_PARSE_FAILED, "保存 JD 解析结果失败");
        }
    }

    private List<CategoryDTO> readCategories(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException e) {
            log.warn("读取岗位 JD 解析结果失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String normalizeTitle(String title, String jdText) {
        String normalized = trimToNull(title);
        return normalized != null ? normalized : inferTitle(jdText);
    }

    private String inferTitle(String jdText) {
        if (jdText == null || jdText.isBlank()) {
            return "未命名岗位";
        }
        return jdText.lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .filter(line -> line.length() <= 80)
            .filter(line -> !line.contains("职责") && !line.contains("要求") && !line.contains("描述"))
            .findFirst()
            .orElse("目标岗位");
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
