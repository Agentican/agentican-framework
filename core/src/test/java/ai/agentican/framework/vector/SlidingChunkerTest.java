package ai.agentican.framework.vector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlidingChunkerTest {

    @Test
    void emptyOrBlankTextProducesNoChunks() {

        var chunker = new SlidingChunker(10, 0);
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk(null).isEmpty());
        assertTrue(chunker.chunk("   ").isEmpty());
    }

    @Test
    void textShorterThanChunkSizeProducesOneChunk() {

        var chunks = new SlidingChunker(100, 10).chunk("hello world");

        assertEquals(1, chunks.size());
        assertEquals("hello world", chunks.getFirst().content());
    }

    @Test
    void slidingWindowProducesOverlap() {

        var text   = "abcdefghijklmnopqrst";
        var chunks = new SlidingChunker(8, 2).chunk(text);

        assertEquals(3, chunks.size());
        assertEquals("abcdefgh", chunks.get(0).content());
        assertEquals("ghijklmn", chunks.get(1).content());
        assertEquals("mnopqrst", chunks.get(2).content());
    }

    @Test
    void zeroOverlapProducesNonOverlappingChunks() {

        var chunks = new SlidingChunker(5, 0).chunk("abcdefghij");

        assertEquals(2, chunks.size());
        assertEquals("abcde", chunks.get(0).content());
        assertEquals("fghij", chunks.get(1).content());
    }

    @Test
    void unevenTailProducesShorterFinalChunk() {

        var chunks = new SlidingChunker(8, 2).chunk("abcdefghijklmno");

        assertEquals(3, chunks.size());
        assertEquals("abcdefgh", chunks.get(0).content());
        assertEquals("ghijklmn", chunks.get(1).content());
        assertEquals("mno",      chunks.get(2).content());
    }

    @Test
    void invalidParametersThrow() {

        assertThrows(IllegalArgumentException.class, () -> new SlidingChunker(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SlidingChunker(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SlidingChunker(10, -1));
        assertThrows(IllegalArgumentException.class, () -> new SlidingChunker(10, 10));
        assertThrows(IllegalArgumentException.class, () -> new SlidingChunker(10, 11));
    }

    @Test
    void defaultsAreReasonable() {

        var chunker = new SlidingChunker();

        assertEquals(800, chunker.chunkSize());
        assertEquals(100, chunker.overlap());
    }
}
