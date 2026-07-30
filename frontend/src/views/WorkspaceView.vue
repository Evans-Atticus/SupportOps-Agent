<template>
  <!-- 工作台页面：左侧导航、中间诊断会话和右侧 Procedure 详情面板。 -->
  <main class="ops-page">
    <canvas ref="ambientCanvas" class="ops-ambient" aria-hidden="true"></canvas>
    <nav class="ops-exit-nav" aria-label="页面导航">
      <router-link to="/" title="返回首页">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m4 10 8-6 8 6v9a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1z" /></svg>
        <span>首页</span>
      </router-link>
      <router-link :to="{ name: 'personal-center' }" title="返回个人中心">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="3.5"/><path d="M5.5 20c.8-4 3-6 6.5-6s5.7 2 6.5 6" /></svg>
        <span>个人中心</span>
      </router-link>
    </nav>

    <div class="ops-frame">
      <section class="ops-shell">
        <aside class="ops-rail" aria-label="Workspace tools">
          <div v-for="item in railIcons" :key="item.label" class="ops-rail-icon" :class="{ active: item.active }">
            {{ item.icon }}
          </div>
          <div class="ops-rail-space"></div>
          <div class="ops-rail-icon">⚙</div>
        </aside>

        <!-- 侧边栏：显示 Agent 名称、快捷操作与历史记录 -->
        <aside class="ops-sidebar">
          <div class="ops-side-head">SupportOps Agent</div>
          <div class="ops-side-body">
            <!-- 快捷操作按钮 -->
            <button v-for="item in sideActions" :key="item.label" type="button" class="ops-side-action" @click="handleSideAction(item.action)">
              <span>{{ item.icon }}</span>{{ item.label }}
            </button>
            <input v-if="historySearchOpen" v-model.trim="historyKeyword" class="ops-history-search" placeholder="搜索历史对话" />
            <p v-if="historyVisible" class="ops-side-label">Previous 7 days</p>
            <div v-if="historyVisible" class="ops-history">
              <button v-for="item in filteredHistory" :key="item.diagnosisId" type="button" class="ops-history-row" @click="loadHistory(item)">
                <!-- 历史状态完全来自后端，不再伪造完成标记。 -->
                <i :class="{ done: ['SUCCESS', 'DEGRADED_SUCCESS'].includes(item.status) }"></i>
                <span>{{ item.title }}</span>
              </button>
            </div>
          </div>
        </aside>

        <section class="ops-main" :class="{ 'has-chat': hasChat, running, 'procedure-open': procedureOpen }">
          <!-- 主区域头部：平台标题与运行状态指示 -->
          <header class="ops-main-head">
            <span class="ops-workspace-title">智能工单诊断平台</span>
            <span class="ops-head-status"><i></i>{{ running ? 'Agent running' : 'Agent ready' }}</span>
          </header>

          <div class="ops-content">
            <div ref="scrollArea" class="ops-scroll">
              <!-- 欢迎/快速操作区：显示品牌与模板按钮 -->
              <section class="ops-welcome">
                <div class="ops-welcome-brand" aria-hidden="true"><BrandMark /></div>
                <h1>{{ isCustomer ? `您好，${currentUser?.displayName || '请问需要什么帮助？'}` : '开始新的工单诊断' }}</h1>
                <p v-if="isCustomer" class="ops-customer-welcome">请直接描述遇到的问题，也可以从下方选择您自己的订单或工单。</p>
                <div v-if="!isCustomer" class="ops-quick-actions">
                  <button v-for="item in quickActions" :key="item.type" type="button" @click="startFromTemplate(item.type)">
                    <span>{{ item.icon }}</span>
                    <b>{{ item.title }}</b>
                    <small>{{ item.text }}</small>
                  </button>
                </div>
              </section>

              <!-- 聊天/诊断区域：当 hasChat 为 true 时显示会话内容 -->
              <section v-if="humanConversationNo" class="ops-conversation ops-human-conversation">
                <div class="ops-human-session-head">
                  <div><b>人工客服会话</b><small>{{ humanConversation?.assignedAgent ? `正在由 ${humanConversation.assignedAgent} 为您服务` : '正在等待客服接入' }}</small></div>
                  <div class="ops-human-session-actions">
                    <span>{{ humanConversation?.serviceMode || 'WAITING_AGENT' }}</span>
                    <button type="button" @click="switchToAgentMode">切换到智能体</button>
                  </div>
                </div>
                <article v-for="item in humanMessages" :key="item.id" class="ops-message" :class="{ user: item.senderType === 'CUSTOMER', recalled: item.content === '该消息已撤回' }">
                  <div class="ops-message-head">
                    <span>{{ item.senderType === 'CUSTOMER' ? '我' : item.senderType === 'SUPPORT_AGENT' ? '人工客服' : '系统' }}</span>
                    <span class="ops-message-meta"><button v-if="canRecallMessage(item)" type="button" @click="recallMessage(item)">撤回</button>{{ formatMessageTime(item.sentAt) }}</span>
                  </div>
                  <p>{{ item.content }}</p>
                </article>
                <p v-if="!humanMessages.length" class="ops-human-empty">已发起人工服务请求，客服接入后可在这里实时沟通。</p>
              </section>

              <section v-else-if="hasChat" class="ops-conversation">
                <p v-if="diagnosisNotice" class="ops-ai-notice" role="status">{{ diagnosisNotice }}</p>
                <div v-if="running" class="ops-processing-state" role="status" aria-live="polite">
                  <i aria-hidden="true"></i>
                  <div><b>{{ processingLabel }}</b><small>后台正在安全处理，请勿重复提交</small></div>
                </div>
                <!-- 用户提交的问题显示块 -->
                <article class="ops-message user">
                  <div class="ops-message-head">
                    <span>{{ isCustomer ? '您的问题' : 'Customer issue' }}</span>
                    <span>{{ ticketNo || 'TK-UNKNOWN' }} · {{ businessNo || 'N/A' }}</span>
                  </div>
                  <p>{{ submittedText }}</p>
                </article>

                <!-- Agent 思考与步骤展示块 -->
                <article class="ops-message">
                  <div class="ops-message-head">
                    <span>{{ isCustomer ? '智能客服' : 'SupportOps Agent' }}</span>
                    <span>{{ activeFlow.scenario }}</span>
                  </div>
                  <div class="ops-thinking">
                    <canvas ref="thinkingCanvas" class="ops-mini-canvas"></canvas>
                    <div>
                      <p>{{ activeFlow.summary }}</p>
                      <div class="ops-step-list">
                        <div
                          v-for="(step, index) in activeFlow.steps"
                          :key="step.code || step.title"
                          class="ops-step"
                          :class="{ visible: index <= visibleStep }"
                        >
                          <span>{{ index + 1 }}</span>{{ step.title }} · {{ isCustomer ? statusLabel(step.status) : step.status }}
                        </div>
                      </div>
                    </div>
                  </div>
                </article>

                <!-- 诊断答案展示块：基于 evidence 展示证据与回复 -->
                <article v-if="showAnswer" class="ops-message">
                  <div class="ops-message-head">
                    <span>{{ isCustomer ? '查询结果' : 'Diagnosis answer' }}</span>
                    <span>{{ isCustomer ? '基于系统数据' : 'Evidence based' }}</span>
                  </div>
                  <div class="ops-answer-grid">
                    <div>
                      <p>{{ activeFlow.reply }}</p>
                      <div class="ops-evidence-chips">
                        <span v-for="item in activeFlow.evidences" :key="`${item.source}-${item.field}`">{{ item.label }}: {{ item.value }}</span>
                      </div>
                    </div>
                    <canvas ref="answerCanvas" class="ops-answer-canvas"></canvas>
                  </div>
                </article>
              </section>
            </div>

            <!-- 输入表单：填写描述、工单号与业务号并提交触发诊断 -->
            <form class="ops-composer-wrap" @submit.prevent="submitPrompt()">
              <div class="ops-composer">
                <textarea v-model="message" :disabled="running || handoffLoading" :placeholder="humanConversationNo ? '输入要发送给人工客服的消息' : isCustomer ? '请描述您遇到的问题' : '描述需要诊断的工单问题'"></textarea>
                <div v-if="diagnosisAttachments.length" class="ops-composer-files">
                  <span v-for="(file, index) in diagnosisAttachments" :key="`${file.name}-${file.lastModified}`">
                    📎 {{ file.name }} · {{ formatFileSize(file.size) }}
                    <button type="button" aria-label="移除附件" @click="removeDiagnosisAttachment(index)">×</button>
                  </span>
                </div>
                <div class="ops-composer-row" :class="{ 'customer-order-only': isCustomer }">
                  <input ref="diagnosisFileInput" class="ops-hidden-file" type="file" multiple accept="image/*,.pdf,.txt,.doc,.docx,.xls,.xlsx" @change="selectDiagnosisAttachments" />
                  <button type="button" class="ops-attachment-button" :disabled="running || handoffLoading" title="添加附件" aria-label="添加附件" @click="diagnosisFileInput?.click()"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8.5 12.5 14 7a3 3 0 0 1 4.24 4.24l-7.07 7.07a5 5 0 0 1-7.07-7.07l7.43-7.43" /></svg></button>
                  <input v-if="!isCustomer" v-model="ticketNo" placeholder="工单号" />
                  <select v-if="isCustomer" v-model="businessNo" :disabled="running || handoffLoading" @change="selectCustomerOrder"><option value="">选择相关订单（可选）</option><option v-for="item in customerOrders" :key="item.id" :value="item.id">{{ item.title }}</option></select>
                  <input v-else v-model="businessNo" placeholder="订单号 / 业务号" />
                  <button type="submit" :disabled="running || handoffLoading || (!message.trim() && !diagnosisAttachments.length)" :title="humanConversationNo ? '发送给人工客服' : '启动诊断'">{{ running || handoffLoading ? '…' : '→' }}</button>
                </div>
              </div>
              <button v-if="isCustomer && !humanConversationNo" type="button" class="ops-human-link" :disabled="handoffLoading" @click="requestHumanService">
                {{ handoffLoading ? '正在切换…' : parkedHumanConversationNo ? (hasChat && currentDiagnosisId ? '携诊断返回人工会话' : '返回人工会话') : '转人工' }}
              </button>
              <p v-if="handoffNotice" class="ops-handoff-notice" role="status">{{ handoffNotice }}</p>
              <p v-if="diagnosisError" role="alert">{{ diagnosisError }}</p>
            </form>
          </div>

          <!-- 侧边流程面板：显示当前 procedure 详细步骤与指引 -->
          <aside class="ops-procedure-panel">
            <div class="ops-procedure-top">
              <h2>{{ isCustomer ? '处理过程' : 'Procedure' }}</h2>
              <div>
                <button v-if="!isCustomer" type="button" :disabled="!currentDiagnosisId" @click="discardCurrent">Discard</button>
                <button v-if="!isCustomer" type="button" class="dark" :disabled="!currentDiagnosisId" @click="applyCurrent">Apply</button>
                <button type="button" class="close" @click="procedureOpen = false">×</button>
              </div>
            </div>

            <div class="ops-procedure-body">
              <h1>{{ isCustomer ? procedureTitle(activeFlow.title) : activeFlow.title }}</h1>
              <dl class="ops-meta">
                <dt>{{ isCustomer ? '适用范围' : 'Audience' }}</dt>
                <dd>{{ activeFlow.audience }}</dd>
              </dl>
              <h2>{{ isCustomer ? '处理说明' : 'Instructions' }}</h2>

              <div
                v-for="(item, index) in activeFlow.instructions"
                :key="`${item.order}-${item.action}-${item.tool}`"
                class="ops-instruction"
                :class="{ visible: index <= visibleInstruction }"
              >
                <!-- 每条 instruction 包含索引、动作、工具与说明 -->
                <span>{{ item.order }}</span>
                <div>
                  <b><em>{{ isCustomer ? instructionActionLabel(item.action) : item.action }}</em><template v-if="!isCustomer">{{ item.tool }}</template></b>
                  <p>{{ item.text }}</p>
                </div>
              </div>

              <article class="ops-procedure-reply" :class="{ visible: procedureReplyVisible }">
                <h3>客服回复</h3>
                <p>{{ activeFlow.reply }}</p>
              </article>
            </div>
          </aside>
        </section>
      </section>
    </div>
  </main>
