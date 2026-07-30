import { request, requestBinary } from './http.js'

function prefix(role) {
  if (role === 'ADMIN') return '/admin'
  if (role === 'SUPPORT_AGENT') return '/agent'
  return '/customer'
}

export function getPortalDashboard(role, signal) {
  return request(`${prefix(role)}/dashboard`, { signal })
}

export function searchPortalModule(role, module, keyword = '', signal) {
  const query = new URLSearchParams({ keyword })
  return request(`${prefix(role)}/modules/${encodeURIComponent(module)}?${query}`, { signal })
}

export function exportPortalModule(role, module, keyword = '') {
  const query = new URLSearchParams({ keyword })
  return request(`${prefix(role)}/modules/${encodeURIComponent(module)}/export?${query}`)
}

export function getPortalModuleItem(role, module, id) {
  return request(`${prefix(role)}/modules/${encodeURIComponent(module)}/items/${encodeURIComponent(id)}`)
}

export function getPortalAdvice(role, module) {
  return request(`${prefix(role)}/modules/${encodeURIComponent(module)}/advice`)
}

export function getProductKnowledgeDocuments(orderNo) {
  return request(`/admin/orders/${encodeURIComponent(orderNo)}/knowledge-documents`)
}

export function uploadProductKnowledgeDocument(orderNo, metadata, file) {
  const body = new FormData()
  body.append('documentType', metadata.documentType)
  body.append('sourceType', metadata.sourceType)
  if (metadata.sourceReference) body.append('sourceReference', metadata.sourceReference)
  if (metadata.version) body.append('version', metadata.version)
  body.append('file', file)
  return request(`/admin/orders/${encodeURIComponent(orderNo)}/knowledge-documents`, {
    method: 'POST', body, timeout: 60_000
  })
}

export function deleteProductKnowledgeDocument(orderNo, documentId) {
  return request(`/admin/orders/${encodeURIComponent(orderNo)}/knowledge-documents/${documentId}`, {
    method: 'DELETE'
  })
}

export function downloadProductKnowledgeDocument(orderNo, documentId) {
  return requestBinary(`/admin/orders/${encodeURIComponent(orderNo)}/knowledge-documents/${documentId}/download`)
}

export function getConversation(conversationNo, signal) {
  return request(`/agent/conversations/${encodeURIComponent(conversationNo)}`, { signal })
}

export function archiveAgentConversation(conversationNo) {
  return request(`/agent/conversations/${encodeURIComponent(conversationNo)}`, { method: 'DELETE' })
}

export function archiveCompletedAgentConversations() {
  return request('/agent/conversations/completed', { method: 'DELETE' })
}

export function replyConversation(conversationNo, content, files = []) {
  const body = new FormData()
  body.append('content', content)
  files.forEach((file) => body.append('files', file))
  return request(`/agent/conversations/${encodeURIComponent(conversationNo)}/messages`, {
    method: 'POST', body, timeout: 30_000
  })
}

export function createConversationRefund(conversationNo, body) {
  return request(`/agent/conversations/${encodeURIComponent(conversationNo)}/refunds`, {
    method: 'POST', body
  })
}

export function requestHumanHandoff(body) {
  return request('/customer/conversations/handoff', { method: 'POST', body })
}

export function getCustomerConversation(conversationNo, signal) {
  return request(`/customer/conversations/${encodeURIComponent(conversationNo)}`, { signal })
}

export function recallCustomerMessage(conversationNo, messageId) {
  return request(`/customer/conversations/${encodeURIComponent(conversationNo)}/messages/${messageId}`, {
    method: 'DELETE'
  })
}

export function sendCustomerConversationMessage(conversationNo, content, ticketNo, businessNo, files = []) {
  const body = new FormData()
  body.append('content', content || '')
  if (ticketNo) body.append('ticketNo', ticketNo)
  if (businessNo) body.append('businessNo', businessNo)
  files.forEach((file) => body.append('files', file))
  return request(`/customer/conversations/${encodeURIComponent(conversationNo)}/messages`, {
    method: 'POST', body, timeout: 30_000
  })
}

export function getPortalRefunds(role, keyword = '', signal) {
  const query = new URLSearchParams({ keyword })
  return request(`${prefix(role)}/refunds?${query}`, { signal })
}

export function createPortalRefund(role, body, idempotencyKey = crypto.randomUUID()) {
  return request(`${prefix(role)}/refunds`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body
  })
}

export function approvePortalRefund(refundNo, approvedAmount, reason = '') {
  return request(`/admin/refunds/${encodeURIComponent(refundNo)}/approve`, {
    method: 'POST', body: { approvedAmount, reason }
  })
}

export function rejectPortalRefund(refundNo, reason) {
  return request(`/admin/refunds/${encodeURIComponent(refundNo)}/reject`, {
    method: 'POST', body: { reason }
  })
}

export function executePortalRefund(refundNo) {
  return request(`/admin/refunds/${encodeURIComponent(refundNo)}/execute`, { method: 'POST' })
}

export function createSupportUser(body) {
  return request('/admin/people', { method: 'POST', body })
}

export function updateSupportUser(userId, body) {
  return request(`/admin/people/${encodeURIComponent(userId)}`, { method: 'PUT', body })
}

export function deleteSupportUser(userId) {
  return request(`/admin/people/${encodeURIComponent(userId)}`, { method: 'DELETE' })
}

export function triggerReservedExternalSync(integration) {
  const paths = {
    ERP: '/admin/integrations/erp/sync/orders-tickets',
    WMS: '/admin/integrations/wms/sync/logistics'
  }
  const path = paths[integration]
  if (!path) throw new Error(`不支持的外部系统：${integration}`)
  return request(path, { method: 'POST' })
}
