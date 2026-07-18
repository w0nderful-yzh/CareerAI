import request from './request';

export type EvidenceCoverageType =
  | 'SUPPORTED'
  | 'EXPRESSION_GAP'
  | 'EVIDENCE_GAP'
  | 'CAPABILITY_GAP';

export interface JdRequirement {
  id: string;
  category: string;
  description: string;
  importance: 'HIGH' | 'MEDIUM' | 'LOW';
  sourceQuote: string;
}

export interface ResumeEvidence {
  sourceType: string;
  sourceLocation: string;
  quote: string;
  strength: 'STRONG' | 'MODERATE' | 'WEAK';
}

export interface RequirementEvidence {
  requirement: JdRequirement;
  resumeEvidence: ResumeEvidence[];
  coverageType: EvidenceCoverageType;
  confidence: number;
  reasoning: string;
  recommendedAction: string;
}

export interface JobMatchReport {
  id: number;
  resumeId: number;
  resumeFilename: string;
  jobId: number;
  jobTitle: string;
  overallScore: number;
  skillScore: number;
  projectScore: number;
  keywordScore: number;
  summary: string;
  matchedHighlights: string[];
  gaps: string[];
  actionItems: string[];
  evidenceMappings: RequirementEvidence[];
  createdAt: string;
}

export const jobMatchApi = {
  list(jobId?: number) {
    return request.get<JobMatchReport[]>('/api/job-matches', {
      params: jobId ? { jobId } : undefined,
    });
  },
};