</template>

<script setup>
// 工作台是纯前端演示页面，使用定时器和 Canvas 模拟诊断过程。
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import BrandMark from '../components/BrandMark.vue'
import { getCurrentUser } from '../api/auth.js'
import { applyDiagnosis, discardDiagnosis, getDiagnosisHistory, uploadDiagnosisAttachments } from '../api/diagnosis.js'
import { getCustomerConversation, recallCustomerMessage, requestHumanHandoff, searchPortalModule, sendCustomerConversationMessage } from '../api/portal.js'
import { ROLE, primaryRole } from '../auth/roles.js'
import { useDiagnosis } from '../composables/useDiagnosis.js'

// 不同问题类型对应的诊断步骤、证据和客服回复模板。
const flows = {
  payment: {
    title: 'Payment callback recovery',
    scenario: '支付成功但订单未更新',
    summary: '识别为支付成功但订单状态未同步，需要核对订单状态、支付流水和回调结果。',
    steps: ['理解客户问题', '提取订单号', '查询订单状态', '查询支付流水', '生成证据链', '输出客服回复'],
    evidences: ['支付流水 SUCCESS', '订单仍为 PENDING', '支付回调 FAILED'],
    code: 'payment_status == "SUCCESS" && order_status == "PENDING"',
    reply: '您好，经核查您的支付已成功，但订单状态暂未同步，原因是支付回调处理失败。我们将为您触发订单状态补偿，处理完成后订单会恢复正常。',
    instructions: [
      { index: '1.', action: 'Call', tool: 'OrderQueryTool', text: '查询订单状态、订单金额和最近更新时间。若订单仍为待支付，继续核对支付流水。' },
      { index: '2.', action: 'Call', tool: 'PaymentQueryTool', text: '确认支付流水是否为 SUCCESS，并检查支付回调状态、回调时间和失败原因。' },
      { index: '2.1', action: 'If', tool: 'callback failed', text: '当支付成功但订单仍为 PENDING 时，判断为支付回调失败导致状态未同步。' },
      { index: '3.', action: 'Create', tool: 'evidence chain', text: '生成证据链：支付成功、订单未更新、回调失败，并关联工单与订单号。' },
      { index: '4.', action: 'Reply', tool: 'customer response', text: '生成客服回复并建议触发订单状态补偿任务。' }
    ]
  },
  refund: {
    title: 'Refund closure recovery',
    scenario: '订单已取消但仍扣款',
    summary: '识别为订单取消后的扣款或退款闭环异常，需要核对订单、支付和退款记录。',
    steps: ['理解客户问题', '确认订单取消', '核对支付流水', '检查退款记录', '生成退款建议', '输出客服回复'],
    evidences: ['订单 CANCELLED', '支付流水 SUCCESS', '退款缺失或失败'],
    code: 'order_status == "CANCELLED" && payment_status == "SUCCESS"',
    reply: '您好，经核查您的订单已取消，但支付流水显示扣款成功，目前退款流程未完成。我们建议为您创建或重试退款单，并同步处理进度。',
    instructions: [
      { index: '1.', action: 'Call', tool: 'OrderQueryTool', text: '确认订单是否已取消，并读取取消时间。' },
      { index: '2.', action: 'Call', tool: 'PaymentQueryTool', text: '检查是否存在成功扣款流水。' },
      { index: '3.', action: 'Call', tool: 'RefundQueryTool', text: '检查退款单是否缺失或退款失败。' },
      { index: '4.', action: 'Action', tool: 'create refund', text: '若退款流程未闭环，建议创建或重试退款单。' }
    ]
  },
  api: {
    title: 'API failure investigation',
    scenario: 'API 调用频繁失败',
    summary: '识别为接口稳定性问题，需要聚合时间窗口内失败率和错误码。',
    steps: ['理解问题范围', '聚合调用记录', '计算失败率', '识别错误码', '生成排查建议', '输出处理建议'],
    evidences: ['10 分钟失败率 > 30%', '错误集中 TIMEOUT/502', '异常集中在同一 endpoint'],
    code: 'failure_rate(last_10_min) > 0.30',
    reply: '系统检测到该接口近期失败率较高，错误集中在超时或下游异常。建议升级技术支持检查接口稳定性。',
    instructions: [
      { index: '1.', action: 'Call', tool: 'ApiCallQueryTool', text: '查询最近 10 分钟接口调用记录。' },
      { index: '2.', action: 'Compute', tool: 'failure rate', text: '统计失败率，并聚合 TIMEOUT、502、RATE_LIMITED 等错误码。' },
      { index: '3.', action: 'If', tool: 'rate exceeds threshold', text: '若失败率超过 30%，生成接口异常诊断结论。' },
      { index: '4.', action: 'Escalate', tool: 'technical support', text: '建议升级技术支持检查下游服务稳定性。' }
    ]
  }
}

