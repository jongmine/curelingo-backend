package com.curelingo.curelingo.publicdata.mysql.egen;

public class EgenApiException extends RuntimeException {
    private final String resultCode;

    public EgenApiException(String resultCode) {
        super("E-Gen request failed; resultCode=" + resultCode);
        this.resultCode = resultCode;
    }

    public EgenApiException(String resultCode, Throwable cause) {
        super("E-Gen request failed; resultCode=" + resultCode, cause);
        this.resultCode = resultCode;
    }

    public String resultCode() {
        return resultCode;
    }
}
