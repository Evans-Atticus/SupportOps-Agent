package com.example.supportops.module.portal.service;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.portal.dao.PortalDAO;
import com.example.supportops.module.portal.model.dto.RefundCreateDTO;
import com.example.supportops.module.portal.model.dto.RefundDecisionDTO;
import com.example.supportops.module.portal.model.dto.SupportUserCreateDTO;
import com.example.supportops.module.portal.model.dto.SupportUserUpdateDTO;
import com.example.supportops.module.portal.model.dto.ConversationReplyDTO;
import com.example.supportops.module.portal.model.dto.ConversationRefundCreateDTO;
import com.example.supportops.module.portal.model.dto.HandoffRequestDTO;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.ConversationAttachmentRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.ConversationContextRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.ConversationMessageRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.CustomerConversationRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.DashboardCountsRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.ItemRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.LogisticsTimelineRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.RefundRecord;
import com.example.supportops.module.portal.model.query.PortalQueryRecords.UserContextRecord;
import com.example.supportops.module.portal.model.vo.PortalModels.DashboardVO;
import com.example.supportops.module.portal.model.vo.PortalModels.AdviceVO;
import com.example.supportops.module.portal.model.vo.PortalModels.ExportVO;
import com.example.supportops.module.portal.model.vo.PortalModels.ItemVO;
import com.example.supportops.module.portal.model.vo.PortalModels.MetricVO;
import com.example.supportops.module.portal.model.vo.PortalModels.OffboardResultVO;
import com.example.supportops.module.portal.model.vo.PortalModels.RefundVO;
import com.example.supportops.module.portal.model.vo.PortalModels.SearchResultVO;
import com.example.supportops.module.portal.model.vo.PortalModels.ConversationAttachmentVO;
import com.example.supportops.module.portal.model.vo.PortalModels.ConversationContextVO;
import com.example.supportops.module.portal.model.vo.PortalModels.ConversationMessageVO;
import com.example.supportops.module.portal.model.vo.PortalModels.CustomerConversationVO;
import com.example.supportops.module.portal.model.vo.PortalModels.HandoffResultVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PortalService {
    private static final Set<String> CUSTOMER_MODULES = Set.of(
            "orders", "logistics", "tickets", "refunds", "messages", "profile", "service");
    private static final Set<String> AGENT_MODULES = Set.of(
            "workspace", "conversations", "tickets", "orders", "logistics", "refunds", "diagnoses");
    private static final Set<String> ADMIN_MODULES = Set.of(
            "overview", "people", "ticket-stats", "orders", "logistics", "refunds",
            "agent-management", "integrations", "audit");

    private final PortalDAO dao;
    private final PasswordEncoder passwordEncoder;
    public PortalService(PortalDAO dao, PasswordEncoder passwordEncoder) {
        this.dao = dao;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public DashboardVO dashboard(String username) {
        UserContextRecord context = requiredContext(username);
        DashboardCountsRecord counts;
        List<MetricVO> metrics;
        if ("ADMIN".equals(context.roleCode())) {
            counts = dao.adminDashboard();
            metrics = List.of(
                    metric("people", "客服人员", counts.first(), "当前启用账号", "normal"),
                    metric("tickets", "待处理工单", counts.second(), "需要服务团队处理", "warning"),
                    metric("refunds", "退款待审批", counts.third(), "审批操作需要权限", "warning"),
                    metric("logistics", "物流异常", counts.fourth(), "需要核验履约节点", "danger"));
        } else if ("SUPPORT_AGENT".equals(context.roleCode())) {
            counts = dao.agentDashboard(context.userId());
            metrics = List.of(
                    metric("conversations", "待回复会话", counts.first(), "当前客服接待", "warning"),
                    metric("tickets", "待处理工单", counts.second(), "团队业务范围", "normal"),
                    metric("refunds", "退款跟进", counts.third(), "可查看审批与到账", "normal"),
                    metric("diagnoses", "诊断任务", counts.fourth(), "本人发起", "normal"));
        } else {
            if (context.customerId() == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "消费者账号尚未绑定客户档案");
            }
            counts = dao.customerDashboard(context.customerId(), context.userId());
            metrics = List.of(
                    metric("orders", "我的订单", counts.first(), "当前账号本人数据", "normal"),
                    metric("tickets", "进行中售后", counts.second(), "等待处理或补充", "warning"),
                    metric("refunds", "退款处理中", counts.third(), "可查看审批与到账", "normal"),
                    metric("messages", "未读消息", counts.fourth(), "服务进度通知", "warning"));
        }
        return new DashboardVO(context.roleCode(), metrics);
    }

    @Transactional(readOnly = true)
    public SearchResultVO search(String username, String module, String keyword) {
        UserContextRecord context = requiredContext(username);
        String safeModule = module == null ? "" : module.trim().toLowerCase();
        String safeKeyword = keyword == null ? "" : keyword.trim();
        if (safeKeyword.length() > 100) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "搜索关键词不能超过100个字符");
        }
        assertModule(context.roleCode(), safeModule);
        Long customerScope = "CUSTOMER".equals(context.roleCode()) ? context.customerId() : null;
        List<ItemRecord> records = switch (safeModule) {
            case "people" -> dao.selectPeople(safeKeyword);
            case "tickets", "workspace" -> dao.selectTickets(safeKeyword, customerScope);
            case "orders", "service" -> dao.selectOrders(safeKeyword, customerScope);
            case "profile" -> dao.selectCustomerProfile(safeKeyword, requiredCustomerScope(context));
            case "logistics" -> dao.selectLogistics(safeKeyword, customerScope);
            case "conversations" -> dao.selectConversations(safeKeyword, context.userId());
            case "diagnoses" -> dao.selectDiagnoses(safeKeyword, context.userId(), false);
            case "messages" -> dao.selectMessages(safeKeyword, context.userId());
            case "audit" -> dao.selectAudit(safeKeyword);
            case "ticket-stats" -> dao.selectTicketStatistics(safeKeyword);
            case "overview" -> dao.selectOverview(safeKeyword);
            case "agent-management" -> dao.selectAgentSettings(safeKeyword);
            case "integrations" -> integrationItems(safeKeyword);
            case "refunds" -> List.of();
            default -> throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "不支持的模块");
        };
        List<ItemVO> items = records.stream()
                .map(record -> "orders".equals(safeModule) ? orderItem(record) : item(record)).toList();
        return new SearchResultVO(safeModule, safeKeyword, items.size(), items);
    }

    @Transactional(readOnly = true)
    public List<RefundVO> refunds(String username, String keyword) {
        UserContextRecord context = requiredContext(username);
        Long customerScope = "CUSTOMER".equals(context.roleCode()) ? context.customerId() : null;
        return dao.selectRefunds(keyword == null ? "" : keyword.trim(), customerScope)
                .stream().map(this::refund).toList();
    }

    @Transactional(readOnly = true)
    public ExportVO exportModule(String username, String module, String keyword) {
        SearchResultVO result = search(username, module, keyword);
        StringBuilder csv = new StringBuilder("\uFEFF编号,标题,详情,补充信息,状态,更新时间\r\n");
        result.items().forEach(item -> csv.append(csv(item.id())).append(',')
                .append(csv(item.title())).append(',')
                .append(csv(item.detail())).append(',')
                .append(csv(item.meta())).append(',')
                .append(csv(item.status())).append(',')
                .append(csv(item.occurredAt() == null ? "" : item.occurredAt().toString()))
                .append("\r\n"));
        String fileName = result.module() + "-"
                + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".csv";
        return new ExportVO(fileName, "text/csv;charset=UTF-8", csv.toString());
    }

    @Transactional(readOnly = true)
    public ItemVO moduleItem(String username, String module, String id) {
        UserContextRecord context = requiredContext(username);
        String safeModule = module == null ? "" : module.trim().toLowerCase();
        assertModule(context.roleCode(), safeModule);
        if ("logistics".equals(safeModule)) {
            Long customerScope = "CUSTOMER".equals(context.roleCode()) ? context.customerId() : null;
            return logisticsItem(id, dao.selectLogisticsTimeline(id, customerScope));
        }
        return search(username, module, id).items().stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "记录不存在或无权查看"));
    }

    private ItemVO logisticsItem(String trackingNo, List<LogisticsTimelineRecord> records) {
        if (records.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "运单不存在或无权查看");
        }
        List<LogisticsTimelineRecord> carrierEvents = records.stream()
                .filter(record -> "CARRIER".equals(record.sourceType())).toList();
        List<LogisticsTimelineRecord> timelineSource = carrierEvents.isEmpty() ? records : carrierEvents;
        LogisticsTimelineRecord latest = timelineSource.get(0);
        List<Map<String, Object>> timeline = timelineSource.stream().map(record -> {
            Map<String, Object> event = new java.util.LinkedHashMap<>();
            event.put("id", record.id());
            event.put("status", record.logisticsStatus());
            event.put("description", record.statusDescription());
            event.put("location", record.currentLocation());
            event.put("facility", record.facilityName());
            event.put("eventTime", record.eventTime());
            return event;
        }).toList();
        Map<String, Object> extra = new java.util.LinkedHashMap<>();
        extra.put("orderNo", latest.orderNo());
        extra.put("product", latest.product());
        extra.put("carrierName", latest.carrierName());
        extra.put("originLocation", latest.originLocation());
        extra.put("destinationLocation", latest.destinationLocation());
        extra.put("currentLocation", latest.currentLocation());
        extra.put("facilityName", latest.facilityName());
        extra.put("courierName", latest.courierNameMasked());
        extra.put("courierPhone", latest.courierPhoneMasked());
        extra.put("estimatedDeliveryAt", latest.estimatedDeliveryAt());
        extra.put("timeline", timeline);
        boolean platformDelayed = records.stream().filter(record -> "LOCAL".equals(record.sourceType()))
                .findFirst().map(local -> local.eventTime().isBefore(latest.eventTime())).orElse(false);
        extra.put("platformSyncDelayed", platformDelayed);
        String detail = String.join(" · ", java.util.stream.Stream.of(
                latest.product(), latest.currentLocation(), latest.statusDescription())
                .filter(value -> value != null && !value.isBlank()).toList());
        return new ItemVO(trackingNo, trackingNo + " · 订单 " + latest.orderNo(), detail,
                latest.carrierName(), latest.logisticsStatus(), latest.eventTime(), extra);
    }

    @Transactional(readOnly = true)
    public AdviceVO advice(String username, String module) {
        SearchResultVO result = search(username, module, "");
        return switch (module) {
            case "refunds" -> new AdviceVO("优先处理高风险退款",
                    "当前可见退款记录需要结合订单、历史售后和支付渠道状态进行复核。",
                    "筛选待审批或处理中退款");
            case "logistics" -> new AdviceVO("先核验异常物流节点",
                    "建议优先检查状态异常、长时间未同步以及即将超过承诺时效的运单。",
                    "筛选异常运单");
            case "tickets", "workspace" -> new AdviceVO("按优先级与 SLA 处理",
                    "当前共有 " + result.total() + " 条可见记录，优先处理高优先级和即将超时工单。",
                    "查看待处理工单");
            default -> new AdviceVO("智能体数据建议",
                    "当前模块共有 " + result.total() + " 条可见记录，可通过编号、客户和状态进一步定位。",
                    "刷新当前模块");
        };
    }

    @Transactional
    public ItemVO replyConversation(String username, String conversationNo, ConversationReplyDTO request) {
        UserContextRecord context = requiredContext(username);
        if (!"SUPPORT_AGENT".equals(context.roleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有客服人员可以发送人工回复");
        }
        if (!dao.insertConversationReply(conversationNo, context.userId(), request.content().trim())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会话不存在或未分配给当前客服");
        }
        return moduleItem(username, "conversations", conversationNo);
    }

    @Transactional(readOnly = true)
    public ConversationContextVO conversation(String username, String conversationNo) {
        UserContextRecord context = requiredContext(username);
        if (!"SUPPORT_AGENT".equals(context.roleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有客服人员可以查看接待会话");
        }
        ConversationContextRecord stored = dao.findAgentConversation(conversationNo, context.userId());
        if (stored == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会话不存在或未分配给当前客服");
        }
        return conversation(stored);
    }

    @Transactional
    public ConversationMessageVO sendConversationMessage(String username, String conversationNo,
                                                         String content, List<MultipartFile> files) {
        UserContextRecord context = requiredContext(username);
        if (!"SUPPORT_AGENT".equals(context.roleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有客服人员可以发送人工回复");
        }
        String safeContent = content == null ? "" : content.trim();
        List<MultipartFile> safeFiles = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty()).toList();
        if (safeContent.isBlank() && safeFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "回复内容和附件不能同时为空");
        }
        if (safeContent.length() > 2000 || safeFiles.size() > 5) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "回复最多2000字且最多上传5个附件");
        }
        ConversationContextRecord stored = dao.findAgentConversation(conversationNo, context.userId());
        if (stored == null || dao.claimConversation(stored.conversationId(), context.userId()) != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会话不存在或已由其他客服接待");
        }
        String messageContent = safeContent.isBlank() ? "已发送附件" : safeContent;
        long messageId = dao.insertAgentMessage(stored.conversationId(), context.userId(), messageContent);
        List<ConversationAttachmentVO> attachments = new ArrayList<>();
        for (MultipartFile file : safeFiles) {
            validateAttachment(file);
            String fileName = safeFileName(file.getOriginalFilename());
            String contentType = file.getContentType() == null
                    ? "application/octet-stream" : file.getContentType();
            try {
                dao.insertConversationAttachment(messageId, fileName, contentType, file.getBytes());
            } catch (IOException exception) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "附件读取失败：" + fileName);
            }
            attachments.add(new ConversationAttachmentVO(0, fileName, contentType, file.getSize()));
        }
        return new ConversationMessageVO(messageId, "SUPPORT_AGENT", messageContent,
                LocalDateTime.now(), attachments);
    }

    @Transactional
    public RefundVO createConversationRefund(String username, String conversationNo,
                                             ConversationRefundCreateDTO request, String requestId) {
        UserContextRecord context = requiredContext(username);
        if (!"SUPPORT_AGENT".equals(context.roleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有接待客服可以从会话发起退款");
        }
        ConversationContextRecord conversation = dao.findAgentConversation(conversationNo, context.userId());
        if (conversation == null || conversation.orderNo() == null || conversation.ticketNo() == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "当前会话尚未关联有效工单和订单");
        }
        if (request.amount().compareTo(conversation.refundableAmount()) > 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "申请金额不能超过当前可退金额 " + conversation.refundableAmount());
        }
        String refundNo = newRefundNo();
        String channel = request.refundChannel() == null || request.refundChannel().isBlank()
                ? conversation.refundChannel() : request.refundChannel().trim();
        if (!dao.createRefund(refundNo, conversation.orderNo(), conversation.ticketNo(), context.userId(),
                request.amount(), request.reason().trim(), channel)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "关联订单或工单不存在");
        }
        dao.insertAudit(context.userId(), "REFUND_SUBMIT_FROM_CONVERSATION", refundNo,
                "会话 " + conversationNo + "，申请金额 " + request.amount(), requestId);
        return refund(dao.findRefund(refundNo));
    }

    @Transactional
    public HandoffResultVO requestHandoff(String username, HandoffRequestDTO request) {
        UserContextRecord context = requiredContext(username);
        if (!"CUSTOMER".equals(context.roleCode()) || context.customerId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有客户可以请求转人工客服");
        }
        String conversationNo = blankToNull(request.conversationNo());
        String content = blankToNull(request.content());
        String assignedAgent = null;
        long conversationId;
        Long diagnosisId = request.diagnosisId() == null ? null
                : dao.findCustomerDiagnosis(request.diagnosisId(), context.userId(), context.customerId());
        if (request.diagnosisId() != null && diagnosisId == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "关联的智能诊断不存在或不属于当前客户");
        }
        if (conversationNo == null) {
            conversationNo = "CONV-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    .format(LocalDateTime.now()) + "-" + UUID.randomUUID().toString()
                    .substring(0, 4).toUpperCase();
            conversationId = dao.createHandoffConversation(conversationNo, context.customerId(),
                    null, blankToNull(request.ticketNo()),
                    blankToNull(request.businessNo()), diagnosisId, content);
        } else {
            conversationId = dao.findConversationIdForCustomer(conversationNo, context.customerId());
            assignedAgent = dao.findAssignedAgentName(conversationNo);
            if (conversationId != 0) {
                dao.reopenCustomerConversation(conversationId, context.customerId());
            }
            if (conversationId != 0 && diagnosisId != null) {
                dao.updateCustomerConversationDiagnosis(conversationId, context.customerId(), diagnosisId);
            }
        }
        if (conversationId == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "无法创建人工客服会话");
        }
        if (content != null) {
            dao.insertCustomerMessage(conversationId, context.userId(), content);
        }
        return new HandoffResultVO(conversationNo,
                assignedAgent == null ? "WAITING_AGENT" : "AGENT_SERVING", assignedAgent);
    }

    @Transactional(readOnly = true)
    public CustomerConversationVO customerConversation(String username, String conversationNo) {
        UserContextRecord context = requiredContext(username);
        if (!"CUSTOMER".equals(context.roleCode()) || context.customerId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有客户可以查看自己的人工会话");
        }
        CustomerConversationRecord stored = dao.findCustomerConversation(conversationNo, context.customerId());
        if (stored == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "人工会话不存在");
        }
        return new CustomerConversationVO(stored.conversationNo(), stored.serviceMode(),
                stored.assignedAgent(), conversationMessages(stored.conversationId()));
    }

    @Transactional
    public CustomerConversationVO recallCustomerMessage(String username, String conversationNo, long messageId) {
        UserContextRecord context = requiredContext(username);
        if (!"CUSTOMER".equals(context.roleCode()) || context.customerId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有客户可以撤回自己的消息");
        }
        if (!dao.recallCustomerMessage(conversationNo, context.customerId(), context.userId(), messageId)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "消息不存在、已超过 1 分钟，或无权撤回该消息");
        }
        return customerConversation(username, conversationNo);
    }

    @Transactional
    public CustomerConversationVO sendCustomerConversationMessage(
            String username, String conversationNo, String content,
            String ticketNo, String businessNo, List<MultipartFile> files) {
        UserContextRecord context = requiredContext(username);
        if (!"CUSTOMER".equals(context.roleCode()) || context.customerId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有客户可以发送人工会话消息");
        }
        CustomerConversationRecord conversation = dao.findCustomerConversation(
                conversationNo, context.customerId());
        if (conversation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "人工会话不存在");
        }
        String safeContent = content == null ? "" : content.trim();
        List<MultipartFile> safeFiles = files == null ? List.of() : files.stream()
                .filter(file -> file != null && !file.isEmpty()).toList();
        if (safeContent.isBlank() && safeFiles.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "消息和附件不能同时为空");
        }
        if (safeContent.length() > 2000 || safeFiles.size() > 5) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "消息最多 2000 字且最多上传 5 个附件");
        }
        dao.updateCustomerConversationBusiness(conversation.conversationId(), context.customerId(),
                blankToNull(ticketNo), blankToNull(businessNo));
        String messageContent = safeContent.isBlank() ? "已发送附件" : safeContent;
        long messageId = dao.insertCustomerMessage(
                conversation.conversationId(), context.userId(), messageContent);
        for (MultipartFile file : safeFiles) {
            validateAttachment(file);
            String fileName = safeFileName(file.getOriginalFilename());
            String contentType = file.getContentType() == null
                    ? "application/octet-stream" : file.getContentType();
            try {
                dao.insertConversationAttachment(messageId, fileName, contentType, file.getBytes());
            } catch (IOException exception) {
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "附件读取失败：" + fileName);
            }
        }
        return customerConversation(username, conversationNo);
    }

    @Transactional
    public int archiveConversation(String username, String conversationNo) {
        UserContextRecord context = requiredContext(username);
        if (!"SUPPORT_AGENT".equals(context.roleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有客服可以清理会话队列");
        }
        if (dao.archiveConversation(conversationNo, context.userId()) != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "会话不存在、已清理或不在当前客服队列");
        }
        return 1;
    }

    @Transactional
    public int archiveCompletedConversations(String username) {
        UserContextRecord context = requiredContext(username);
        if (!"SUPPORT_AGENT".equals(context.roleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有客服可以清理会话队列");
        }
        return dao.archiveCompletedConversations(context.userId());
    }

    private long requiredCustomerScope(UserContextRecord context) {
        if (context.customerId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "消费者账号尚未绑定客户档案");
        }
        return context.customerId();
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    @Transactional
    public RefundVO createRefund(String username, RefundCreateDTO request, String requestId) {
        UserContextRecord context = requiredContext(username);
        if ("ADMIN".equals(context.roleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "管理员不能代替客户发起退款");
        }
        if ("CUSTOMER".equals(context.roleCode())
                && (context.customerId() == null
                || !dao.orderBelongsToCustomer(request.orderNo(), context.customerId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能为本人的订单申请退款");
        }
        String refundNo = "RF-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        if (!dao.createRefund(refundNo, request.orderNo(), request.ticketNo(), context.userId(),
                request.amount(), request.reason(), request.refundChannel())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单或工单不存在");
        }
        dao.insertAudit(context.userId(), "REFUND_SUBMIT", refundNo,
                "申请金额 " + request.amount(), requestId);
        return refund(dao.findRefund(refundNo));
    }

    @Transactional
    public RefundVO approve(String username, String refundNo, RefundDecisionDTO request, String requestId) {
        UserContextRecord context = requireAdmin(username);
        RefundRecord stored = requiredRefund(refundNo);
        BigDecimal amount = request.approvedAmount() == null ? stored.requestedAmount() : request.approvedAmount();
        if (amount.signum() <= 0 || amount.compareTo(stored.requestedAmount()) > 0
                || amount.compareTo(stored.orderAmount()) > 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "批准金额必须大于0且不能超过申请金额和订单可退金额");
        }
        if (dao.approveRefund(stored.id(), amount, context.userId()) != 1) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "当前退款状态不能批准");
        }
        dao.insertAudit(context.userId(), "REFUND_APPROVE", refundNo,
                "批准金额 " + amount + note(request.reason()), requestId);
        dao.insertNotificationForRefund(stored.id(), "退款审批已通过",
                "退款 " + refundNo + " 已批准，批准金额 " + amount + "。");
        return refund(dao.findRefund(refundNo));
    }

    @Transactional
    public RefundVO reject(String username, String refundNo, RefundDecisionDTO request, String requestId) {
        UserContextRecord context = requireAdmin(username);
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "拒绝退款必须填写原因");
        }
        RefundRecord stored = requiredRefund(refundNo);
        if (dao.rejectRefund(stored.id(), request.reason().trim(), context.userId()) != 1) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "当前退款状态不能拒绝");
        }
        dao.insertAudit(context.userId(), "REFUND_REJECT", refundNo, request.reason().trim(), requestId);
        dao.insertNotificationForRefund(stored.id(), "退款申请未通过", request.reason().trim());
        return refund(dao.findRefund(refundNo));
    }

    @Transactional
    public RefundVO execute(String username, String refundNo, String requestId) {
        UserContextRecord context = requireAdmin(username);
        RefundRecord stored = requiredRefund(refundNo);
        if (dao.executeRefund(stored.id()) != 1) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION, "只有已批准退款可以执行");
        }
        dao.insertAudit(context.userId(), "REFUND_EXECUTE", refundNo,
                "退款已提交支付渠道", requestId);
        dao.insertNotificationForRefund(stored.id(), "退款正在处理中",
                "退款 " + refundNo + " 已提交支付渠道，预计3个工作日内到账。");
        return refund(dao.findRefund(refundNo));
    }

    @Transactional
    public ItemVO createSupportUser(String username, SupportUserCreateDTO request) {
        requireAdmin(username);
        try {
            dao.insertSupportUser(request.username(), passwordEncoder.encode(request.password()),
                    request.displayName());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        return new ItemVO(request.username(), request.displayName(), "新建客服账号",
                "SUPPORT_AGENT", "ACTIVE", LocalDateTime.now());
    }

    @Transactional
    public ItemVO updateSupportUser(String username, long userId, SupportUserUpdateDTO request, String requestId) {
        UserContextRecord administrator = requireAdmin(username);
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "客服账号编号无效");
        }
        String updatedUsername = request.username().trim();
        if (dao.supportUsernameExistsForOther(updatedUsername, userId)) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS, "登录账号已被其他用户使用");
        }
        try {
            if (!dao.updateSupportUser(userId, updatedUsername, request.displayName().trim(),
                    request.status(), request.dailyQuota())) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "客服账号不存在");
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS, "登录账号已被其他用户使用");
        }
        dao.insertAudit(administrator.userId(), "SUPPORT_USER_UPDATE", String.valueOf(userId),
                "登录账号=" + updatedUsername
                        + "，姓名=" + request.displayName().trim()
                        + "，状态=" + request.status()
                        + "，每日额度=" + request.dailyQuota(),
                requestId);
        return moduleItem(username, "people", String.valueOf(userId));
    }

    @Transactional
    public void deleteSupportUser(String username, long userId, String requestId) {
        UserContextRecord administrator = requireAdmin(username);
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "客服账号编号无效");
        }
        if (dao.supportUserReferenceCount(userId) > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_IN_USE,
                    "该客服账号已有会话、工单或审批记录，请改为禁用账号");
        }
        try {
            if (!dao.deleteSupportUser(userId)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "客服账号不存在");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.RESOURCE_IN_USE,
                    "该客服账号已被业务数据引用，请改为禁用账号");
        }
        dao.insertAudit(administrator.userId(), "SUPPORT_USER_DELETE", String.valueOf(userId),
                "管理员删除无业务关联的客服账号", requestId);
    }

    @Transactional
    public OffboardResultVO offboardSupportUser(String username, long userId, String requestId) {
        UserContextRecord administrator = requireAdmin(username);
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "客服账号编号无效");
        }
        int reassignedConversations = dao.releaseActiveConversations(userId);
        if (!dao.disableSupportUser(userId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "客服账号不存在");
        }
        dao.insertAudit(administrator.userId(), "SUPPORT_USER_OFFBOARD", String.valueOf(userId),
                "账号已禁用，回收待处理会话=" + reassignedConversations, requestId);
        return new OffboardResultVO(userId, "DISABLED", reassignedConversations);
    }

    private UserContextRecord requiredContext(String username) {
        UserContextRecord context = dao.findContext(username);
        if (context == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return context;
    }

    private ConversationContextVO conversation(ConversationContextRecord stored) {
        List<ConversationMessageVO> messages = conversationMessages(stored.conversationId());
        return new ConversationContextVO(stored.conversationNo(), stored.customerName(),
                stored.ticketNo(), stored.orderNo(), stored.orderAmount(), stored.refundableAmount(),
                stored.refundChannel(), stored.serviceMode(), suggestedReply(stored), messages);
    }

    private List<ConversationMessageVO> conversationMessages(long conversationId) {
        Map<Long, List<ConversationAttachmentVO>> attachmentsByMessage = new HashMap<>();
        for (ConversationAttachmentRecord attachment : dao.selectConversationAttachments(conversationId)) {
            attachmentsByMessage.computeIfAbsent(attachment.messageId(), ignored -> new ArrayList<>())
                    .add(new ConversationAttachmentVO(attachment.id(), attachment.fileName(),
                            attachment.contentType(), attachment.sizeBytes()));
        }
        return dao.selectConversationMessages(conversationId)
                .stream().map(message -> new ConversationMessageVO(
                        message.id(), message.senderType(), message.content(), message.sentAt(),
                        attachmentsByMessage.getOrDefault(message.id(), List.of()))).toList();
    }

    private String suggestedReply(ConversationContextRecord conversation) {
        if (conversation.diagnosisId() == null) {
            return null;
        }
        if (conversation.diagnosisReply() != null && !conversation.diagnosisReply().isBlank()) {
            return conversation.diagnosisReply();
        }
        String scenario = conversation.scenarioHint() == null ? "" : conversation.scenarioHint();
        String refundStatus = conversation.refundStatus() == null ? "" : conversation.refundStatus();
        return switch (scenario) {
            case "ORDER_INFORMATION_QUERY" -> conversation.orderAmount() == null
                    ? "您好，已收到您的订单信息查询，我会根据您关联的订单继续核对。"
                    : "您好，已为您查询关联订单，当前订单金额为 ¥"
                    + conversation.orderAmount().stripTrailingZeros().toPlainString()
                    + "。如需查询订单的其他信息，请告诉我具体字段。";
            case "LOGISTICS_STATUS_NOT_SYNCED" ->
                    "您好，已收到您反馈的未收货问题。我们正在核验物流签收凭证和配送节点，请您同时确认是否由家人、前台或驿站代收，核实后会尽快向您同步处理结果。";
            case "ORDER_CANCELLED_BUT_CHARGED" -> switch (refundStatus) {
                case "EXECUTING", "APPROVED" ->
                        "您好，您的退款已通过审核，正在由支付渠道处理，将按原支付方式退回。我们会继续跟进到账状态并及时向您同步。";
                case "SUCCEEDED" ->
                        "您好，系统显示退款已处理成功并按原支付方式退回，请您留意对应支付账户的入账记录；如仍未查到，我们会继续协助核对。";
                case "FAILED" ->
                        "您好，核查到本次退款处理未成功，我们正在进一步确认失败原因并安排重新处理，进展会及时向您同步。";
                default ->
                        "您好，已为您核查取消订单的退款进度。当前退款申请仍在审核处理中，审核完成后将按原支付方式退回，我们会继续跟进并同步结果。";
            };
            case "PAYMENT_SUCCESS_ORDER_PENDING" -> switch (refundStatus) {
                case "EXECUTING", "APPROVED" ->
                        "您好，退款已审批通过，正在由支付渠道处理，预计 1—3 个工作日原路到账。";
                case "SUCCEEDED" ->
                        "您好，系统显示退款已处理成功，请您留意原支付账户的入账记录；如仍未到账，我们会继续协助核查。";
                default ->
                        "您好，我们已核查到支付成功但订单状态尚未同步，正在处理订单状态补偿，并会持续跟进相关退款进度。";
            };
            default -> "您好，我们已收到您的问题，正在结合关联工单和订单信息进一步核查，处理进展会及时向您同步。";
        };
    }

    private void validateAttachment(MultipartFile file) {
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "单个附件不能超过5MB：" + safeFileName(file.getOriginalFilename()));
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        boolean allowed = type.startsWith("image/")
                || type.equals("application/pdf")
                || type.startsWith("text/")
                || type.contains("word")
                || type.contains("excel")
                || type.contains("spreadsheet")
                || type.equals("application/octet-stream");
        if (!allowed) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "不支持的附件类型：" + safeFileName(file.getOriginalFilename()));
        }
    }

    private String safeFileName(String originalName) {
        String value = originalName == null || originalName.isBlank() ? "attachment" : originalName;
        return Path.of(value).getFileName().toString().replaceAll("[\\r\\n]", "_");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String newRefundNo() {
        return "RF-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private UserContextRecord requireAdmin(String username) {
        UserContextRecord context = requiredContext(username);
        if (!"ADMIN".equals(context.roleCode())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return context;
    }

    private RefundRecord requiredRefund(String refundNo) {
        RefundRecord stored = dao.findRefund(refundNo);
        if (stored == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "退款申请不存在");
        }
        return stored;
    }

    private void assertModule(String role, String module) {
        Set<String> allowed = switch (role) {
            case "ADMIN" -> ADMIN_MODULES;
            case "SUPPORT_AGENT" -> AGENT_MODULES;
            case "CUSTOMER" -> CUSTOMER_MODULES;
            default -> Set.of();
        };
        if (!allowed.contains(module)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色不能访问该模块");
        }
    }

    private List<ItemRecord> integrationItems(String keyword) {
        List<ItemRecord> all = List.of(
                new ItemRecord("ORDER", "订单系统", "订单与产品查询", "ERP", "ONLINE", null),
                new ItemRecord("PAYMENT", "支付系统", "支付与退款执行", "PAYMENT", "ONLINE", null),
                new ItemRecord("LOGISTICS", "物流系统", "承运商轨迹查询", "TMS", "ONLINE", null));
        if (keyword.isBlank()) {
            return all;
        }
        String lower = keyword.toLowerCase();
        return all.stream().filter(item -> (item.id() + item.title() + item.detail() + item.meta() + item.status())
                .toLowerCase().contains(lower)).toList();
    }

    private MetricVO metric(String code, String label, long value, String note, String tone) {
        return new MetricVO(code, label, Long.toString(value), note, tone);
    }

    private ItemVO item(ItemRecord record) {
        return new ItemVO(record.id(), record.title(), record.detail(), record.meta(),
                record.status(), record.occurredAt());
    }

    private ItemVO orderItem(ItemRecord record) {
        Map<String, Object> extra = new java.util.LinkedHashMap<>();
        extra.put("knowledgeDocumentCount", dao.countProductKnowledgeForOrder(record.id()));
        return new ItemVO(record.id(), record.title(), record.detail(), record.meta(),
                record.status(), record.occurredAt(), extra);
    }

    private RefundVO refund(RefundRecord record) {
        return new RefundVO(record.refundNo(), record.customerName(), record.orderNo(), record.product(),
                record.requestedAmount(), record.approvedAmount(), record.status(), record.riskLevel(),
                record.riskMessage(), record.refundChannel(), record.rejectReason(), record.requestedAt(),
                record.reviewedAt(), record.expectedArrivalAt(), record.completedAt());
    }

    private String note(String reason) {
        return reason == null || reason.isBlank() ? "" : "，说明：" + reason.trim();
    }
}
