import api from './api';

export const medicineService = {
  async getAll({ page = 0, size = 10, sort = 'name,asc', search = '', active = null } = {}) {
    const params = { page, size, sort };
    if (search && search.trim()) {
      params.search = search.trim();
    }
    if (active !== null && active !== undefined && active !== '') {
      params.active = active;
    }
    const response = await api.get('/medicines', { params });
    return response.data; // PageResponse<MedicineResponse>
  },

  async getById(id) {
    const response = await api.get(`/medicines/${id}`);
    return response.data;
  },

  async getByName(name) {
    const response = await api.get(`/medicines/name/${encodeURIComponent(name)}`);
    return response.data; // MedicineStockResponse
  },

  async create(medicineData) {
    const response = await api.post('/medicines', medicineData);
    return response.data;
  },

  async update(id, medicineData) {
    const response = await api.put(`/medicines/${id}`, medicineData);
    return response.data;
  },

  async deactivate(id) {
    const response = await api.patch(`/medicines/${id}/deactivate`);
    return response.data;
  },

  async activate(id) {
    const response = await api.patch(`/medicines/${id}/activate`);
    return response.data;
  }
};
