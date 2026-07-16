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

export type JobMatchTaskStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface JobMatchTask {
  id: number;
  status: JobMatchTaskStatus;
  resumeId: number;
  jobId: number;
  reportId?: number;
  retryCount: number;
  errorMessage?: string;
  report?: JobMatchReport;
  createdAt: string;
  updatedAt: string;
}

export interface CreateJobMatchRequest {
  resumeId: number;
  jobId: number;
}

export const jobMatchApi = {
  list(jobId?: number) {
    return request.get<JobMatchReport[]>('/api/job-matches', {
      params: jobId ? { jobId } : undefined,
    });
  },

  create(data: CreateJobMatchRequest) {
    return request.post<JobMatchReport>('/api/job-matches', data, {
      timeout: 180000,
    });
  },

  createTask(data: CreateJobMatchRequest) {
    return request.post<JobMatchTask>('/api/job-matches/tasks', data);
  },

  getTask(taskId: number) {
    return request.get<JobMatchTask>(`/api/job-matches/tasks/${taskId}`);
  },
};
