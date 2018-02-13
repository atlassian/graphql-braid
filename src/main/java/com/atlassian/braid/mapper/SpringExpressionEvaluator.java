package com.atlassian.braid.mapper;

import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.Map;
import java.util.Optional;

/**
 * An expression evaluator that treats them as Spring expressions
 */
public class SpringExpressionEvaluator implements ExpressionEvaluator {

    private static final org.springframework.expression.ExpressionParser PARSER = new SpelExpressionParser();

    @Override
    public <T> Optional<T> getValue(Map<String, Object> source, String sourcePath) {
        if (!sourcePath.contains("[")) {
            sourcePath = "['" + sourcePath + "']";
        }
        return Optional.ofNullable(maybeGetValue(source, sourcePath)).map(SpringExpressionEvaluator::cast);
    }



    private Object maybeGetValue(Object source, String sourcePath) {
        return PARSER.parseExpression(sourcePath).getValue(source);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
