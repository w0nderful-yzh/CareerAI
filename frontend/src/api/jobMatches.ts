import request from './request';

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
  createdAt: string;
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
};
