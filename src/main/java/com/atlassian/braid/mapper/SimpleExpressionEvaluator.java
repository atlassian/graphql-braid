package com.atlassian.braid.mapper;

import java.util.Map;
import java.util.Optional;

/**
 * A simple expression evaluator that treats paths as map keys
 */
public class SimpleExpressionEvaluator implements ExpressionEvaluator {
    @Override
    public <T> Optional<T> getValue(Map<String, Object> source, String sourcePath) {
        return Optional.ofNullable((T) source.get(sourcePath));
    }
}
