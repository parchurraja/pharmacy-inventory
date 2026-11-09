import api from './api';

export const salesService = {
  async createSale(saleData) {
    const response = await api.post('/sales', saleData);
    return response.data; // SaleResponseDTO
  },

  async getHistory({ page = 0, size = 10, sort = 'saleDate,desc' } = {}) {
    const response = await api.get('/sales', {
      params: { page, size, sort }
    });
    return response.data; // PageResponse<SaleResponseDTO>
  },

  async getById(id) {
    const response = await api.get(`/sales/${id}`);
    return response.data;
  },

  async getByInvoice(invoiceNumber) {
    const response = await api.get(`/sales/invoice/${encodeURIComponent(invoiceNumber)}`);
    return response.data;
  },

  async processReturn(saleId, returnData) {
    const response = await api.post(`/sales/${saleId}/return`, returnData);
    return response.data; // SaleReturnResponse
  }
};
