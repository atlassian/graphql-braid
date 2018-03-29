package com.atlassian.braid.source;

import com.atlassian.braid.BraidContext;
import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.document.DocumentMapper;
import com.atlassian.braid.document.DocumentMappers;

/**
 * Specific schema source that runs an actual GraphQL query
 *
 * @param <C> the GraphQL context
 */
public interface QueryExecutorSchemaSource<C extends BraidContext> extends SchemaSource<C> {
    default DocumentMapper getDocumentMapper() {
        return DocumentMappers.noop();
    }
}
