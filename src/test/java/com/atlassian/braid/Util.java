package com.atlassian.braid;

import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.language.Document;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Map;


class Util {

    static TypeDefinitionRegistry parseRegistry(String schemaPath) {
        try {
            SchemaParser schemaParser = new SchemaParser();
            String docString = read(schemaPath);
            return schemaParser.parse(docString);
        } catch (ParseCancellationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String read(String schemaPath) throws IOException {
        try {
            return read(new InputStreamReader(Util.class.getResourceAsStream(schemaPath)));
        } catch (IOException ex) {
            throw new RuntimeException("Can't find " + schemaPath, ex);
        }
    }

    private static String read(Reader reader) throws IOException {
        char[] buffer = new char[1024 * 4];
        StringWriter sw = new StringWriter();
        int n;
        while (-1 != (n = reader.read(buffer))) {
            sw.write(buffer, 0, n);
        }
        return sw.toString();
    }
}