// 左侧工具栏、快捷操作、历史会话和首页模板数据。
const railIcons = [
  { icon: '◼', label: 'Inbox' },
  { icon: '⌕', label: 'Search' },
  { icon: '✓', label: 'Agent', active: true },
  { icon: '●', label: 'Monitor' },
  { icon: '▦', label: 'Data' },
  { icon: '…', label: 'More' }
]
const sideActions = [
  { icon: '⌕', label: 'Search', action: 'search' },
  { icon: '+', label: 'New chat', action: 'new' },
  { icon: '●', label: 'Scheduled', action: 'history' }
]
const history = ref([])
const historyVisible = ref(false)
const historySearchOpen = ref(false)
const historyKeyword = ref('')
const filteredHistory = computed(() => history.value.filter((item) => {
  const keyword = historyKeyword.value.toLowerCase()
  return !keyword || [item.title, item.description, item.ticketNo, item.businessNo]
    .some((value) => String(value || '').toLowerCase().includes(keyword))
}))
const quickActions = [
  { type: 'payment', icon: '◼', title: 'Create a payment recovery procedure', text: 'Build a step-by-step workflow for order payment issues' },
  { type: 'api', icon: '☁', title: 'Review API failure monitors', text: 'See which integrations are failing and why' },
  { type: 'refund', icon: '●', title: 'Investigate refund exception', text: 'Check refund closure and customer reply guidance' }
]

