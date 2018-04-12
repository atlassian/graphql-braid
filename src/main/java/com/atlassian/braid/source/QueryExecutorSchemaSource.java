package com.atlassian.braid.source;

import com.atlassian.braid.SchemaSource;
import com.atlassian.braid.document.DocumentMapper;
import com.atlassian.braid.document.DocumentMappers;

/**
 * Specific schema source that runs an actual GraphQL query
 */
public interface QueryExecutorSchemaSource extends SchemaSource {
    default DocumentMapper getDocumentMapper() {
        return DocumentMappers.noop();
    }
}
