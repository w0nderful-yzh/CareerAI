import request from './request';
import type { CategoryDTO } from './skill';

export type JobStatus = 'TRACKING' | 'APPLIED' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'ARCHIVED';

export interface JobItem {
  id: number;
  title: string;
  company?: string;
  location?: string;
  sourceUrl?: string;
  status: JobStatus;
  jdText: string;
  parsedCategories: CategoryDTO[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateJobRequest {
  title?: string;
  company?: string;
  location?: string;
  sourceUrl?: string;
  jdText: string;
  parsedCategories?: CategoryDTO[];
}

export interface JobParseResponse {
  suggestedTitle: string;
  categories: CategoryDTO[];
}

export const jobApi = {
  list(status?: JobStatus) {
    return request.get<JobItem[]>('/api/jobs', {
      params: status ? { status } : undefined,
    });
  },

  parseJd(jdText: string) {
    return request.post<JobParseResponse>('/api/jobs/parse-jd', { jdText }, {
      timeout: 180000,
    });
  },

  create(data: CreateJobRequest) {
    return request.post<JobItem>('/api/jobs', data, {
      timeout: 180000,
    });
  },

  updateStatus(id: number, status: JobStatus) {
    return request.patch<JobItem>(`/api/jobs/${id}/status`, { status });
  },

  delete(id: number) {
    return request.delete<void>(`/api/jobs/${id}`);
  },
};
