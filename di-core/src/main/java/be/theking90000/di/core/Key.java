package be.theking90000.di.core;

public record Key<T>(Class<T> type, Object qualifier) {
        public static <T> Key<T> of(Class<T> type) { return new Key<>(type, null); }
        public static <T> Key<T> of(Class<T> type, Object qualifier) { return new Key<>(type, qualifier); }
        @Override public String toString() {
            return qualifier == null ? type.getSimpleName() : type.getSimpleName() + "@" + qualifier;
        }
    }

