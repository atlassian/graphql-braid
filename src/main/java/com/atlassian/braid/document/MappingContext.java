package com.atlassian.braid.document;

import graphql.language.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;

public class MappingContext {

    private final List<String> path;
    private final Field field;

    public MappingContext(List<String> path, Field field) {
        this.path = path;
        this.field = field;
    }

    public Field getField() {
        return field;
    }

    public static MappingContext of(Field field, String... path) {
        return new MappingContext(asList(path), field);
    }

    public static MappingContext from(MappingContext mappingContext, Field field) {
        final ArrayList<String> paths = new ArrayList<>();
        paths.addAll(mappingContext.path);
//        paths.add() TODO do something intelligent here
        return new MappingContext(paths, field);
    }

    public String getSpringPath(String targetKey) {
        return Stream.concat(path.stream(), Stream.of(targetKey))
                .map(p -> "['" + p + "']")
                .collect(joining());
    }
}
