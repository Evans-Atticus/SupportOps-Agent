package com.example.supportops.module.ticket.model.enums;

public enum TicketStatus {
    OPEN,
    PROCESSING,
    RESOLVED,
    CLOSED;

    public boolean canTransitionTo(TicketStatus target) {
        if (this == target) {
            return true;
        }
        return switch (this) {
            case OPEN -> target == PROCESSING || target == CLOSED;
            case PROCESSING -> target == RESOLVED || target == CLOSED;
            case RESOLVED -> target == PROCESSING || target == CLOSED;
            case CLOSED -> false;
        };
    }
}
