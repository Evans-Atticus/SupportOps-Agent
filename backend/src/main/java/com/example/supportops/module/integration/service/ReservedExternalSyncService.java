package com.example.supportops.module.integration.service;

import com.example.supportops.module.integration.model.ExternalSyncModels.SyncReservationVO;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * ERP/WMS 同步的预留实现。
 *
 * <p>当前实现不会访问外部系统。后续接入时可新增正式实现并替换该 Bean，
 * Controller 与前端调用路径无需改变。</p>
 */
@Service
public class ReservedExternalSyncService implements ExternalSyncService {
    private static final String NOT_CONFIGURED = "NOT_CONFIGURED";

    @Override
    public SyncReservationVO syncErpOrdersAndTickets(String requestedBy) {
        return reservation(
                "ERP",
                "ORDERS_AND_TICKETS",
                "ERP 接口尚未配置，订单与工单信息未执行同步。",
                requestedBy,
                "配置 ERP 地址、认证凭据和订单/工单数据映射后启用正式同步实现。"
        );
    }

    @Override
    public SyncReservationVO syncWmsLogistics(String requestedBy) {
        return reservation(
                "WMS",
                "LOGISTICS",
                "WMS 接口尚未配置，物流信息未执行同步。",
                requestedBy,
                "配置 WMS 地址、认证凭据和运单/物流节点映射后启用正式同步实现。"
        );
    }

    private SyncReservationVO reservation(String integration, String resourceType, String message,
                                          String requestedBy, String nextStep) {
        return new SyncReservationVO(
                integration,
                resourceType,
                NOT_CONFIGURED,
                false,
                message,
                requestedBy,
                OffsetDateTime.now(),
                nextStep
        );
    }
}
