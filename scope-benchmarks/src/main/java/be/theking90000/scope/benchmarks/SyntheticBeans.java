package be.theking90000.scope.benchmarks;

import java.util.concurrent.atomic.AtomicLong;

public final class SyntheticBeans {
    private SyntheticBeans() {
    }

    public record RootContext(String name) {
    }

    public record PlayerContext(int id) {
    }

    public record NestedContext(int depth) {
    }

    public static final class GlobalService {
        private final long marker = 42L;

        public long marker() {
            return marker;
        }
    }

    public static final class Handler {
        private long calls;

        public long onEvent() {
            return ++calls;
        }
    }

    public static final class Bean00 {
        private final PlayerContext player;
        private final GlobalService global;

        public Bean00(PlayerContext player, GlobalService global) {
            this.player = player;
            this.global = global;
        }

        public long checksum() {
            return player.id() + global.marker();
        }
    }

    public static final class Bean01 {
        private final Bean00 previous;

        public Bean01(Bean00 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean02 {
        private final Bean01 previous;

        public Bean02(Bean01 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean03 {
        private final Bean02 previous;

        public Bean03(Bean02 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean04 {
        private final Bean03 previous;

        public Bean04(Bean03 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean05 {
        private final Bean04 previous;

        public Bean05(Bean04 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean06 {
        private final Bean05 previous;

        public Bean06(Bean05 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean07 {
        private final Bean06 previous;

        public Bean07(Bean06 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean08 {
        private final Bean07 previous;

        public Bean08(Bean07 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean09 {
        private final Bean08 previous;

        public Bean09(Bean08 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean10 {
        private final Bean09 previous;

        public Bean10(Bean09 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean11 {
        private final Bean10 previous;

        public Bean11(Bean10 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean12 {
        private final Bean11 previous;

        public Bean12(Bean11 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean13 {
        private final Bean12 previous;

        public Bean13(Bean12 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean14 {
        private final Bean13 previous;

        public Bean14(Bean13 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean15 {
        private final Bean14 previous;

        public Bean15(Bean14 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean16 {
        private final Bean15 previous;

        public Bean16(Bean15 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean17 {
        private final Bean16 previous;

        public Bean17(Bean16 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean18 {
        private final Bean17 previous;

        public Bean18(Bean17 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean19 {
        private final Bean18 previous;

        public Bean19(Bean18 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean20 {
        private final Bean19 previous;

        public Bean20(Bean19 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean21 {
        private final Bean20 previous;

        public Bean21(Bean20 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean22 {
        private final Bean21 previous;

        public Bean22(Bean21 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean23 {
        private final Bean22 previous;

        public Bean23(Bean22 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean24 {
        private final Bean23 previous;

        public Bean24(Bean23 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean25 {
        private final Bean24 previous;

        public Bean25(Bean24 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean26 {
        private final Bean25 previous;

        public Bean26(Bean25 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean27 {
        private final Bean26 previous;

        public Bean27(Bean26 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean28 {
        private final Bean27 previous;

        public Bean28(Bean27 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean29 {
        private final Bean28 previous;

        public Bean29(Bean28 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean30 {
        private final Bean29 previous;

        public Bean30(Bean29 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class Bean31 {
        private final Bean30 previous;

        public Bean31(Bean30 previous) {
            this.previous = previous;
        }

        public long checksum() {
            return previous.checksum() + 1;
        }
    }

    public static final class HookCounters {
        public final AtomicLong created = new AtomicLong();
        public final AtomicLong disposed = new AtomicLong();
    }

    public static final class HookTarget {
        private final PlayerContext player;

        public HookTarget(PlayerContext player) {
            this.player = player;
        }

        public int playerId() {
            return player.id();
        }
    }
}
