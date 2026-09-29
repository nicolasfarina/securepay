package com.securepay.payment;

public enum PaymentStatus {
    PENDING,
    APPROVED,
    REJECTED,
    UNKNOWN,
    REFUNDED;

    public boolean canTransitionTo(PaymentStatus target) {
        return switch (this) {
            case PENDING -> target == APPROVED || target == REJECTED || target == UNKNOWN;
            case UNKNOWN -> target == APPROVED || target == REJECTED;
            case APPROVED -> target == REFUNDED;
            case REJECTED, REFUNDED -> false;
        };
    }
}
