package ai.agentican.framework.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JsonTest {

    @Test
    void unwrapMarkdownFences_handlesNullAndBlank() {

        assertNull(Json.unwrapMarkdownFences(null));
        assertEquals("", Json.unwrapMarkdownFences(""));
        assertEquals("", Json.unwrapMarkdownFences("   "));
    }

    @Test
    void unwrapMarkdownFences_leavesUnfencedJsonAlone() {

        assertEquals("{\"a\":1}", Json.unwrapMarkdownFences("{\"a\":1}"));
        assertEquals("[1,2,3]", Json.unwrapMarkdownFences("  [1,2,3]  "));
    }

    @Test
    void unwrapMarkdownFences_stripsLanguageTaggedFences() {

        var input = """
                ```json
                {"results":[{"id":"abc","labels":["work"]}]}
                ```
                """;

        assertEquals("{\"results\":[{\"id\":\"abc\",\"labels\":[\"work\"]}]}",
                Json.unwrapMarkdownFences(input));
    }

    @Test
    void unwrapMarkdownFences_stripsBareFences() {

        var input = """
                ```
                [{"id":"x"}]
                ```
                """;

        assertEquals("[{\"id\":\"x\"}]", Json.unwrapMarkdownFences(input));
    }

    @Test
    void unwrapMarkdownFences_stripsLeadingFenceWithoutTrailing() {

        var input = "```json\n{\"a\":1}";

        assertEquals("{\"a\":1}", Json.unwrapMarkdownFences(input));
    }

    @Test
    void unwrapMarkdownFences_preservesInnerBackticks() {

        var input = """
                ```json
                {"snippet":"see `the docs`"}
                ```
                """;

        assertEquals("{\"snippet\":\"see `the docs`\"}", Json.unwrapMarkdownFences(input));
    }
}
