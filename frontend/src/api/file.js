import http from './request'

export const fileApi = {
  upload: (formData, onUploadProgress) =>
    http.post('/api/file/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress,
    }),

  download: (fileId) => http.get(`/api/file/download/${fileId}`, { responseType: 'blob' }),
  getFileInfo: (fileId) => http.get(`/api/file/info/${fileId}`),
}
