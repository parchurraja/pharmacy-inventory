import api from './api';

export const inventoryService = {
  async getLowStockAlerts() {
    const response = await api.get('/inventory/alerts/low-stock');
    return response.data; // List<LowStockAlertResponse>
  },

  async getExpiringBatches(days = 30) {
    const response = await api.get('/inventory/alerts/expiring', {
      params: { days }
    });
    return response.data; // List<ExpiringSoonResponse>
  },

  async getExpiredBatches() {
    const response = await api.get('/inventory/expired');
    return response.data; // List<BatchDetailResponse>
  },

  async disposeExpiredBatch(batchId) {
    const response = await api.post(`/inventory/expired/${batchId}/dispose`);
    return response.data; // StockAdjustmentResponse
  },

  async adjustStock(adjustmentData) {
    const response = await api.post('/inventory/adjustments', adjustmentData);
    return response.data; // StockAdjustmentResponse
  },

  async getAdjustments({ page = 0, size = 10, sort = 'adjustedAt,desc' } = {}) {
    const response = await api.get('/inventory/adjustments', {
      params: { page, size, sort }
    });
    return response.data; // PageResponse<StockAdjustmentResponse>
  }
};
