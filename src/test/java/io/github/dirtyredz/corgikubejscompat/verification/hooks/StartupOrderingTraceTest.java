package io.github.dirtyredz.corgikubejscompat.verification.hooks;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StartupOrderingTraceTest {
    @Test void registryHolderInsertionThenIdenticalAssignmentIsOneMutation() {
        List<String> changes = new ArrayList<>();
        List<Object> unchanged = new ArrayList<>();
        var map = new TracedRootMap(new HashMap<>(), (operation, key) -> {
            if (operation.equals("put-unchanged")) {
                unchanged.add(key);
                return null;
            }
            changes.add(operation + ":" + key);
            return new StartupOrderingHooks.Mutation();
        });
        for (String key : List.of("blending_function", "villager_trades_item_listing", "condition")) {
            Object holder = map.computeIfAbsent(key, ignored -> new Object());
            assertSame(holder, map.put(key, holder)); // Installed MappedRegistry's second assignment.
        }
        assertEquals(3, changes.size(), "Three insertions, not six mutations");
        assertEquals(3, unchanged.size(), "Identical assignments remain visible in the trace");
        var iterator = map.keySet().iterator();
        map.put("condition", new Object());
        assertEquals(4, changes.size(), "A different holder is a real replacement");
        assertDoesNotThrow(iterator::next, "Replacement preserves HashMap iterator semantics");
        map.put("fourth", new Object());
        assertThrows(java.util.ConcurrentModificationException.class, iterator::next);
    }
}
