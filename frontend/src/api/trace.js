import { request } from './http.js'

function query(params = {}) {
  const values = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value != null && String(value).trim()) values.set(key, String(value).trim())
  })
  const text = values.toString()
  return text ? `?${text}` : ''
}

export const getTraceOverview = (signal) => request('/trace/overview', { signal })
export const getProducts = (params, signal) => request(`/trace/products${query(params)}`, { signal })
export const getPurchases = (params, signal) => request(`/trace/purchases${query(params)}`, { signal })
export const getBatches = (params, signal) => request(`/trace/batches${query(params)}`, { signal })
export const getQuality = (params, signal) => request(`/trace/quality${query(params)}`, { signal })
export const getInventory = (params, signal) => request(`/trace/inventory${query(params)}`, { signal })
export const createInboundOrder = (body, signal) => request('/trace/inventory/inbound', { method: 'POST', body, signal })
export const getLogistics = (params, signal) => request(`/trace/logistics${query(params)}`, { signal })
export const getSales = (params, signal) => request(`/trace/sales${query(params)}`, { signal })
export const getAfterSaleTickets = (params, signal) => request(`/trace/tickets${query(params)}`, { signal })
export const getRecalls = (params, signal) => request(`/trace/recalls${query(params)}`, { signal })
export const searchTrace = (code, signal) => request(`/trace/search/${encodeURIComponent(code)}`, { signal })
