<template>
  <main class="analytics-page">
    <header class="analytics-header">
      <DiagnosticBrandLink />
      <div class="analytics-heading">
        <span>管理员专属 / SERVICE OPERATIONS ANALYTICS</span>
        <h1>工单与售后数据看板</h1>
        <p>与个人中心“工单与售后统计”共享同一后端统计快照</p>
      </div>
      <RouterLink to="/personal-center?view=ticket-stats">返回工单统计</RouterLink>
    </header>

    <section v-if="loading" class="analytics-state">正在读取统一统计快照…</section>
    <section v-else-if="error" class="analytics-state error">
      <b>数据看板暂时无法加载</b><p>{{ error }}</p><button type="button" @click="loadAnalytics">重新加载</button>
    </section>

    <template v-else-if="snapshot">
      <section class="analytics-toolbar">
        <label>开始日期<input v-model="filters.from" type="date" :max="filters.to || undefined" /></label>
        <label>结束日期<input v-model="filters.to" type="date" :min="filters.from || undefined" /></label>
        <label>受理渠道<select v-model="filters.channel"><option value="">全部渠道</option><option v-for="item in channels" :key="item" :value="item">{{ item }}</option></select></label>
        <label>优先级<select v-model="filters.priority"><option value="">全部优先级</option><option value="URGENT">URGENT</option><option value="HIGH">HIGH</option><option value="NORMAL">NORMAL</option><option value="LOW">LOW</option></select></label>
        <label>工单状态<select v-model="filters.status"><option value="">全部状态</option><option value="OPEN">待处理</option><option value="PROCESSING">处理中</option><option value="RESOLVED">已解决</option><option value="CLOSED">已关闭</option></select></label>
        <button type="button" :disabled="loading || invalidDateRange" @click="loadAnalytics">应用筛选</button>
        <span v-if="invalidDateRange" class="date-range-error">结束日期不能早于开始日期</span>
        <p>生成时间 {{ formatDateTime(snapshot.generatedAt) }}<small>渠道与优先级同时作用于关联退款</small></p>
      </section>

      <section class="analytics-metrics">
        <article v-for="item in metricCards" :key="item.label" :class="item.tone">
          <i>{{ item.icon }}</i>
          <div><span>{{ item.label }}</span><strong>{{ item.value }}</strong><small>{{ item.note }}</small></div>
        </article>
      </section>

      <section class="analytics-grid">
        <article class="analytics-panel trend-panel">
          <header><div><h2>近 7 天业务趋势</h2><p>所选统计范围内最近 7 天的工单新增、已解决工单与退款申请</p></div><ul><li><i class="created"></i>新增工单</li><li><i class="resolved"></i>已解决</li><li><i class="refund"></i>退款申请</li></ul></header>
          <div class="trend-chart">
            <div class="trend-y-axis" aria-label="动态纵轴">
              <span>{{ trendMaximum }}</span><span>{{ Math.ceil(trendMaximum / 2) }}</span><span>0</span>
            </div>
            <div class="trend-series">
              <div v-for="point in visibleTrend" :key="point.date" class="trend-column">
                <div class="trend-bars">
                  <i class="created" :style="{ height: barHeight(point.createdTickets, trendMaximum) }" :title="`新增 ${point.createdTickets}`"></i>
                  <i class="resolved" :style="{ height: barHeight(point.resolvedTickets, trendMaximum) }" :title="`已解决 ${point.resolvedTickets}`"></i>
                  <i class="refund" :style="{ height: barHeight(point.refundRequests, trendMaximum) }" :title="`退款 ${point.refundRequests}`"></i>
                </div>
                <small>{{ shortDate(point.date) }}</small>
              </div>
            </div>
          </div>
        </article>

        <article class="analytics-panel scenario-panel">
          <header><div><h2>问题场景排行</h2><p>直接取自工单 scenario_hint，不使用前端推测分类</p></div></header>
          <ol class="scenario-ranking">
            <li v-for="(item, index) in snapshot.scenarioDistribution" :key="item.key">
              <em>{{ index + 1 }}</em>
              <span><b>{{ scenarioLabel(item.key) }}</b><small>{{ item.key }}</small></span>
              <div><i :style="{ width: percentage(item.value, scenarioMaximum) }"></i></div>
              <strong>{{ item.value }}</strong>
            </li>
          </ol>
        </article>

        <article class="analytics-panel status-panel">
          <header><div><h2>工单状态分布</h2><p>分项合计 {{ distributionTotal(snapshot.statusDistribution) }}，与工单总数一致</p></div></header>
          <div class="donut-layout">
            <div class="donut-chart" :style="{ background: statusDonut }">
              <div><strong>{{ snapshot.summary.ticketTotal }}</strong><small>工单总数</small></div>
            </div>
            <ul class="chart-legend">
              <li v-for="(item, index) in snapshot.statusDistribution" :key="item.key">
                <span><i :style="{ background: chartColors[index % chartColors.length] }"></i>{{ statusLabel(item.key) }}</span>
                <b>{{ item.value }}</b><em>{{ percentage(item.value, snapshot.summary.ticketTotal) }}</em>
              </li>
            </ul>
          </div>
        </article>

        <article class="analytics-panel priority-panel">
          <header><div><h2>优先级分布</h2><p>识别需要优先投入的服务压力</p></div></header>
          <div class="visual-bars">
            <div v-for="item in snapshot.priorityDistribution" :key="item.key">
              <span><i :class="`priority-${item.key.toLowerCase()}`"></i>{{ priorityLabel(item.key) }}</span>
              <div><b :class="`priority-${item.key.toLowerCase()}`" :style="{ width: percentage(item.value, snapshot.summary.ticketTotal) }"></b></div>
              <strong>{{ item.value }}</strong><small>{{ percentage(item.value, snapshot.summary.ticketTotal) }}</small>
            </div>
          </div>
        </article>

      </section>

    </template>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import DiagnosticBrandLink from '../components/DiagnosticBrandLink.vue'
