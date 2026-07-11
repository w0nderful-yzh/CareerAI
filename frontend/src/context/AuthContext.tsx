import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { authApi } from '../api/auth';
import type { CurrentUser, LoginRequest, RegisterRequest } from '../types/auth';
import { clearAuthToken, getAuthToken, setAuthToken } from '../utils/authStorage';

interface AuthContextValue {
  user: CurrentUser | null;
  loading: boolean;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    if (!getAuthToken()) {
      setUser(null);
      setLoading(false);
      return;
    }

    try {
      const currentUser = await authApi.me();
      setUser(currentUser);
    } catch {
      clearAuthToken();
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refreshUser();
  }, [refreshUser]);

  const login = useCallback(async (data: LoginRequest) => {
    const response = await authApi.login(data);
    setAuthToken(response.accessToken);
    setUser(response.user);
  }, []);

  const register = useCallback(async (data: RegisterRequest) => {
    const response = await authApi.register(data);
    setAuthToken(response.accessToken);
    setUser(response.user);
  }, []);

  const logout = useCallback(() => {
    clearAuthToken();
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    user,
    loading,
    login,
    register,
    logout,
    refreshUser,
  }), [user, loading, login, register, logout, refreshUser]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
