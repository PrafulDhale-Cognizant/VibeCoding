package com.simplifiedbilling.inventory.domain;

public enum ProductUnit {
    PIECE("Piece", "pc", false),
    KILOGRAM("Kilogram", "kg", true),
    GRAM("Gram", "g", true),
    LITRE("Litre", "L", true),
    MILLILITRE("Millilitre", "ml", true),
    PACKET("Packet", "pkt", false),
    BOX("Box", "box", false),
    DOZEN("Dozen", "doz", false);

    private final String displayName;
    private final String symbol;
    private final boolean decimalAllowed;

    ProductUnit(String displayName, String symbol, boolean decimalAllowed) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.decimalAllowed = decimalAllowed;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isDecimalAllowed() {
        return decimalAllowed;
    }
}
