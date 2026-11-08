package com.udyam.digital.order;

/** One product in an order. */
public final class OrderLine {
    public final String productId;
    public final String name;
    public final int quantity;
    public final int monthlyInr;
    public final int setupInr;
    /** typical_install_days from product_catalogue.csv. */
    public final int installDays;

    public OrderLine(String productId, String name, int quantity, int monthlyInr, int setupInr, int installDays) {
        this.productId = productId;
        this.name = name;
        this.quantity = quantity;
        this.monthlyInr = monthlyInr;
        this.setupInr = setupInr;
        this.installDays = installDays;
    }

    /** Needs a technician at the site (lines, routers, cameras, sensors). SIMs and cloud products don't. */
    public boolean needsTechnician() { return OrderTimeline.needsTechnician(productId); }
}
