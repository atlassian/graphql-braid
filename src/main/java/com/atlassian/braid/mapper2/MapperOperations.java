package com.atlassian.braid.mapper2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

final class MapperOperations {

    private MapperOperations() {
    }

    static MapperOperation noop(){
        return new NoopOperation();
    }

    static MapperOperation composed(MapperOperation... operations) {
        return composed(asList(operations));
    }

    static MapperOperation composed(List<MapperOperation> operations) {
        return new ComposedOperation(operations);
    }

    private static class NoopOperation implements MapperOperation {
        @Override
        public void accept(Map<String, Object> input, Map<String, Object> output) {
        }
    }

    private static class ComposedOperation implements MapperOperation {
        private final List<MapperOperation> operations;

        private ComposedOperation(List<MapperOperation> operations) {
            this.operations = new ArrayList<>(operations);
        }

        @Override
        public void accept(Map<String, Object> input, Map<String, Object> output) {
            operations.forEach(op -> op.accept(input, output));
        }
    }
}
