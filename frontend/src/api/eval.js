import http from './request'

export const evalApi = {
  triggerEval: (data) => http.post('/api/eval/trigger', data),
  getTaskStatus: (taskId) => http.get(`/api/task/status/${taskId}`),
  getReport: (studentNo, courseId, stage) => http.get(`/api/eval/report/${studentNo}/${courseId}/${stage}`),
  getReportsByCourse: (courseId, stage) => http.get(`/api/eval/reports/${courseId}/${stage}`),
  publishEval: (data) => http.post('/api/eval/publish', data),
}
