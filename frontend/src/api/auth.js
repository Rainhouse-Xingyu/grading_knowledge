import http from './request'

export const authApi = {
  login: (data) => http.post('/api/auth/login', data),
  casCallback: (data) => http.post('/api/auth/cas/callback', data),
  getUserInfo: () => http.get('/api/auth/me'),
  logout: () => http.post('/api/auth/logout'),
  checkLockStatus: (username) => http.get('/api/auth/lock-status', { params: { username } }),
  changePassword: (data) => http.post('/api/auth/change-password', data),
}
