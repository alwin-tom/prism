package com.prism.imagegen;

public class ImageProviderException extends RuntimeException {

    public enum Kind {
        AUTH, BUDGET, TIMEOUT, PROVIDER, INVALID, SKIPPED
    }

    private final Kind kind;

    public ImageProviderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public ImageProviderException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
