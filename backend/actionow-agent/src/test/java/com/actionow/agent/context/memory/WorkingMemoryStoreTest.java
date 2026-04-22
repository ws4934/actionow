package com.actionow.agent.context.memory;

import com.actionow.agent.metrics.AgentMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorkingMemoryStore 单元测试
 * 纯内存模式（无 Redis），验证核心存取、LRU 淘汰、截断逻辑。
 */
class WorkingMemoryStoreTest {

    private WorkingMemoryStore store;
    private static final String SESSION = "test-session-1";

    @BeforeEach
    void setUp() {
        AgentMetrics metrics = new AgentMetrics(new SimpleMeterRegistry());
        store = new WorkingMemoryStore(null, new ObjectMapper(), metrics);
    }

    @Test
    void putAndGet() {
        store.put(SESSION, "key1", "value1", "tool_a", false);
        WorkingMemoryStore.MemoryEntry entry = store.get(SESSION, "key1");

        assertNotNull(entry);
        assertEquals("key1", entry.getKey());
        assertEquals("value1", entry.getValue());
        assertEquals("tool_a", entry.getSource());
        assertFalse(entry.isPinned());
        assertFalse(entry.isTruncated());
        assertEquals(6, entry.getCharCount());
    }

    @Test
    void getReturnsNullForMissingKey() {
        assertNull(store.get(SESSION, "nonexistent"));
        assertNull(store.get("no-such-session", "key"));
    }

    @Test
    void putOverwritesExistingKey() {
        store.put(SESSION, "k", "old", null, false);
        store.put(SESSION, "k", "new", null, false);

        assertEquals(1, store.size(SESSION));
        assertEquals("new", store.get(SESSION, "k").getValue());
    }

    @Test
    void removeEntry() {
        store.put(SESSION, "k", "v", null, false);
        assertTrue(store.remove(SESSION, "k"));
        assertNull(store.get(SESSION, "k"));
        assertEquals(0, store.size(SESSION));
    }

    @Test
    void removeReturnsFalseForMissing() {
        assertFalse(store.remove(SESSION, "nope"));
    }

    @Test
    void clearSession() {
        store.put(SESSION, "a", "1", null, false);
        store.put(SESSION, "b", "2", null, false);
        store.clearSession(SESSION);
        assertEquals(0, store.size(SESSION));
        assertTrue(store.list(SESSION).isEmpty());
    }

    @Test
    void listReturnsAllEntries() {
        store.put(SESSION, "a", "1", null, false);
        store.put(SESSION, "b", "2", null, true);

        var entries = store.list(SESSION);
        assertEquals(2, entries.size());
    }

    @Test
    void listReturnsEmptyForUnknownSession() {
        assertTrue(store.list("unknown").isEmpty());
    }

    @Test
    void lruEvictsOldestNonPinnedEntry() {
        // MAX_ENTRIES_PER_SESSION = 30, fill it up
        for (int i = 0; i < 30; i++) {
            store.put(SESSION, "key-" + i, "value-" + i, null, false);
        }
        assertEquals(30, store.size(SESSION));

        // Add one more — should evict key-0 (oldest)
        store.put(SESSION, "key-30", "value-30", null, false);
        assertEquals(30, store.size(SESSION));
        assertNull(store.get(SESSION, "key-0"), "Oldest entry should be evicted");
        assertNotNull(store.get(SESSION, "key-30"));
    }

    @Test
    void pinnedEntryNotEvicted() {
        // Pin the first entry
        store.put(SESSION, "pinned", "important", null, true);

        // Fill remaining 29 slots
        for (int i = 1; i < 30; i++) {
            store.put(SESSION, "key-" + i, "v", null, false);
        }
        assertEquals(30, store.size(SESSION));

        // Overflow — should evict key-1 (oldest non-pinned), not "pinned"
        store.put(SESSION, "overflow", "v", null, false);
        assertEquals(30, store.size(SESSION));
        assertNotNull(store.get(SESSION, "pinned"), "Pinned entry must survive LRU");
        assertNull(store.get(SESSION, "key-1"), "Oldest non-pinned should be evicted");
    }

    @Test
    void truncatesLargeValues() {
        String largeValue = "x".repeat(60_000);
        store.put(SESSION, "big", largeValue, null, false);

        WorkingMemoryStore.MemoryEntry entry = store.get(SESSION, "big");
        assertNotNull(entry);
        assertTrue(entry.isTruncated());
        assertEquals(60_000, entry.getCharCount()); // original length preserved
        assertTrue(entry.getValue().length() < 60_000, "Stored value should be truncated");
        assertTrue(entry.getValue().contains("截断"));
    }

    @Test
    void buildIndexSummaryFormatsCorrectly() {
        store.put(SESSION, "characters", "list of characters", "query_characters", false);
        store.put(SESSION, "pdf", "pdf content", null, true);

        String index = store.buildIndexSummary(SESSION);
        assertNotNull(index);
        assertTrue(index.contains("characters"));
        assertTrue(index.contains("query_characters"));
        assertTrue(index.contains("pdf"));
        assertTrue(index.contains("recall_from_memory"));
    }

    @Test
    void buildIndexSummaryReturnsNullForEmptySession() {
        assertNull(store.buildIndexSummary("empty-session"));
    }

    @Test
    void nullParametersHandledGracefully() {
        // None of these should throw
        store.put(null, "k", "v", null, false);
        store.put(SESSION, null, "v", null, false);
        store.put(SESSION, "k", null, null, false);
        assertNull(store.get(null, "k"));
        assertNull(store.get(SESSION, null));
        assertFalse(store.remove(null, "k"));
        assertTrue(store.list(null).isEmpty());
    }

    @Test
    void sessionIsolation() {
        store.put("s1", "k", "v1", null, false);
        store.put("s2", "k", "v2", null, false);

        assertEquals("v1", store.get("s1", "k").getValue());
        assertEquals("v2", store.get("s2", "k").getValue());

        store.clearSession("s1");
        assertNull(store.get("s1", "k"));
        assertNotNull(store.get("s2", "k"));
    }
}
