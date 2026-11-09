import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor: attach Bearer token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('medistock_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: handle errors & 401s
api.interceptors.response.use(
  (response) => {
    return response.data; // Backend sends MedicineDTO.ApiResponse<T>
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('medistock_token');
      localStorage.removeItem('medistock_user');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }

    // Format clean error message
    const apiError = error.response?.data;
    let message = 'An unexpected error occurred. Please try again.';

    if (apiError?.message) {
      message = apiError.message;
    } else if (apiError?.errors && typeof apiError.errors === 'object') {
      message = Object.values(apiError.errors).join(', ');
    } else if (error.message) {
      message = error.message;
    }

    return Promise.reject(new Error(message));
  }
);

export default api;
