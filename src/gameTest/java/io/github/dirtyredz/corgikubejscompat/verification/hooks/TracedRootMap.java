package io.github.dirtyredz.corgikubejscompat.verification.hooks;

import java.util.*;

/** Delegates to the original HashMap; all mutation paths, including views, are traced. */
final class TracedRootMap extends AbstractMap<Object, Object> {
    private final Map<Object, Object> delegate;
    TracedRootMap(Map<Object, Object> delegate) { this.delegate = delegate; }
    @Override public Object get(Object key) { return delegate.get(key); }
    @Override public boolean containsKey(Object key) { return delegate.containsKey(key); }
    @Override public int size() { return delegate.size(); }
    @Override public Object put(Object key, Object value) {
        try (var mutation = RaceHooks.mutation("put", key)) { return delegate.put(key, value); }
    }
    @Override public Object remove(Object key) {
        if (!delegate.containsKey(key)) return null;
        try (var mutation = RaceHooks.mutation("remove", key)) { return delegate.remove(key); }
    }
    @Override public Set<Object> keySet() {
        return new AbstractSet<>() {
            @Override public int size() { return delegate.size(); }
            @Override public Iterator<Object> iterator() {
                Iterator<Object> iterator = delegate.keySet().iterator();
                return new Iterator<>() {
                    private Object key;
                    @Override public boolean hasNext() { return iterator.hasNext(); }
                    @Override public Object next() { return key = iterator.next(); }
                    @Override public void remove() {
                        try (var mutation = RaceHooks.mutation("keyIterator.remove", key)) { iterator.remove(); }
                    }
                };
            }
        };
    }
    @Override public Set<Entry<Object, Object>> entrySet() {
        return new AbstractSet<>() {
            @Override public int size() { return delegate.size(); }
            @Override public Iterator<Entry<Object, Object>> iterator() {
                Iterator<Entry<Object, Object>> iterator = delegate.entrySet().iterator();
                return new Iterator<>() {
                    private Object key;
                    @Override public boolean hasNext() { return iterator.hasNext(); }
                    @Override public Entry<Object, Object> next() {
                        Entry<Object, Object> entry = iterator.next();
                        key = entry.getKey();
                        return new SimpleEntry<>(entry) {
                            @Override public Object setValue(Object value) {
                                try (var mutation = RaceHooks.mutation("entry.setValue", entry.getKey())) {
                                    super.setValue(value);
                                    return entry.setValue(value);
                                }
                            }
                        };
                    }
                    @Override public void remove() {
                        try (var mutation = RaceHooks.mutation("entryIterator.remove", key)) { iterator.remove(); }
                    }
                };
            }
        };
    }
}
