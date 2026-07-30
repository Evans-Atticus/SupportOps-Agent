import { request, setAccessToken } from './http.js'

export async function login(username, password, signal) {
  const token = await request('/auth/login', { method: 'POST', body: { username, password }, signal })
  setAccessToken(token.accessToken)
  return token
}

export function register(username, displayName, password, signal) {
  return request('/auth/register', {
    method: 'POST', body: { username, displayName, password }, signal
  })
}

export function logout() {
  setAccessToken(null)
}

export function getCurrentUser(signal) {
  return request('/auth/me', { signal })
}
