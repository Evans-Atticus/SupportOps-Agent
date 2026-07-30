<template>
  <!-- 诊断首页：导航、诊断流程、雷达图、视频和待诊断队列。 -->
  <main class="diagnostic-home">
    <div class="diag-orbit" aria-hidden="true"><i></i><b></b><span></span><span></span><span></span></div>
    <header class="diag-nav">
      <DiagnosticBrandLink />

      <nav class="diag-links" aria-label="Primary navigation">
        <button v-for="item in navItems" :key="item.cn" type="button" :class="{ active: item.active, reserved: item.reserved }"
                :title="item.reserved ? '功能建设中，入口暂时保留' : ''"
                @click="openNavigation(item)">
          <b>{{ item.cn }}</b><small>{{ item.en }}</small>
        </button>
      </nav>

      <div class="diag-nav-actions">
        <RouterLink v-if="!currentUser" class="diag-login-button" to="/login">Login</RouterLink>
        <span class="system-live"><i></i>系统运行中<small>System Online</small></span>
        <!-- 会话用户控件：未登录时禁用，登录后通过悬停或键盘聚焦打开账户菜单。 -->
        <div class="home-user-control" :class="{ 'is-authenticated': currentUser }">
          <button v-if="currentUser" type="button" class="home-user-trigger" aria-haspopup="menu">
            <span class="home-user-avatar" aria-hidden="true">{{ avatarInitial }}</span>
            <span class="home-user-copy"><b>{{ currentUser.displayName }}</b><small>@{{ currentUser.username }}</small></span>
            <span class="home-user-chevron">⌄</span>
          </button>
          <button v-else type="button" class="home-user-trigger is-disabled" aria-haspopup="menu">
            <span class="home-user-avatar" aria-hidden="true">●</span>
            <span class="home-user-copy"><b>{{ sessionLoading ? '读取中' : '未登录' }}</b><small>{{ sessionLoading ? 'Loading' : 'No account' }}</small></span>
            <span class="home-user-chevron">⌄</span>
          </button>

          <div v-if="currentUser" class="home-user-menu" role="menu">
            <header><span class="home-user-avatar">{{ avatarInitial }}</span><p><b>{{ currentUser.displayName }}</b><small>{{ currentUser.username }}</small></p></header>
            <button type="button" role="menuitem" @click="goToPersonalCenter"><span>⌂</span>进入个人中心<small>Open personal center</small></button>
            <button type="button" role="menuitem" @click="switchAccount"><span>⇄</span>切换账户<small>Switch account</small></button>
            <button type="button" role="menuitem" class="logout-action" @click="signOut"><span>↗</span>退出账户<small>Sign out</small></button>
          </div>
          <div v-else-if="!sessionLoading" class="home-user-menu home-user-menu--guest" role="menu">
            <button type="button" role="menuitem" @click="goToLogin"><span>→</span>登录账户<small>Sign in to continue</small></button>
          </div>
        </div>
      </div>
    </header>

    <section class="diag-layout">
      <aside class="diag-left-rail">
        <article class="diag-intro diag-glass">
          <span class="diag-kicker">诊断洞察 <i>/</i> INSIGHT</span>
          <b class="sparkle">✦</b>
          <h1>每一次诊断<br />都推动更快解决。</h1>
          <p>AI 智能识别工单异常，快速定位根因，给出精准诊断建议。</p>
        </article>

        <article class="diag-glass diag-stats">
          <div v-for="stat in statistics" :key="stat.label"><span>{{ stat.label }}<small>{{ stat.en }}</small></span><strong>{{ stat.value }}</strong><em>{{ stat.delta }}</em></div>
        </article>

        <article class="diag-glass distribution-card">
          <span class="card-heading">今日工单分布 <small>/ Today's ticket distribution</small></span>
          <div class="distribution-dots"><i>▦</i><i>▰</i><i>⌂</i><b>+12</b></div>
          <div class="distribution-breakdown"><span><b>42%</b>订单延迟</span><span><b>31%</b>物流异常</span><span><b>27%</b>仓储问题</span></div>
          <p>订单、物流、仓储等多源信号实时关联</p>
        </article>
      </aside>

      <!-- 中央诊断控制台：展示当前诊断模式和两步处理流程。 -->
      <section class="diagnosis-console diag-glass">
        <div class="console-orbit" aria-hidden="true"><i></i><b></b><span></span><span></span><span></span></div>
        <header class="console-header">
          <div>
            <span class="diag-kicker">诊断工作台 <i>/</i> DIAGNOSIS CONTROL PLANE</span>
            <h2>订单延迟升级工单</h2>
            <p>针对订单延迟问题，进行根因定位与处置建议推荐。</p>
          </div>
          <div class="mode-tabs" role="tablist">
            <button v-for="mode in modes" :key="mode.cn" type="button" :class="{ active: activeMode === mode.cn }" @click="activeMode = mode.cn"><b>{{ mode.cn }}</b><small>{{ mode.en }}</small></button>
          </div>
        </header>

        <div class="diagnosis-steps">
          <button v-for="(step, index) in workflow" :key="step.title" type="button" class="diagnosis-step" :class="{ active: activeStep === index }" @click="activeStep = index">
            <span>0{{ index + 1 }} / {{ step.phase }}</span>
            <h3>{{ step.title }}</h3>
            <p>{{ step.text }}</p>
            <em>{{ step.badge }}</em><small>{{ step.badgeEn }}</small>
          </button>
          <span class="step-connector">→</span>
        </div>

        <!-- 诊断过程明细与置信度柱状图。 -->
        <div class="console-bottom">
          <article class="trace-card">
            <h3>实时诊断轨迹 <small>/ Live reasoning trace</small></h3>
            <div v-for="item in trace" :key="item.cn" class="trace-row"><i :class="item.type"></i><span>{{ item.cn }}<small>{{ item.en }}</small></span><time>{{ item.time }}</time></div>
          </article>
          <article class="confidence-card">
            <h3>诊断置信度 <small>/ Diagnostic confidence</small><strong>94%</strong><em>高置信<br />High</em></h3>
            <div class="confidence-bars"><i v-for="height in chartBars" :key="height" :style="{ height }"></i></div>
          </article>
        </div>
      </section>

      <aside class="diag-right-rail">
        <article class="radar-card diag-glass">
          <header><span>诊断置信度雷达 <small>/ Diagnostic radar</small></span><b>● 正常<small>Normal</small></b></header>
          <div class="radar-chart">
            <i class="radar-line line-a"></i><i class="radar-line line-b"></i>
            <svg class="radar-trace" viewBox="0 0 120 120" aria-hidden="true">
              <polygon points="17,59 94,31 87,95 14,84" />
              <circle class="trace-node trace-node-a" cx="17" cy="59" r="3.8" />
              <circle class="trace-node trace-node-b" cx="94" cy="31" r="3.8" />
              <circle class="trace-node trace-node-c" cx="87" cy="95" r="3.8" />
              <circle class="trace-node trace-node-d" cx="14" cy="84" r="3.8" />
            </svg>
          </div>
          <div class="radar-labels"><span>历史相似性<small>Historical similarity</small></span><span>证据充分性<small>Evidence sufficiency</small></span><span>模型稳定性<small>Model stability</small></span></div>
        </article>

        <!-- 产品介绍视频：播放按钮通过 YouTube iframe API 控制播放状态。 -->
        <article class="video-card">
          <span>PRODUCT BRIEFING <i>/</i> 产品简报</span>
          <iframe ref="briefingFrame" src="https://www.youtube.com/embed/185XGEMefgc?autoplay=1&mute=1&enablejsapi=1&loop=1&playlist=185XGEMefgc&controls=0&modestbranding=1&rel=0&playsinline=1" title="SOA product briefing" allow="autoplay; encrypted-media; picture-in-picture"></iframe>
          <button type="button" class="video-play" :aria-label="isBriefingPlaying ? 'Pause product briefing' : 'Play product briefing'" @click="toggleBriefingVideo">{{ isBriefingPlaying ? 'Ⅱ' : '▶' }}</button>
          <small>▶ Watch the latest updates</small>
        </article>

        <article class="queue-card diag-glass">
          <h3>待诊断队列 <small>/ Active queue</small></h3>
          <div v-for="item in queue" :key="item.cn"><i :class="item.type"></i><span>{{ item.cn }}<small>{{ item.en }}</small></span><b>{{ item.count }}</b></div>
          <button v-if="isCustomerRole" type="button" @click="router.push('/workspace')">查看全部 <small>/ View all</small><span>›</span></button>
        </article>
      </aside>
    </section>

    <section class="diag-bottom-grid">
      <article class="diag-glass signals-card"><h3>关联信号 <small>/ Related signals</small><button>查看全部 <small>/ View all</small> ›</button></h3><div><i>▦</i><i>▰</i><i>⌂</i><i>♟</i><b>+8</b></div><p>订单、物流、仓储、支付等多源信号实时关联<br /><small>Multi-source signals in real-time</small></p></article>
      <article v-for="metric in bottomMetrics" :key="metric.cn" class="diag-glass metric-card"><h3>{{ metric.cn }} <small>/ {{ metric.en }}</small></h3><strong>{{ metric.value }}</strong><svg viewBox="0 0 120 42" aria-hidden="true"><path :d="metric.path" fill="none" :stroke="metric.color" stroke-width="2.2" stroke-linecap="round" /></svg><p>{{ metric.detail }}</p></article>
    </section>

    <Transition name="access-notice">
      <div v-if="accessDeniedNotice" class="access-denied-notice" role="alert" aria-live="assertive">
        <span aria-hidden="true">!</span>
        <p><b>无访问权限</b><small>{{ accessDeniedNotice }}</small></p>
      </div>
    </Transition>

    <button v-if="isCustomerRole" class="assistant-bubble" type="button" aria-label="打开 SOA 助手" @click="toggleAssistant">?</button>

    <Transition name="assistant-rise">
      <section v-if="assistantOpen && isCustomerRole" class="assistant-panel" aria-label="SOA assistant">
        <div class="assistant-prompts" aria-label="常见问题">
          <button v-for="prompt in prompts" :key="prompt.question" type="button" :class="{ active: selectedQuestion === prompt.question }" @click="selectPrompt(prompt)">{{ prompt.question }}</button>
        </div>
        <div class="assistant-response-card">
          <header><span>SOA ASSISTANT <i>/</i> 智能问答</span><button type="button" aria-label="Close assistant" @click="assistantOpen = false">×</button></header>
          <article>
            <span class="response-label">[ RESPONSE ]</span>
            <h2>{{ selectedQuestion }}</h2>
            <p>{{ selectedAnswer }}</p>
            <div class="assistant-metrics"><strong>76%</strong><span>平均自主解决率</span><strong>1M+</strong><span>每周处理对话</span></div>
          </article>
        </div>
        <form @submit.prevent="submitQuestion"><input v-model="question" placeholder="随便问什么..." /><button type="button" aria-label="切换问题" @click="selectNextPrompt">•••</button><button type="submit" aria-label="Send">↑</button></form>
      </section>
    </Transition>
  </main>
