package org.whispersystems.whisperpush.exception;

import android.net.Uri;

public class IllegalUriException extends Exception {

    public IllegalUriException(Uri uri) {
        this(uri, null);
    }

    public IllegalUriException(String detailMessage) {
        super(detailMessage);
    }

    public IllegalUriException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public IllegalUriException(Uri uri, Throwable throwable) {
        super(String.valueOf(uri), throwable);
    }

}
