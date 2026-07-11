import request from './request';
import type { AuthResponse, CurrentUser, LoginRequest, RegisterRequest } from '../types/auth';

export const authApi = {
  login(data: LoginRequest) {
    return request.post<AuthResponse>('/api/auth/login', data);
  },

  register(data: RegisterRequest) {
    return request.post<AuthResponse>('/api/auth/register', data);
  },

  me() {
    return request.get<CurrentUser>('/api/auth/me');
  },
};
