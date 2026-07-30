<template>
  <main class="trace-center">
    <header class="trace-topbar">
      <RouterLink class="trace-brand" to="/"><BrandMark /><span><strong>工单诊断智能体</strong><small>SupportOps Agent</small></span></RouterLink>
      <b class="trace-product">产品溯源中心</b>
      <input v-model="globalKeyword" class="global-search" placeholder="搜索产品、批次、序列号、订单或运单" @keyup.enter="globalSearch" />
      <span class="source-online"><i></i>数据源在线</span>
      <RouterLink class="workspace-link" to="/workspace">智能诊断</RouterLink>
    </header>

    <aside class="trace-sidebar">
      <div class="workspace-name"><small>CURRENT WORKSPACE</small><strong>产品溯源中心</strong></div>
      <section v-for="group in navGroups" :key="group.title" class="nav-group">
        <h2>{{ group.title }}</h2>
        <button v-for="item in group.items" :key="item.key" type="button" :class="{ active: activeModule === item.key }" @click="selectModule(item.key)">
          <span>{{ item.icon }}</span>{{ item.label }}<em v-if="item.badge">{{ item.badge }}</em>
        </button>
      </section>
    </aside>

    <section class="trace-workspace">
      <div class="breadcrumb">首页 / 产品溯源中心 / <b>{{ currentLabel }}</b></div>
      <div class="trace-page">
        <header class="page-heading">
          <div><h1>{{ currentLabel }}</h1><p>{{ currentDescription }}</p></div>
          <div class="heading-actions">
            <button v-if="activeModule === 'warehouse'" type="button" class="soft-button" @click="inboundOpen = true">新增入库单</button>
            <button v-if="activeModule === 'warehouse'" type="button" class="soft-button">同步 WMS</button>
            <button v-if="activeModule !== 'overview' && activeModule !== 'query'" type="button" class="primary-button" @click="loadActive">刷新数据</button>
            <button v-if="activeModule === 'overview'" type="button" class="primary-button" @click="selectModule('query')">开始溯源</button>
          </div>
        </header>

        <p v-if="error" class="trace-error">{{ error }}</p>

        <template v-if="activeModule === 'overview'">
          <div class="kpi-grid">
            <article><span>在管产品</span><strong>{{ overview.products ?? '--' }}</strong><small>统一产品档案</small></article>
            <article><span>今日流转事件</span><strong>{{ overview.eventsToday ?? '--' }}</strong><small>采购至售后全链路</small></article>
            <article><span>待处理异常</span><strong>{{ overview.pendingAnomalies ?? '--' }}</strong><small class="warning">等待业务人员处置</small></article>
            <article><span>全链路可追溯率</span><strong>{{ overview.traceabilityRate ? `${overview.traceabilityRate}%` : '--' }}</strong><small>目标 99.5%</small></article>
          </div>
          <section class="trace-panel lifecycle-panel">
            <h2>全生命周期链路 <small>以批次 LOT-20260705-A18 为例</small></h2>
            <div class="lifecycle"><article v-for="step in lifecycle" :key="step.title"><b>{{ step.title }}</b><span>{{ step.text }}</span></article></div>
          </section>
          <div class="overview-bottom">
            <section class="trace-panel"><h2>近期异常</h2><table><thead><tr><th>异常对象</th><th>环节</th><th>异常说明</th><th>状态</th></tr></thead><tbody><tr><td><b>LOT-20260718-B06</b></td><td>质量检验</td><td>抽检不合格率超过阈值</td><td><span class="status danger">待处置</span></td></tr><tr><td><b>OUT-20260712-018</b></td><td>仓储库存</td><td>出库任务超过 24 小时</td><td><span class="status warning">处理中</span></td></tr></tbody></table></section>
            <aside class="trace-panel source-panel"><h2>数据源健康度</h2><div v-for="source in overview.sources" :key="source.source"><i></i>{{ source.source }}<span>{{ source.latencyMs }}ms</span></div></aside>
          </div>
        </template>

        <template v-else-if="activeModule === 'query'">
          <section class="trace-panel query-panel">
            <form @submit.prevent="runTraceSearch"><input v-model.trim="traceCode" placeholder="输入溯源码、序列号、批次号、订单号或运单号" required /><button class="primary-button" :disabled="loading">{{ loading ? '查询中…' : '开始查询' }}</button></form>
          </section>
          <section v-if="traceDetail" class="trace-panel trace-detail">
            <div class="trace-summary"><div><span>溯源码</span><strong>{{ traceDetail.traceCode }}</strong></div><div><span>产品编码</span><strong>{{ traceDetail.productCode }}</strong></div><div><span>生产批次</span><strong>{{ traceDetail.batchNo }}</strong></div><div><span>当前状态</span><strong>{{ traceDetail.currentStatus }}</strong></div></div>
            <div class="event-list"><article v-for="event in traceDetail.events" :key="event.eventId"><i></i><div><b>{{ event.stage }} · {{ event.title }}</b><span>{{ event.source }} / {{ event.sourceRecordNo }} · {{ formatDate(event.occurredAt) }}</span></div><em>{{ event.status }}</em></article></div>
            <button type="button" class="diagnosis-button" @click="diagnoseTrace(traceDetail)">将当前可信溯源数据交给智能体</button>
          </section>
        </template>

        <template v-else>
          <section class="trace-panel search-panel">
            <form @submit.prevent="loadActive">
              <input v-model.trim="filters.keyword" :placeholder="currentConfig.searchPlaceholder" />
              <select v-if="currentConfig.statusOptions" v-model="filters.status"><option value="">{{ currentConfig.statusLabel }}</option><option v-for="option in currentConfig.statusOptions" :key="option">{{ option }}</option></select>
              <button class="primary-button" :disabled="loading">{{ loading ? '查询中…' : '查询' }}</button>
              <button type="button" class="soft-button" @click="resetSearch">重置</button>
              <span>共 {{ rows.length }} 条记录</span>
            </form>
          </section>
          <section class="trace-panel data-panel">
            <div v-if="loading" class="empty-state">正在读取可信业务数据…</div>
            <div v-else-if="!rows.length" class="empty-state">没有查询到符合条件的数据</div>
            <table v-else><thead><tr><th v-for="column in currentConfig.columns" :key="column.key">{{ column.label }}</th><th>操作</th></tr></thead><tbody><tr v-for="(row, index) in rows" :key="rowKey(row, index)"><td v-for="column in currentConfig.columns" :key="column.key"><span v-if="column.badge" class="status" :class="statusClass(row[column.key])">{{ display(row[column.key]) }}</span><b v-else-if="column.strong">{{ display(row[column.key]) }}</b><span v-else>{{ display(row[column.key]) }}</span></td><td><button type="button" class="table-action" @click="handleRow(row)">{{ activeModule === 'warehouse' ? '查看溯源' : '智能诊断' }}</button></td></tr></tbody></table>
          </section>
        </template>
      </div>
    </section>

    <div v-if="inboundOpen" class="modal-backdrop" @click.self="closeInbound">
      <section class="inbound-modal" role="dialog" aria-modal="true" aria-labelledby="inbound-title">
        <header><div><h2 id="inbound-title">新增入库单</h2><p>保存后由后端生成唯一入库单号与产品溯源码。</p></div><button type="button" @click="closeInbound">×</button></header>
        <form @submit.prevent="submitInbound">
          <div class="form-grid">
            <label>入库单号（可选）<input v-model.trim="inbound.inboundNo" placeholder="留空由系统生成" /></label>
            <label>来源采购单号<input v-model.trim="inbound.sourcePurchaseNo" required placeholder="PO-20260701-036" /></label>
            <label>产品编码<input v-model.trim="inbound.productCode" required placeholder="SKU-A018" /></label>
            <label>生产批次号<input v-model.trim="inbound.batchNo" required placeholder="LOT-20260705-A18" /></label>
            <label>入库仓库<select v-model="inbound.warehouse" required><option>华东一号仓</option><option>华南二号仓</option></select></label>
            <label>目标库位<input v-model.trim="inbound.location" required placeholder="A-08-16" /></label>
            <label>入库数量<input v-model.number="inbound.quantity" type="number" min="1" required /></label>
            <label>入库类型<select v-model="inbound.inboundType" required><option>采购入库</option><option>生产入库</option><option>退货入库</option></select></label>
            <label class="wide">备注<input v-model.trim="inbound.remark" maxlength="200" placeholder="可选，最多 200 字" /></label>
          </div>
          <p v-if="modalError" class="trace-error">{{ modalError }}</p>
          <footer><button type="button" class="soft-button" @click="closeInbound">取消</button><button class="primary-button" :disabled="savingInbound">{{ savingInbound ? '保存中…' : '创建入库单' }}</button></footer>
        </form>
      </section>
    </div>
    <div v-if="notice" class="trace-notice">{{ notice }}</div>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import BrandMark from '../components/BrandMark.vue'
