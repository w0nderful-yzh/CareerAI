package com.yzh666.careerai.modules.jobmatch.dto;

import java.util.List;

/** JD 要求与简历证据之间的可解释映射。 */
public record RequirementEvidenceDTO(
    JdRequirementDTO requirement,
    List<ResumeEvidenceDTO> resumeEvidence,
    String coverageType,
    Integer confidence,
    String reasoning,
    String recommendedAction
) {
}
