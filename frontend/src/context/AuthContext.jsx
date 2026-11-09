import React, { createContext, useContext, useState, useEffect } from 'react';
import { authService } from '../services/authService';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('medistock_token'));
  const [user, setUser] = useState(() => {
    const saved = localStorage.getItem('medistock_user');
    return saved ? JSON.parse(saved) : null;
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (token && user) {
      localStorage.setItem('medistock_token', token);
      localStorage.setItem('medistock_user', JSON.stringify(user));
    } else {
      localStorage.removeItem('medistock_token');
      localStorage.removeItem('medistock_user');
    }
    setLoading(false);
  }, [token, user]);

  const login = async (username, password) => {
    const data = await authService.login(username, password);
    setToken(data.token);
    const userInfo = {
      username: data.username,
      role: data.role,
      fullName: data.fullName || data.username,
    };
    setUser(userInfo);
    return userInfo;
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    localStorage.removeItem('medistock_token');
    localStorage.removeItem('medistock_user');
    window.location.href = '/login';
  };

  const value = {
    token,
    user,
    isAuthenticated: !!token && !!user,
    isAdmin: user?.role === 'ROLE_ADMIN',
    isPharmacist: user?.role === 'ROLE_PHARMACIST',
    login,
    logout,
    loading,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
