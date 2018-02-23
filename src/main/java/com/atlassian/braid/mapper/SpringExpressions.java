package com.atlassian.braid.mapper;

import com.atlassian.braid.collections.BraidObjects;
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
        return PARSER.parseExpression(sourcePath).getValue(source);
    }
}
