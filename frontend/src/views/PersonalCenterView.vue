<template>
  <main class="role-center">
    <div v-if="loading" class="center-state">
      <span class="state-spinner"></span>
      <h1>正在进入个人中心</h1>
      <p>正在读取账号角色与权限…</p>
    </div>

    <div v-else-if="loadError" class="center-state">
      <span class="state-icon">!</span>
      <h1>个人中心暂时无法加载</h1>
      <p>{{ loadError }}</p>
      <button type="button" @click="loadSession">重新加载</button>
    </div>

    <div v-else class="center-shell">
      <aside class="center-sidebar">
        <DiagnosticBrandLink />

        <section class="identity-card">
          <div class="identity-avatar">{{ avatarInitial }}</div>
          <div>
            <span>{{ roleMeta.eyebrow }}</span>
            <strong>{{ currentUser.displayName }}</strong>
            <small>@{{ currentUser.username }}</small>
          </div>
          <i></i>
        </section>

        <nav class="center-nav" :aria-label="`${roleMeta.label}功能导航`">
          <p>{{ roleMeta.navTitle }}</p>
          <button
            v-for="item in navigation"
            :key="item.key"
            type="button"
            :class="{ active: activeView === item.key }"
            @click="selectView(item.key)"
          >
            <span>{{ item.icon }}</span>
            <b>{{ item.label }}<small>{{ item.subtitle }}</small></b>
            <em v-if="navBadge(item) !== null">{{ navBadge(item) }}</em>
          </button>
        </nav>

        <div class="sidebar-footer">
          <RouterLink to="/">← 返回公开首页</RouterLink>
          <button type="button" @click="signOut">退出登录</button>
        </div>
      </aside>

      <section class="center-main">
        <header class="center-topbar">
          <div>
            <span>{{ roleMeta.eyebrow }} / {{ roleMeta.en }}</span>
            <h1>{{ activeNav.label }}</h1>
          </div>
          <div class="topbar-actions">
            <button type="button" class="notification-button" aria-label="消息通知" @click="openNotifications">♢<i></i></button>
            <div class="topbar-user">
              <b>{{ currentUser.displayName }}</b>
              <small>{{ roleMeta.label }}</small>
            </div>
          </div>
        </header>

        <div class="center-content">
          <section class="welcome-strip">
            <div>
              <span>{{ greeting }}，{{ currentUser.displayName }}</span>
              <h2>{{ sectionCopy.title }}</h2>
              <p>{{ sectionCopy.description }}</p>
            </div>
            <button v-if="sectionCopy.action" type="button" @click="handlePrimaryAction">{{ sectionCopy.action }} <b>→</b></button>
          </section>

          <section class="module-search panel" role="search">
            <div>
              <span>⌕</span>
              <input ref="moduleSearchInput" v-model.trim="searchKeyword" :placeholder="moduleSearchPlaceholder" :aria-label="`${activeNav.label}搜索`" />
              <button v-if="searchKeyword" type="button" aria-label="清空搜索" @click="searchKeyword = ''">×</button>
            </div>
            <button
              v-if="syncReservation"
              type="button"
              class="reserved-sync-button"
              :disabled="syncLoading"
              :title="`${syncReservation.system} 接口待配置，当前仅保留入口`"
              @click="handleReservedSync"
            >
              <span>↻</span>
              <b>{{ syncLoading ? '正在检查配置…' : syncReservation.label }}</b>
              <small>{{ syncLoading ? '请稍候' : '预留同步入口' }}</small>
            </button>
            <p><b>{{ remoteLoading ? '…' : searchResultCount }}</b> 条匹配结果<small>{{ remoteError || '支持编号、客户、状态和业务关键词' }}</small></p>
          </section>

          <template v-if="activeView === 'conversations'">
            <section class="conversation-layout">
              <aside class="conversation-list panel">
                <header><h3>客户会话</h3><div class="conversation-list-tools"><button type="button" :disabled="deleteActionLoading" @click="clearCompletedConversations">清理已完成</button><span>{{ filteredConversations.length }} 个待回复</span></div></header>
                <div
                  v-for="conversation in filteredConversations"
                  :key="conversation.id"
                  class="conversation-swipe-row"
                  :class="{ swiped: swipedConversationId === conversation.id }"
                  @pointerdown="beginConversationSwipe($event, conversation.id)"
                  @pointerup="finishConversationSwipe($event, conversation.id)"
                  @pointercancel="cancelConversationSwipe"
                >
                  <button type="button" class="conversation-delete-action" :disabled="deleteActionLoading" @click.stop="deleteConversation(conversation)">删除</button>
                  <button type="button" class="conversation-card" :class="{ active: selectedConversationId === conversation.id }" @click="openConversationFromList(conversation)">
                    <i>{{ conversation.name.charAt(0) }}</i>
                    <span><b>{{ conversation.name }}</b><small>{{ conversation.preview }}</small></span>
                    <em>{{ conversation.time }}</em>
                  </button>
                </div>
                <p v-if="!filteredConversations.length" class="search-empty">没有找到匹配的客户会话</p>
              </aside>
              <section class="conversation-window panel">
                <header>
                  <div>
                    <i>{{ (conversationContext?.customerName || '客').charAt(0) }}</i>
                    <span><b>{{ conversationContext?.customerName || '请选择客户会话' }}</b>
                      <small v-if="conversationContext">工单 {{ conversationContext.ticketNo || '未关联' }} · 订单 {{ conversationContext.orderNo || '未关联' }}</small>
                    </span>
                  </div>
                </header>
                <div class="message-stream">
                  <article v-for="message in conversationMessages" :key="message.id" class="message" :class="{ customer: message.senderType === 'CUSTOMER', agent: message.senderType !== 'CUSTOMER' }">
                    <span>{{ message.senderType === 'CUSTOMER' ? '客户' : message.senderType === 'BOT' ? '智能客服' : '客服' }}</span>
                    <p>{{ message.content }}</p>
                    <ul v-if="message.attachments?.length" class="message-attachments">
                      <li v-for="attachment in message.attachments" :key="attachment.id || attachment.fileName">📎 {{ attachment.fileName }} <small>{{ formatFileSize(attachment.sizeBytes) }}</small></li>
                    </ul>
                    <time>{{ formatMessageTime(message.sentAt) }}</time>
                  </article>
                  <p v-if="conversationLoading" class="search-empty">正在加载会话消息…</p>
                  <p v-else-if="!conversationMessages.length" class="search-empty">请选择左侧会话开始处理</p>
                  <article v-if="conversationContext?.orderNo" class="refund-context">
                    <header><b>关联业务信息</b><span class="status processing">{{ conversationContext.serviceMode }}</span></header>
                    <dl>
                      <div><dt>工单号</dt><dd>{{ conversationContext.ticketNo || '未关联' }}</dd></div>
                      <div><dt>订单号</dt><dd>{{ conversationContext.orderNo }}</dd></div>
                      <div><dt>订单金额</dt><dd>{{ money(conversationContext.orderAmount) }}</dd></div>
                      <div><dt>当前可退</dt><dd>{{ money(conversationContext.refundableAmount) }}</dd></div>
                      <div><dt>退款渠道</dt><dd>{{ conversationContext.refundChannel || '原路退回' }}</dd></div>
                    </dl>
                  </article>
                </div>
                <footer>
                  <p v-if="suggestedReply">智能体建议回复：{{ suggestedReply }}</p>
                  <textarea v-model.trim="conversationReply" maxlength="2000" placeholder="输入给客户的回复，按 Ctrl + Enter 发送" @keydown.ctrl.enter.prevent="sendSuggestedReply"></textarea>
                  <ul v-if="conversationAttachments.length" class="composer-attachments">
                    <li v-for="(file, index) in conversationAttachments" :key="`${file.name}-${file.lastModified}`">
                      <span>📎 {{ file.name }} · {{ formatFileSize(file.size) }}</span>
                      <button type="button" @click="removeConversationAttachment(index)">×</button>
                    </li>
                  </ul>
                  <input ref="conversationFileInput" class="visually-hidden" type="file" multiple accept="image/*,.pdf,.txt,.doc,.docx,.xls,.xlsx" @change="selectConversationAttachments" />
                  <div>
                    <button type="button" :disabled="!conversationContext" @click="openConversationRefund">发起退款</button>
                    <button type="button" :disabled="!conversationContext" @click="conversationFileInput?.click()">添加附件</button>
                    <button type="button" class="primary" :disabled="primaryActionLoading || !conversationContext || (!conversationReply && !conversationAttachments.length)" @click="sendSuggestedReply">发送回复</button>
                  </div>
                </footer>
              </section>
            </section>
          </template>

          <template v-else-if="activeView === 'ticket-stats'">
            <section class="metric-grid">
              <article v-for="metric in displayMetrics" :key="metric.label" class="center-metric-card panel">
                <span>{{ metric.label }}</span><strong>{{ metric.value }}</strong><small :class="metric.tone">{{ metric.note }}</small>
              </article>
            </section>

            <section class="panel data-panel ticket-stat-panel">
              <header class="panel-heading">
                <div>
                  <h3>工单明细</h3>
                  <p>搜索、统计卡与数据看板均来自同一统计快照；状态、优先级和渠道均为工单原始字段</p>
                </div>
                <button type="button" @click="showAllRecords">查看全部</button>
              </header>
              <form class="ticket-filter-bar" @submit.prevent="applyTicketFilters">
                <label>开始日期<input v-model="ticketFilters.from" type="date" :max="ticketFilters.to || undefined" /></label>
                <label>结束日期<input v-model="ticketFilters.to" type="date" :min="ticketFilters.from || undefined" /></label>
                <label>优先级
                  <select v-model="ticketFilters.priority">
                    <option value="">全部优先级</option><option value="URGENT">紧急</option>
                    <option value="HIGH">高优先级</option><option value="NORMAL">普通</option><option value="LOW">低优先级</option>
                  </select>
                </label>
                <label>工单状态
                  <select v-model="ticketFilters.status">
                    <option value="">全部状态</option><option value="OPEN">待处理</option>
                    <option value="PROCESSING">处理中</option><option value="RESOLVED">已解决</option><option value="CLOSED">已关闭</option>
                  </select>
                </label>
                <label>受理渠道
                  <select v-model="ticketFilters.channel">
                    <option value="">全部渠道</option><option value="WEB">WEB</option><option value="APP">APP</option><option value="API">API</option>
                  </select>
                </label>
                <button type="submit" :disabled="remoteLoading">筛选</button>
                <button type="button" class="secondary" :disabled="remoteLoading" @click="resetTicketFilters">重置</button>
              </form>
              <div class="table-wrap">
                <table class="ticket-stat-table">
                  <thead>
                    <tr><th>工单号</th><th>客户 / 业务单号</th><th>状态</th><th>优先级</th><th>受理渠道</th><th>问题场景</th><th>创建时间</th></tr>
                  </thead>
                  <tbody>
                    <tr v-for="record in filteredRecords" :key="record.id">
                      <td><b>{{ record.title }}</b></td>
                      <td><b>{{ record.customerName }}</b><small>{{ record.businessNo || '未关联业务单号' }}</small></td>
                      <td><span :class="['status', record.statusTone]">{{ record.status }}</span></td>
                      <td><b>{{ record.rawPriority }}</b></td>
                      <td>{{ record.channel }}</td>
                      <td>{{ record.scenario }}</td>
                      <td>{{ formatRelative(record.occurredAt) }}</td>
                    </tr>
                    <tr v-if="!filteredRecords.length"><td colspan="7" class="table-empty">没有符合状态、优先级或受理渠道条件的工单。</td></tr>
                  </tbody>
                </table>
              </div>
              <p class="analytics-integrity-note">
                当前页面显示 {{ filteredRecords.length }} 条工单；未输入搜索词时，该数量必须与上方“工单总数”一致。
                退款指标在完整数据看板中按这些工单关联的退款申请聚合。
              </p>
            </section>
          </template>

          <template v-else-if="activeView === 'refunds' || activeView === 'refund-approval'">
            <section class="metric-grid">
              <article v-for="metric in refundMetrics" :key="metric.label" class="center-metric-card panel">
                <span>{{ metric.label }}</span><strong>{{ metric.value }}</strong><small :class="metric.tone">{{ metric.note }}</small>
              </article>
            </section>

            <section class="panel data-panel">
              <header class="panel-heading">
                <div><h3>{{ role === ROLE.ADMIN ? '退款审批队列' : role === ROLE.SUPPORT_AGENT ? '客户退款信息' : '我的退款记录' }}</h3><p>{{ refundTableDescription }}</p></div>
                <div class="filter-pills">
                  <button :class="{ active: !searchKeyword }" @click="setRefundFilter('')">全部</button>
                  <button :class="{ active: searchKeyword === 'UNDER_REVIEW' }" @click="setRefundFilter('UNDER_REVIEW')">审核中</button>
                  <button :class="{ active: searchKeyword === 'EXECUTING' }" @click="setRefundFilter('EXECUTING')">处理中</button>
                  <button :class="{ active: searchKeyword === 'SUCCEEDED' }" @click="setRefundFilter('SUCCEEDED')">已到账</button>
                </div>
              </header>
              <div class="table-wrap">
                <table>
                  <thead><tr><th>退款单号</th><th>{{ role === ROLE.CUSTOMER ? '关联订单' : '客户 / 订单' }}</th><th>申请金额</th><th>批准金额</th><th>风险提示</th><th>状态</th><th>到账进度</th><th>操作</th></tr></thead>
                  <tbody>
                    <tr v-for="refund in filteredRefunds" :key="refund.id">
                      <td><b>{{ refund.id }}</b><small>{{ refund.createdAt }}</small></td>
                      <td><b>{{ role === ROLE.CUSTOMER ? refund.order : refund.customer }}</b><small>{{ role === ROLE.CUSTOMER ? refund.product : refund.order }}</small></td>
                      <td>{{ refund.requested }}</td>
                      <td>{{ refund.approved }}</td>
                      <td><span :class="['risk', refund.riskTone]">{{ refund.risk }}</span></td>
                      <td><span :class="['status', refund.statusTone]">{{ refund.status }}</span></td>
                      <td><b>{{ refund.arrival }}</b><small>{{ refund.channel }}</small></td>
                      <td>
                        <button type="button" class="table-action" @click="openRefund(refund)">
                          {{ role === ROLE.ADMIN && refund.status === '待审批' ? '审批' : '查看详情' }}
                        </button>
                      </td>
                    </tr>
                    <tr v-if="!filteredRefunds.length"><td colspan="8" class="table-empty">没有找到匹配的退款记录</td></tr>
                  </tbody>
                </table>
              </div>
              <p v-if="role === ROLE.SUPPORT_AGENT" class="permission-note">
                客服可以查看客户退款申请、审批结果、批准金额、退款渠道和到账状态，以便准确回复客户；批准、拒绝、修改金额和执行退款仅对具备审批权限的账号开放。
              </p>
            </section>
          </template>

          <template v-else-if="activeView === 'service'">
            <section class="service-layout">
              <section class="service-chat panel">
                <header><span class="agent-orb">S</span><div><h3>SupportOps 智能服务</h3><p>在线 · 可以查询订单、物流、售后与退款</p></div></header>
                <div class="service-intro">
                  <span>你好，我是你的智能售后助手</span>
                  <h3>今天需要我帮你处理什么？</h3>
                  <p>你可以直接描述问题，也可以从下方选择一个常见服务。</p>
                </div>
                <div class="quick-service-grid">
                  <button v-for="item in filteredQuickActions" :key="item.title" type="button" @click="customerPrompt = item.prompt">
                    <i>{{ item.icon }}</i><span><b>{{ item.title }}</b><small>{{ item.text }}</small></span><em>→</em>
                  </button>
                  <p v-if="!filteredQuickActions.length" class="search-empty quick-empty">没有找到匹配的快捷服务，你仍可以在下方直接描述问题。</p>
                </div>
                <form @submit.prevent="startCustomerDiagnosis">
                  <input v-model="customerPrompt" placeholder="例如：退款已经通过了，什么时候能到账？" />
                  <button type="submit">发送</button>
                </form>
              </section>
              <aside class="service-side">
                <article class="panel"><span>进行中的服务</span><strong>2</strong><p>1 个退款处理中，1 个包裹运输中</p></article>
                <article class="panel timeline-mini"><h3>最近进度</h3><div><i></i><span><b>退款审批通过</b><small>今天 09:48</small></span></div><div><i></i><span><b>包裹到达上海转运中心</b><small>昨天 22:16</small></span></div></article>
              </aside>
            </section>
          </template>

          <template v-else>
            <section class="metric-grid">
              <article v-for="metric in displayMetrics" :key="metric.label" class="center-metric-card panel">
                <span>{{ metric.label }}</span><strong>{{ metric.value }}</strong><small :class="metric.tone">{{ metric.note }}</small>
              </article>
            </section>

            <section class="content-grid">
              <article class="panel data-panel">
                <header class="panel-heading"><div><h3>{{ sectionData.tableTitle }}</h3><p>{{ sectionData.tableDescription }}</p></div><button type="button" @click="showAllRecords">查看全部</button></header>
                <div class="record-list">
                  <div v-for="record in filteredRecords" :key="record.title">
                    <span :class="['record-icon', record.tone]">{{ record.icon }}</span>
                    <p><b>{{ record.title }}</b><small>{{ record.detail }}</small></p>
                    <em>{{ record.meta }}</em>
                    <span :class="['status', record.statusTone || 'normal']">{{ record.status }}</span>
                    <div class="record-actions">
                      <button v-if="activeView === 'orders' && role === ROLE.ADMIN" type="button" class="knowledge-action" @click="openProductKnowledge(record)">
                        📎 附件{{ record.extra?.knowledgeDocumentCount ? ` ${record.extra.knowledgeDocumentCount}` : '' }}
                      </button>
                      <button v-if="activeView === 'people' && role === ROLE.ADMIN" type="button" @click="openSupportUserEditor(record)">修改</button>
                      <button v-if="activeView === 'people' && role === ROLE.ADMIN" type="button" class="danger-action" @click="confirmSupportUserDeletion(record)">删除</button>
                      <button type="button" @click="openRecord(record)">查看</button>
                    </div>
                  </div>
                  <p v-if="!filteredRecords.length" class="search-empty">没有找到匹配的记录，请尝试编号、客户名或状态关键词。</p>
                </div>
              </article>

              <aside class="side-stack">
                <article class="panel progress-panel">
                  <header><h3>当前数据状态</h3><span>与列表同步</span></header>
                  <div class="progress-ring" :style="{ '--progress': recordHealth.progress }"><strong>{{ recordHealth.progress }}</strong><small>正常占比</small></div>
                  <ul><li v-for="item in recordHealth.breakdown" :key="item.label"><span><i :style="{ background: item.color }"></i>{{ item.label }}</span><b>{{ item.value }}</b></li></ul>
                </article>
                <article class="panel assistant-tip">
                  <span>✦ 智能体提示</span>
                  <p>{{ dataTip }}</p>
                  <button type="button" @click="openAdvice">查看建议 →</button>
                </article>
              </aside>
            </section>
          </template>
        </div>
      </section>
    </div>

    <Transition name="modal-fade">
      <div v-if="selectedRecord || selectedAdvice" class="modal-backdrop" @click.self="closeInfoModal">
        <section class="refund-modal record-detail-modal">
          <header>
            <div><span>{{ selectedAdvice ? '智能体建议' : `${activeNav.label}详情` }}</span><h2>{{ selectedAdvice?.title || selectedRecord?.title }}</h2></div>
            <button type="button" @click="closeInfoModal">×</button>
          </header>
          <div class="refund-modal-body">
            <template v-if="selectedRecord">
              <div v-if="['logistics', 'my-logistics'].includes(activeView) && selectedRecord.extra?.timeline" class="logistics-detail">
                <section class="logistics-route-card">
                  <div><span>发件地</span><b>{{ selectedRecord.extra.originLocation || '待更新' }}</b></div>
                  <i><em></em><strong>运输路线</strong><em></em></i>
                  <div><span>收件地</span><b>{{ selectedRecord.extra.destinationLocation || '待更新' }}</b></div>
                </section>
                <dl>
                  <div><dt>承运商 / 运单号</dt><dd>{{ selectedRecord.extra.carrierName || selectedRecord.meta }} · {{ selectedRecord.id }}</dd></div>
                  <div><dt>当前进度</dt><dd>{{ selectedRecord.status }}</dd></div>
                  <div><dt>预计送达</dt><dd>{{ formatRelative(selectedRecord.extra.estimatedDeliveryAt) }}</dd></div>
                  <div><dt>当前位置</dt><dd>{{ selectedRecord.extra.currentLocation || '待更新' }}</dd></div>
                  <div><dt>当前网点</dt><dd>{{ selectedRecord.extra.facilityName || '待更新' }}</dd></div>
                  <div v-if="selectedRecord.extra.courierName"><dt>派送员</dt><dd>{{ selectedRecord.extra.courierName }} · {{ selectedRecord.extra.courierPhone }}</dd></div>
                </dl>
                <section class="logistics-timeline">
                  <h3>物流轨迹</h3>
                  <ol>
                    <li v-for="(event, index) in selectedRecord.extra.timeline" :key="event.id" :class="{ latest: index === 0 }">
                      <i></i><div><b>{{ localizeBusinessStatus(event.status) }} · {{ event.location || event.facility || '位置待更新' }}</b><p>{{ event.description }}</p><time>{{ formatRelative(event.eventTime) }}</time></div>
                    </li>
                  </ol>
                </section>
              </div>
              <template v-else>
                <dl>
                  <div><dt>记录编号</dt><dd>{{ selectedRecord.id }}</dd></div>
                  <div><dt>当前状态</dt><dd>{{ selectedRecord.status }}</dd></div>
                  <div><dt>补充信息</dt><dd>{{ selectedRecord.meta || '--' }}</dd></div>
                </dl>
                <article><span>业务详情</span><p>{{ selectedRecord.detail }}</p></article>
                <p class="record-time">更新时间：{{ formatRelative(selectedRecord.occurredAt) }}</p>
              </template>
            </template>
            <template v-else>
              <article><span>分析结论</span><p>{{ selectedAdvice.content }}</p></article>
              <p class="record-time">建议动作：{{ selectedAdvice.suggestedAction }}</p>
            </template>
          </div>
          <footer><button type="button" class="primary" @click="closeInfoModal">关闭</button></footer>
        </section>
      </div>
    </Transition>

    <Transition name="modal-fade">
      <div v-if="supportUserModalOpen" class="modal-backdrop" @click.self="closeSupportUserModal">
        <form class="refund-modal support-user-modal" @submit.prevent="submitSupportUser">
          <header><div><span>人员管理</span><h2>{{ supportUserMode === 'edit' ? '修改客服信息' : '添加客服人员' }}</h2></div><button type="button" @click="closeSupportUserModal">×</button></header>
          <div class="refund-modal-body">
            <label>登录账号<input v-model.trim="supportUserForm.username" required minlength="3" maxlength="32" pattern="[A-Za-z0-9_-]{3,32}" placeholder="例如 support02" /></label>
            <label>客服姓名<input v-model.trim="supportUserForm.displayName" required minlength="2" maxlength="50" placeholder="例如 客服小王" /></label>
            <label v-if="supportUserMode === 'create'">初始密码<input v-model="supportUserForm.password" required minlength="8" maxlength="72" type="password" placeholder="至少 8 位，必须包含字母和数字" /></label>
            <label v-else>账号状态
              <select v-model="supportUserForm.status"><option value="ACTIVE">启用</option><option value="DISABLED">禁用</option></select>
            </label>
            <label v-if="supportUserMode === 'edit'">每日服务额度<input v-model.number="supportUserForm.dailyQuota" required type="number" min="1" max="10000" /></label>
            <p v-if="supportUserMode === 'edit'" class="form-help">登录账号必须全局唯一；客服角色保持为 SUPPORT_AGENT，不允许修改。</p>
          </div>
          <footer>
            <button type="button" @click="closeSupportUserModal">取消</button>
            <button type="submit" class="primary" :disabled="primaryActionLoading">{{ supportUserMode === 'edit' ? '保存修改' : '创建客服账号' }}</button>
          </footer>
        </form>
      </div>
    </Transition>

    <Transition name="modal-fade">
      <div v-if="selectedRefund" class="modal-backdrop" @click.self="selectedRefund = null">
        <section class="refund-modal">
          <header><div><span>退款详情</span><h2>{{ selectedRefund.id }}</h2></div><button type="button" @click="selectedRefund = null">×</button></header>
          <div class="refund-modal-body">
            <dl>
              <div><dt>客户</dt><dd>{{ selectedRefund.customer }}</dd></div>
              <div><dt>订单号</dt><dd>{{ selectedRefund.order }}</dd></div>
              <div><dt>申请金额</dt><dd>{{ selectedRefund.requested }}</dd></div>
              <div><dt>批准金额</dt><dd>{{ selectedRefund.approved }}</dd></div>
              <div><dt>退款渠道</dt><dd>{{ selectedRefund.channel }}</dd></div>
              <div><dt>到账进度</dt><dd>{{ selectedRefund.arrival }}</dd></div>
            </dl>
            <article><span>风险提示</span><p>{{ selectedRefund.riskDetail }}</p></article>
            <label v-if="role === ROLE.ADMIN && selectedRefund.rawStatus === 'UNDER_REVIEW'">批准金额<input v-model="approvalAmount" inputmode="decimal" /></label>
            <label v-if="role === ROLE.ADMIN && selectedRefund.rawStatus === 'UNDER_REVIEW'">审批说明<textarea v-model="decisionReason" placeholder="拒绝退款时必须填写原因；批准时可选填" /></label>
          </div>
          <footer v-if="role === ROLE.ADMIN && selectedRefund.rawStatus === 'UNDER_REVIEW'">
            <button type="button" :disabled="refundActionLoading" @click="rejectSelectedRefund">拒绝退款</button>
            <button type="button" class="primary" :disabled="refundActionLoading" @click="approveSelectedRefund">批准退款</button>
          </footer>
          <footer v-else-if="role === ROLE.ADMIN && selectedRefund.rawStatus === 'APPROVED'">
            <button type="button" class="primary" :disabled="refundActionLoading" @click="executeSelectedRefund">执行退款</button>
          </footer>
          <footer v-else><button type="button" class="primary" @click="selectedRefund = null">关闭</button></footer>
        </section>
      </div>
    </Transition>

    <Transition name="modal-fade">
      <div v-if="conversationRefundOpen" class="modal-backdrop" @click.self="closeConversationRefund">
        <form class="refund-modal" @submit.prevent="submitConversationRefund">
          <header>
            <div><span>会话关联退款</span><h2>发起退款申请</h2></div>
            <button type="button" @click="closeConversationRefund">×</button>
          </header>
          <div class="refund-modal-body">
            <dl>
              <div><dt>会话号</dt><dd>{{ conversationContext?.conversationNo }}</dd></div>
              <div><dt>工单号</dt><dd>{{ conversationContext?.ticketNo || '未关联' }}</dd></div>
              <div><dt>订单号</dt><dd>{{ conversationContext?.orderNo || '未关联' }}</dd></div>
              <div><dt>当前可退</dt><dd>{{ money(conversationContext?.refundableAmount) }}</dd></div>
              <div><dt>退款渠道</dt><dd>{{ conversationContext?.refundChannel || '原路退回' }}</dd></div>
            </dl>
            <label>申请金额<input v-model="conversationRefundForm.amount" required type="number" min="0.01" :max="conversationContext?.refundableAmount || undefined" step="0.01" /></label>
            <label>退款原因<textarea v-model.trim="conversationRefundForm.reason" required maxlength="500" placeholder="请填写客户诉求和退款依据" /></label>
          </div>
          <footer>
            <button type="button" @click="closeConversationRefund">取消</button>
            <button type="submit" class="primary" :disabled="refundActionLoading">提交退款申请</button>
          </footer>
        </form>
      </div>
    </Transition>

    <Transition name="toast-rise"><p v-if="notice" class="center-toast">{{ notice }}</p></Transition>

    <Transition name="modal-fade">
      <div v-if="knowledgeModalOpen" class="modal-backdrop" @click.self="closeProductKnowledge">
        <section class="refund-modal knowledge-modal">
          <header>
            <div><span>产品知识附件 / RAG</span><h2>{{ knowledgeOrder?.title }}</h2></div>
            <button type="button" @click="closeProductKnowledge">×</button>
          </header>
          <div class="refund-modal-body knowledge-modal-body">
            <p class="knowledge-explain">资料按订单中的 SKU 隔离。上传后会自动解析并切片，只有“已索引”的内容会进入智能体检索证据。</p>
            <form class="knowledge-upload" @submit.prevent="submitProductKnowledge">
              <label>资料类型
                <select v-model="knowledgeForm.documentType">
                  <option value="PRODUCT_MANUAL">产品说明书</option><option value="SPECIFICATION">规格参数</option>
                  <option value="USAGE_GUIDE">使用指南</option><option value="TROUBLESHOOTING">故障排查</option>
                  <option value="AFTER_SALES_SOP">售后 SOP</option><option value="FAQ">常见问题</option><option value="OTHER">其他资料</option>
                </select>
              </label>
              <label>资料来源
                <select v-model="knowledgeForm.sourceType"><option value="MANUAL">管理员上传</option><option value="ERP">ERP 同步</option></select>
              </label>
              <label>版本<input v-model.trim="knowledgeForm.version" maxlength="32" placeholder="例如 2026.07" /></label>
              <label>来源编号<input v-model.trim="knowledgeForm.sourceReference" maxlength="255" placeholder="ERP/PIM 文档编号（可选）" /></label>
              <label class="knowledge-file-field">选择文件
                <input ref="knowledgeFileInput" type="file" accept=".pdf,.doc,.docx,.txt,.md,.html,.csv,.json,.xlsx,.pptx" required @change="onKnowledgeFileChange" />
              </label>
              <button type="submit" :disabled="knowledgeUploading || !knowledgeFile">{{ knowledgeUploading ? '解析索引中…' : '上传并索引' }}</button>
            </form>
            <div v-if="knowledgeLoading" class="knowledge-empty">正在读取产品附件…</div>
            <div v-else-if="!knowledgeDocuments.length" class="knowledge-empty">该 SKU 暂无产品资料。上传说明书、规格文件或售后 SOP 后，智能体才能基于这些内容回答。</div>
            <ul v-else class="knowledge-list">
              <li v-for="document in knowledgeDocuments" :key="document.id">
                <div class="knowledge-document-head">
                  <span>📄</span><p><b>{{ document.originalName }}</b><small>{{ knowledgeTypeLabel(document.documentType) }} · {{ knowledgeSourceLabel(document.sourceType) }} · v{{ document.version }} · {{ formatFileSize(document.sizeBytes) }}</small></p>
                  <em :class="['knowledge-index-status', document.indexStatus.toLowerCase()]">{{ knowledgeStatusLabel(document.indexStatus) }}</em>
                </div>
                <p class="knowledge-preview">{{ document.preview || document.errorMessage || '暂无可预览文本' }}</p>
                <div class="knowledge-document-actions"><button type="button" @click="downloadKnowledge(document)">下载原件</button><button type="button" class="danger-action" @click="removeKnowledge(document)">删除</button></div>
              </li>
            </ul>
          </div>
          <footer><button type="button" class="primary" @click="closeProductKnowledge">完成</button></footer>
        </section>
      </div>
    </Transition>

    <ConfirmDialog
      :open="Boolean(pendingSupportUserDeletion)"
      eyebrow="人员管理"
      title="删除客服账号"
      :message="`确定删除“${pendingSupportUserDeletion?.title || ''}（${pendingSupportUserDeletion?.username || ''}）”吗？删除后无法恢复。已有业务记录的账号不能删除，请改为禁用。`"
      confirm-label="确认删除"
      :busy="deleteActionLoading"
      danger
      @cancel="cancelSupportUserDeletion"
      @confirm="deleteSelectedSupportUser"
    />
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DiagnosticBrandLink from '../components/DiagnosticBrandLink.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { getCurrentUser, logout } from '../api/auth.js'
import { getServiceOperationsAnalytics } from '../api/analytics.js'
import {
  archiveAgentConversation,
  archiveCompletedAgentConversations,
  approvePortalRefund,
  createConversationRefund,
  createSupportUser,
  deleteSupportUser,
  deleteProductKnowledgeDocument,
  downloadProductKnowledgeDocument,
  executePortalRefund,
  exportPortalModule,
  getPortalAdvice,
  getConversation,
  getPortalModuleItem,
  getPortalRefunds,
  getProductKnowledgeDocuments,
  rejectPortalRefund,
  replyConversation,
  searchPortalModule,
  triggerReservedExternalSync,
  uploadProductKnowledgeDocument,
  updateSupportUser
} from '../api/portal.js'
import { ROLE, primaryRole, roleLabel } from '../auth/roles.js'

