package com.atlassian.braid.mapper;

/**
 * An operation to run on the mapper
 */
public interface FieldOperation {
    void execute(Mapper mapper);
}
