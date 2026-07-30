import { onBeforeUnmount, ref } from 'vue'
import { createDiagnosis, getDiagnosis } from '../api/diagnosis.js'

const TERMINAL_STATUSES = new Set(['SUCCESS', 'FAILED', 'DEGRADED_SUCCESS', 'DISCARDED'])
const OPTIMISTIC_STEPS = [
  ['UNDERSTAND_TICKET', '正在理解工单'],
  ['QUERY_BUSINESS', '正在查询业务数据'],
  ['DIAGNOSE_RULES', '正在执行规则诊断'],
  ['GENERATE_REPLY', '正在生成客服回复'],
  ['BUILD_REPORT', '正在整理诊断报告']
].map(([code, title], index) => ({ code, title, status: index === 0 ? 'RUNNING' : 'PENDING' }))
const MAX_TRANSIENT_POLL_FAILURES = 5

/** 管理异步诊断生命周期：重复提交和页面卸载都会取消旧请求与轮询。 */
export function useDiagnosis() {
  const diagnosis = ref(null)
  const submitting = ref(false)
  const error = ref('')
  let controller = null
  let pollTimer = null
  let transientPollFailures = 0

  function cancel() {
    window.clearTimeout(pollTimer)
    controller?.abort()
    controller = null
  }

  async function submit(input, onCreated) {
    cancel()
    error.value = ''
    submitting.value = true
    transientPollFailures = 0
    controller = new AbortController()
    try {
      const task = await createDiagnosis(input, { signal: controller.signal, idempotencyKey: crypto.randomUUID() })
      // 后端任务异步执行，先展示确定性的阶段占位，避免真实模型等待期间页面看起来无响应。
      diagnosis.value = { ...task, steps: OPTIMISTIC_STEPS }
      if (onCreated) await onCreated(task)
      await poll(task.diagnosisId, task.pollAfterMs || 800)
    } catch (cause) {
      if (cause.name !== 'AbortError') error.value = formatError(cause)
    } finally {
      submitting.value = false
    }
  }

  async function poll(id, delay) {
    try {
      const detail = await getDiagnosis(id, controller.signal)
      transientPollFailures = 0
      diagnosis.value = detail
      if (!TERMINAL_STATUSES.has(detail.status)) {
        await wait(delay)
        if (!controller.signal.aborted) await poll(id, delay)
      }
    } catch (cause) {
      // 一次网络抖动或轮询超时不代表后台任务失败，保留思考动画并自动重试。
      const transient = cause.code === 'TIMEOUT' || cause.code === 'NETWORK_ERROR'
      if (!controller.signal.aborted && transient && transientPollFailures < MAX_TRANSIENT_POLL_FAILURES) {
        transientPollFailures += 1
        await wait(Math.min(1000 * transientPollFailures, 3000))
        if (!controller.signal.aborted) return poll(id, delay)
      }
      throw cause
    }
  }

  function wait(delay) {
    return new Promise((resolve) => { pollTimer = window.setTimeout(resolve, delay) })
  }

  async function load(id) {
    cancel()
    error.value = ''
    controller = new AbortController()
    try { diagnosis.value = await getDiagnosis(id, controller.signal) }
    catch (cause) { if (cause.name !== 'AbortError') error.value = formatError(cause) }
  }

  onBeforeUnmount(cancel)
  return { diagnosis, submitting, error, submit, load, cancel }
}

function formatError(error) {
  return `${error.message}${error.requestId ? `（请求 ID：${error.requestId}）` : ''}`
}