import { getServiceOperationsAnalytics } from '../api/analytics.js'
import { getCurrentUser } from '../api/auth.js'
import { ROLE, primaryRole } from '../auth/roles.js'

const router = useRouter()
const loading = ref(true)
const error = ref('')
const snapshot = ref(null)
const today = new Date()
const start = new Date(today)
start.setDate(start.getDate() - 29)
const isoDate = (value) => [
  value.getFullYear(),
  String(value.getMonth() + 1).padStart(2, '0'),
  String(value.getDate()).padStart(2, '0')
].join('-')
const filters = reactive({ from: isoDate(start), to: isoDate(today), channel: '', priority: '', status: '' })
const invalidDateRange = computed(() => Boolean(filters.from && filters.to && filters.to < filters.from))

const channels = ['WEB', 'APP', 'API']
const chartColors = ['#41c8bd', '#706ee2', '#ff9361', '#f0c75e', '#4b91e2', '#a96ddd', '#85b65d']
const metricCards = computed(() => {
  const item = snapshot.value.summary
  return [
    { icon: '▥', tone: 'aqua', label: '工单总数', value: item.ticketTotal, note: `${snapshot.value.filter.from} 至 ${snapshot.value.filter.to}` },
    { icon: '◷', tone: 'violet', label: '待处理工单', value: item.pendingTickets, note: 'OPEN + PROCESSING' },
    { icon: '✓', tone: 'green', label: '已解决工单', value: item.resolvedTickets, note: 'RESOLVED + CLOSED' },
    { icon: '⚡', tone: 'orange', label: '高优先级工单', value: item.highPriorityTickets, note: 'HIGH + URGENT' },
    { icon: '↩', tone: 'violet', label: '退款申请', value: item.refundTotal, note: `待审批 ${item.pendingRefunds} 笔` },
    { icon: '!', tone: 'red', label: '高风险退款', value: item.highRiskRefunds, note: 'risk_level = HIGH' },
    { icon: '¥', tone: 'aqua', label: '申请退款金额', value: money(item.requestedRefundAmount), note: '退款明细申请金额之和' },
    { icon: '¥', tone: 'green', label: '批准退款金额', value: money(item.approvedRefundAmount), note: '已产生批准金额之和' }
  ]
})
const visibleTrend = computed(() => snapshot.value.trend.slice(-7))
const trendMaximum = computed(() => Math.max(1, ...visibleTrend.value.flatMap((point) =>
  [point.createdTickets, point.resolvedTickets, point.refundRequests]
)))
const scenarioMaximum = computed(() => Math.max(1,
  ...snapshot.value.scenarioDistribution.map((item) => Number(item.value))))
