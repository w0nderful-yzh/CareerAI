import request from './request';
import type { AgentRun, CreateAgentRunRequest } from '../types/agent';

export const agentApi = {
  createRun(data: CreateAgentRunRequest) {
    return request.post<AgentRun>('/api/agent/runs', data, {
      timeout: 180000,
    });
  },

  getRun(runId: string) {
    return request.get<AgentRun>(`/api/agent/runs/${runId}`);
  },

  resumeRun(runId: string) {
    return request.post<AgentRun>(`/api/agent/runs/${runId}/resume`, undefined, {
      timeout: 180000,
    });
  },
};
