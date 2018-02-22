package com.atlassian.braid.mapper2;

public class Mappers {
    static NewMapper from(MapperOperation operation) {
        return new MapperImpl(operation);
    }
}
