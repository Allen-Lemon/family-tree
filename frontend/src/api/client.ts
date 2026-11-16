import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

export const http = axios.create({ baseURL: '/api/v1', withCredentials: true, timeout: 15000, headers: { Accept: 'application/json' } })

type CsrfInfo = { headerName:string; parameterName:string; token:string }
let csrf: CsrfInfo | null = null
let csrfRequest: Promise<CsrfInfo> | null = null
async function ensureCsrf() {
  if (csrf) return csrf
  csrfRequest ||= http.get<CsrfInfo>('/auth/csrf', { headers: { 'X-Skip-CSRF': '1' } }).then((response) => response.data).finally(() => { csrfRequest = null })
  csrf = await csrfRequest
  return csrf
}

http.interceptors.request.use(async (config) => {
  const method = (config.method || 'get').toLowerCase()
  if (!['get', 'head', 'options'].includes(method) && config.headers['X-Skip-CSRF'] !== '1') {
    const token = await ensureCsrf()
    config.headers[token.headerName] = token.token
  }
  delete config.headers['X-Skip-CSRF']
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ message?: string; detail?: string }>) => {
    const status = error.response?.status
    if (status === 401) {
      clearCsrf()
      if (location.pathname !== '/login') location.assign(`/login?redirect=${encodeURIComponent(location.pathname + location.search)}`)
    } else if (status === 403) ElMessage.error(error.response?.data?.message || '您没有执行此操作的权限')
    else if (status === 409) ElMessage.warning(error.response?.data?.message || '数据冲突，请刷新后重试')
    else if (status && status >= 500) ElMessage.error('服务暂时不可用，请稍后重试')
    return Promise.reject(error)
  },
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<T>(config)
  return response.data
}
export function clearCsrf() { csrf = null }