</template>

<script setup>
// 首页只保存演示所需的轻量交互状态，业务数据以静态配置形式集中管理。
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DiagnosticBrandLink from '../components/DiagnosticBrandLink.vue'
import { getDiagnosisHistory } from '../api/diagnosis.js'
import { getAccessToken } from '../api/http.js'
import { getCurrentUser, logout } from '../api/auth.js'
import { ROLE, primaryRole } from '../auth/roles.js'

const activeMode = ref('诊断模式')
const router = useRouter()
const route = useRoute()
const currentUser = ref(null)
const sessionLoading = ref(Boolean(getAccessToken()))
const activeStep = ref(0)
const assistantOpen = ref(false)
const question = ref('')
const briefingFrame = ref(null)
const isBriefingPlaying = ref(true)
const selectedQuestion = ref('SOA能为我做什么？')
const accessDeniedNotice = ref('')
let accessNoticeTimer
const isCustomerRole = computed(() => primaryRole(currentUser.value) === ROLE.CUSTOMER)

// 顶部导航及诊断模式选项。
const navItems = computed(() => [
  { cn: '首页', en: 'Home', active: true },
  { cn: '个人中心', en: 'Profile', to: '/personal-center' },
  ...(isCustomerRole.value
    ? [{ cn: '智能客服', en: 'AI Service', to: '/workspace' }]
    : []),
  ...(primaryRole(currentUser.value) === ROLE.ADMIN
    ? [{ cn: '数据看板', en: 'Analytics', to: '/analytics' }]
    : [])
])
const modes = [{ cn: '诊断模式', en: 'Diagnosis' }, { cn: '模拟模式', en: 'Simulation' }, { cn: '监控模式', en: 'Monitoring' }]
// 左侧概览指标、中央流程、实时轨迹和右侧队列数据。
// 未登录时不伪造统计值；登录后的首页会用当前账号的真实历史实时计算。
const statistics = ref([
  { label: '自动完成率', en: 'Completion rate', value: '--', delta: '登录后可见' },
  { label: '成功诊断率', en: 'Success rate', value: '--', delta: '登录后可见' },
  { label: '诊断任务数', en: 'Diagnosis tasks', value: '--', delta: '最近 100 条' }
])
const workflow = [
  { phase: '识别阶段', title: '识别工单上下文', text: '解析工单内容，提取意图、紧急程度、账户状态与客户情绪。', badge: '已识别低风险', badgeEn: 'Low risk identified' },
  { phase: '验证阶段', title: '交叉验证关键证据', text: '关联订单、物流、仓储与履约数据，核验延迟原因与影响范围。', badge: '置信度 94%', badgeEn: 'Confidence 94%' }
]
const trace = [
  { cn: '已匹配订单 8872456319', en: 'Matched order 8872456319', time: '0.4秒', type: 'aqua' },
  { cn: '物流轨迹核验完成', en: 'Logistics trace verified', time: '0.8秒', type: 'orange' },
  { cn: '检测到仓储出库异常', en: 'Warehouse outbound anomaly detected', time: '1.2秒', type: 'violet' },
  { cn: '延迟原因初步锁定：仓储出库延迟', en: 'Preliminary root cause: warehouse outbound delay', time: '1.6秒', type: 'violet' },
  { cn: '影响范围评估完成', en: 'Impact assessment completed', time: '1.9秒', type: 'green' }
]
const chartBars = ['42%', '54%', '50%', '73%', '66%', '90%', '82%']
const mockQueue = [
  { cn: '订单延迟待诊断', en: 'Demo · Order delay', count: '04', type: 'orange' },
  { cn: '物流异常待诊断', en: 'Demo · Logistics issue', count: '02', type: 'orange' },
  { cn: '退款到账异常', en: 'Demo · Refund arrival', count: '01', type: 'violet' }
]
const queue = ref([...mockQueue])
const bottomMetrics = [
  { cn: '本周诊断工单', en: 'Diagnosed this week', value: '18.4k', detail: '较上周 ↑ 12.7%', color: '#786cf6', path: 'M2 34 C18 16 30 37 47 20 S70 5 84 19 S104 17 118 4' },
  { cn: '已辅助处理', en: 'Assisted resolutions', value: '2,118', detail: '已辅助处置给出诊断建议', color: '#22bdb7', path: 'M2 22 C20 7 31 35 48 23 S77 6 90 21 S109 26 118 13' },
  { cn: '诊断满意度', en: 'Satisfaction score', value: '4.8/5', detail: '客户对诊断准确性的评分', color: '#ff7b36', path: 'M2 31 C17 14 30 36 47 21 S69 7 84 22 S104 14 118 15' }
]
// 助手快捷问题及对应回答。
const prompts = [
  { question: 'SOA能为我做什么？', answer: 'SOA 可以理解客户问题、检索可信知识、执行流程动作，并在需要时将完整上下文交接给人工团队。' },
  { question: 'SOA能带来什么结果？', answer: 'SOA 聚焦真实业务结果：更高的自主解决率、更低的人工压力，以及可持续优化的客户体验。' },
  { question: 'Can SOA 与我的帮助台集成吗？', answer: 'SOA 可以接入现有帮助台、知识库和订单系统，在不替换原有流程的前提下处理复杂工单。' }
]
const selectedAnswer = computed(() => prompts.find(item => item.question === selectedQuestion.value)?.answer || 'SOA 已收到你的问题，会结合工单、知识库与业务信号给出下一步诊断建议。')
const avatarInitial = computed(() => (currentUser.value?.displayName || currentUser.value?.username || 'U').trim().charAt(0).toUpperCase())