import { createInboundOrder, getAfterSaleTickets, getBatches, getInventory, getLogistics, getProducts, getPurchases, getQuality, getRecalls, getSales, getTraceOverview, searchTrace } from '../api/trace.js'

const router = useRouter()
const activeModule = ref('overview')
const overview = ref({ sources: [] })
const rows = ref([])
const loading = ref(false)
const error = ref('')
const notice = ref('')
const globalKeyword = ref('')
const traceCode = ref('SN-A018-00462')
const traceDetail = ref(null)
const inboundOpen = ref(false)
const savingInbound = ref(false)
const modalError = ref('')
const filters = reactive({ keyword: '', status: '' })
const inbound = reactive({ inboundNo: '', sourcePurchaseNo: 'PO-20260701-036', productCode: 'SKU-A018', batchNo: 'LOT-20260705-A18', warehouse: '华东一号仓', location: 'A-08-16', quantity: 100, inboundType: '采购入库', remark: '' })

const navGroups = [
  { title: '全局态势', items: [{ key: 'overview', label: '溯源总览', icon: '⌂' }, { key: 'query', label: '溯源查询', icon: '⌕' }] },
  { title: '基础与生产', items: [{ key: 'products', label: '产品档案', icon: '□' }, { key: 'suppliers', label: '供应商与采购', icon: '◇' }, { key: 'batches', label: '生产批次', icon: '▦' }, { key: 'quality', label: '质量检验', icon: '✓', badge: 2 }] },
  { title: '流通与履约', items: [{ key: 'warehouse', label: '仓储库存', icon: '▤', badge: 3 }, { key: 'logistics', label: '物流运输', icon: '⇢' }, { key: 'sales', label: '销售流向', icon: '¥' }] },
  { title: '服务与风控', items: [{ key: 'tickets', label: '售后工单', icon: '☷', badge: 5 }, { key: 'recalls', label: '风险召回', icon: '!', badge: 1 }] }
]

