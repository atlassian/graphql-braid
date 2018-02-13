package com.atlassian.braid.mapper;

import java.util.Map;
import java.util.Optional;

/**
 * Evaluates an expression against the source
 */
public interface ExpressionEvaluator {
    <T> Optional<T> getValue(Map<String, Object> source, String sourcePath);
}
