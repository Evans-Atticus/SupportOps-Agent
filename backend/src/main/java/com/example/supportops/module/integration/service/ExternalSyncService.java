package com.example.supportops.module.integration.service;

import com.example.supportops.module.integration.model.ExternalSyncModels.SyncReservationVO;

public interface ExternalSyncService {
    SyncReservationVO syncErpOrdersAndTickets(String requestedBy);

    SyncReservationVO syncWmsLogistics(String requestedBy);
}