const configs = {
  overview: { label: '溯源总览', description: '统一掌握产品从采购、生产、仓储、物流到销售和售后的全生命周期。' },
  query: { label: '溯源查询', description: '通过追溯码、序列号、批次号、订单号或运单号还原完整生命周期。' },
  products: { label: '产品档案', description: '维护 SKU、规格、追溯规则和包装层级。', searchPlaceholder: '输入产品编码或名称', columns: [{ key: 'productCode', label: '产品编码', strong: true }, { key: 'name', label: '产品名称' }, { key: 'specification', label: '规格型号' }, { key: 'traceMode', label: '追溯方式' }, { key: 'packageLevel', label: '包装层级' }, { key: 'stockQuantity', label: '库存' }, { key: 'status', label: '状态', badge: true }], load: () => getProducts({ keyword: filters.keyword }) },
  suppliers: { label: '供应商与采购', description: '将采购单、供应批次和原材料关联到生产批次。', searchPlaceholder: '输入采购单号，如 PO-20260701-036', statusLabel: '全部收货状态', statusOptions: ['已收货', '部分收货'], columns: [{ key: 'purchaseNo', label: '采购单号', strong: true }, { key: 'supplier', label: '供应商' }, { key: 'productName', label: '原料/产品' }, { key: 'materialBatchNo', label: '供应批次' }, { key: 'receiptStatus', label: '收货状态', badge: true }, { key: 'qualityStatus', label: '质检状态', badge: true }, { key: 'productionBatchNo', label: '关联生产批次' }], load: () => getPurchases({ purchaseNo: filters.keyword, status: filters.status }) },
  batches: { label: '生产批次', description: '记录工厂、产线、投料、生产步骤和成品信息。', searchPlaceholder: '输入生产批次号，如 LOT-20260705-A18', statusLabel: '全部批次状态', statusOptions: ['已完工', '质量复核'], columns: [{ key: 'batchNo', label: '生产批次', strong: true }, { key: 'productCode', label: '产品' }, { key: 'factory', label: '工厂' }, { key: 'productionLine', label: '产线' }, { key: 'plannedQuantity', label: '计划数量' }, { key: 'qualifiedQuantity', label: '合格数量' }, { key: 'status', label: '状态', badge: true }], load: () => getBatches({ batchNo: filters.keyword, status: filters.status }) },
  quality: { label: '质量检验', description: '覆盖来料、过程、成品和出厂检验。', searchPlaceholder: '输入检验单号，如 QC-0705-118', statusLabel: '全部检验结果', statusOptions: ['合格', '不合格'], columns: [{ key: 'inspectionNo', label: '检验单号', strong: true }, { key: 'inspectionType', label: '检验类型' }, { key: 'batchNo', label: '对象批次' }, { key: 'sampleQuantity', label: '抽检数量' }, { key: 'failedQuantity', label: '不合格数量' }, { key: 'result', label: '结果', badge: true }, { key: 'inspector', label: '检验员' }], load: () => getQuality({ inspectionNo: filters.keyword, result: filters.status }) },
  warehouse: { label: '仓储库存', description: '查看批次库存、库位、冻结、移库、盘点和出入库任务。', searchPlaceholder: '输入库存或任务编号，如 OUT-20260712-018', statusLabel: '全部库存状态', statusOptions: ['正常', '已入库', '出库超时'], columns: [{ key: 'referenceNo', label: '库存/任务编号', strong: true }, { key: 'productCode', label: '产品' }, { key: 'batchNo', label: '批次' }, { key: 'warehouse', label: '仓库' }, { key: 'location', label: '库位' }, { key: 'quantity', label: '数量' }, { key: 'status', label: '状态', badge: true }, { key: 'traceCode', label: '溯源码' }], load: () => getInventory({ referenceNo: filters.keyword, status: filters.status }) },
  logistics: { label: '物流运输', description: '关联包裹、运单、承运商、运输节点与签收状态。', searchPlaceholder: '输入运单号，如 SF202607060005', columns: [{ key: 'trackingNo', label: '运单号', strong: true }, { key: 'orderNo', label: '订单号' }, { key: 'productCode', label: '产品' }, { key: 'batchNo', label: '批次' }, { key: 'carrier', label: '承运商' }, { key: 'status', label: '状态', badge: true }, { key: 'latestLocation', label: '最新位置' }], load: () => getLogistics({ trackingNo: filters.keyword }) },
  sales: { label: '销售流向', description: '查看商品到渠道、经销商、门店及最终客户的流向。', searchPlaceholder: '输入销售订单号', columns: [{ key: 'orderNo', label: '销售订单', strong: true }, { key: 'channel', label: '渠道' }, { key: 'customer', label: '客户/经销商' }, { key: 'productCode', label: '产品' }, { key: 'batchNo', label: '批次' }, { key: 'region', label: '区域' }, { key: 'status', label: '状态', badge: true }], load: () => getSales({ orderNo: filters.keyword }) },
  tickets: { label: '售后工单', description: '将客户问题与订单、序列号、生产批次和全链路事实关联。', searchPlaceholder: '输入工单号，如 TK-0706-005', statusLabel: '全部工单状态', statusOptions: ['处理中', '待诊断'], columns: [{ key: 'ticketNo', label: '工单号', strong: true }, { key: 'problem', label: '客户问题' }, { key: 'businessNo', label: '业务号' }, { key: 'productCode', label: '产品' }, { key: 'batchNo', label: '批次' }, { key: 'priority', label: '优先级', badge: true }, { key: 'status', label: '状态', badge: true }, { key: 'owner', label: '责任人' }], load: () => getAfterSaleTickets({ ticketNo: filters.keyword, status: filters.status }) },
  recalls: { label: '风险召回', description: '从问题批次反查库存、在途、渠道、客户和售后影响范围。', searchPlaceholder: '输入风险批次号，如 LOT-20260718-B06', statusLabel: '全部风险等级', statusOptions: ['高风险', '中风险'], columns: [{ key: 'recallNo', label: '召回任务', strong: true }, { key: 'batchNo', label: '风险批次' }, { key: 'productCode', label: '产品' }, { key: 'producedQuantity', label: '生产数量' }, { key: 'affectedQuantity', label: '受影响数量' }, { key: 'riskLevel', label: '风险等级', badge: true }, { key: 'status', label: '状态', badge: true }], load: () => getRecalls({ batchNo: filters.keyword, riskLevel: filters.status }) }
}

