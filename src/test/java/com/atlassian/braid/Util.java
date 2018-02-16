package com.atlassian.braid;

import com.google.common.base.Charsets;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;


public class Util {

    public static TypeDefinitionRegistry parseRegistry(String schemaPath) {
        try {
            SchemaParser schemaParser = new SchemaParser();
            String docString = read(schemaPath);
            return schemaParser.parse(docString);
        } catch (ParseCancellationException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String read(String path) throws IOException {
        try (Reader reader = getResourceAsReader(path)) {
            return read(reader);
        } catch (IOException ex) {
            throw new RuntimeException("Issue reading resource at '" + path + "'", ex);
        }
    }

    public static Reader getResourceAsReader(String schemaPath) {
        try {
            return new InputStreamReader(getResourceAsStream(schemaPath), Charsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("It's UTF-8");
        }
    }

    private static InputStream getResourceAsStream(String schemaPath) {
        return Util.class.getResourceAsStream(schemaPath);
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