const route = useRoute()
const router = useRouter()
const currentUser = ref(null)
const loading = ref(true)
const loadError = ref('')
const activeView = ref('')
const searchKeyword = ref('')
const moduleSearchInput = ref(null)
const supportUserModalOpen = ref(false)
const supportUserMode = ref('create')
const editingSupportUserId = ref(null)
const primaryActionLoading = ref(false)
const deleteActionLoading = ref(false)
const pendingSupportUserDeletion = ref(null)
const supportUserForm = ref({
  username: '', displayName: '', password: '', status: 'ACTIVE', dailyQuota: 50
})
const selectedRecord = ref(null)
const selectedAdvice = ref(null)
const knowledgeModalOpen = ref(false)
const knowledgeOrder = ref(null)
const knowledgeDocuments = ref([])
const knowledgeLoading = ref(false)
const knowledgeUploading = ref(false)
const knowledgeFileInput = ref(null)
const knowledgeFile = ref(null)
const knowledgeForm = reactive({ documentType: 'PRODUCT_MANUAL', sourceType: 'MANUAL', version: '1', sourceReference: '' })
const selectedConversationId = ref('')
const swipedConversationId = ref('')
const suggestedReply = ref('')
const conversationContext = ref(null)
const conversationMessages = computed(() => conversationContext.value?.messages || [])
const conversationLoading = ref(false)
const conversationReply = ref('')
const conversationFileInput = ref(null)
const conversationAttachments = ref([])
const conversationRefundOpen = ref(false)
const conversationRefundForm = reactive({ amount: '', reason: '', refundChannel: '' })
const selectedRefund = ref(null)
const approvalAmount = ref('')
const decisionReason = ref('')
const refundActionLoading = ref(false)
const syncLoading = ref(false)
const customerPrompt = ref('')
const notice = ref('')
const remoteItems = ref(null)
const remoteRefundRows = ref(null)
const ticketAnalytics = ref(null)
const ticketFilterToday = new Date()
const ticketFilterStart = new Date(ticketFilterToday)
ticketFilterStart.setDate(ticketFilterStart.getDate() - 29)
const localIsoDate = (value) => [
  value.getFullYear(),
  String(value.getMonth() + 1).padStart(2, '0'),
  String(value.getDate()).padStart(2, '0')
].join('-')
const ticketFilters = reactive({
  from: localIsoDate(ticketFilterStart),
  to: localIsoDate(ticketFilterToday),
  priority: '',
  status: '',
  channel: ''
})
const remoteLoading = ref(false)
const remoteError = ref('')
let searchTimer
let conversationRefreshTimer
let portalController
let conversationSwipeStartX = 0
let conversationSwipeStartY = 0
let conversationSwipeMoved = false

