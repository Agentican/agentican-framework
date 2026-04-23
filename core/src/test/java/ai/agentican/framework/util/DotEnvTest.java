package ai.agentican.framework.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DotEnvTest {

    @Test
    void parsesKeyValuePairs() {

        var map = parse("""
                FOO=bar
                COUNT=42
                """);

        assertEquals("bar", map.get("FOO"));
        assertEquals("42",  map.get("COUNT"));
    }

    @Test
    void ignoresBlanksAndComments() {

        var map = parse("""
                # top comment
                FOO=bar

                # another

                BAZ=qux
                """);

        assertEquals(Map.of("FOO", "bar", "BAZ", "qux"), map);
    }

    @Test
    void stripsExportPrefix() {

        var map = parse("export API_KEY=abc123");

        assertEquals("abc123", map.get("API_KEY"));
    }

    @Test
    void stripsInlineCommentsFromUnquotedValues() {

        var map = parse("FOO=bar # inline comment");

        assertEquals("bar", map.get("FOO"));
    }

    @Test
    void preservesCommentCharsInsideQuotedValues() {

        var map = parse("""
                FOO="value # with hash"
                BAR='single-quoted # here'
                """);

        assertEquals("value # with hash", map.get("FOO"));
        assertEquals("single-quoted # here", map.get("BAR"));
    }

    @Test
    void stripsSurroundingQuotes() {

        var map = parse("""
                A="double"
                B='single'
                C=bare
                """);

        assertEquals("double", map.get("A"));
        assertEquals("single", map.get("B"));
        assertEquals("bare",   map.get("C"));
    }

    @Test
    void skipsLinesWithoutEquals() {

        var map = parse("""
                not_a_pair
                FOO=bar
                """);

        assertEquals(Map.of("FOO", "bar"), map);
    }

    private static Map<String, String> parse(String content) {

        try {
            var method = DotEnv.class.getDeclaredMethod("parseLine", String.class, Map.class);
            method.setAccessible(true);

            var out = new LinkedHashMap<String, String>();

            for (var line : content.split("\n"))
                method.invoke(null, line, out);

            return out;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