// Canvas 引用分别用于页面背景、分析过程图和证据置信度图。
const ambientCanvas = ref(null)
const thinkingCanvas = ref(null)
const answerCanvas = ref(null)
const scrollArea = ref(null)
const message = ref('')
const ticketNo = ref('')
const businessNo = ref('')
const currentUser = ref(null)
const isCustomer = computed(() => primaryRole(currentUser.value) === ROLE.CUSTOMER)
const customerTickets = ref([])
const customerOrders = ref([])
const diagnosisFileInput = ref(null)
const diagnosisAttachments = ref([])
const handoffLoading = ref(false)
const handoffNotice = ref('')
const humanConversationNo = ref('')
const parkedHumanConversationNo = ref('')
const humanConversation = ref(null)
const humanMessages = computed(() => humanConversation.value?.messages || [])
const route = useRoute()
const activeFlow = ref(flows.payment)
const submittedText = ref('')
const hasChat = ref(false)
const { diagnosis, submitting, error: diagnosisError, submit: submitDiagnosis, load: loadDiagnosis } = useDiagnosis()
const running = computed(() => submitting.value)
const processingLabel = computed(() => {
  const status = diagnosis.value?.status
  return ({
    PENDING: '任务已进入队列…',
    UNDERSTANDING: '正在理解工单…',
    QUERYING: '正在查询订单与业务数据…',
    DIAGNOSING: '正在执行 Java 诊断规则…',
    GENERATING_REPLY: '正在生成客服回复…'
  })[status] || '智能体正在思考…'
})

