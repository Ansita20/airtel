package com.udyam.digital.order;

/** The six steps of an order, in order. */
public enum OrderStep {
    PLAN_CHOSEN, KYC_COMPLETE, PAYMENT_DONE, INSTALL_BOOKED, TECHNICIAN_VISIT, ACTIVE;

    public enum Status { DONE, CURRENT, UPCOMING }
}