const lifecycle = [{ title: '供应商采购', text: '采购与原料批次' }, { title: '生产加工', text: '产线与生产批次' }, { title: '质量检验', text: '检验记录与结论' }, { title: '仓储出库', text: '仓库、库位与任务' }, { title: '物流运输', text: '运单与签收节点' }, { title: '销售与售后', text: '客户流向与工单' }]
const currentConfig = computed(() => configs[activeModule.value])
const currentLabel = computed(() => currentConfig.value.label)
const currentDescription = computed(() => currentConfig.value.description)

async function selectModule(key) {
  activeModule.value = key
  filters.keyword = ''
  filters.status = ''
  error.value = ''
  if (key === 'overview') await loadOverview()
  else if (key !== 'query') await loadActive()
}

async function loadOverview() {
  loading.value = true
  try { overview.value = await getTraceOverview() } catch (cause) { error.value = cause.message } finally { loading.value = false }
}

async function loadActive() {
  if (!currentConfig.value.load) return
  loading.value = true
  error.value = ''
  try { rows.value = await currentConfig.value.load() } catch (cause) { rows.value = []; error.value = cause.message } finally { loading.value = false }
}

async function resetSearch() { filters.keyword = ''; filters.status = ''; await loadActive() }

async function runTraceSearch() {
  loading.value = true
  error.value = ''
  try { traceDetail.value = await searchTrace(traceCode.value) } catch (cause) { traceDetail.value = null; error.value = cause.message } finally { loading.value = false }
}

