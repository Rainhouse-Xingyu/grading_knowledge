import http from './request'

export const userApi = {
  getCourseStudents: (courseId) => http.get(`/api/user/students/${courseId}`),
  getStudentProgress: (studentNo, courseId) => http.get(`/api/user/progress/${studentNo}/${courseId}`),
  getFinalScore: (studentNo, courseId) => http.get(`/api/user/final-score/${studentNo}/${courseId}`),
}