function statusLabel(status) {
  return ({
    PENDING: '等待处理',
    RUNNING: '处理中',
    SUCCESS: '已完成',
    DEGRADED: '已使用安全回复',
    FAILED: '处理失败',
    SKIPPED: '已跳过'
  })[status] || '处理中'
}

function procedureTitle(title) {
  return ({
    'Order facts lookup': '订单信息查询',
    'Payment callback recovery': '支付状态核查',
    'Cancelled order refund recovery': '取消订单退款核查',
    'Coupon eligibility explanation': '优惠券使用条件核查',
    'Member benefit grant recovery': '会员权益到账核查',
    'Logistics route lookup': '物流路线与进度查询',
    'Logistics status synchronization': '物流状态核查',
    'API failure window analysis': '接口异常核查',
    'Invoice qualification validation': '发票开具条件核查',
    'Product trace anomaly analysis': '产品溯源核查'
  })[title] || title
}

function instructionActionLabel(action) {
  return ({
    Understand: '理解需求', Query: '查询数据', Reply: '生成回复',
    Call: '查询数据', Compare: '核对信息', Verify: '核验信息',
    Recover: '处理问题', Escalate: '升级处理', Explain: '说明原因',
    Aggregate: '汇总信息', Group: '分类核对', Validate: '校验信息',
    Request: '请求补充', Sync: '同步状态', Create: '整理结果', If: '条件判断'
  })[action] || action
}
const currentDiagnosisId = computed(() => diagnosis.value?.diagnosisId)
const diagnosisNotice = computed(() => diagnosis.value?.errorCode?.includes('AI_QUOTA_EXHAUSTED')
  ? (diagnosis.value.errorMessage || '模型额度已用完，当前已停止调用大模型。')
  : '')
const procedureOpen = ref(false)
const showAnswer = ref(false)
const visibleStep = ref(-1)
const visibleInstruction = ref(-1)
const procedureReplyVisible = ref(false)

// 保存定时器和动画帧编号，便于重新提交及组件卸载时统一清理。
let ambientFrame
let thinkingFrame
let answerFrame
let humanConversationTimer

function handleSideAction(action) {
  if (action === 'new') startNewChat()
  if (action === 'history') {
    historyVisible.value = !historyVisible.value
    if (historyVisible.value) refreshHistory()
  }
  if (action === 'search') {
    historySearchOpen.value = !historySearchOpen.value
    historyVisible.value = true
  }
}

function startNewChat() {
  message.value = ''
  ticketNo.value = ''
  businessNo.value = ''
  submittedText.value = ''
  hasChat.value = false
  humanConversationNo.value = ''
  parkedHumanConversationNo.value = ''
  humanConversation.value = null
  handoffNotice.value = ''
  diagnosisAttachments.value = []
  procedureOpen.value = false
  showAnswer.value = false
  window.clearInterval(humanConversationTimer)
}

// 快捷模板只负责填入真实演示数据，场景判断由后端 AI/规则链完成。
function startFromTemplate(type) {
  const templates = {
    payment: { ticketNo: 'TK-0706-001', businessNo: 'O202607060001', text: '客户说已经支付成功，银行卡也扣款了，但是订单仍显示待支付，请帮我诊断原因并生成客服回复。' },
    refund: { ticketNo: 'TK-0706-002', businessNo: 'O202607060002', text: '客户反馈订单已经取消但仍然扣款，请检查支付和退款流程是否闭环。' },
    api: { ticketNo: 'TK-0706-007', businessNo: '/api/payment/callback', text: '请检查最近 API 调用频繁失败的问题，统计失败率、错误码和下游服务状态。' }
  }
  const template = templates[type]
  ticketNo.value = template.ticketNo
  businessNo.value = template.businessNo
  message.value = template.text
  submitPrompt()
}

