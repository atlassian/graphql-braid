package com.atlassian.braid.mapper;

import com.atlassian.braid.java.util.BraidObjects;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.Map;
import java.util.Optional;

final class SpringExpressions {
    private static final org.springframework.expression.ExpressionParser PARSER = new SpelExpressionParser();

    // not static on purpose
    <T> Optional<T> get(Map<String, Object> map, String key) {
        final String sourcePath = !key.contains("[") ? "['" + key + "']" : key;
        return Optional.ofNullable(maybeGetValue(map, sourcePath)).map(BraidObjects::cast);
    }

    private static Object maybeGetValue(Object source, String sourcePath) {
        try {
            return PARSER.parseExpression(sourcePath).getValue(source);
        } catch (SpelEvaluationException e) {
            throw new MapperException(e, "Exception getting value in %s for path: %s", source, sourcePath);
        }
    }
}
