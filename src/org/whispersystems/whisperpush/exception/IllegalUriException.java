package org.whispersystems.whisperpush.exception;

public class IllegalUriException extends Exception {

    public IllegalUriException() {
    }

    public IllegalUriException(String detailMessage) {
        super(detailMessage);
    }

    public IllegalUriException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public IllegalUriException(Throwable throwable) {
        super(throwable);
    }

}
