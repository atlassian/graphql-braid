package com.atlassian.braid;

import graphql.language.Document;
import graphql.parser.Parser;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;

/**
 */
public class Util {

    public static Document parseDocument(String schemaPath) {
        try {
            Parser parser = new Parser();
            String docString = read(new InputStreamReader(Util.class.getResourceAsStream(schemaPath)));
            return parser.parseDocument(docString);
        } catch (ParseCancellationException | IOException e) {
            throw new RuntimeException(e);
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