const role = computed(() => primaryRole(currentUser.value))
const avatarInitial = computed(() => (currentUser.value?.displayName || currentUser.value?.username || 'U').trim().charAt(0).toUpperCase())
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 11) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const roleMeta = computed(() => ({
  [ROLE.ADMIN]: { label: roleLabel(ROLE.ADMIN), eyebrow: '管理中心', en: 'ADMIN CENTER', navTitle: '管理与决策' },
  [ROLE.SUPPORT_AGENT]: { label: roleLabel(ROLE.SUPPORT_AGENT), eyebrow: '客服中心', en: 'AGENT WORKSPACE', navTitle: '客户服务' },
  [ROLE.CUSTOMER]: { label: roleLabel(ROLE.CUSTOMER), eyebrow: '客户中心', en: 'CUSTOMER CENTER', navTitle: '我的服务' }
}[role.value]))

const navigation = computed(() => ({
  [ROLE.ADMIN]: [
    { key: 'overview', icon: '⌂', label: '运营总览', subtitle: 'Overview', search: '搜索指标或业务模块' },
    { key: 'people', icon: '♙', label: '人员管理', subtitle: 'People' },
    { key: 'ticket-stats', icon: '▥', label: '工单与售后统计', subtitle: 'Ticket analytics' },
    { key: 'orders', icon: '□', label: '订单与产品', subtitle: 'Orders & products' },
    { key: 'logistics', icon: '→', label: '物流数据', subtitle: 'Logistics' },
    { key: 'refund-approval', icon: '¥', label: '退款审批', subtitle: 'Refund approval' },
    { key: 'agent-management', icon: '✦', label: '智能体管理', subtitle: 'Agent settings' },
    { key: 'integrations', icon: '◇', label: '系统集成', subtitle: 'Integrations' },
    { key: 'audit', icon: '≡', label: '审计日志', subtitle: 'Audit log' }
  ],
  [ROLE.SUPPORT_AGENT]: [
    { key: 'workspace', icon: '⌂', label: '工作台', subtitle: 'Workspace' },
    { key: 'conversations', icon: '◌', label: '客户对话', subtitle: 'Conversations' },
    { key: 'tickets', icon: '▥', label: '售后工单', subtitle: 'Tickets' },
    { key: 'orders', icon: '□', label: '订单与产品', subtitle: 'Orders & products' },
    { key: 'logistics', icon: '→', label: '物流履约', subtitle: 'Fulfillment' },
    { key: 'refunds', icon: '¥', label: '退款与审批', subtitle: 'Refunds' },
    { key: 'diagnoses', icon: '⌁', label: '诊断历史', subtitle: 'Diagnosis history' }
  ],
  [ROLE.CUSTOMER]: [
    { key: 'service', icon: '✦', label: '智能服务', subtitle: 'AI service' },
    { key: 'my-orders', icon: '□', label: '我的订单', subtitle: 'My orders' },
    { key: 'my-logistics', icon: '→', label: '物流跟踪', subtitle: 'Tracking' },
    { key: 'after-sales', icon: '▥', label: '售后服务', subtitle: 'After-sales' },
    { key: 'refunds', icon: '¥', label: '退款中心', subtitle: 'Refunds' },
    { key: 'messages', icon: '◌', label: '消息中心', subtitle: 'Messages' },
    { key: 'profile', icon: '♙', label: '个人资料', subtitle: 'Profile' }
  ]
}[role.value] || []))

const activeNav = computed(() => navigation.value.find((item) => item.key === activeView.value) || navigation.value[0] || {})
const syncReservation = computed(() => {
  if (role.value !== ROLE.ADMIN) return null
  if (activeView.value === 'ticket-stats') {
    return { system: 'ERP', label: '同步 ERP 工单数据' }
  }
  if (activeView.value === 'orders') {
    return { system: 'ERP', label: '同步订单/工单信息' }
  }
  if (activeView.value === 'logistics') {
    return { system: 'WMS', label: '同步物流信息' }
  }
  return null
})
const normalizedSearch = computed(() => searchKeyword.value.trim().toLocaleLowerCase('zh-CN'))

function matchesSearch(...values) {
  if (!normalizedSearch.value) return true
  return values.flat().filter((value) => value != null)
    .some((value) => String(value).toLocaleLowerCase('zh-CN').includes(normalizedSearch.value))
}

const sectionCopy = computed(() => {
  const copies = {
    overview: ['掌握今天的服务运营状态', '聚合客服负载、工单趋势、智能体解决率与退款风险。', '导出今日简报'],
    people: ['让合适的人处理合适的问题', '管理客服账号、客服组、角色权限与当前服务负载。', '添加客服人员'],
    'ticket-stats': ['从售后数据中找到改进机会', '追踪工单量、处理状态、优先级、受理渠道和问题分布。', '查看完整报表'],
    workspace: ['聚焦今天最需要处理的客户问题', '待回复会话、即将超时工单与退款进度已经按优先级整理。', '刷新优先队列'],
    conversations: ['与客户保持连续、准确的沟通', '客户问题、智能体摘要和关联退款信息都在同一个会话中。', '刷新客户会话'],
    tickets: ['让每一张售后工单都有明确下一步', '查看客户上下文、可信业务事实、智能诊断与SLA。', '查看待处理'],
    orders: ['统一查看订单与产品事实', '关联客户、支付、售后、物流和产品风险信息。', '查询订单'],
    logistics: ['更快定位履约异常', '对比平台、仓库和承运商节点，发现状态冲突。', '查看异常'],
    refunds: ['持续跟进每一笔客户退款', '查看申请、批准金额、审批结果、退款渠道和到账状态。', '查看待跟进'],
    'refund-approval': ['安全、高效地完成退款审批', '集中查看申请证据、风险提示、历史退款与到账进度。', '处理待审批'],
    'agent-management': ['管理智能客服运行状态', '查看模型调用、运行状态与规则效果。', '刷新运行状态'],
    integrations: ['维护业务系统连接', '查看订单、支付、物流等系统的连接状态。', '刷新连接状态'],
    audit: ['管理审计日志', '所有高风险操作与数据访问均可追溯。', '导出审计日志'],
    diagnoses: ['复核智能诊断结果', '查看诊断编号、关联工单、场景、置信度与处理状态。', '刷新诊断记录'],
    'my-orders': ['查看我的订单与商品', '订单号、商品、金额和履约状态集中展示。', '查询我的订单'],
    'my-logistics': ['跟踪我的包裹', '通过运单号、订单号或商品名称查看最新物流进度。', '查看运输中'],
    'after-sales': ['管理我的售后服务', '查看工单号、关联订单与当前处理状态。', '查看处理中'],
    messages: ['查看服务通知', '退款、物流和售后进度会及时通知。', '查看未读消息'],
    profile: ['管理个人资料', '查看当前账号绑定的客户资料和联系方式。', '刷新个人资料'],
    service: ['你的问题，从这里开始解决', '智能体可以查询订单、物流、售后和退款，复杂问题会无缝转交人工客服。', '进入智能客服']
  }
  const value = copies[activeView.value] || [`管理${activeNav.value.label || '当前模块'}`, '所有数据均按当前账号权限范围展示。', '查看详情']
  return { title: value[0], description: value[1], action: value[2] }
})

function buildSectionData(key) {
  return {
    tableTitle: activeNav.value.label === '工作台' ? '优先处理队列' : `${activeNav.value.label}最新记录`,
    tableDescription: '统计卡与列表均来自当前接口返回结果'
  }
}

const sectionData = computed(() => buildSectionData(activeView.value))
const displayedRecords = computed(() => remoteItems.value ?? [])
const filteredRecords = computed(() => displayedRecords.value.filter((record) =>
  matchesSearch(record.title, record.detail, record.meta, record.status, record.rawStatus,
    record.rawPriority, record.channel, record.scenario, record.customerName, record.businessNo)
))

function includesAny(record, pattern) {
  return pattern.test([record.title, record.detail, record.meta, record.status].filter(Boolean).join(' '))
}

