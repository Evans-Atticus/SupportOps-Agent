package com.example.supportops.module.trace.integration;

import com.example.supportops.common.exception.BusinessException;
import com.example.supportops.common.exception.ErrorCode;
import com.example.supportops.module.trace.model.TraceModels;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * 本地演示数据源。集合是线程安全的，写入仅用于入库单演示；生产环境可用真实平台 Gateway 替换。
 */
@Primary
@Component
public class DemoTraceDataGateway implements TraceDataGateway {
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 7, 22, 10, 30);
    private final TraceCodeGenerator traceCodeGenerator;
    private final AtomicInteger inboundSequence = new AtomicInteger(1);
    private final CopyOnWriteArrayList<TraceModels.Inventory> inventory = new CopyOnWriteArrayList<>();

    public DemoTraceDataGateway(TraceCodeGenerator traceCodeGenerator) {
        this.traceCodeGenerator = traceCodeGenerator;
        inventory.add(new TraceModels.Inventory("INV-A018-01", "SKU-A018", "LOT-20260705-A18",
                "华东一号仓", "A-08-16", 486, 10, "正常", "库存",
                "TR1-8K4M2X7P9Q-A6", BASE_TIME.minusHours(1)));
        inventory.add(new TraceModels.Inventory("OUT-20260712-018", "SKU-B026", "LOT-20260712-D03",
                "华东一号仓", "D-02-11", 1, 0, "出库超时", "出库",
                "TR1-6Q9D4H2W7R-31", BASE_TIME.minusDays(1)));
    }

    @Override
    public TraceModels.Overview overview() {
        return new TraceModels.Overview(12_680, 8_426, 11, 98.7, List.of(
                new TraceModels.SourceHealth("ERP 采购与销售", "ONLINE", 218),
                new TraceModels.SourceHealth("MES 生产平台", "ONLINE", 126),
                new TraceModels.SourceHealth("QMS 质检平台", "ONLINE", 164),
                new TraceModels.SourceHealth("WMS 仓储平台", "ONLINE", 187),
                new TraceModels.SourceHealth("TMS 物流平台", "ONLINE", 302),
                new TraceModels.SourceHealth("售后工单平台", "ONLINE", 96)));
    }

    @Override
    public List<TraceModels.Product> products(String keyword) {
        return filter(List.of(
                new TraceModels.Product("SKU-A018", "智能数据终端", "DT-8 / 128G", "智能设备",
                        "序列号 + 批次", "单品 / 箱 / 托盘", 1_268, "启用"),
                new TraceModels.Product("SKU-B026", "工业传感器", "TS-200", "智能设备",
                        "批次", "单品 / 箱", 846, "启用")), keyword, Object::toString);
    }

    @Override
    public List<TraceModels.Purchase> purchases(String purchaseNo, String status) {
        return filterStatus(filter(List.of(
                new TraceModels.Purchase("PO-20260701-036", "华东元器件有限公司", "核心控制板",
                        "RM-0701-C36", "已收货", "合格", "LOT-20260705-A18"),
                new TraceModels.Purchase("PO-20260718-102", "新锐电子", "温度传感器",
                        "RM-0718-T02", "部分收货", "待检", null)), purchaseNo,
                TraceModels.Purchase::purchaseNo), status, TraceModels.Purchase::receiptStatus);
    }

    @Override
    public List<TraceModels.ProductionBatch> batches(String batchNo, String status) {
        return filterStatus(filter(List.of(
                new TraceModels.ProductionBatch("LOT-20260705-A18", "SKU-A018", "上海一厂", "A02",
                        500, 496, "已完工", LocalDateTime.of(2026, 7, 5, 18, 2),
                        List.of("投料 RM-0701-C36", "组装完成", "程序烧录 FW 5.2.1", "老化测试 8 小时", "终检通过", "成品入库")),
                new TraceModels.ProductionBatch("LOT-20260718-B06", "SKU-B026", "苏州二厂", "B06",
                        500, 470, "质量复核", null,
                        List.of("投料 RM-0718-T02", "组装完成", "过程检验不合格", "暂停入库"))),
                batchNo, TraceModels.ProductionBatch::batchNo), status, TraceModels.ProductionBatch::status);
    }

    @Override
    public List<TraceModels.QualityInspection> inspections(String inspectionNo, String result) {
        return filterStatus(filter(List.of(
                new TraceModels.QualityInspection("QC-0705-118", "成品检验", "LOT-20260705-A18",
                        50, 0, "合格", "李岚", LocalDateTime.of(2026, 7, 5, 17, 36)),
                new TraceModels.QualityInspection("QC-0718-206", "过程检验", "LOT-20260718-B06",
                        30, 4, "不合格", "郑工", LocalDateTime.of(2026, 7, 22, 10, 26))),
                inspectionNo, TraceModels.QualityInspection::inspectionNo), result, TraceModels.QualityInspection::result);
    }

    @Override
    public List<TraceModels.Inventory> inventory(String referenceNo, String status) {
        return filterStatus(filter(List.copyOf(inventory), referenceNo, TraceModels.Inventory::referenceNo),
                status, TraceModels.Inventory::status);
    }

    @Override
    public TraceModels.Inventory createInbound(TraceModels.InboundOrderCreate request) {
        String inboundNo = hasText(request.inboundNo()) ? request.inboundNo().trim()
                : "IN-20260722-" + String.format("%03d", inboundSequence.getAndIncrement());
        if (inventory.stream().anyMatch(item -> item.referenceNo().equalsIgnoreCase(inboundNo))) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "入库单号已存在: " + inboundNo);
        }
        TraceModels.Inventory created = new TraceModels.Inventory(inboundNo, request.productCode(), request.batchNo(),
                request.warehouse(), request.location(), request.quantity(), 0, "已入库", request.inboundType(),
                traceCodeGenerator.next(), LocalDateTime.now());
        inventory.add(0, created);
        return created;
    }

    @Override
    public List<TraceModels.Logistics> logistics(String trackingNo) {
        return filter(List.of(new TraceModels.Logistics("SF202607060005", "O202607060005", "SKU-A018",
                "LOT-20260705-A18", "SN-A018-00462", "顺丰速运", "已签收", "上海市浦东新区",
                LocalDateTime.of(2026, 7, 6, 10, 20))), trackingNo, TraceModels.Logistics::trackingNo);
    }

    @Override
    public List<TraceModels.Sale> sales(String orderNo) {
        return filter(List.of(
                new TraceModels.Sale("O202607060005", "直营商城", "林蕾", "SKU-A018",
                        "LOT-20260705-A18", "SN-A018-00462", "上海", 1, "已签收"),
                new TraceModels.Sale("SO-20260720-086", "华南经销商", "粤海商贸", "SKU-B026",
                        "LOT-20260718-B06", null, "广东", 50, "暂停发货")), orderNo, TraceModels.Sale::orderNo);
    }

    @Override
    public List<TraceModels.AfterSaleTicket> tickets(String ticketNo, String status) {
        return filterStatus(filter(List.of(
                new TraceModels.AfterSaleTicket("TK-0706-005", "签收状态未同步", "O202607060005",
                        "SKU-A018", "LOT-20260705-A18", "高", "处理中", "刘扬"),
                new TraceModels.AfterSaleTicket("TK-QC-206", "同批产品故障集中出现", "LOT-20260718-B06",
                        "SKU-B026", "LOT-20260718-B06", "紧急", "待诊断", "Patrick")),
                ticketNo, TraceModels.AfterSaleTicket::ticketNo), status, TraceModels.AfterSaleTicket::status);
    }

    @Override
    public List<TraceModels.Recall> recalls(String batchNo, String riskLevel) {
        return filterStatus(filter(List.of(new TraceModels.Recall("RC-20260722-001", "LOT-20260718-B06",
                "SKU-B026", 500, 196, "高风险", "等待审批", List.of(
                new TraceModels.RecallScope("华东一号仓", "库存批次", 96, "已冻结"),
                new TraceModels.RecallScope("华南二号仓", "库存批次", 50, "已冻结"),
                new TraceModels.RecallScope("粤海商贸", "经销商订单", 50, "待召回")))),
                batchNo, TraceModels.Recall::batchNo), riskLevel, TraceModels.Recall::riskLevel);
    }

    @Override
    public TraceModels.TraceDetail trace(String code) {
        if (hasText(code) && List.of("LOT-20260718-B06", "SKU-B026", "TK-QC-206")
                .contains(code.trim().toUpperCase(Locale.ROOT))) {
            return new TraceModels.TraceDetail("TR1-RISK-B06-42", "SKU-B026", "LOT-20260718-B06",
                    null, "风险冻结", List.of(
                    event("EV-R1", "生产", "B06 产线生产完成", "MES", "LOT-20260718-B06", "苏州二厂", 18, "已完工"),
                    event("EV-R2", "质检", "过程抽检发现 4 件不合格", "QMS", "QC-0718-206", "苏州二厂", 19, "不合格"),
                    event("EV-R3", "仓储", "同批库存已冻结", "WMS", "RC-20260722-001", "华东/华南仓", 20, "已冻结"),
                    event("EV-R4", "销售", "经销商订单暂停发货", "ERP", "SO-20260720-086", "广东", 21, "暂停发货"),
                    event("EV-R5", "召回", "影响范围等待审批", "TRACE", "RC-20260722-001", "全国", 22, "待召回")));
        }
        TraceModels.Inventory stored = inventory.stream().filter(item -> matchesInventory(item, code)).findFirst().orElse(null);
        if (stored != null && !matchesKnownTrace(code)) {
            return new TraceModels.TraceDetail(stored.traceCode(), stored.productCode(), stored.batchNo(), null,
                    stored.status(), List.of(new TraceModels.TraceEvent("EV-IN-" + stored.referenceNo(), "仓储",
                    "产品完成入库", "WMS", stored.referenceNo(), stored.warehouse() + " / " + stored.location(),
                    stored.updatedAt(), stored.status())));
        }
        if (!matchesKnownTrace(code)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "未找到溯源对象: " + code);
        }
        return new TraceModels.TraceDetail("TR1-8K4M2X7P9Q-A6", "SKU-A018", "LOT-20260705-A18",
                "SN-A018-00462", "客户已签收", List.of(
                event("EV-01", "采购", "核心控制板采购收货", "ERP", "PO-20260701-036", "上海一厂", 1, "合格"),
                event("EV-02", "生产", "A02 产线生产完工", "MES", "LOT-20260705-A18", "上海一厂", 2, "已完工"),
                event("EV-03", "质检", "成品检验通过", "QMS", "QC-0705-118", "上海一厂", 3, "合格"),
                event("EV-04", "仓储", "成品入库并完成拣货", "WMS", "INV-A018-01", "华东一号仓", 4, "已出库"),
                event("EV-05", "物流", "承运商完成签收", "TMS", "SF202607060005", "上海浦东", 5, "已签收"),
                event("EV-06", "销售", "直营商城订单完成", "ERP", "O202607060005", "上海", 6, "已履约")));
    }

    private TraceModels.TraceEvent event(String id, String stage, String title, String source,
                                         String recordNo, String location, int day, String status) {
        return new TraceModels.TraceEvent(id, stage, title, source, recordNo, location,
                LocalDateTime.of(2026, 7, day, 10, 0), status);
    }

    private boolean matchesKnownTrace(String value) {
        if (!hasText(value)) return false;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return List.of("TR1-8K4M2X7P9Q-A6", "SN-A018-00462", "LOT-20260705-A18", "SKU-A018",
                "O202607060005", "SF202607060005").contains(normalized);
    }

    private boolean matchesInventory(TraceModels.Inventory item, String code) {
        if (!hasText(code)) return false;
        String expected = code.trim().toLowerCase(Locale.ROOT);
        return List.of(item.traceCode(), item.referenceNo(), item.batchNo(), item.productCode()).stream()
                .filter(this::hasText).map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(expected::equals);
    }

    private <T> List<T> filter(List<T> source, String keyword, Function<T, String> field) {
        if (!hasText(keyword)) return source;
        String expected = keyword.trim().toLowerCase(Locale.ROOT);
        return source.stream().filter(item -> value(field.apply(item)).contains(expected)).toList();
    }

    private <T> List<T> filterStatus(List<T> source, String status, Function<T, String> field) {
        if (!hasText(status)) return source;
        String expected = status.trim().toLowerCase(Locale.ROOT);
        return source.stream().filter(item -> value(field.apply(item)).equals(expected)).toList();
    }

    private String value(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