const statusDonut = computed(() => {
  const total = snapshot.value.summary.ticketTotal
  if (!total) return '#e8edef'
  let startAt = 0
  const segments = snapshot.value.statusDistribution.map((item, index) => {
    const endAt = startAt + Number(item.value) * 100 / total
    const segment = `${chartColors[index % chartColors.length]} ${startAt}% ${endAt}%`
    startAt = endAt
    return segment
  })
  return `conic-gradient(${segments.join(',')})`
})

onMounted(async () => {
  try {
    const user = await getCurrentUser()
    if (primaryRole(user) !== ROLE.ADMIN) {
      await router.replace('/personal-center')
      return
    }
    await loadAnalytics()
  } catch (loadError) {
    error.value = loadError.message || '无法验证管理员权限'
    loading.value = false
  }
})

async function loadAnalytics() {
  if (invalidDateRange.value) return
  loading.value = true
  error.value = ''
  try {
    snapshot.value = await getServiceOperationsAnalytics(filters)
  } catch (loadError) {
    error.value = loadError.message || '统计快照读取失败'
  } finally {
    loading.value = false
  }
}

function distributionTotal(items) {
  return items.reduce((sum, item) => sum + Number(item.value), 0)
}
function percentage(value, total) {
  if (!Number(total)) return '0%'
  const result = Number(value) * 100 / Number(total)
  return `${Number.isInteger(result) ? result : result.toFixed(1)}%`
}
function statusLabel(value) {
  return { OPEN: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', CLOSED: '已关闭' }[value] || value
}
function priorityLabel(value) {
  return { URGENT: '紧急', HIGH: '高优先级', NORMAL: '普通', LOW: '低优先级' }[value] || value
}
function scenarioLabel(value) {
  return {
    ORDER_INFORMATION_QUERY: '订单信息查询',
    LOGISTICS_TRACKING_QUERY: '物流进度查询',
    LOGISTICS_STATUS_NOT_SYNCED: '物流状态未同步',
    API_FREQUENT_FAILURE: '接口频繁失败',
    COUPON_UNAVAILABLE: '优惠券不可用',
    INVOICE_ISSUE_FAILED: '发票开具失败',
    ORDER_CANCELLED_BUT_CHARGED: '订单取消仍扣款',
    PAYMENT_SUCCESS_ORDER_PENDING: '支付成功订单待确认',
    MEMBER_BENEFIT_NOT_RECEIVED: '会员权益未到账',
    UNCLASSIFIED: '未分类'
  }[value] || value
}
function barHeight(value, maximum) {
  return value ? `${Math.max(1.5, Number(value) * 100 / maximum)}%` : '0'
}
function shortDate(value) {
  return value.slice(5).replace('-', '/')
}
function money(value) {
  return `¥${Number(value || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}
function formatDateTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '--'
}
</script>

<style scoped>
.analytics-page{min-height:100vh;padding:26px 4vw 50px;color:#20283a;background:radial-gradient(circle at 10% 0,#caf3ee,transparent 26%),radial-gradient(circle at 75% 0,#e4dcfa,transparent 30%),#f5f7f6;font-family:"Microsoft YaHei UI","PingFang SC",sans-serif}.analytics-header{display:flex;align-items:center;gap:28px;max-width:1560px;margin:auto;padding:16px 22px;border:1px solid #fff;border-radius:22px;background:#ffffffc9;box-shadow:0 18px 48px #46536b12}.analytics-heading span{color:#6973d8;font-size:12px;font-weight:900;letter-spacing:.1em}.analytics-heading h1{margin:5px 0 2px;font-size:28px}.analytics-heading p{margin:0;color:#8993a5;font-size:13px}.analytics-header>a:last-child{margin-left:auto;padding:11px 16px;border-radius:999px;background:#263047;color:#fff;text-decoration:none;font-size:13px}.analytics-state{max-width:1560px;margin:22px auto;padding:50px;border-radius:20px;background:#fff;text-align:center}.analytics-state.error{color:#a54752}.analytics-state p{color:#778195}.analytics-state button,.analytics-toolbar>button{padding:10px 18px;border:0;border-radius:11px;background:#273047;color:#fff}.analytics-toolbar{display:flex;align-items:end;gap:12px;max-width:1560px;margin:20px auto;padding:16px 20px;border:1px solid #fff;border-radius:18px;background:#ffffffd8}.analytics-toolbar label{display:grid;gap:6px;color:#6f7a8f;font-size:12px}.analytics-toolbar input,.analytics-toolbar select{height:38px;padding:0 10px;border:1px solid #dde3e7;border-radius:10px;background:#fff;color:#273047}.analytics-toolbar p{margin:0 0 2px auto;color:#69758b;font-size:12px;text-align:right}.analytics-toolbar p small{display:block;margin-top:5px;color:#989faf}.analytics-metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:13px;max-width:1560px;margin:0 auto}.analytics-metrics article,.analytics-panel{border:1px solid #fff;border-radius:18px;background:#ffffffd5;box-shadow:0 14px 36px #46536b0d}.analytics-metrics article{display:flex;align-items:center;gap:14px;padding:18px}.analytics-metrics article>i{display:grid;place-items:center;flex:0 0 46px;height:46px;border-radius:15px;font-size:20px;font-style:normal}.analytics-metrics article.aqua>i{background:#ddf8f5;color:#199f95}.analytics-metrics article.violet>i{background:#eceaff;color:#625ed2}.analytics-metrics article.green>i{background:#e2f6eb;color:#25885f}.analytics-metrics article.orange>i{background:#fff0e5;color:#c96b39}.analytics-metrics article.red>i{background:#ffe9ec;color:#bc4e5d}.analytics-metrics span{color:#7c879a;font-size:13px}.analytics-metrics strong{display:block;margin:7px 0 5px;font-size:28px}.analytics-metrics small{color:#789085;font-size:11px}.analytics-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;max-width:1560px;margin:15px auto}.analytics-panel{overflow:hidden;padding:20px}.analytics-panel>header{display:flex;align-items:center;margin-bottom:18px}.analytics-panel h2{margin:0;font-size:18px}.analytics-panel header p{margin:5px 0 0;color:#8b95a6;font-size:12px}.trend-panel{grid-column:1/-1}.trend-panel ul{display:flex;gap:15px;margin-left:auto;padding:0;list-style:none;color:#7d8798;font-size:11px}.trend-panel li i{display:inline-block;width:8px;height:8px;margin-right:5px;border-radius:50%}.created{background:#43c7bd}.resolved{background:#6f6dde}.refund{background:#ff9361}.trend-chart{display:flex;align-items:end;gap:10px;height:230px;padding:15px 8px 0;border-top:1px solid #edf0f1;overflow-x:auto}.trend-column{display:grid;grid-template-rows:1fr auto;flex:1;height:100%;min-width:34px}.trend-bars{display:flex;align-items:end;justify-content:center;gap:3px;height:185px}.trend-bars i{width:24%;min-width:5px;border-radius:5px 5px 1px 1px;transition:.2s}.trend-bars i:hover{filter:brightness(.92);transform:scaleX(1.15)}.trend-column small{text-align:center;color:#8993a4;font-size:10px}.donut-layout{display:grid;grid-template-columns:190px minmax(0,1fr);align-items:center;gap:24px;min-height:245px}.donut-chart{display:grid;place-items:center;width:180px;height:180px;border-radius:50%}.donut-chart>div{display:grid;place-items:center;width:112px;height:112px;border-radius:50%;background:#fff;box-shadow:0 5px 18px #32405614}.donut-chart strong{font-size:31px}.donut-chart small{color:#8a95a7;font-size:11px}.chart-legend{margin:0;padding:0;list-style:none}.chart-legend li{display:grid;grid-template-columns:1fr 35px 48px;align-items:center;gap:10px;padding:11px 4px;border-bottom:1px solid #edf0f1;cursor:pointer}.chart-legend li:hover{background:#f6f8fb}.chart-legend span{color:#687489;font-size:13px}.chart-legend span i{display:inline-block;width:9px;height:9px;margin-right:8px;border-radius:50%}.chart-legend b{text-align:right}.chart-legend em{color:#8b95a6;font-size:12px;font-style:normal;text-align:right}.visual-bars{display:grid;gap:20px;min-height:235px;align-content:center}.visual-bars>div{display:grid;grid-template-columns:110px minmax(0,1fr) 30px 48px;align-items:center;gap:12px;cursor:pointer}.visual-bars>div:hover{transform:translateX(2px)}.visual-bars span{color:#687489;font-size:13px}.visual-bars span i{display:inline-block;width:9px;height:9px;margin-right:7px;border-radius:50%}.visual-bars>div>div{height:13px;overflow:hidden;border-radius:999px;background:#edf1f2}.visual-bars>div>div b{display:block;height:100%;border-radius:inherit}.priority-urgent{background:#e65c6a}.priority-high{background:#ff9361}.priority-normal{background:#6f6dde}.priority-low{background:#43c7bd}.visual-bars strong{text-align:right}.visual-bars small{color:#8b95a6;text-align:right}.channel-cards{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;min-height:235px;align-content:center}.channel-cards article{display:grid;justify-items:center;padding:22px 10px;border:1px solid #e8ecee;border-radius:17px;background:linear-gradient(155deg,#fbffff,#f5f2ff);cursor:pointer;transition:.2s}.channel-cards article:hover{transform:translateY(-3px);box-shadow:0 12px 26px #42516c14}.channel-cards i{display:grid;place-items:center;width:48px;height:48px;border-radius:16px;background:linear-gradient(145deg,#45c9bf,#6f6ee0);color:#fff;font-size:23px;font-style:normal}.channel-cards span{margin-top:12px;color:#738095;font-size:12px}.channel-cards strong{margin-top:5px;font-size:25px}.channel-cards small{margin-top:3px;color:#6f79d4}.scenario-ranking{display:grid;gap:8px;margin:0;padding:0;list-style:none}.scenario-ranking li{display:grid;grid-template-columns:28px minmax(190px,1.35fr) minmax(120px,1fr) 26px;align-items:center;gap:10px;padding:8px;border-radius:10px;cursor:pointer}.scenario-ranking li:hover{background:#f5f7fa}.scenario-ranking em{display:grid;place-items:center;width:25px;height:25px;border-radius:8px;background:#efedff;color:#6864d2;font-size:11px;font-style:normal}.scenario-ranking span b,.scenario-ranking span small{display:block}.scenario-ranking span b{font-size:12px}.scenario-ranking span small{overflow:hidden;margin-top:3px;color:#99a1af;font-size:9px;text-overflow:ellipsis;white-space:nowrap}.scenario-ranking li>div{height:8px;overflow:hidden;border-radius:999px;background:#edf1f2}.scenario-ranking li>div i{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,#43c7bd,#706ee2)}.scenario-ranking strong{text-align:right}.analytics-table-panel{max-width:1520px;margin:15px auto 0}.attention-heading{display:flex;align-items:center}.attention-mark{display:grid;place-items:center;width:34px;height:34px;margin-right:11px;border-radius:11px;background:#fff0e5;color:#c96738;font-weight:900}.table-tools{display:flex;align-items:center;gap:10px;margin-left:auto}.analytics-table-panel>header label{display:flex;align-items:center;gap:8px;color:#7d8798;font-size:12px}.table-tools>button{padding:9px 13px;border:1px solid #dfe3f4;border-radius:10px;background:#f1f1ff;color:#575ec8;cursor:pointer}.analytics-table-panel input{width:270px;height:36px;padding:0 11px;border:1px solid #dfe4e7;border-radius:10px}.analytics-table-wrap{overflow:auto;margin:0 -20px -20px}.analytics-table-wrap table{width:100%;border-collapse:collapse;white-space:nowrap}.analytics-table-wrap th,.analytics-table-wrap td{padding:12px 15px;border-top:1px solid #e9edef;text-align:left;font-size:12px}.analytics-table-wrap th{background:#f5f7f7;color:#758095}.analytics-table-wrap td small{display:block;margin-top:4px;color:#929baa}.ticket-status{display:inline-block;padding:5px 8px;border-radius:999px;background:#e9f7f1;color:#247e62}.ticket-status.open{background:#fff0e6;color:#bd6638}.ticket-status.processing{background:#ecebff;color:#6561cb}.empty-cell{text-align:center!important;color:#8a94a5}.analytics-integrity-note{font-size:12px}
@media(max-width:1050px){.analytics-metrics{grid-template-columns:repeat(2,1fr)}.analytics-grid{grid-template-columns:1fr}.trend-panel{grid-column:auto}.analytics-toolbar{flex-wrap:wrap}.analytics-toolbar p{width:100%;text-align:left}.analytics-heading p{display:none}}
@media(max-width:700px){.analytics-page{padding:12px}.analytics-header{align-items:flex-start;gap:10px}.analytics-heading h1{font-size:20px}.analytics-header>a:last-child{display:none}.analytics-toolbar label{width:calc(50% - 8px)}.analytics-toolbar input,.analytics-toolbar select{width:100%}.analytics-metrics{grid-template-columns:1fr}.analytics-grid{display:block}.analytics-panel{margin-bottom:12px}.trend-column{min-width:42px}.donut-layout{grid-template-columns:1fr;justify-items:center}.chart-legend{width:100%}.channel-cards{grid-template-columns:1fr 1fr 1fr}.scenario-ranking li{grid-template-columns:28px minmax(130px,1fr) 26px}.scenario-ranking li>div{display:none}.analytics-table-panel>header{display:block}.table-tools{align-items:stretch;flex-direction:column;margin:12px 0 0}.analytics-table-panel input{width:100%}}
.trend-panel{grid-column:auto;display:flex;flex-direction:column}.trend-panel,.scenario-panel{min-height:360px}.trend-panel .trend-chart{flex:1;align-items:stretch;height:auto;min-height:300px}.trend-y-axis{display:flex;flex:0 0 28px;flex-direction:column;justify-content:space-between;padding:2px 0 20px;color:#8993a4;font-size:10px;text-align:right}.trend-series{display:flex;align-items:end;flex:1;gap:10px;height:100%;background:linear-gradient(to bottom,#edf0f1 1px,transparent 1px) 0 0/100% 50%}.trend-panel .trend-column{height:100%}.trend-panel .trend-bars{height:100%}.scenario-panel .scenario-ranking{align-content:center;min-height:270px}.date-range-error{align-self:center;color:#bd4f5b;font-size:12px;font-weight:700}
</style>