function recordMetric(label, value, note, tone = 'positive') {
  return { label, value: String(value), note, tone }
}

const recordSummary = computed(() => {
  const rows = filteredRecords.value
  const warningRows = rows.filter((row) => row.statusTone === 'warning'
    || includesAny(row, /异常|失败|风险|待处理|待补充|离线|休息|取消|未读/i))
  const warningSet = new Set(warningRows)
  const processing = rows.filter((row) => !warningSet.has(row) && (row.statusTone === 'processing'
    || includesAny(row, /处理中|运输中|执行中|在线|SERVING|ACTIVE/i))).length
  const completed = rows.filter((row) =>
    includesAny(row, /已完成|完成|已解决|已签收|成功|正常|已到账|已采纳|SUCCESS/i)).length
  return { total: rows.length, warning: warningRows.length, processing, completed }
})

const displayMetrics = computed(() => {
  const rows = filteredRecords.value
  const summary = recordSummary.value
  const sameSource = `由下方 ${summary.total} 条记录计算`
  if (activeView.value === 'ticket-stats' && ticketAnalytics.value) {
    const ticketRows = filteredRecords.value
    const pending = ticketRows.filter((row) => ['OPEN', 'PROCESSING'].includes(row.rawStatus)).length
    const resolved = ticketRows.filter((row) => ['RESOLVED', 'CLOSED'].includes(row.rawStatus)).length
    const highPriority = ticketRows.filter((row) => ['HIGH', 'URGENT'].includes(row.rawPriority)).length
    const range = `${ticketAnalytics.value.filter.from} 至 ${ticketAnalytics.value.filter.to}`
    return [
      recordMetric('工单总数', ticketRows.length, searchKeyword.value ? '按当前搜索结果计算' : range),
      recordMetric('待处理工单', pending, 'OPEN + PROCESSING', pending ? 'warning' : 'positive'),
      recordMetric('已解决工单', resolved, 'RESOLVED + CLOSED'),
      recordMetric('高优先级工单', highPriority, 'HIGH + URGENT', highPriority ? 'warning' : 'positive')
    ]
  }
  if (activeView.value === 'people') {
    const online = rows.filter((row) => includesAny(row, /在线|ACTIVE/i)).length
    const workloads = rows.map((row) => Number(row.detail?.match(/(\d+)\s*个处理中工单/)?.[1] || 0))
    const average = rows.length ? (workloads.reduce((sum, value) => sum + value, 0) / rows.length).toFixed(1) : '0.0'
    const groups = new Set(rows.map((row) => row.detail?.split('·')[0]?.trim()).filter(Boolean)).size
    return [
      recordMetric('客服记录数', rows.length, sameSource),
      recordMetric('当前在线', online, `${rows.length - online} 人非在线`, online < rows.length ? 'warning' : 'positive'),
      recordMetric('人均处理中工单', average, '按列表工单数计算'),
      recordMetric('涉及客服组', groups, sameSource)
    ]
  }
  if (['orders', 'my-orders'].includes(activeView.value)) {
    const afterSales = rows.filter((row) => includesAny(row, /售后|退款/)).length
    const inProgress = rows.filter((row) => includesAny(row, /运输中|待支付|处理中|已发货/)).length
    return [
      recordMetric('当前订单记录', rows.length, sameSource),
      recordMetric('关联售后', afterSales, sameSource, afterSales ? 'warning' : 'positive'),
      recordMetric('履约进行中', inProgress, sameSource),
      recordMetric('已完成', summary.completed, sameSource)
    ]
  }
  if (['logistics', 'my-logistics'].includes(activeView.value)) {
    const transit = rows.filter((row) => includesAny(row, /运输中|揽收|转运|派送/)).length
    return [
      recordMetric('当前运单记录', rows.length, sameSource),
      recordMetric('运输中', transit, sameSource),
      recordMetric('状态异常', summary.warning, sameSource, summary.warning ? 'warning' : 'positive'),
      recordMetric('已完成/签收', summary.completed, sameSource)
    ]
  }
  return [
    recordMetric('当前记录', summary.total, sameSource),
    recordMetric('处理中/在线', summary.processing, sameSource),
    recordMetric('需关注', summary.warning, sameSource, summary.warning ? 'warning' : 'positive'),
    recordMetric('已完成/正常', summary.completed, sameSource)
  ]
})

const recordHealth = computed(() => {
  const { total, warning, processing } = recordSummary.value
  const normal = Math.max(0, total - warning - processing)
  const percent = (value) => total ? `${Math.round(value * 100 / total)}%` : '0%'
  return {
    progress: percent(total - warning),
    breakdown: [
      { label: '正常/已完成', value: percent(normal), color: '#38c7bb' },
      { label: '处理中', value: percent(processing), color: '#7474e8' },
      { label: '需关注', value: percent(warning), color: '#ff9a64' }
    ]
  }
})

const dataTip = computed(() => {
  if (remoteError.value) return `当前接口异常：${remoteError.value}。页面未使用模拟数据替代。`
  if (activeView.value === 'refunds' || activeView.value === 'refund-approval') {
    const riskCount = filteredRefunds.value.filter((row) => /高风险|需关注/.test(row.risk)).length
    return riskCount
      ? `当前退款列表中有 ${riskCount} 条风险记录，建议核验订单、历史退款和支付状态。`
      : `当前 ${filteredRefunds.value.length} 条退款记录中没有识别到风险项。`
  }
  if (!recordSummary.value.total) return '当前筛选条件下没有业务记录。'
  return recordSummary.value.warning
    ? `当前列表共 ${recordSummary.value.total} 条记录，其中 ${recordSummary.value.warning} 条需要关注。`
    : `当前列表共 ${recordSummary.value.total} 条记录，未识别到异常或待处理项。`
})

const visibleRefunds = computed(() => remoteRefundRows.value ?? [])
const filteredRefunds = computed(() => visibleRefunds.value.filter((refund) =>
  matchesSearch(refund.id, refund.customer, refund.order, refund.product, refund.requested,
    refund.approved, refund.risk, refund.status, refund.arrival, refund.channel)
))
const refundMetrics = computed(() => {
  const rows = filteredRefunds.value
  const note = `由下方 ${rows.length} 条退款记录计算`
  const pending = rows.filter((row) => /待审批|审核中|已提交/.test(row.status)).length
  const risk = rows.filter((row) => /高风险|需关注/.test(row.risk)).length
  const executing = rows.filter((row) => /处理中|执行中|已批准/.test(row.status)).length
  const arrived = rows.filter((row) => /已到账|成功/.test(row.status)).length
  return [
    recordMetric('当前退款记录', rows.length, note),
    recordMetric('待审批/审核', pending, note, pending ? 'warning' : 'positive'),
    recordMetric('风险记录', risk, note, risk ? 'warning' : 'positive'),
    recordMetric('处理中/已到账', executing + arrived, `${executing} 笔处理中，${arrived} 笔已到账`)
  ]
})

const refundTableDescription = computed(() => role.value === ROLE.SUPPORT_AGENT
  ? '展示负责工单关联客户的退款事实，用于持续沟通和进度跟进'
  : role.value === ROLE.ADMIN ? '审批动作需要独立权限并记录完整审计' : '只展示当前账号本人的退款记录')

const displayedConversations = computed(() => (remoteItems.value ?? [])
  .map((item, index) => ({
      id: item.id, name: item.title, preview: item.detail, time: formatRelative(item.occurredAt), index
    })))
const filteredConversations = computed(() => displayedConversations.value.filter((conversation) =>
  matchesSearch(conversation.name, conversation.preview, conversation.time)
))

const customerQuickActions = [
  { icon: '□', title: '订单问题', text: '查询订单、支付与商品', prompt: '我想查询订单状态' },
  { icon: '→', title: '物流跟踪', text: '查看包裹最新进度', prompt: '我的包裹到哪里了？' },
  { icon: '¥', title: '退款进度', text: '查询审批与到账状态', prompt: '退款什么时候能到账？' },
  { icon: '▥', title: '申请售后', text: '退货、换货、补发', prompt: '我想申请售后服务' }
]
const filteredQuickActions = computed(() => customerQuickActions.filter((item) =>
  matchesSearch(item.title, item.text, item.prompt)
))

const moduleSearchPlaceholder = computed(() => ({
  overview: '搜索客服、工单、退款或物流指标',
  people: '搜索客服姓名、登录账号或账号状态',
  'ticket-stats': '搜索工单状态、优先级或受理渠道',
  workspace: '搜索待办、客户、工单号或状态',
  conversations: '搜索客户姓名或对话内容',
  tickets: '搜索工单号、客户、订单或状态',
  orders: '搜索订单号、客户、SKU或商品',
  logistics: '搜索运单号、订单、承运商或状态',
  refunds: '搜索退款单号、客户、订单或退款状态',
  'refund-approval': '搜索退款单号、客户、风险或审批状态',
  diagnoses: '搜索诊断编号、工单号、场景或状态',
  'agent-management': '搜索调用类型、模型名称或运行状态',
  integrations: '搜索系统名称、连接状态或告警',
  audit: '搜索操作人、资源、动作或请求编号',
  service: '搜索可办理的智能服务',
  'my-orders': '搜索我的订单号、商品或订单状态',
  'my-logistics': '搜索运单号、商品或物流状态',
  'after-sales': '搜索售后单号、商品或处理状态',
  messages: '搜索消息标题或通知内容',
  profile: '搜索个人资料或安全设置'
}[activeView.value] || `搜索${activeNav.value.label || '当前模块'}`))

const searchResultCount = computed(() => {
  if (activeView.value === 'conversations') return filteredConversations.value.length
  if (activeView.value === 'refunds' || activeView.value === 'refund-approval') return filteredRefunds.value.length
  if (activeView.value === 'service') return filteredQuickActions.value.length
  return filteredRecords.value.length
})

function navBadge(item) {
  if (item.key !== activeView.value || remoteLoading.value || remoteError.value) return null
  return searchResultCount.value
}

watch(() => route.query.view, (value) => {
  if (typeof value === 'string' && navigation.value.some((item) => item.key === value)) activeView.value = value
})

watch(navigation, (items) => {
  if (!items.some((item) => item.key === activeView.value)) activeView.value = items[0]?.key || ''
})

onMounted(async () => {
  await loadSession()
  conversationRefreshTimer = window.setInterval(refreshAgentConversations, 5000)
})
onBeforeUnmount(() => {
  window.clearTimeout(searchTimer)
  window.clearInterval(conversationRefreshTimer)
  portalController?.abort()
})

watch([activeView, searchKeyword], () => {
  if (!currentUser.value || loading.value) return
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(loadModuleData, 280)
})

watch(selectedConversationId, async (conversationNo) => {
  conversationReply.value = ''
  conversationAttachments.value = []
  if (conversationFileInput.value) conversationFileInput.value.value = ''
  if (activeView.value === 'conversations' && conversationNo) {
    await loadConversationContext(conversationNo)
  } else {
    conversationContext.value = null
  }
})

async function loadSession() {
  loading.value = true
  loadError.value = ''
  try {
    currentUser.value = await getCurrentUser()
    const requested = typeof route.query.view === 'string' ? route.query.view : ''
    activeView.value = navigation.value.some((item) => item.key === requested) ? requested : navigation.value[0]?.key
    await loadModuleData()
  } catch (error) {
    loadError.value = error.message || '无法读取当前账号'
  } finally {
    loading.value = false
  }
}

async function applyTicketFilters() {
  if (ticketFilters.from && ticketFilters.to && ticketFilters.from > ticketFilters.to) {
    showNotice('开始日期不能晚于结束日期')
    return
  }
  searchKeyword.value = ''
  await loadModuleData()
}

async function resetTicketFilters() {
  ticketFilters.from = localIsoDate(ticketFilterStart)
  ticketFilters.to = localIsoDate(ticketFilterToday)
  ticketFilters.priority = ''
  ticketFilters.status = ''
  ticketFilters.channel = ''
  searchKeyword.value = ''
  await loadModuleData()
}

async function loadModuleData() {
  portalController?.abort()
  portalController = new AbortController()
  remoteLoading.value = true
  remoteError.value = ''
  if (activeView.value === 'refunds' || activeView.value === 'refund-approval') {
    remoteRefundRows.value = []
    remoteItems.value = null
  } else {
    remoteItems.value = []
    remoteRefundRows.value = null
  }
  try {
    if (activeView.value === 'refunds' || activeView.value === 'refund-approval') {
      const rows = await getPortalRefunds(role.value, searchKeyword.value, portalController.signal)
      remoteRefundRows.value = rows.map(portalRefundRow)
      remoteItems.value = null
    } else if (activeView.value === 'ticket-stats' && role.value === ROLE.ADMIN) {
      ticketAnalytics.value = await getServiceOperationsAnalytics(ticketFilters, portalController.signal)
      remoteItems.value = ticketAnalytics.value.tickets.map(analyticsTicketItem)
      remoteRefundRows.value = null
    } else {
      ticketAnalytics.value = null
      const result = await searchPortalModule(
        role.value, activeView.value, searchKeyword.value, portalController.signal
      )
      remoteItems.value = result.items.map(portalItem)
      remoteRefundRows.value = null
      if (activeView.value === 'conversations') {
        const available = new Set(remoteItems.value.map((item) => item.id))
        if (!available.has(selectedConversationId.value)) {
          selectedConversationId.value = remoteItems.value[0]?.id || ''
        } else if (selectedConversationId.value) {
          await loadConversationContext(selectedConversationId.value)
        }
      }
    }
  } catch (error) {
    if (error.name !== 'AbortError') {
      remoteError.value = error.message || '搜索暂时不可用'
      if (activeView.value === 'refunds' || activeView.value === 'refund-approval') remoteRefundRows.value = []
      else remoteItems.value = []
    }
  } finally {
    remoteLoading.value = false
  }
}

async function refreshAgentConversations() {
  if (role.value !== ROLE.SUPPORT_AGENT || activeView.value !== 'conversations' || remoteLoading.value) return
  try {
    const result = await searchPortalModule(role.value, 'conversations', searchKeyword.value)
    remoteItems.value = result.items.map(portalItem)
    const available = new Set(remoteItems.value.map((item) => item.id))
    if (!available.has(selectedConversationId.value)) {
      selectedConversationId.value = remoteItems.value[0]?.id || ''
    } else if (selectedConversationId.value) {
      await loadConversationContext(selectedConversationId.value, true)
    }
  } catch {
    // 定时刷新失败时保留当前列表，下一轮自动重试，避免打断客服正在输入的回复。
  }
}

