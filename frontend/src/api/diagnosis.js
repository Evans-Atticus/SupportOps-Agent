import { request } from './http.js'

export function createDiagnosis(input, { signal, idempotencyKey } = {}) {
  return request('/diagnoses', {
    method: 'POST', body: input, signal,
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {},
    timeout: 30_000
  })
}

export function getDiagnosis(id, signal) { return request(`/diagnoses/${id}`, { signal, timeout: 30_000 }) }
export function uploadDiagnosisAttachments(id, files) {
  const body = new FormData()
  files.forEach((file) => body.append('files', file))
  return request(`/diagnoses/${id}/attachments`, { method: 'POST', body, timeout: 30_000 })
}
export function getDiagnosisHistory(limit = 20, signal) { return request(`/diagnoses?limit=${limit}`, { signal }) }
export function applyDiagnosis(id, signal) { return request(`/diagnoses/${id}/apply`, { method: 'POST', signal }) }
export function discardDiagnosis(id, signal) { return request(`/diagnoses/${id}/discard`, { method: 'POST', signal }) }
