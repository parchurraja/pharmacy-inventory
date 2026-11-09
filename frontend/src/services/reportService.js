import api from './api';

export const reportService = {
  async getSummary(params = {}) {
    const response = await api.get('/reports/summary', { params });
    return response.data;
  }
};
