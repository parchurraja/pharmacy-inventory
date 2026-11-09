import api from './api';

export const authService = {
  async login(username, password) {
    const response = await api.post('/auth/login', { username, password });
    return response.data; // AuthDTO.AuthResponse: { token, type, username, role, fullName }
  },

  async register(userData) {
    const response = await api.post('/auth/register', userData);
    return response.data;
  }
};
