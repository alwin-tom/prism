package com.prism.creation;

public class OpenRouterException extends RuntimeException {

    public enum Kind {
        AUTH, RATE, PROVIDER, TIMEOUT, EMPTY, MALFORMED, CONTEXT, STRUCTURED, PAID, BUDGET, LENGTH
    }

    private final Kind kind;
    private final boolean reasoningOverBudget;
    private final String provider;
    private final boolean reasoningLocked;

    public OpenRouterException(Kind kind, String message) {
        this(kind, message, false, "");
    }

    public OpenRouterException(Kind kind, String message, boolean reasoningOverBudget, String provider) {
        this(kind, message, reasoningOverBudget, provider, false);
    }

    public OpenRouterException(Kind kind, String message, boolean reasoningOverBudget, String provider, boolean reasoningLocked) {
        super(message);
        this.kind = kind;
        this.reasoningOverBudget = reasoningOverBudget;
        this.provider = provider == null ? "" : provider;
        this.reasoningLocked = reasoningLocked;
    }

    public Kind kind() {
        return kind;
    }

    public boolean reasoningOverBudget() {
        return reasoningOverBudget;
    }

    public String provider() {
        return provider;
    }

    public boolean reasoningLocked() {
        return reasoningLocked;
    }
}