// 提交真实异步诊断；useDiagnosis 负责 800ms 轮询与旧请求取消。
async function submitPrompt() {
  const text = message.value.trim()
  if (running.value) return
  if (humanConversationNo.value) {
    if (!text && !diagnosisAttachments.value.length) return
    await sendHumanMessage(text)
    return
  }
  if (!text) return
  if (isCustomer.value && /(转人工客服|人工客服|转人工)/.test(text)) {
    await requestHumanService()
    return
  }
  if (!ticketNo.value.trim()) {
    diagnosisError.value = isCustomer.value ? '请选择有关联工单的订单后再启动诊断' : '请输入工单号'
    return
  }
  submittedText.value = text
  // 提交内容已经进入会话记录，编辑框立即清空，避免用户误以为尚未发送或重复提交。
  message.value = ''
  hasChat.value = true
  procedureOpen.value = false
  showAnswer.value = false
  visibleStep.value = -1
  visibleInstruction.value = -1
  procedureReplyVisible.value = false
  await nextTick()
  drawThinkingCanvas()
  await submitDiagnosis(
    { ticketNo: ticketNo.value.trim(), businessNo: businessNo.value.trim() || null, description: text },
    async (task) => {
      if (!diagnosisAttachments.value.length) return
      await uploadDiagnosisAttachments(task.diagnosisId, diagnosisAttachments.value)
      diagnosisAttachments.value = []
      if (diagnosisFileInput.value) diagnosisFileInput.value.value = ''
    }
  )
  await refreshHistory()
}

function selectDiagnosisAttachments(event) {
  const merged = [...diagnosisAttachments.value, ...(event.target.files || [])]
  if (merged.length > 5) {
    diagnosisError.value = '每次最多添加 5 个附件'
  } else {
    const oversized = merged.find((file) => file.size > 5 * 1024 * 1024)
    if (oversized) diagnosisError.value = `附件 ${oversized.name} 超过 5MB`
    else {
      diagnosisError.value = ''
      diagnosisAttachments.value = merged
    }
  }
  event.target.value = ''
}

function removeDiagnosisAttachment(index) {
  diagnosisAttachments.value.splice(index, 1)
}

function formatFileSize(size) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

async function requestHumanService() {
  if (!isCustomer.value || handoffLoading.value) return
  handoffLoading.value = true
  handoffNotice.value = ''
  try {
    const result = await requestHumanHandoff({
      content: '',
      ticketNo: ticketNo.value.trim() || null,
      businessNo: businessNo.value.trim() || null,
      conversationNo: humanConversationNo.value || parkedHumanConversationNo.value || null,
      diagnosisId: hasChat.value ? (currentDiagnosisId.value || null) : null
    })
    humanConversationNo.value = result.conversationNo
    parkedHumanConversationNo.value = ''
    hasChat.value = true
    handoffNotice.value = result.assignedAgent
      ? `已通知 ${result.assignedAgent}，会话号 ${result.conversationNo}`
      : `人工服务请求已进入队列，会话号 ${result.conversationNo}`
    await loadHumanConversation()
    startHumanConversationPolling()
  } catch (error) {
    handoffNotice.value = error.message || '请求人工客服失败'
  } finally {
    handoffLoading.value = false
  }
}

function switchToAgentMode() {
  if (!humanConversationNo.value) return
  parkedHumanConversationNo.value = humanConversationNo.value
  humanConversationNo.value = ''
  humanConversation.value = null
  handoffNotice.value = '已切换到智能体；完成诊断后可携诊断返回原人工会话'
  hasChat.value = false
  window.clearInterval(humanConversationTimer)
}

async function sendHumanMessage(content) {
  if (!content || handoffLoading.value) return
  handoffLoading.value = true
  try {
    humanConversation.value = await sendCustomerConversationMessage(
      humanConversationNo.value, content, ticketNo.value || null,
      businessNo.value || null, diagnosisAttachments.value
    )
    message.value = ''
    diagnosisAttachments.value = []
    if (diagnosisFileInput.value) diagnosisFileInput.value.value = ''
  } catch (error) {
    handoffNotice.value = error.message || '消息发送失败'
  } finally {
    handoffLoading.value = false
  }
}

function canRecallMessage(item) {
  if (item.senderType !== 'CUSTOMER' || item.content === '该消息已撤回' || !item.sentAt) return false
  const age = Date.now() - new Date(item.sentAt).getTime()
  return age >= 0 && age <= 60_000
}

async function recallMessage(item) {
  if (!canRecallMessage(item) || handoffLoading.value) return
  handoffLoading.value = true
  try {
    humanConversation.value = await recallCustomerMessage(humanConversationNo.value, item.id)
    handoffNotice.value = '消息已撤回'
  } catch (error) {
    handoffNotice.value = error.message || '消息撤回失败'
    await loadHumanConversation()
  } finally {
    handoffLoading.value = false
  }
}

async function loadHumanConversation() {
  if (!humanConversationNo.value) return
  try {
    humanConversation.value = await getCustomerConversation(humanConversationNo.value)
    await nextTick()
    if (scrollArea.value) scrollArea.value.scrollTop = scrollArea.value.scrollHeight
  } catch (error) {
    handoffNotice.value = error.message || '人工会话加载失败'
  }
}

function startHumanConversationPolling() {
  window.clearInterval(humanConversationTimer)
  humanConversationTimer = window.setInterval(loadHumanConversation, 3000)
}

function formatMessageTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : ''
}

async function loadCustomerBusinessRecords() {
  try {
    const [orders, tickets] = await Promise.all([
      searchPortalModule(ROLE.CUSTOMER, 'orders'),
      searchPortalModule(ROLE.CUSTOMER, 'tickets')
    ])
    customerOrders.value = orders.items || []
    customerTickets.value = tickets.items || []
  } catch (error) {
    diagnosisError.value = error.message || '加载您的订单失败'
  }
}

function selectCustomerOrder() {
  const related = customerTickets.value.find((item) => item.title?.includes(businessNo.value))
  ticketNo.value = related?.id || ''
}

async function refreshHistory() {
  try { history.value = await getDiagnosisHistory(20) }
  catch (error) { if (error.status === 401) diagnosisError.value = '登录已过期，请重新登录。' }
}

async function loadHistory(item) {
  submittedText.value = item.description || item.title
  ticketNo.value = item.ticketNo
  businessNo.value = item.businessNo || ''
  hasChat.value = true
  await loadDiagnosis(item.diagnosisId)
}

async function applyCurrent() {
  if (!currentDiagnosisId.value) return
  await applyDiagnosis(currentDiagnosisId.value)
  await refreshHistory()
}

async function discardCurrent() {
  if (!currentDiagnosisId.value) return
  await discardDiagnosis(currentDiagnosisId.value)
  procedureOpen.value = false
  await loadDiagnosis(currentDiagnosisId.value)
  await refreshHistory()
}

// 将后端聚合报告转换为现有视觉组件需要的轻量视图，不生成任何业务事实。
watch(diagnosis, (detail) => {
  if (!detail) return
  const steps = detail.steps || []
  const procedure = detail.procedure || {}
  activeFlow.value = {
    title: detail.title || '诊断处理中',
    scenario: detail.scenarioName || detail.status,
    summary: detail.summary || '正在收集业务信号并执行诊断规则…',
    steps,
    evidences: detail.evidences || [],
    reply: detail.customerReply || detail.errorMessage || '诊断尚未完成。',
    audience: procedure.audience || '客服 / 技术支持',
    instructions: procedure.instructions || []
  }
  visibleStep.value = Math.max(-1, steps.findLastIndex((step) => step.status !== 'PENDING'))
  const finished = ['SUCCESS', 'DEGRADED_SUCCESS'].includes(detail.status)
  showAnswer.value = finished
  procedureOpen.value = finished && Boolean(procedure.instructions?.length)
  visibleInstruction.value = finished ? (procedure.instructions?.length || 0) - 1 : -1
  procedureReplyVisible.value = finished
  nextTick(() => {
    if (showAnswer.value) drawAnswerCanvas()
    if (scrollArea.value) scrollArea.value.scrollTop = scrollArea.value.scrollHeight
  })
}, { deep: true })

// 让 Canvas 适配设备像素比，避免在高分屏上绘制模糊。
function setupCanvas(canvas) {
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const rect = canvas.getBoundingClientRect()
  canvas.width = rect.width * dpr
  canvas.height = rect.height * dpr
  const ctx = canvas.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  return { ctx, width: rect.width, height: rect.height }
}

// 绘制分析节点之间的关系和脉冲动画。
function drawThinkingCanvas() {
  cancelAnimationFrame(thinkingFrame)
  const canvas = thinkingCanvas.value
  if (!canvas) return
  let time = 0

  function draw() {
    const { ctx, width, height } = setupCanvas(canvas)
    time += 0.03
    ctx.clearRect(0, 0, width, height)
    ctx.fillStyle = '#f8f5ef'
    ctx.fillRect(0, 0, width, height)
    const nodes = [
      [28, 34, 'Order'],
      [126, 30, 'Pay'],
      [48, 88, 'SOP'],
      [126, 88, 'Reply']
    ]
    ctx.strokeStyle = 'rgba(151, 100, 184, .42)'
    ctx.lineWidth = 1.4
    nodes.forEach((node, index) => {
      const next = nodes[(index + 1) % nodes.length]
      ctx.beginPath()
      ctx.moveTo(node[0], node[1])
      ctx.lineTo(next[0], next[1])
      ctx.stroke()
    })
    nodes.forEach((node, index) => {
      const pulse = Math.sin(time * 2 + index) * 3
      ctx.beginPath()
      ctx.arc(node[0], node[1], 8 + pulse, 0, Math.PI * 2)
      ctx.fillStyle = index === 1 ? '#55b6f2' : '#2fa66a'
      ctx.fill()
      ctx.fillStyle = '#5e5850'
      ctx.font = '10px Segoe UI'
      ctx.fillText(node[2], node[0] - 16, node[1] + 24)
    })
    thinkingFrame = requestAnimationFrame(draw)
  }
  draw()
}