function beginConversationSwipe(event, conversationId) {
  conversationSwipeStartX = event.clientX
  conversationSwipeStartY = event.clientY
  conversationSwipeMoved = false
  if (swipedConversationId.value && swipedConversationId.value !== conversationId) {
    swipedConversationId.value = ''
  }
}

function finishConversationSwipe(event, conversationId) {
  const distanceX = event.clientX - conversationSwipeStartX
  const distanceY = event.clientY - conversationSwipeStartY
  if (Math.abs(distanceX) <= Math.abs(distanceY) * 1.2) return
  if (distanceX < -42) {
    conversationSwipeMoved = true
    swipedConversationId.value = conversationId
  } else if (distanceX > 24) {
    conversationSwipeMoved = true
    swipedConversationId.value = ''
  }
}

function cancelConversationSwipe() {
  conversationSwipeMoved = false
}

function openConversationFromList(conversation) {
  if (conversationSwipeMoved) {
    conversationSwipeMoved = false
    return
  }
  if (swipedConversationId.value === conversation.id) {
    swipedConversationId.value = ''
    return
  }
  selectedConversationId.value = conversation.id
}

async function deleteConversation(conversation) {
  if (deleteActionLoading.value) return
  deleteActionLoading.value = true
  try {
    await archiveAgentConversation(conversation.id)
    swipedConversationId.value = ''
    await loadModuleData()
    showNotice('会话已从客服队列移除；客户再次发消息时会自动恢复')
  } catch (error) {
    showNotice(error.message || '会话删除失败')
  } finally {
    deleteActionLoading.value = false
  }
}

async function clearCompletedConversations() {
  if (deleteActionLoading.value) return
  if (!window.confirm('确定清理所有等待客户回复和已结束的会话吗？')) return
  deleteActionLoading.value = true
  try {
    const count = await archiveCompletedAgentConversations()
    await loadModuleData()
    showNotice(count ? `已清理 ${count} 个已完成会话` : '当前没有可清理的已完成会话')
  } catch (error) {
    showNotice(error.message || '批量清理失败')
  } finally {
    deleteActionLoading.value = false
  }
}

function analyticsTicketItem(ticket) {
  const status = localizeBusinessStatus(ticket.status)
  const priority = { URGENT: '紧急', HIGH: '高', NORMAL: '普通', LOW: '低' }[ticket.priority] || ticket.priority
  return {
    id: ticket.ticketNo,
    title: ticket.ticketNo,
    detail: `${ticket.customerName} · ${ticket.businessNo || '未关联业务单号'}`,
    meta: `${priority}优先级 · ${ticket.channel}`,
    status,
    rawStatus: ticket.status,
    rawPriority: ticket.priority,
    channel: ticket.channel,
    scenario: ticket.scenario || 'UNCLASSIFIED',
    customerName: ticket.customerName,
    businessNo: ticket.businessNo,
    occurredAt: ticket.createdAt,
    icon: '▥',
    tone: ticket.priority === 'URGENT' ? 'orange' : 'violet',
    statusTone: ['OPEN'].includes(ticket.status) ? 'warning'
      : ticket.status === 'PROCESSING' ? 'processing' : 'normal'
  }
}

async function handleReservedSync() {
  if (!syncReservation.value || syncLoading.value) return
  syncLoading.value = true
  try {
    const result = await triggerReservedExternalSync(syncReservation.value.system)
    showNotice(result.message || `${result.integration} 接口待配置，当前未执行同步。`)
  } catch (error) {
    showNotice(error.message || '无法检查外部系统同步配置')
  } finally {
    syncLoading.value = false
  }
}

function portalItem(item) {
  const status = localizeBusinessStatus(item.status)
  const localizedDetail = localizeBusinessDetail(item.detail)
  const [username = '', roleCode = ''] = String(localizedDetail || '').split('·').map((part) => part.trim())
  const dailyQuota = Number(String(item.meta || '').match(/(\d+)/)?.[1] || 0)
  return {
    id: item.id, title: item.title, detail: localizedDetail, meta: item.meta || '--', status,
    occurredAt: item.occurredAt, extra: item.extra || {}, icon: '◇', tone: 'violet',
    rawStatus: item.status, username, roleCode, dailyQuota,
    statusTone: /异常|失败|风险|需关注|待处理|等待|待支付|已取消|离线/.test(status) ? 'warning'
      : /处理中|运输|已发货|在线|服务中/.test(status) ? 'processing' : 'normal'
  }
}

function localizeBusinessDetail(value) {
  return String(value || '').replace(
    /\b(PENDING_PAYMENT|PARTIALLY_REFUNDED|OUT_FOR_DELIVERY|ARRIVED_TRANSIT|NOT_CONFIGURED|IN_TRANSIT|PICKED_UP|DISCONNECTED|PROCESSING|CANCELLED|COMPLETED|DELIVERED|REFUNDED|CONNECTED|SHIPPED|UNPAID|PAID|SUCCESS|FAILED|EXCEPTION)\b/g,
    (status) => ({ UNPAID: '未支付', PAID: '已支付', REFUNDED: '已退款', PARTIALLY_REFUNDED: '部分退款' }[status]
      || localizeBusinessStatus(status))
  )
}

function localizeBusinessStatus(value) {
  const status = String(value || '--').toUpperCase()
  return {
    PENDING_PAYMENT: '待支付',
    UNPAID: '待支付',
    PAID: '已支付',
    CANCELLED: '已取消',
    SHIPPED: '已发货',
    IN_TRANSIT: '运输中',
    PICKED_UP: '已揽收',
    ARRIVED_TRANSIT: '已到达转运中心',
    OUT_FOR_DELIVERY: '派送中',
    EXCEPTION: '运输异常',
    DELIVERED: '已签收',
    COMPLETED: '已完成',
    OPEN: '待处理',
    PENDING: '待处理',
    PROCESSING: '处理中',
    RESOLVED: '已解决',
    ACTIVE: '在线',
    ONLINE: '在线',
    INACTIVE: '离线',
    OFFLINE: '离线',
    SERVING: '服务中',
    ATTENTION: '需关注',
    METRIC: '统计指标',
    SUCCESS: '成功',
    FAILED: '失败',
    ABNORMAL: '异常',
    CONNECTED: '已连接',
    DISCONNECTED: '未连接',
    NOT_CONFIGURED: '待配置'
  }[status] || value || '--'
}

function portalRefundRow(row) {
  return {
    id: row.refundNo, customer: row.customerName, order: row.orderNo, product: row.product || '商品',
    requested: money(row.requestedAmount), approved: row.approvedAmount == null ? '待审批' : money(row.approvedAmount),
    risk: riskLabel(row.riskLevel), riskTone: { HIGH: 'high', MEDIUM: 'medium', LOW: 'low' }[row.riskLevel] || 'low',
    rawStatus: row.status,
    status: refundStatus(row.status),
    statusTone: ['SUBMITTED', 'UNDER_REVIEW', 'NEED_MORE_INFO'].includes(row.status) ? 'warning'
      : ['APPROVED', 'EXECUTING'].includes(row.status) ? 'processing' : 'normal',
    arrival: row.completedAt ? `${new Date(row.completedAt).toLocaleString('zh-CN')} 已到账`
      : row.expectedArrivalAt ? `预计 ${new Date(row.expectedArrivalAt).toLocaleDateString('zh-CN')} 前`
        : '尚未执行',
    channel: row.refundChannel || '--', createdAt: formatRelative(row.requestedAt),
    riskDetail: row.riskMessage || '暂无风险提示'
  }
}

function money(value) {
  return value == null ? '--' : `¥${Number(value).toFixed(2)}`
}

function riskLabel(value) {
  return { HIGH: '高风险', MEDIUM: '需关注', LOW: '低风险' }[value] || value || '未评估'
}

function refundStatus(value) {
  return {
    SUBMITTED: '已提交', UNDER_REVIEW: '待审批', NEED_MORE_INFO: '待补充',
    APPROVED: '已批准', REJECTED: '已拒绝', EXECUTING: '退款处理中',
    SUCCEEDED: '已到账', FAILED: '执行失败', CANCELLED: '已撤回'
  }[value] || value
}

