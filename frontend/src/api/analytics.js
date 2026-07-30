import { request } from './http.js'

export function getServiceOperationsAnalytics(filters = {}, signal) {
  const query = new URLSearchParams()
  for (const key of ['from', 'to', 'channel', 'priority', 'status']) {
    if (filters[key]) query.set(key, filters[key])
  }
  const suffix = query.size ? `?${query}` : ''
  return request(`/admin/analytics/service-operations${suffix}`, { signal })
}