function globalSearch() { if (!globalKeyword.value.trim()) return; traceCode.value = globalKeyword.value.trim(); selectModule('query').then(runTraceSearch) }
function display(value) { return value == null || value === '' ? '--' : Array.isArray(value) ? value.join('、') : value }
function rowKey(row, index) { return row.productCode || row.purchaseNo || row.batchNo || row.inspectionNo || row.referenceNo || row.trackingNo || row.orderNo || row.ticketNo || row.recallNo || index }
function statusClass(value = '') { return /不合格|超时|紧急|高风险|暂停/.test(value) ? 'danger' : /待|部分|复核|处理中/.test(value) ? 'warning' : 'success' }
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '--' }

function handleRow(row) {
  if (activeModule.value === 'warehouse') { traceCode.value = row.traceCode || row.batchNo; selectModule('query').then(runTraceSearch); return }
  const ticketNo = row.ticketNo || 'TK-TRACE-001'
  const businessNo = row.businessNo || row.orderNo || row.batchNo || row.purchaseNo || row.inspectionNo || row.trackingNo || row.productCode
  router.push({ name: 'workspace', query: { ticketNo, businessNo, description: `请结合产品溯源中心的可信数据分析 ${businessNo}，定位异常根因并生成客服回复。` } })
}

function diagnoseTrace(detail) {
  router.push({ name: 'workspace', query: { ticketNo: 'TK-TRACE-001', businessNo: detail.batchNo, description: `请分析溯源码 ${detail.traceCode}、产品 ${detail.productCode}、批次 ${detail.batchNo} 的完整生命周期，定位异常并给出处置建议。` } })
}

