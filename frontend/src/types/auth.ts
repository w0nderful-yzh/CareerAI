export interface CurrentUser {
  id: number;
  username: string;
  displayName: string;
  anonymous: boolean;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: CurrentUser;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest extends LoginRequest {
  displayName?: string;
}