/** 顶部导航只对已上线功能执行跳转，其余栏目保持当前演示状态。 */
function openNavigation(item) {
  if (item.to === '/workspace' && !isCustomerRole.value) {
    showAccessDenied()
    return
  }
  if (item.to) router.push(item.to)
}

function showAccessDenied() {
  window.clearTimeout(accessNoticeTimer)
  accessDeniedNotice.value = '智能客服仅对客户账号开放，当前账号无法访问。'
  accessNoticeTimer = window.setTimeout(() => { accessDeniedNotice.value = '' }, 3600)
}

onMounted(async () => {
  if (route.query.accessDenied === 'ai-service') {
    showAccessDenied()
    const query = { ...route.query }
    delete query.accessDenied
    await router.replace({ query })
  }
  if (!getAccessToken()) {
    sessionLoading.value = false
    return
  }
  try {
    // 以后端 /auth/me 为会话真值，不从 JWT 或本地存储伪造用户资料。
    currentUser.value = await getCurrentUser()
    if (!isCustomerRole.value) return
    const items = await getDiagnosisHistory(100)
    const terminal = items.filter((item) => ['SUCCESS', 'DEGRADED_SUCCESS', 'FAILED', 'DISCARDED'].includes(item.status))
    const successful = items.filter((item) => ['SUCCESS', 'DEGRADED_SUCCESS'].includes(item.status))
    statistics.value = [
      { label: '自动完成率', en: 'Completion rate', value: percent(terminal.length, items.length), delta: '真实历史' },
      { label: '成功诊断率', en: 'Success rate', value: percent(successful.length, terminal.length), delta: '真实历史' },
      { label: '诊断任务数', en: 'Diagnosis tasks', value: String(items.length), delta: '最近 100 条' }
    ]
    const active = items.filter((item) => !['SUCCESS', 'DEGRADED_SUCCESS', 'FAILED', 'DISCARDED'].includes(item.status))
    const counts = active.reduce((groups, item) => {
      const key = item.scenarioType || '待识别'
      groups[key] = [...(groups[key] || []), item]
      return groups
    }, {})
    const activeQueue = Object.entries(counts).slice(0, 3).map(([scenario, rows], index) => ({
      cn: scenario, en: 'Active diagnosis', count: String(rows.length).padStart(2, '0'), type: index === 2 ? 'violet' : 'orange'
    }))
    // 当前没有真实待诊断任务时保留演示数据，避免首页队列出现空白板块。
    queue.value = activeQueue.length ? activeQueue : [...mockQueue]
  } catch {
    // 首页失败保持占位，不影响用户进入登录页或工作台查看详细错误。
  } finally {
    sessionLoading.value = false
  }
})