function formatRelative(value) {
  if (!value) return '--'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function selectView(key) {
  activeView.value = key
  searchKeyword.value = ''
  router.replace({ name: 'personal-center', query: { view: key } })
}

function signOut() {
  logout()
  router.replace('/')
}

async function handlePrimaryAction() {
  if (primaryActionLoading.value) return
  if (activeView.value === 'people') {
    supportUserMode.value = 'create'
    editingSupportUserId.value = null
    supportUserForm.value = {
      username: '', displayName: '', password: '', status: 'ACTIVE', dailyQuota: 50
    }
    supportUserModalOpen.value = true
    return
  }
  if (activeView.value === 'service') {
    await router.push('/workspace')
    return
  }
  if (activeView.value === 'ticket-stats' && role.value === ROLE.ADMIN) {
    await router.push('/analytics')
    return
  }
  if (['overview', 'audit'].includes(activeView.value)) {
    await downloadCurrentModule()
    return
  }
  const presets = {
    logistics: '异常',
    'my-logistics': '运输中',
    refunds: role.value === ROLE.ADMIN ? 'UNDER_REVIEW' : 'EXECUTING',
    'refund-approval': 'UNDER_REVIEW',
    tickets: 'OPEN',
    'after-sales': 'PROCESSING',
    messages: 'UNREAD'
  }
  if (presets[activeView.value]) searchKeyword.value = presets[activeView.value]
  await loadModuleData()
  if (['orders', 'my-orders'].includes(activeView.value)) moduleSearchInput.value?.focus()
  showNotice(`${sectionCopy.value.action}已完成，共找到 ${searchResultCount.value} 条记录`)
}

async function downloadCurrentModule() {
  primaryActionLoading.value = true
  try {
    const file = await exportPortalModule(role.value, activeView.value, searchKeyword.value)
    const blob = new Blob([file.content], { type: file.contentType })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = file.fileName
    link.click()
    URL.revokeObjectURL(url)
    showNotice(`已生成 ${file.fileName}`)
  } catch (error) {
    showNotice(error.message || '导出失败')
  } finally {
    primaryActionLoading.value = false
  }
}

async function submitSupportUser() {
  primaryActionLoading.value = true
  try {
    if (supportUserMode.value === 'edit') {
      await updateSupportUser(editingSupportUserId.value, {
        username: supportUserForm.value.username,
        displayName: supportUserForm.value.displayName,
        status: supportUserForm.value.status,
        dailyQuota: Number(supportUserForm.value.dailyQuota)
      })
    } else {
      await createSupportUser({
        username: supportUserForm.value.username,
        displayName: supportUserForm.value.displayName,
        password: supportUserForm.value.password
      })
    }
    const message = supportUserMode.value === 'edit' ? '客服信息修改成功' : '客服账号创建成功'
    closeSupportUserModal()
    await loadModuleData()
    showNotice(message)
  } catch (error) {
    showNotice(error.message || (supportUserMode.value === 'edit' ? '修改客服信息失败' : '创建客服账号失败'))
  } finally {
    primaryActionLoading.value = false
  }
}

function openSupportUserEditor(record) {
  supportUserMode.value = 'edit'
  editingSupportUserId.value = record.id
  supportUserForm.value = {
    username: record.username,
    displayName: record.title,
    password: '',
    status: record.rawStatus === 'DISABLED' ? 'DISABLED' : 'ACTIVE',
    dailyQuota: record.dailyQuota || 50
  }
  supportUserModalOpen.value = true
}

function confirmSupportUserDeletion(record) {
  pendingSupportUserDeletion.value = record
}

function cancelSupportUserDeletion() {
  if (!deleteActionLoading.value) pendingSupportUserDeletion.value = null
}

async function deleteSelectedSupportUser() {
  const record = pendingSupportUserDeletion.value
  if (!record || deleteActionLoading.value) return
  deleteActionLoading.value = true
  try {
    await deleteSupportUser(record.id)
    pendingSupportUserDeletion.value = null
    await loadModuleData()
    showNotice(`客服账号 ${record.username} 已删除`)
  } catch (error) {
    showNotice(error.message || '删除客服账号失败')
  } finally {
    deleteActionLoading.value = false
  }
}

function closeSupportUserModal() {
  supportUserModalOpen.value = false
  supportUserMode.value = 'create'
  editingSupportUserId.value = null
  supportUserForm.value = {
    username: '', displayName: '', password: '', status: 'ACTIVE', dailyQuota: 50
  }
}

function showAllRecords() {
  searchKeyword.value = ''
  loadModuleData()
}

function setRefundFilter(status) {
  searchKeyword.value = status
  loadModuleData()
}

async function openRecord(record) {
  if (!record.id) {
    selectedRecord.value = record
    return
  }
  primaryActionLoading.value = true
  try {
    selectedRecord.value = await getPortalModuleItem(role.value, activeView.value, record.id)
  } catch (error) {
    showNotice(error.message || '详情加载失败')
  } finally {
    primaryActionLoading.value = false
  }
}

async function openProductKnowledge(record) {
  knowledgeModalOpen.value = true
  knowledgeOrder.value = record
  knowledgeDocuments.value = []
  knowledgeLoading.value = true
  try {
    knowledgeDocuments.value = await getProductKnowledgeDocuments(record.id)
  } catch (error) {
    showNotice(error.message || '产品附件加载失败')
  } finally {
    knowledgeLoading.value = false
  }
}

function closeProductKnowledge() {
  if (knowledgeUploading.value) return
  knowledgeModalOpen.value = false
  knowledgeOrder.value = null
  knowledgeDocuments.value = []
  knowledgeFile.value = null
  if (knowledgeFileInput.value) knowledgeFileInput.value.value = ''
}

function onKnowledgeFileChange(event) {
  knowledgeFile.value = event.target.files?.[0] || null
}

async function submitProductKnowledge() {
  if (!knowledgeOrder.value || !knowledgeFile.value || knowledgeUploading.value) return
  knowledgeUploading.value = true
  try {
    await uploadProductKnowledgeDocument(knowledgeOrder.value.id, knowledgeForm, knowledgeFile.value)
    knowledgeDocuments.value = await getProductKnowledgeDocuments(knowledgeOrder.value.id)
    knowledgeOrder.value.extra.knowledgeDocumentCount = knowledgeDocuments.value.length
    knowledgeFile.value = null
    if (knowledgeFileInput.value) knowledgeFileInput.value.value = ''
    showNotice('产品资料已解析、切片并建立检索索引')
  } catch (error) {
    showNotice(error.message || '产品资料上传失败')
  } finally {
    knowledgeUploading.value = false
  }
}

async function removeKnowledge(document) {
  if (!knowledgeOrder.value || !window.confirm(`确定删除“${document.originalName}”及其检索切片吗？`)) return
  try {
    await deleteProductKnowledgeDocument(knowledgeOrder.value.id, document.id)
    knowledgeDocuments.value = knowledgeDocuments.value.filter((item) => item.id !== document.id)
    knowledgeOrder.value.extra.knowledgeDocumentCount = knowledgeDocuments.value.length
    showNotice('产品资料及其检索切片已删除')
  } catch (error) {
    showNotice(error.message || '产品资料删除失败')
  }
}

async function downloadKnowledge(knowledgeDocument) {
  try {
    const file = await downloadProductKnowledgeDocument(knowledgeOrder.value.id, knowledgeDocument.id)
    const url = URL.createObjectURL(file.blob)
    const link = window.document.createElement('a')
    link.href = url
    link.download = file.fileName || knowledgeDocument.originalName
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    showNotice(error.message || '附件下载失败')
  }
}

function knowledgeTypeLabel(value) {
  return { PRODUCT_MANUAL: '产品说明书', SPECIFICATION: '规格参数', USAGE_GUIDE: '使用指南', TROUBLESHOOTING: '故障排查', AFTER_SALES_SOP: '售后 SOP', FAQ: '常见问题', OTHER: '其他资料' }[value] || value
}

function knowledgeSourceLabel(value) {
  return { ERP: 'ERP 同步', MANUAL: '管理员上传' }[value] || value
}

function knowledgeStatusLabel(value) {
  return { PENDING: '待索引', INDEXED: '已索引', FAILED: '索引失败' }[value] || value
}

async function openAdvice() {
  primaryActionLoading.value = true
  try {
    selectedAdvice.value = await getPortalAdvice(role.value, activeView.value)
  } catch (error) {
    showNotice(error.message || '智能建议加载失败')
  } finally {
    primaryActionLoading.value = false
  }
}

function closeInfoModal() {
  selectedRecord.value = null
  selectedAdvice.value = null
}

function openNotifications() {
  const target = role.value === ROLE.CUSTOMER ? 'messages'
    : role.value === ROLE.SUPPORT_AGENT ? 'conversations' : 'audit'
  selectView(target)
}

async function sendSuggestedReply() {
  const conversationNo = selectedConversationId.value || displayedConversations.value[0]?.id
  if (!conversationNo) {
    showNotice('当前没有可回复的客户会话')
    return
  }
  primaryActionLoading.value = true
  try {
    await replyConversation(conversationNo, conversationReply.value, conversationAttachments.value)
    conversationReply.value = ''
    conversationAttachments.value = []
    if (conversationFileInput.value) conversationFileInput.value.value = ''
    await loadConversationContext(conversationNo)
    showNotice('回复已写入客户会话')
  } catch (error) {
    showNotice(error.message || '发送回复失败')
  } finally {
    primaryActionLoading.value = false
  }
}

async function loadConversationContext(conversationNo = selectedConversationId.value, silent = false) {
  if (!conversationNo) {
    conversationContext.value = null
    suggestedReply.value = ''
    return
  }
  if (!silent) {
    conversationLoading.value = true
    suggestedReply.value = ''
  }
  try {
    conversationContext.value = await getConversation(conversationNo)
    suggestedReply.value = conversationContext.value?.suggestedReply || ''
  } catch (error) {
    if (!silent) {
      conversationContext.value = null
      suggestedReply.value = ''
      showNotice(error.message || '会话消息加载失败')
    }
  } finally {
    if (!silent) conversationLoading.value = false
  }
}

function selectConversationAttachments(event) {
  const selected = [...(event.target.files || [])]
  const merged = [...conversationAttachments.value, ...selected]
  if (merged.length > 5) {
    showNotice('每次最多添加 5 个附件')
    event.target.value = ''
    return
  }
  const oversized = merged.find((file) => file.size > 5 * 1024 * 1024)
  if (oversized) {
    showNotice(`附件 ${oversized.name} 超过 5MB`)
    event.target.value = ''
    return
  }
  conversationAttachments.value = merged
  event.target.value = ''
}

function removeConversationAttachment(index) {
  conversationAttachments.value.splice(index, 1)
}

function formatFileSize(size) {
  const bytes = Number(size || 0)
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function formatMessageTime(value) {
  if (!value) return ''
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}

function openConversationRefund() {
  if (!conversationContext.value?.orderNo) {
    showNotice('当前会话未关联订单，不能发起退款')
    return
  }
  conversationRefundForm.amount = Number(conversationContext.value.refundableAmount || 0).toFixed(2)
  conversationRefundForm.reason = ''
  conversationRefundForm.refundChannel = conversationContext.value.refundChannel || ''
  conversationRefundOpen.value = true
}

function closeConversationRefund() {
  if (!primaryActionLoading.value) conversationRefundOpen.value = false
}

async function submitConversationRefund() {
  const amount = Number(conversationRefundForm.amount)
  if (!Number.isFinite(amount) || amount <= 0) {
    showNotice('请输入有效的退款金额')
    return
  }
  if (amount > Number(conversationContext.value?.refundableAmount || 0)) {
    showNotice('退款金额不能超过当前可退金额')
    return
  }
  if (!conversationRefundForm.reason.trim()) {
    showNotice('请填写退款原因')
    return
  }
  primaryActionLoading.value = true
  try {
    const result = await createConversationRefund(selectedConversationId.value, {
      amount,
      reason: conversationRefundForm.reason.trim(),
      refundChannel: conversationRefundForm.refundChannel || null
    })
    conversationRefundOpen.value = false
    await loadConversationContext(selectedConversationId.value)
    showNotice(`退款单 ${result.refundNo || result.id} 已创建，可在“退款与审批”模块跟进`)
  } catch (error) {
    showNotice(error.message || '发起退款失败')
  } finally {
    primaryActionLoading.value = false
  }
}

function openRefund(refund) {
  selectedRefund.value = refund
  approvalAmount.value = refund.approved.startsWith('¥') ? refund.approved.replace('¥', '') : refund.requested.replace('¥', '')
  decisionReason.value = ''
}

async function approveSelectedRefund() {
  const amount = Number(approvalAmount.value)
  if (!Number.isFinite(amount) || amount <= 0) {
    showNotice('请输入有效的批准金额')
    return
  }
  await runRefundAction(
    () => approvePortalRefund(selectedRefund.value.id, amount, decisionReason.value.trim()),
    '退款已批准，可继续执行退款'
  )
}

async function rejectSelectedRefund() {
  if (!decisionReason.value.trim()) {
    showNotice('拒绝退款必须填写原因')
    return
  }
  await runRefundAction(
    () => rejectPortalRefund(selectedRefund.value.id, decisionReason.value.trim()),
    '退款申请已拒绝'
  )
}

async function executeSelectedRefund() {
  await runRefundAction(
    () => executePortalRefund(selectedRefund.value.id),
    '退款已提交支付渠道执行'
  )
}

async function runRefundAction(action, successMessage) {
  if (refundActionLoading.value) return
  refundActionLoading.value = true
  try {
    await action()
    selectedRefund.value = null
    await loadModuleData()
    showNotice(successMessage)
  } catch (error) {
    showNotice(error.message || '退款操作失败')
  } finally {
    refundActionLoading.value = false
  }
}

function showNotice(message) {
  notice.value = message
  window.setTimeout(() => { notice.value = '' }, 3200)
}

function startCustomerDiagnosis() {
  if (!customerPrompt.value.trim()) return
  router.push({ name: 'workspace', query: { description: customerPrompt.value.trim() } })
}
</script>

<style scoped>
.role-center{min-height:100vh;color:#20283a;background:radial-gradient(circle at 8% 3%,#c8f2ed 0,transparent 25%),radial-gradient(circle at 76% 0,#e2dafa 0,transparent 31%),linear-gradient(135deg,#f4f8f7,#f8f6f3 48%,#f2f4fb);font-family:"Microsoft YaHei UI","PingFang SC",sans-serif}.center-state{min-height:100vh;display:grid;place-content:center;justify-items:center;text-align:center}.center-state h1{margin:18px 0 7px}.center-state p{margin:0;color:#7d879a}.center-state button{margin-top:20px;padding:10px 18px;border:0;border-radius:12px;background:#252d42;color:#fff}.state-spinner{width:42px;height:42px;border:4px solid #dce5e6;border-top-color:#48c9bf;border-radius:50%;animation:center-spin .8s linear infinite}.state-icon{display:grid;place-items:center;width:42px;height:42px;border-radius:50%;background:#fff0f1;color:#c85261;font-size:24px}.center-shell{display:grid;grid-template-columns:248px minmax(0,1fr);min-height:100vh}.center-sidebar{position:sticky;top:0;height:100vh;display:flex;flex-direction:column;padding:22px 16px;border-right:1px solid #fff;background:#ffffffad;box-shadow:18px 0 55px #4d5b7510;backdrop-filter:blur(20px)}.center-brand{display:flex;align-items:center;gap:10px;padding:0 8px 20px;color:inherit;text-decoration:none}.center-brand :deep(.brand-mark){width:40px;height:40px}.center-brand b,.center-brand small{display:block}.center-brand b{font-size:17px}.center-brand small{margin-top:3px;color:#8a94a7;font-size:10px}.identity-card{position:relative;display:flex;align-items:center;gap:10px;padding:13px;border:1px solid #ffffff;border-radius:16px;background:linear-gradient(120deg,#f4ffff,#f4f1ff);box-shadow:0 10px 25px #49566d0d}.identity-avatar{display:grid;place-items:center;width:40px;height:40px;border-radius:13px;background:linear-gradient(145deg,#40cbbf,#706fe4);color:#fff;font-weight:900}.identity-card span,.identity-card strong,.identity-card small{display:block}.identity-card span{color:#6f79d5;font-size:8px;font-weight:800}.identity-card strong{margin-top:2px;font-size:12px}.identity-card small{margin-top:2px;color:#8c96a7;font-size:9px}.identity-card i{position:absolute;right:12px;top:14px;width:7px;height:7px;border-radius:50%;background:#2ab989;box-shadow:0 0 0 4px #2ab9891f}.center-nav{margin-top:20px}.center-nav>p{margin:0 10px 8px;color:#9aa3b2;font-size:9px;font-weight:800;letter-spacing:.12em}.center-nav button{display:flex;align-items:center;width:100%;min-height:46px;margin:3px 0;padding:7px 10px;border:0;border-radius:12px;background:transparent;color:#59657a;text-align:left;cursor:pointer}.center-nav button>span{display:grid;place-items:center;width:30px;height:30px;margin-right:9px;border-radius:9px;background:#eef2f5;color:#6c778a;font-size:13px}.center-nav button b{font-size:11px}.center-nav button small{display:block;margin-top:2px;color:#9ca5b4;font-size:8px;font-weight:500}.center-nav button em{margin-left:auto;min-width:20px;padding:3px 6px;border-radius:20px;background:#fff0e8;color:#cc6b38;font-size:8px;font-style:normal;text-align:center}.center-nav button.active{background:linear-gradient(100deg,#dff6f4,#e9e7fa);color:#3948ae}.center-nav button.active>span{background:linear-gradient(145deg,#47c9bf,#706fe4);color:#fff;box-shadow:0 7px 15px #5f70d934}.sidebar-footer{margin-top:auto;padding:15px 8px 2px;border-top:1px solid #dfe5ea}.sidebar-footer a,.sidebar-footer button{display:block;width:100%;padding:8px 4px;border:0;background:transparent;color:#7d8799;font-size:10px;text-align:left;text-decoration:none;cursor:pointer}.sidebar-footer button{color:#bd5964}.center-main{min-width:0}.center-topbar{position:sticky;top:0;z-index:5;display:flex;align-items:center;min-height:76px;padding:0 30px;border-bottom:1px solid #ffffff;background:#f7f8f6c7;backdrop-filter:blur(20px)}.center-topbar>div>span{color:#6e77d8;font-size:8px;font-weight:900;letter-spacing:.13em}.center-topbar h1{margin:4px 0 0;font-size:20px}.topbar-actions{display:flex;align-items:center;gap:12px;margin-left:auto}.topbar-actions label{display:flex;align-items:center;width:260px;height:36px;padding:0 13px;border:1px solid #e4e7eb;border-radius:999px;background:#fff}.topbar-actions label span{color:#9aa3b1}.topbar-actions input{flex:1;min-width:0;margin-left:8px;border:0;outline:0;font-size:10px}.notification-button{position:relative;width:36px;height:36px;border:1px solid #e4e7eb;border-radius:50%;background:#fff;color:#657086}.notification-button i{position:absolute;right:5px;top:4px;width:6px;height:6px;border-radius:50%;background:#ff835d}.topbar-user b,.topbar-user small{display:block}.topbar-user b{font-size:10px}.topbar-user small{margin-top:2px;color:#929baa;font-size:8px}.center-content{padding:26px 30px 40px}.welcome-strip{position:relative;overflow:hidden;display:flex;align-items:center;min-height:142px;padding:22px 28px;border:1px solid #ffffff;border-radius:22px;background:radial-gradient(circle at 78% 30%,#ffffffb8 0 8%,transparent 9%),linear-gradient(105deg,#e0f7f3,#e9e5fa 66%,#f8ece5);box-shadow:0 18px 44px #49566d0e}.welcome-strip:after{position:absolute;right:80px;width:170px;height:170px;border:1px solid #ffffff9c;border-radius:50%;content:"";box-shadow:0 0 0 28px #ffffff24,0 0 0 57px #ffffff1c}.welcome-strip span{color:#5261ce;font-size:10px;font-weight:800}.welcome-strip h2{margin:8px 0 5px;font:600 25px Georgia,"Microsoft YaHei UI",serif}.welcome-strip p{margin:0;color:#778195;font-size:10px}.welcome-strip>button{z-index:1;margin-left:auto;padding:11px 16px;border:0;border-radius:999px;background:#242d42;color:#fff;font-size:10px;cursor:pointer}.welcome-strip>button b{margin-left:8px}.panel{border:1px solid #ffffff;border-radius:18px;background:#ffffffc9;box-shadow:0 14px 35px #47536c0d;backdrop-filter:blur(15px)}.metric-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:13px;margin-top:16px}.center-metric-card{padding:17px}.center-metric-card span{color:#7c8799;font-size:9px}.center-metric-card strong{display:block;margin:8px 0 6px;font-size:24px}.center-metric-card small{font-size:8px}.positive{color:#24a77d}.warning{color:#d07141}.content-grid{display:grid;grid-template-columns:minmax(0,1fr) 290px;gap:15px;margin-top:15px}.data-panel{overflow:hidden}.panel-heading{display:flex;align-items:center;min-height:62px;padding:0 18px;border-bottom:1px solid #e9ecef}.panel-heading h3{margin:0;font-size:13px}.panel-heading p{margin:5px 0 0;color:#929baa;font-size:8px}.panel-heading>button{margin-left:auto;border:0;background:transparent;color:#5463cf;font-size:9px}.record-list>div{display:grid;grid-template-columns:38px minmax(0,1fr) 110px 80px 46px;align-items:center;min-height:66px;padding:0 18px;border-bottom:1px solid #eceff1}.record-list>div:last-child{border-bottom:0}.record-icon{display:grid;place-items:center;width:28px;height:28px;border-radius:9px}.record-icon.violet{background:#eeebff;color:#6f67db}.record-icon.aqua{background:#e3f8f6;color:#25a89f}.record-icon.orange{background:#fff0e6;color:#cd7142}.record-list p{margin:0}.record-list p b,.record-list p small{display:block}.record-list p b{font-size:10px}.record-list p small{margin-top:4px;color:#8d97a7;font-size:8px}.record-list em{color:#7e889a;font-size:8px;font-style:normal}.record-list button,.table-action{border:0;background:transparent;color:#5365d3;font-size:9px;cursor:pointer}.status{display:inline-block;width:max-content;padding:5px 8px;border-radius:999px;background:#e8f8f2;color:#258662;font-size:8px}.status.warning{background:#fff0e7;color:#bd683b}.status.processing{background:#ecebff;color:#6561ce}.side-stack{display:grid;gap:15px}.progress-panel{padding:18px}.progress-panel header{display:flex;align-items:center}.progress-panel h3{margin:0;font-size:12px}.progress-panel header span{margin-left:auto;color:#8c96a6;font-size:8px}.progress-ring{display:grid;place-items:center;width:118px;height:118px;margin:17px auto;border-radius:50%;background:conic-gradient(#49c9bf var(--progress),#e9edf0 0);box-shadow:inset 0 0 0 13px #fff}.progress-ring strong,.progress-ring small{grid-area:1/1}.progress-ring strong{margin-top:-14px;font-size:23px}.progress-ring small{margin-top:24px;color:#8d97a7;font-size:8px}.progress-panel ul{margin:0;padding:0;list-style:none}.progress-panel li{display:flex;padding:7px 0;border-bottom:1px solid #edf0f2;color:#737e92;font-size:8px}.progress-panel li b{margin-left:auto;color:#30394d}.progress-panel li i{display:inline-block;width:6px;height:6px;margin-right:6px;border-radius:50%}.assistant-tip{padding:17px;background:linear-gradient(135deg,#282f46,#3a4261);color:#fff}.assistant-tip span{color:#76ded5;font-size:9px;font-weight:800}.assistant-tip p{margin:10px 0;color:#d7dbea;font-size:9px;line-height:1.7}.assistant-tip button{padding:0;border:0;background:transparent;color:#fff;font-size:8px}.conversation-layout{display:grid;grid-template-columns:280px minmax(0,1fr);gap:15px;margin-top:16px;height:570px}.conversation-list{overflow:hidden}.conversation-list header{display:flex;align-items:center;height:62px;padding:0 16px;border-bottom:1px solid #eaedf0}.conversation-list h3{margin:0;font-size:13px}.conversation-list header span{margin-left:auto;color:#c3663d;font-size:8px}.conversation-list>button{display:grid;grid-template-columns:36px minmax(0,1fr) auto;align-items:center;width:100%;min-height:72px;padding:10px 13px;border:0;border-bottom:1px solid #edf0f2;background:transparent;text-align:left}.conversation-list>button.active{background:linear-gradient(90deg,#e5f7f5,#eeecfb)}.conversation-list>button>i{display:grid;place-items:center;width:32px;height:32px;border-radius:11px;background:#e5e8fc;color:#6465cb;font-size:10px;font-style:normal}.conversation-list>button span{min-width:0}.conversation-list b,.conversation-list small{display:block}.conversation-list b{font-size:10px}.conversation-list small{overflow:hidden;margin-top:5px;color:#858fa2;font-size:8px;text-overflow:ellipsis;white-space:nowrap}.conversation-list em{color:#9ba4b2;font-size:7px;font-style:normal}.conversation-window{display:grid;grid-template-rows:64px 1fr auto;overflow:hidden}.conversation-window>header{display:flex;align-items:center;padding:0 18px;border-bottom:1px solid #e9edef}.conversation-window>header>div{display:flex;align-items:center;gap:10px}.conversation-window>header i{display:grid;place-items:center;width:34px;height:34px;border-radius:11px;background:#e6f7f5;color:#29a69c;font-size:10px;font-style:normal}.conversation-window>header b,.conversation-window>header small{display:block}.conversation-window>header b{font-size:11px}.conversation-window>header small{margin-top:3px;color:#8f99a8;font-size:8px}.message-stream{overflow:auto;padding:20px;background:#f7f8f7a6}.message{max-width:72%;margin-bottom:14px}.message>span,.message time{color:#8f99a8;font-size:7px}.message p{margin:5px 0;padding:11px 13px;border-radius:5px 14px 14px 14px;background:#fff;font-size:9px;line-height:1.65;box-shadow:0 7px 18px #4c58700b}.message.agent{margin-left:auto}.message.agent p{border-radius:14px 5px 14px 14px 14px;background:linear-gradient(110deg,#e1f7f4,#ebe8fb)}.refund-context{margin:12px 0;padding:14px;border:1px solid #e0e5e7;border-radius:14px;background:#fff}.refund-context header{display:flex}.refund-context header b{font-size:10px}.refund-context header span{margin-left:auto}.refund-context dl,.refund-modal dl{display:grid;grid-template-columns:repeat(3,1fr);gap:13px;margin:14px 0 0}.refund-context dt,.refund-modal dt{color:#929baa;font-size:7px}.refund-context dd,.refund-modal dd{margin:4px 0 0;font-size:9px;font-weight:800}.conversation-window>footer{padding:13px 16px;border-top:1px solid #e8ebed;background:#fff}.conversation-window>footer p{margin:0 0 10px;padding:10px;border-radius:10px;background:#f2f5f7;color:#667286;font-size:8px}.conversation-window>footer div{display:flex;justify-content:flex-end;gap:8px}.conversation-window>footer button,.refund-modal footer button{padding:8px 12px;border:1px solid #e0e4e8;border-radius:9px;background:#fff;color:#677286;font-size:8px}.conversation-window>footer button.primary,.refund-modal footer button.primary{border:0;background:#273047;color:#fff}.filter-pills{display:flex;gap:4px;margin-left:auto}.filter-pills button{padding:6px 9px;border:0;border-radius:999px;background:transparent;color:#8791a2;font-size:8px}.filter-pills button.active{background:#e9e9fb;color:#5b60c7}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse;white-space:nowrap}th{height:40px;padding:0 13px;background:#f5f6f7;color:#7b8699;font-size:8px;text-align:left}td{height:62px;padding:0 13px;border-top:1px solid #eceff1;color:#596579;font-size:9px}td>b,td>small{display:block}td>small{margin-top:4px;color:#929baa;font-size:7px}.risk{display:inline-block;padding:5px 8px;border-radius:999px;font-size:8px}.risk.low{background:#e7f8f1;color:#298867}.risk.medium{background:#fff4df;color:#b77924}.risk.high{background:#ffeaec;color:#bc4e5c}.permission-note{margin:0;padding:12px 16px;border-top:1px solid #e8ebed;background:#f7f8fb;color:#737e91;font-size:8px;line-height:1.6}.service-layout{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:15px;margin-top:16px}.service-chat{padding:22px}.service-chat>header{display:flex;align-items:center;gap:11px}.agent-orb{display:grid;place-items:center;width:38px;height:38px;border-radius:13px;background:linear-gradient(145deg,#43c9bf,#706fe2);color:#fff;font-weight:900}.service-chat h3,.service-chat p{margin:0}.service-chat header h3{font-size:12px}.service-chat header p{margin-top:4px;color:#2ba680;font-size:8px}.service-intro{padding:28px 0 18px}.service-intro span{color:#5e67d0;font-size:9px;font-weight:800}.service-intro h3{margin-top:8px;font:600 24px Georgia,"Microsoft YaHei UI",serif}.service-intro p{margin-top:6px;color:#808a9c;font-size:9px}.quick-service-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}.quick-service-grid button{display:flex;align-items:center;min-height:70px;padding:12px;border:1px solid #e8ebed;border-radius:14px;background:#fafbfa;text-align:left}.quick-service-grid button i{display:grid;place-items:center;width:34px;height:34px;margin-right:10px;border-radius:11px;background:#e7f7f4;color:#28a79e;font-style:normal}.quick-service-grid b,.quick-service-grid small{display:block}.quick-service-grid b{font-size:10px}.quick-service-grid small{margin-top:4px;color:#8b95a7;font-size:8px}.quick-service-grid em{margin-left:auto;color:#6b70d7;font-style:normal}.service-chat form{display:flex;margin-top:17px;padding:5px;border:1px solid #e1e5e9;border-radius:14px;background:#fff}.service-chat form input{flex:1;min-width:0;padding:0 10px;border:0;outline:0;font-size:9px}.service-chat form button{padding:10px 16px;border:0;border-radius:10px;background:#283147;color:#fff;font-size:9px}.service-side{display:grid;align-content:start;gap:15px}.service-side>article{padding:18px}.service-side>article>span{color:#818b9d;font-size:9px}.service-side>article>strong{display:block;margin:8px 0;font-size:30px}.service-side>article>p{margin:0;color:#818b9d;font-size:8px}.timeline-mini h3{margin:0 0 13px;font-size:11px}.timeline-mini>div{display:flex;gap:9px;padding:10px 0;border-top:1px solid #edf0f1}.timeline-mini i{width:7px;height:7px;margin-top:3px;border-radius:50%;background:#41c2b6}.timeline-mini b,.timeline-mini small{display:block}.timeline-mini b{font-size:9px}.timeline-mini small{margin-top:4px;color:#929baa;font-size:7px}.modal-backdrop{position:fixed;inset:0;z-index:30;display:grid;place-items:center;background:#20283a55;backdrop-filter:blur(5px)}.refund-modal{width:min(620px,90vw);overflow:hidden;border:1px solid #fff;border-radius:20px;background:#f9faf9;box-shadow:0 30px 90px #29324855}.refund-modal>header{display:flex;align-items:center;padding:19px 22px;border-bottom:1px solid #e6eaec;background:linear-gradient(100deg,#e3f7f4,#ece9fa)}.refund-modal>header span{color:#6770d0;font-size:8px}.refund-modal h2{margin:5px 0 0;font-size:18px}.refund-modal>header button{margin-left:auto;width:32px;height:32px;border:0;border-radius:50%;background:#fff}.refund-modal-body{padding:20px 22px}.refund-modal-body article{margin-top:18px;padding:13px;border-radius:12px;background:#fff1e9}.refund-modal-body article span{color:#c5683b;font-size:8px;font-weight:800}.refund-modal-body article p{margin:6px 0 0;color:#75685f;font-size:9px;line-height:1.6}.refund-modal-body>label{display:block;margin-top:15px;color:#687386;font-size:9px}.refund-modal-body input{display:block;width:100%;height:38px;margin-top:6px;padding:0 10px;border:1px solid #dfe4e8;border-radius:10px}.refund-modal footer{display:flex;justify-content:flex-end;gap:8px;padding:14px 22px;border-top:1px solid #e5e9eb}.center-toast{position:fixed;right:28px;bottom:24px;z-index:40;max-width:440px;margin:0;padding:12px 16px;border-radius:12px;background:#273047;color:#fff;font-size:9px;box-shadow:0 15px 36px #27304747}.modal-fade-enter-active,.modal-fade-leave-active,.toast-rise-enter-active,.toast-rise-leave-active{transition:.2s ease}.modal-fade-enter-from,.modal-fade-leave-to{opacity:0}.toast-rise-enter-from,.toast-rise-leave-to{opacity:0;transform:translateY(10px)}@keyframes center-spin{to{transform:rotate(360deg)}}@media(max-width:1100px){.center-shell{grid-template-columns:210px minmax(0,1fr)}.center-content{padding:20px}.metric-grid{grid-template-columns:repeat(2,1fr)}.content-grid,.service-layout{grid-template-columns:1fr}.side-stack,.service-side{grid-template-columns:repeat(2,1fr)}.topbar-actions label{width:190px}.conversation-layout{grid-template-columns:230px minmax(0,1fr)}}@media(max-width:760px){.center-shell{display:block}.center-sidebar{position:static;width:auto;height:auto}.center-nav{display:grid;grid-template-columns:repeat(2,1fr)}.sidebar-footer{display:none}.center-topbar{padding:0 16px}.topbar-actions label,.topbar-user{display:none}.center-content{padding:15px}.welcome-strip{padding:18px}.welcome-strip:after{display:none}.welcome-strip>button{display:none}.metric-grid{grid-template-columns:1fr 1fr}.conversation-layout{display:block;height:auto}.conversation-list{display:none}.conversation-window{min-height:560px}.quick-service-grid{grid-template-columns:1fr}.refund-context dl,.refund-modal dl{grid-template-columns:repeat(2,1fr)}}
.module-search{display:flex;align-items:center;min-height:66px;margin-top:15px;padding:10px 16px}
.module-search>div{display:flex;align-items:center;flex:1;max-width:620px;height:40px;padding:0 13px;border:1px solid #e0e5e8;border-radius:12px;background:#f9fbfa}
.module-search>div>span{color:#7d8799}
.module-search input{flex:1;min-width:0;margin-left:9px;border:0;background:transparent;outline:0;color:#30394d;font-size:10px}
.module-search>div>button{width:26px;height:26px;border:0;border-radius:50%;background:#e9edf0;color:#7f899a;cursor:pointer}
.module-search>p{margin:0 0 0 auto;color:#7c8799;font-size:8px;text-align:right}
.module-search>p b{color:#5866d1;font-size:11px}
.module-search>p small{display:block;margin-top:4px;color:#a0a8b5}
.reserved-sync-button{display:grid;grid-template-columns:auto auto;align-items:center;column-gap:7px;margin-left:auto;padding:8px 12px;border:1px dashed #8995bc;border-radius:11px;background:#f2f3ff;color:#4d5875;text-align:left;cursor:pointer;transition:.18s ease}
.reserved-sync-button:hover{border-style:solid;background:#e9eafe;transform:translateY(-1px)}
.reserved-sync-button:disabled{cursor:wait;opacity:.65;transform:none}
.reserved-sync-button>span{grid-row:1/3;color:#6873db;font-size:17px}.reserved-sync-button b{font-size:9px}.reserved-sync-button small{color:#9aa3b2;font-size:7px}
.reserved-sync-button+ p{margin-left:18px}
.search-empty{margin:0;padding:28px 16px;color:#929baa;font-size:9px;text-align:center}
.quick-empty{grid-column:1/-1;border:1px dashed #dfe4e7;border-radius:12px}
.table-empty{height:100px;color:#929baa;text-align:center}
.refund-modal-body textarea{display:block;width:100%;min-height:70px;margin-top:6px;padding:9px 10px;border:1px solid #dfe4e8;border-radius:10px;font:inherit;resize:vertical}

/* 个人中心可读性基线：避免导航、表格和说明文字落入 7–10px 的不可读区间。 */
.role-center :where(p,small,em,dt,dd,th,td,label,input,textarea,button){font-size:12px!important}
.center-sidebar .center-nav button b{font-size:14px}
.center-sidebar .center-nav button small,.identity-card small,.center-brand small{font-size:12px!important}
.center-topbar>div>span,.welcome-strip span{font-size:13px}
.center-topbar h1{font-size:26px}
.welcome-strip h2{font-size:31px}
.welcome-strip p{font-size:14px!important}
.center-metric-card span{font-size:13px}.center-metric-card strong{font-size:30px}.center-metric-card small{font-size:12px!important}
.panel-heading h3,.progress-panel h3,.timeline-mini h3{font-size:16px}
.record-list p b,.conversation-list b,.conversation-window>header b,.quick-service-grid b{font-size:13px}
.record-list p small,.conversation-list small,.conversation-window>header small,.quick-service-grid small{font-size:12px!important}
.status,.risk{font-size:12px}
.module-search input{font-size:13px}
.reserved-sync-button b{font-size:13px}.reserved-sync-button small{font-size:11px!important}
.refund-modal footer button:disabled{cursor:wait;opacity:.55}
.message.agent p{border-radius:14px 5px 14px 14px}
.visually-hidden{position:absolute!important;width:1px!important;height:1px!important;padding:0!important;margin:-1px!important;overflow:hidden!important;clip:rect(0,0,0,0)!important;white-space:nowrap!important;border:0!important}
.conversation-window>footer textarea{display:block;width:100%;min-height:62px;max-height:130px;margin:0 0 9px;padding:10px 12px;border:1px solid #dfe4e8;border-radius:11px;background:#fff;color:#293247;font:inherit;line-height:1.55;resize:vertical;box-sizing:border-box}
.conversation-list-tools{display:flex;align-items:center;gap:7px;margin-left:auto}.conversation-list-tools button{padding:4px 7px;border:1px solid #e1e5ea;border-radius:7px;background:#fff;color:#69748a;font-size:8px;cursor:pointer}.conversation-list-tools button:disabled{cursor:wait;opacity:.55}.conversation-list-tools span{margin-left:0!important}.conversation-swipe-row{position:relative;overflow:hidden;border-bottom:1px solid #edf0f2;background:#e85f68}.conversation-delete-action{position:absolute;inset:0 0 0 auto;width:72px;border:0;background:#e85f68;color:#fff;font-size:11px;font-weight:700;cursor:pointer}.conversation-card{position:relative;z-index:1;display:grid;grid-template-columns:36px minmax(0,1fr) auto;align-items:center;width:100%;min-height:72px;padding:10px 13px;border:0;background:#fff;text-align:left;transition:transform .2s ease,background .2s ease;touch-action:pan-y;cursor:pointer}.conversation-card.active{background:linear-gradient(90deg,#e5f7f5,#eeecfb)}.conversation-card>i{display:grid;place-items:center;width:32px;height:32px;border-radius:11px;background:#e5e8fc;color:#6465cb;font-size:10px;font-style:normal}.conversation-card>span{min-width:0}.conversation-card em{color:#9ba4b2;font-size:7px;font-style:normal}.conversation-swipe-row.swiped .conversation-card{transform:translateX(-72px)}
.composer-attachments,.message-attachments{display:flex;flex-wrap:wrap;gap:7px;margin:0 0 9px;padding:0;list-style:none}
.composer-attachments li,.message-attachments li{display:flex;align-items:center;gap:6px;padding:5px 8px;border-radius:8px;background:#f0f1ff;color:#626b83;font-size:11px}
.composer-attachments button{width:18px;height:18px;padding:0!important;border:0!important;border-radius:50%!important;background:#fff!important;color:#6d75a0!important}
.message-attachments{margin-top:-7px}.message-attachments small{color:#8a93a5}

/* 放大字号后的统一防重叠规则。 */
.module-search{gap:14px;flex-wrap:nowrap}
.content-grid{align-items:start}.side-stack{position:sticky;top:92px;align-self:start;align-content:start;grid-auto-rows:max-content}
.module-search>div{min-width:280px}
.module-search>p{flex:0 1 210px;min-width:170px;overflow-wrap:anywhere}
.reserved-sync-button{flex:0 0 auto}
.panel-heading>div{min-width:0}.panel-heading>button{flex:0 0 auto}
.record-list>div{grid-template-columns:38px minmax(220px,1fr) minmax(96px,130px) minmax(112px,max-content) 54px;gap:12px}
.record-list p{min-width:0}
.record-list p b,.record-list p small{overflow-wrap:anywhere}
.record-list em{min-width:0;text-align:right;overflow-wrap:anywhere}
.record-list .status{max-width:100%;justify-self:start;white-space:nowrap}
.record-list>div>button{justify-self:end;white-space:nowrap}
.filter-pills{flex-wrap:wrap;justify-content:flex-end}
.topbar-actions,.topbar-actions label{min-width:0}
.record-list>div{grid-template-columns:38px minmax(220px,1fr) minmax(96px,130px) minmax(112px,max-content) minmax(164px,max-content)}
.record-actions{display:flex;align-items:center;justify-self:end;gap:8px;white-space:nowrap}
.record-actions button:first-child:not(:last-child){padding:6px 10px;border:1px solid #dfe3f5;border-radius:8px;background:#f2f3ff}
.record-actions .danger-action{padding:6px 10px;border:1px solid #f1cbd0;border-radius:8px;background:#fff4f5;color:#bd4f5b}
.record-actions .knowledge-action{padding:6px 10px;border:1px solid #d7e6e2;border-radius:8px;background:#eff9f6;color:#258977}
.knowledge-modal{width:min(860px,94vw);max-height:88vh;overflow:auto}.knowledge-modal-body{display:grid;gap:16px}.knowledge-explain{margin:0;padding:11px 13px;border-radius:11px;background:#eef7f5;color:#62756f;line-height:1.6}.knowledge-upload{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:11px;padding:14px;border:1px solid #e2e8e7;border-radius:14px;background:#fff}.knowledge-upload label{display:grid;gap:6px;color:#687386;font-size:11px}.knowledge-upload input,.knowledge-upload select{width:100%;height:38px;padding:0 10px;border:1px solid #dfe4e8;border-radius:9px;background:#fff;box-sizing:border-box}.knowledge-upload .knowledge-file-field{grid-column:1/-1}.knowledge-upload button{grid-column:1/-1;height:40px;border:0;border-radius:10px;background:#283147;color:#fff;cursor:pointer}.knowledge-upload button:disabled{cursor:wait;opacity:.55}.knowledge-empty{padding:26px;border:1px dashed #d9e1df;border-radius:13px;color:#7d8997;text-align:center}.knowledge-list{display:grid;gap:10px;margin:0;padding:0;list-style:none}.knowledge-list>li{padding:14px;border:1px solid #e2e6e8;border-radius:13px;background:#fff}.knowledge-document-head{display:flex;align-items:center;gap:10px}.knowledge-document-head>span{font-size:20px}.knowledge-document-head p{min-width:0;margin:0}.knowledge-document-head b,.knowledge-document-head small{display:block}.knowledge-document-head small{margin-top:4px;color:#8792a2}.knowledge-index-status{margin-left:auto;padding:5px 8px;border-radius:999px;background:#edf2f3;color:#75808e;font-size:10px;font-style:normal}.knowledge-index-status.indexed{background:#e4f6ef;color:#278766}.knowledge-index-status.failed{background:#fff0f1;color:#b94f5b}.knowledge-preview{margin:12px 0 0;padding:10px;border-radius:9px;background:#f7f8f8;color:#657184;line-height:1.65;white-space:pre-line}.knowledge-document-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:10px}.knowledge-document-actions button{padding:6px 10px;border:1px solid #dfe4e8;border-radius:8px;background:#fff;color:#5867c9;cursor:pointer}.knowledge-document-actions .danger-action{border-color:#f1cbd0;color:#bd4f5b}
.support-user-modal select{display:block;width:100%;height:38px;margin-top:6px;padding:0 10px;border:1px solid #dfe4e8;border-radius:10px;background:#fff}
.support-user-modal input:disabled{background:#f0f2f4;color:#8c95a5;cursor:not-allowed}
.support-user-modal .form-help{margin:14px 0 0;color:#7d8799;line-height:1.6}
.ticket-stat-panel{margin-top:15px}
.ticket-stat-table th,.ticket-stat-table td{font-size:12px!important}
.ticket-stat-table td{height:70px}
.ticket-stat-table td:nth-child(6){max-width:260px;white-space:normal;overflow-wrap:anywhere}
.analytics-integrity-note{margin:0;padding:13px 16px;border-top:1px solid #e8ecee;background:#f7f9f9;color:#6f7c8f;line-height:1.65}
.ticket-filter-bar{display:grid;grid-template-columns:repeat(2,minmax(135px,1fr)) repeat(3,minmax(120px,.8fr)) auto auto;align-items:end;gap:10px;padding:14px 16px;border-bottom:1px solid #e8ecee;background:#fbfcfc}
.ticket-filter-bar label{display:grid;gap:6px;color:#68758a;font-size:11px}
.ticket-filter-bar input,.ticket-filter-bar select{height:38px;padding:0 10px;border:1px solid #dfe4e8;border-radius:9px;background:#fff;color:#273047}
.ticket-filter-bar button{height:38px;padding:0 15px;border:0;border-radius:9px;background:#273047;color:#fff;cursor:pointer}
.ticket-filter-bar button.secondary{border:1px solid #dfe4e8;background:#fff;color:#68758a}
.ticket-filter-bar button:disabled{cursor:not-allowed;opacity:.55}
.record-detail-modal:has(.logistics-detail){width:min(760px,94vw)}
.logistics-detail{display:grid;gap:16px}
.logistics-route-card{display:grid;grid-template-columns:minmax(0,1fr) 150px minmax(0,1fr);align-items:center;gap:14px;padding:16px;border:1px solid #dfece9;border-radius:15px;background:linear-gradient(110deg,#edf9f6,#f5f2ff)}
.logistics-route-card>div:last-child{text-align:right}.logistics-route-card span,.logistics-route-card b{display:block}.logistics-route-card span{color:#7d899b;font-size:11px}.logistics-route-card b{margin-top:5px;color:#243049;font-size:14px}.logistics-route-card>i{display:flex;align-items:center;color:#5e70d6;font-style:normal}.logistics-route-card>i em{height:1px;flex:1;background:#9abbd0}.logistics-route-card>i em:last-child:after{float:right;margin-top:-4px;border-width:4px 0 4px 6px;border-style:solid;border-color:transparent transparent transparent #7897c4;content:""}.logistics-route-card>i strong{margin:0 8px;font-size:10px;white-space:nowrap}
.logistics-detail dl{grid-template-columns:repeat(3,minmax(0,1fr));margin:0}.logistics-detail dd{overflow-wrap:anywhere}
.logistics-timeline{padding:16px;border:1px solid #e5e9eb;border-radius:15px;background:#fff}.logistics-timeline h3{margin:0 0 12px;font-size:14px}.logistics-timeline ol{margin:0;padding:0;list-style:none}.logistics-timeline li{position:relative;display:flex;gap:12px;padding:0 0 16px}.logistics-timeline li:last-child{padding-bottom:0}.logistics-timeline li:before{position:absolute;left:5px;top:10px;bottom:-2px;width:1px;background:#dfe5e8;content:""}.logistics-timeline li:last-child:before{display:none}.logistics-timeline li>i{z-index:1;width:11px;height:11px;margin-top:3px;border:3px solid #fff;border-radius:50%;background:#aab4c1;box-shadow:0 0 0 1px #cdd5dc}.logistics-timeline li.latest>i{background:#35bda8;box-shadow:0 0 0 2px #caf0e8}.logistics-timeline li div{min-width:0}.logistics-timeline li b{font-size:12px}.logistics-timeline li p{margin:5px 0;color:#667286;font-size:11px;line-height:1.55}.logistics-timeline li time{color:#98a1ae;font-size:10px}

@media(max-width:1250px){
  .module-search{flex-wrap:wrap}
  .module-search>p{margin-left:0}
  .ticket-filter-bar{grid-template-columns:repeat(3,minmax(130px,1fr)) auto auto}
}
@media(max-width:1100px){
  .side-stack{position:static}
}
@media(max-width:900px){
  .topbar-actions label{display:none}
  .record-list>div{grid-template-columns:38px minmax(180px,1fr) minmax(90px,120px) minmax(105px,max-content) minmax(98px,max-content)}
}
@media(max-width:760px){
  .ticket-filter-bar{grid-template-columns:1fr 1fr}
  .record-list>div{grid-template-columns:38px minmax(0,1fr) auto;gap:7px 10px;min-height:104px;padding:12px 14px}
  .record-list>div>.record-icon{grid-column:1;grid-row:1/4}
  .record-list>div>p{grid-column:2;grid-row:1}
  .record-list>div>em{grid-column:2;grid-row:2;text-align:left}
  .record-list>div>.status{grid-column:2;grid-row:3}
  .record-list>div>.record-actions{grid-column:3;grid-row:1/4;align-self:center;flex-direction:column}
  .panel-heading{align-items:flex-start;flex-wrap:wrap;padding:14px 16px}
  .panel-heading>button,.filter-pills{margin-top:10px}
}
@media(max-width:760px){.module-search{align-items:stretch;flex-direction:column;gap:8px}.module-search>p{margin-left:0;text-align:left}.module-search>p small{display:none}}
</style>