function closeInbound() { inboundOpen.value = false; modalError.value = '' }

async function submitInbound() {
  savingInbound.value = true
  modalError.value = ''
  try {
    const created = await createInboundOrder({ ...inbound })
    closeInbound()
    notice.value = `入库单 ${created.referenceNo} 创建成功，溯源码：${created.traceCode}`
    filters.keyword = created.referenceNo
    await loadActive()
    window.setTimeout(() => { notice.value = '' }, 4500)
  } catch (cause) { modalError.value = cause.message } finally { savingInbound.value = false }
}

onMounted(loadOverview)
</script>

<style scoped>
.trace-center{--ink:#232638;--muted:#7e879a;--line:rgba(103,110,143,.16);--blue:#6670dc;display:grid;grid-template:78px 1fr/244px 1fr;min-width:1180px;min-height:100vh;color:var(--ink);background:radial-gradient(circle at 8% 12%,rgba(88,220,211,.20),transparent 28%),radial-gradient(circle at 72% 5%,rgba(164,132,241,.20),transparent 30%),linear-gradient(rgba(110,120,155,.05) 1px,transparent 1px),linear-gradient(90deg,rgba(110,120,155,.05) 1px,transparent 1px),#f8f7f4;background-size:auto,auto,68px 68px,68px 68px,auto;font-family:"Microsoft YaHei UI","PingFang SC",sans-serif}.trace-topbar{grid-column:1/-1;display:flex;align-items:center;height:58px;margin:14px 16px 0;padding:0 18px;border:1px solid #ffffffd9;border-radius:18px;background:#ffffffd1;box-shadow:0 15px 38px #3e466417;backdrop-filter:blur(18px)}.trace-brand{display:flex;align-items:center;gap:10px;width:228px;color:inherit;text-decoration:none}.trace-brand :deep(.brand-mark){width:38px;height:38px}.trace-brand strong,.trace-brand small{display:block}.trace-brand strong{font-size:16px}.trace-brand small{margin-top:2px;color:#8993a4;font-size:9px}.trace-product{padding-left:20px;border-left:1px solid var(--line);color:#525ac0;font-size:15px}.global-search{width:370px;height:36px;margin-left:34px;padding:0 16px;border:1px solid var(--line);border-radius:999px;background:#ffffffb8;font-size:12px;outline:none}.source-online{display:flex;align-items:center;gap:7px;margin-left:auto;color:#667286;font-size:11px}.source-online i{width:8px;height:8px;border-radius:50%;background:#28b58b}.workspace-link{margin-left:18px;padding:9px 16px;border-radius:999px;background:#232638;color:#fff;font-size:11px;text-decoration:none}.trace-sidebar{grid-row:2;margin:10px 0 16px 16px;overflow:auto;border:1px solid #ffffffd9;border-radius:18px;background:#ffffffc7;box-shadow:0 18px 40px #3a415e14}.workspace-name{padding:18px;border-bottom:1px solid var(--line)}.workspace-name small,.workspace-name strong{display:block}.workspace-name small{color:#98a1b0;font-size:9px}.workspace-name strong{margin-top:6px;font-size:17px}.nav-group{padding:12px 10px 2px}.nav-group h2{margin:0;padding:0 10px 6px;color:#99a2b1;font-size:10px}.nav-group button{display:flex;align-items:center;gap:10px;width:100%;height:42px;margin:2px 0;padding:0 11px;border:0;border-radius:11px;background:transparent;color:#596479;font-size:12px;cursor:pointer}.nav-group button>span{display:grid;place-items:center;width:25px;height:25px;border-radius:6px;background:#eef1f5}.nav-group button.active{background:linear-gradient(100deg,#5ad0cd26,#7470df24);color:#5058c7;font-weight:800}.nav-group button.active>span{background:linear-gradient(145deg,#49c9c0,#706ce0);color:#fff}.nav-group em{margin-left:auto;padding:2px 6px;border-radius:10px;background:#fff0e8;color:#c7622d;font-size:9px;font-style:normal}.trace-workspace{grid-row:2;grid-column:2;margin:10px 16px 16px 10px;overflow:auto;border:1px solid #ffffffd9;border-radius:18px;background:#fafaf9a8;box-shadow:0 18px 45px #3a415e14}.breadcrumb{height:42px;padding:14px 24px;border-bottom:1px solid var(--line);color:#8b95a6;font-size:10px}.trace-page{padding:20px 24px 34px}.page-heading{display:flex;align-items:center;min-height:58px;margin-bottom:14px}.page-heading h1{margin:0;font:600 28px Georgia,"Microsoft YaHei UI",serif}.page-heading p{margin:6px 0 0;color:var(--muted);font-size:11px}.heading-actions{display:flex;gap:8px;margin-left:auto}.soft-button,.primary-button,.diagnosis-button{height:36px;padding:0 16px;border:1px solid var(--line);border-radius:999px;background:#fff;color:#47536a;font-size:11px;cursor:pointer}.primary-button{border:0;background:linear-gradient(100deg,#42c5bd,#6670dc);color:#fff;font-weight:800;box-shadow:0 7px 16px #5b67cd2e}.primary-button:disabled{opacity:.6}.diagnosis-button{display:block;margin:0 20px 18px auto;border:0;background:#232638;color:#fff}.kpi-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:11px;margin-bottom:12px}.kpi-grid article,.trace-panel{border:1px solid #ffffffe6;border-radius:14px;background:#ffffffd1;box-shadow:0 10px 26px #4149680e}.kpi-grid article{padding:17px}.kpi-grid span{color:var(--muted);font-size:11px}.kpi-grid strong{display:block;margin:8px 0 5px;font-size:28px}.kpi-grid small{color:#23a979;font-size:10px}.kpi-grid .warning{color:#ed7849}.trace-panel h2{margin:0;padding:15px 17px;border-bottom:1px solid var(--line);font-size:14px}.trace-panel h2 small{margin-left:8px;color:#9099aa;font-size:10px;font-weight:400}.lifecycle{display:grid;grid-template-columns:repeat(6,1fr);gap:9px;padding:19px}.lifecycle article{position:relative;padding:14px;border-radius:10px;background:linear-gradient(135deg,#f5f9f8eb,#f6f4fceb)}.lifecycle article:not(:last-child):after{position:absolute;right:-8px;content:'›';color:#9ba7ba}.lifecycle b,.lifecycle span{display:block}.lifecycle b{font-size:12px}.lifecycle span{margin-top:6px;color:#8791a3;font-size:10px}.overview-bottom{display:grid;grid-template-columns:1fr 320px;gap:12px;margin-top:12px}.source-panel{padding-bottom:8px}.source-panel div{display:flex;align-items:center;min-height:42px;margin:0 16px;border-bottom:1px solid var(--line);font-size:10px}.source-panel i{width:8px;height:8px;margin-right:8px;border-radius:50%;background:#28b58b}.source-panel span{margin-left:auto;color:#8e98a9}.search-panel{margin-bottom:12px}.search-panel form,.query-panel form{display:flex;align-items:center;gap:9px;padding:12px}.search-panel input,.search-panel select,.query-panel input{height:36px;padding:0 13px;border:1px solid var(--line);border-radius:999px;background:#fff;font-size:11px;outline:none}.search-panel input{width:310px}.search-panel select{min-width:150px}.search-panel form>span{margin-left:auto;color:#8993a4;font-size:10px}.query-panel input{flex:1}.data-panel{overflow:hidden}table{width:100%;border-collapse:collapse}th{height:44px;padding:0 14px;background:#f5f5f8bd;color:#778298;font-size:10px;text-align:left}td{height:58px;padding:0 14px;border-top:1px solid var(--line);color:#4f5b70;font-size:11px}td b{color:#253047}.status{display:inline-block;padding:5px 8px;border-radius:999px;background:#e9f8f1;color:#278c65;font-size:9px;font-weight:700}.status.warning{background:#fff0e6;color:#bd642e}.status.danger{background:#fff0f2;color:#bd4f5d}.table-action{border:0;background:transparent;color:#2860de;font-size:10px;cursor:pointer}.empty-state{padding:55px;color:#8791a3;font-size:12px;text-align:center}.trace-error{padding:11px 14px;border-radius:10px;background:#fff0f2;color:#b54958;font-size:11px}.trace-summary{display:grid;grid-template-columns:repeat(4,1fr);border-bottom:1px solid var(--line)}.trace-summary div{padding:16px;border-right:1px solid var(--line)}.trace-summary span,.trace-summary strong{display:block}.trace-summary span{color:#8b95a6;font-size:10px}.trace-summary strong{margin-top:7px;font-size:12px}.event-list{padding:12px 22px}.event-list article{display:flex;align-items:center;min-height:58px;border-bottom:1px solid var(--line)}.event-list i{width:10px;height:10px;margin-right:14px;border-radius:50%;background:linear-gradient(145deg,#42c5bd,#6670dc)}.event-list b,.event-list span{display:block}.event-list b{font-size:12px}.event-list span{margin-top:5px;color:#8791a3;font-size:10px}.event-list em{margin-left:auto;color:#278c65;font-size:10px;font-style:normal}.modal-backdrop{position:fixed;inset:0;z-index:50;display:grid;place-items:center;background:#1c1f304d;backdrop-filter:blur(5px)}.inbound-modal{width:760px;overflow:hidden;border:1px solid #fff;border-radius:18px;background:#fbfaf8;box-shadow:0 28px 80px #2a2d463d}.inbound-modal>header{display:flex;align-items:center;padding:20px 22px;border-bottom:1px solid var(--line);background:linear-gradient(100deg,#4ccac21a,#736de01a)}.inbound-modal h2{margin:0;font-size:20px}.inbound-modal header p{margin:5px 0 0;color:var(--muted);font-size:10px}.inbound-modal header button{margin-left:auto;width:34px;height:34px;border:0;border-radius:50%;background:#fff;font-size:18px;cursor:pointer}.form-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:15px 18px;padding:22px}.form-grid label{color:#59657a;font-size:11px}.form-grid .wide{grid-column:1/-1}.form-grid input,.form-grid select{width:100%;height:40px;margin-top:7px;padding:0 12px;border:1px solid var(--line);border-radius:9px;background:#fff;font-size:12px}.inbound-modal footer{display:flex;justify-content:flex-end;gap:8px;padding:15px 22px;border-top:1px solid var(--line)}.inbound-modal .trace-error{margin:0 22px}.trace-notice{position:fixed;right:28px;bottom:24px;z-index:60;max-width:520px;padding:13px 17px;border-radius:12px;background:#232638;color:#fff;font-size:11px;box-shadow:0 16px 35px #272a4138}@media(max-width:1300px){.trace-center{grid-template-columns:220px 1fr}.trace-sidebar{margin-left:8px}.global-search{width:270px}.trace-product{display:none}.trace-page{padding:18px}.overview-bottom{grid-template-columns:1fr}.kpi-grid{grid-template-columns:repeat(2,1fr)}}
</style>
