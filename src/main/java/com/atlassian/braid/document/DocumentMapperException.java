package com.atlassian.braid.document;

import static java.lang.String.format;

public final class DocumentMapperException extends RuntimeException {
    DocumentMapperException(String msg, Object... args) {
        super(format(msg, args));
    }
}
