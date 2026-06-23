import http from './request'

export const reviewApi = {
  saveComment: (data) => http.put('/api/review/comment', data),
  saveScore: (data) => http.put('/api/review/score', data),
}