// 用动态柱状图表现证据置信度。
function drawAnswerCanvas() {
  cancelAnimationFrame(answerFrame)
  const canvas = answerCanvas.value
  if (!canvas) return
  let time = 0

  function draw() {
    const { ctx, width, height } = setupCanvas(canvas)
    time += 0.025
    ctx.clearRect(0, 0, width, height)
    ctx.fillStyle = '#fbf8f2'
    ctx.fillRect(0, 0, width, height)
    for (let index = 0; index < 5; index += 1) {
      const x = 26 + index * 27
      const barHeight = 24 + Math.sin(time * 3 + index) * 10 + index * 6
      ctx.fillStyle = index < 3 ? '#2fa66a' : '#d8c85b'
      ctx.fillRect(x, height - 24 - barHeight, 15, barHeight)
    }
    ctx.fillStyle = '#5f5a53'
    ctx.font = '11px Segoe UI'
    ctx.fillText('Evidence confidence', 24, 24)
    answerFrame = requestAnimationFrame(draw)
  }
  draw()
}

// 根据视口尺寸重设背景 Canvas，并返回当前绘图上下文。
function resizeAmbient() {
  const canvas = ambientCanvas.value
  if (!canvas) return null
  const dpr = Math.min(window.devicePixelRatio || 1, 2)
  const width = window.innerWidth
  const height = window.innerHeight
  canvas.width = width * dpr
  canvas.height = height * dpr
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`
  const ctx = canvas.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  return { ctx, width, height }
}

// 绘制渐变、流动色块和轻微摆动的网格背景。
function drawAmbient() {
  let time = 0
  function blob(ctx, cx, cy, radius, color, speed) {
    ctx.beginPath()
    for (let index = 0; index <= 120; index += 1) {
      const angle = (Math.PI * 2 * index) / 120
      const r = radius + Math.sin(angle * 3 + time * speed) * 18 + Math.cos(angle * 5 - time * 0.8) * 9
      const x = cx + Math.cos(angle) * r
      const y = cy + Math.sin(angle) * r
      if (index === 0) ctx.moveTo(x, y)
      else ctx.lineTo(x, y)
    }
    ctx.closePath()
    ctx.fillStyle = color
    ctx.fill()
  }

  function draw() {
    const scene = resizeAmbient()
    if (!scene) return
    const { ctx, width, height } = scene
    time += 0.01
    const gradient = ctx.createLinearGradient(0, 0, width, height)
    gradient.addColorStop(0, '#fbfaf6')
    gradient.addColorStop(0.34, '#f5efe6')
    gradient.addColorStop(0.68, '#eef4f4')
    gradient.addColorStop(1, '#f6f1f8')
    ctx.fillStyle = gradient
    ctx.fillRect(0, 0, width, height)
    blob(ctx, width * 0.18 + Math.sin(time) * 22, height * 0.1, 220, 'rgba(223, 207, 151, .42)', 1.1)
    blob(ctx, width * 0.74 + Math.cos(time * 0.75) * 26, height * 0.2, 260, 'rgba(139, 87, 199, .20)', 0.9)
    blob(ctx, width * 0.83 + Math.sin(time * 0.86) * 22, height * 0.78, 205, 'rgba(85, 182, 242, .20)', 1.15)
    ctx.globalAlpha = 0.14
    ctx.strokeStyle = '#d7d0c4'
    for (let x = 0; x < width; x += 54) {
      ctx.beginPath()
      ctx.moveTo(x, 0)
      ctx.lineTo(x + Math.sin(time + x * 0.02) * 2, height)
      ctx.stroke()
    }
    for (let y = 0; y < height; y += 54) {
      ctx.beginPath()
      ctx.moveTo(0, y)
      ctx.lineTo(width, y + Math.cos(time + y * 0.02) * 2)
      ctx.stroke()
    }
    ctx.globalAlpha = 1
    ambientFrame = requestAnimationFrame(draw)
  }
  draw()
}

onMounted(async () => {
  try { currentUser.value = await getCurrentUser() } catch { currentUser.value = null }
  if (isCustomer.value) {
    message.value = ''
    ticketNo.value = ''
    businessNo.value = ''
    await loadCustomerBusinessRecords()
  } else {
    // 内部工作台可通过路由接收待诊断上下文；客户入口始终保持空白。
    if (typeof route.query.ticketNo === 'string') ticketNo.value = route.query.ticketNo
    if (typeof route.query.businessNo === 'string') businessNo.value = route.query.businessNo
    if (typeof route.query.description === 'string') message.value = route.query.description
  }
  drawAmbient()
  refreshHistory()
  window.addEventListener('resize', resizeAmbient)
})

// 释放计时器、事件监听和动画帧，防止离开页面后继续运行。
onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeAmbient)
  cancelAnimationFrame(ambientFrame)
  cancelAnimationFrame(thinkingFrame)
  cancelAnimationFrame(answerFrame)
  window.clearInterval(humanConversationTimer)
})
</script>
