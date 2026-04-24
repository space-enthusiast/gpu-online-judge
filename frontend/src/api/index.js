import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

export const auth = {
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
}

export const problems = {
  list: () => api.get('/problems'),
  get: (id) => api.get(`/problems/${id}`),
}

export const submissions = {
  submit: (data) => api.post('/submissions', data),
  get: (id) => api.get(`/submissions/${id}`),
  streamStatus: (id) => new EventSource(`/api/submissions/${id}/status`),
}

export default api
