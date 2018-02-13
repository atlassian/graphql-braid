package com.atlassian.braid.mapper;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;


class DefaultMapper implements Mapper {

    private static final Consumer<Object> NOOP = __ -> {
    };

    private final Mapper parent;
    private final Consumer<Object> doneAction;
    private final Map<String, Object> source;
    private final Map<String, Object> target;
    private final ExpressionEvaluator expressionEvaluator;

    DefaultMapper(ExpressionEvaluator expressionEvaluator, Map<String, Object> source) {
        this(expressionEvaluator, null, source, null);
    }

    private DefaultMapper(ExpressionEvaluator expressionEvaluator, Mapper parent, Map<String, Object> source, Consumer<Object> doneAction) {
        this.expressionEvaluator = expressionEvaluator;
        this.source = source;
        this.target = new HashMap<>();
        this.parent = parent;
        this.doneAction = doneAction;
    }

    @Override
    public Mapper copy(String key) {
        return copy(key, identity());
    }

    @Override
    public <T, R> Mapper copy(String key, Function<T, R> transform) {
        return copy(key, key, transform);
    }

    @Override
    public <T> Mapper copy(String key, Supplier<T> defaultValue) {
        return copy(key, key, defaultValue);
    }

    @Override
    public <T, R> Mapper copy(String key, Function<T, R> transform, Supplier<R> defaultValue) {
        return copy(key, key, transform, defaultValue);
    }

    @Override
    public Mapper copy(String sourcePath, String targetKey) {
        return copy(sourcePath, targetKey, identity());
    }

    @Override
    public <T> Mapper copy(String sourcePath, String targetKey, Supplier<T> defaultValue) {
        return copy(sourcePath, targetKey, identity(), defaultValue);
    }

    @Override
    public <T, R> Mapper copy(String sourcePath, String targetKey, Function<T, R> transform) {
        return copy(sourcePath, targetKey, transform, () -> null);
    }

    @Override
    public <T, R> Mapper copy(String sourcePath, String targetKey, Function<T, R> transform, Supplier<R> defaultValue) {
        final R value = this.<T>getValue(sourcePath).map(transform).orElseGet(defaultValue);
        return doPut(targetKey, value);
    }

    @Override
    public Mapper map(String key) {
        return new DefaultMapper(expressionEvaluator, this, source, obj -> doPut(key, obj));
    }

    @Override
    public Mapper singletonList(String key) {
        return new DefaultMapper(expressionEvaluator, this, source, obj -> doPut(key, Collections.singletonList(obj)));
    }

    @Override
    public Mapper put(String key, Object value) {
        return doPut(key, value);
    }

    @Override
    public Mapper copyMap(String key) {
        return copyMap(key, key);
    }

    @Override
    public Mapper copyMap(String sourcePath, String targetKey) {
        return this.<Map<String, Object>>getValue(sourcePath)
                .map(value -> new DefaultMapper(expressionEvaluator, this, value, obj -> doPut(targetKey, obj)))
                .orElseGet(() -> new DefaultMapper(expressionEvaluator, this, emptyMap(), NOOP));
    }

    @Override
    public Mapper copyList(String sourcePath, String targetKey) {
        return this.<List<Map<String, Object>>>getValue(sourcePath)
                .map(value -> newListMapper(this, value, obj -> doPut(targetKey, obj)))
                .orElseGet(() -> new DefaultMapper(expressionEvaluator, this, emptyMap(), NOOP));
    }

    private Mapper doPut(String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
        return this;
    }

    private <T> Optional<T> getValue(String sourcePath) {
        return expressionEvaluator.getValue(source, sourcePath);
    }

    @Override
    public Mapper copyList(String key) {
        return copyList(key, key);
    }

    @Override
    public Mapper done() {
        doneAction.accept(target);
        return parent;
    }

    @Override
    public Map<String, Object> build() {
        if (doneAction != null) {
            doneAction.accept(target);
        }
        return target;
    }

    private Mapper newListMapper(Mapper parent, List<Map<String, Object>> source, Consumer<Object> doneAction) {
        List<Mapper> mappers = source.stream()
                .map(s -> new DefaultMapper(expressionEvaluator, null, s, NOOP))
                .collect(toList());
        return newMapperToListOfMappers(parent, doneAction, mappers);
    }

    private Mapper newMapperToListOfMappers(Mapper parent, Consumer<Object> doneAction, List<Mapper> mappers) {
        return (Mapper) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{Mapper.class},
                ((proxy, method, args) -> {

                    // these methods don't return self, so we need to wrap them
                    if (asList("copyList", "map", "singletonList", "copyMap").contains(method.getName())) {
                        List<Mapper> children = new ArrayList<>();
                        for (Mapper m : mappers) {
                            children.add((Mapper) method.invoke(m, args));
                        }
                        return newMapperToListOfMappers((Mapper) proxy, blah -> {
                        }, children);

                    } else if ("done".equals(method.getName())) {
                        doneAction.accept(mappers.stream().map(Mapper::build).collect(toList()));
                        return parent;

                    } else if ("build".equals(method.getName())) {
                        doneAction.accept(mappers.stream().map(Mapper::build).collect(toList()));
                        return null;

                        // normal mapping methods that return self and can easily be applied to each list member
                    } else {
                        for (Mapper m : mappers) {
                            method.invoke(m, args);
                        }
                        return proxy;
                    }
                }));
    }
}