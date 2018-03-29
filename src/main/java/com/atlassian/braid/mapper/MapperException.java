package com.atlassian.braid.mapper;

import static java.lang.String.format;

/**
 * Exception when instantiating or running {@link Mapper mappers}
 */
@SuppressWarnings("WeakerAccess")
public final class MapperException extends RuntimeException {
    MapperException(Throwable cause) {
        super(cause);
    }

    MapperException(String message, Object... args) {
        super(format(message, args));
    }

    MapperException(Throwable cause, String message, Object... args) {
        super(format(message, args), cause);
    }
}