onUnmounted(() => window.clearTimeout(accessNoticeTimer))

/** 退出只清理当前标签页的会话令牌，然后保留在公开首页。 */
function signOut() {
  logout()
  currentUser.value = null
}

/** 切换账户先退出，登录成功后直接进入智能工单诊断平台。 */
async function switchAccount() {
  logout()
  currentUser.value = null
  await router.push({ name: 'login', query: { redirect: '/personal-center' } })
}

/** 从首页登录时预先保留个人中心目标，登录后由角色决定中心内可见模块。 */
async function goToLogin() {
  await router.push({ name: 'login', query: { redirect: '/personal-center' } })
}

async function goToPersonalCenter() {
  await router.push({ name: 'personal-center' })
}

function percent(value, total) {
  return total ? `${Math.round(value * 100 / total)}%` : '0%'
}

// 切换右下角 AI 助手面板。
function toggleAssistant() { assistantOpen.value = !assistantOpen.value }

// 向 YouTube iframe 发送播放器控制命令。
function controlBriefingVideo(command) {
  briefingFrame.value?.contentWindow?.postMessage(JSON.stringify({ event: 'command', func: command, args: [] }), '*')
}
// 播放时解除静音；暂停时仅暂停，不改变用户已经选择的音量状态。
function toggleBriefingVideo() {
  if (isBriefingPlaying.value) {
    controlBriefingVideo('pauseVideo')
  } else {
    controlBriefingVideo('unMute')
    controlBriefingVideo('playVideo')
  }
  isBriefingPlaying.value = !isBriefingPlaying.value
}
// 选择预设问题并同步输入框内容。
function selectPrompt(prompt) { selectedQuestion.value = prompt.question; question.value = prompt.question }
function selectNextPrompt() {
  const currentIndex = prompts.findIndex(item => item.question === selectedQuestion.value)
  selectPrompt(prompts[(currentIndex + 1 + prompts.length) % prompts.length])
}
// 提交自定义问题；演示页面只更新展示标题，不调用后端接口。
function submitQuestion() { if (!question.value.trim()) return; selectedQuestion.value = question.value.trim(); question.value = '' }
</script>
