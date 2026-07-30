const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'
const TOKEN_KEY = 'supportops_access_token'

/** 带状态码和请求 ID 的统一异常，页面可直接展示可追踪的错误信息。 */
export class ApiError extends Error {
  constructor(message, { status = 0, code = 'NETWORK_ERROR', requestId = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.requestId = requestId
  }
}

export function getAccessToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function setAccessToken(token) {
  if (token) sessionStorage.setItem(TOKEN_KEY, token)
  else sessionStorage.removeItem(TOKEN_KEY)
}

/** 统一处理 JSON、JWT、超时和调用方取消。 */
export async function request(path, options = {}) {
  const { timeout = 10_000, signal, headers = {}, body, ...fetchOptions } = options
  const controller = new AbortController()
  let timedOut = false
  const abortFromCaller = () => controller.abort(signal?.reason)
  signal?.addEventListener('abort', abortFromCaller, { once: true })
  const timer = window.setTimeout(() => {
    timedOut = true
    controller.abort()
  }, timeout)
  const token = getAccessToken()
  const multipart = typeof FormData !== 'undefined' && body instanceof FormData

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...fetchOptions,
      body: body == null ? undefined : multipart ? body : JSON.stringify(body),
      signal: controller.signal,
      headers: {
        Accept: 'application/json',
        ...(body == null || multipart ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers
      }
    })
    const payload = await response.json().catch(() => null)
    if (!response.ok || payload?.code !== 'OK') {
      if (response.status === 401) {
        setAccessToken(null)
        window.dispatchEvent(new CustomEvent('supportops:unauthorized'))
      }
      throw new ApiError(friendlyMessage(payload, response), {
        status: response.status,
        code: payload?.code,
        requestId: payload?.requestId || response.headers.get('X-Request-Id')
      })
    }
    return payload.data
  } catch (error) {
    if (timedOut) throw new ApiError('请求超时，请稍后重试', { code: 'TIMEOUT' })
    if (error instanceof ApiError || error.name === 'AbortError') throw error
    throw new ApiError(error.message || '网络连接失败，请确认后端服务已启动')
  } finally {
    window.clearTimeout(timer)
    signal?.removeEventListener('abort', abortFromCaller)
  }
}

export async function requestBinary(path, options = {}) {
  const token = getAccessToken()
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(options.headers || {}) }
  })
  if (!response.ok) {
    const payload = await response.json().catch(() => null)
    throw new ApiError(friendlyMessage(payload, response), {
      status: response.status,
      code: payload?.code,
      requestId: payload?.requestId || response.headers.get('X-Request-Id')
    })
  }
  return {
    blob: await response.blob(),
    fileName: decodeURIComponent(response.headers.get('Content-Disposition')?.match(/filename\*=UTF-8''([^;]+)/i)?.[1] || '')
  }
}

/** 传输状态码只供程序判断，页面优先展示稳定业务错误对应的客户友好文案。 */
function friendlyMessage(payload, response) {
  const messages = {
    LOGIN_IP_ACCOUNT_LIMIT_EXCEEDED: '同一网络一小时内登录的账号数量已达上限，请稍后再试或使用已登录过的账号。',
    ACCOUNT_ALREADY_EXISTS: '该账号已存在，请直接登录或更换账号名称。',
    RESOURCE_IN_USE: '该客服账号已有业务记录，无法删除，请改为禁用账号。'
  }
  return messages[payload?.code] || payload?.message || `请求暂时无法完成（${response.status}）`
}
