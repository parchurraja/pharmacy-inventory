import api from './api';

export const batchService = {
  async getAll({ page = 0, size = 10, sort = 'expiryDate,asc', medicineId = null, active = null } = {}) {
    const params = { page, size, sort };
    if (medicineId) params.medicineId = medicineId;
    if (active !== null && active !== undefined && active !== '') params.active = active;

    const response = await api.get('/batches', { params });
    return response.data; // PageResponse<BatchDetailResponse>
  },

  async getById(id) {
    const response = await api.get(`/batches/${id}`);
    return response.data;
  },

  async create(batchData) {
    const response = await api.post('/batches', batchData);
    return response.data;
  },

  async update(id, batchData) {
    const response = await api.put(`/batches/${id}`, batchData);
    return response.data;
  }
};
