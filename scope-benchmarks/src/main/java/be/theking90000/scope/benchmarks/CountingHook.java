package be.theking90000.scope.benchmarks;

import be.theking90000.scope.BeanCreated;
import be.theking90000.scope.Disposer;
import be.theking90000.scope.OnCreatedHook;

public final class CountingHook implements OnCreatedHook {
    private final SyntheticBeans.HookCounters counters;

    public CountingHook(SyntheticBeans.HookCounters counters) {
        this.counters = counters;
    }

    @Override
    public Disposer onCreated(BeanCreated event) {
        if (!(event.bean() instanceof SyntheticBeans.HookTarget)) {
            return null;
        }

        counters.created.incrementAndGet();
        return counters.disposed::incrementAndGet;
    }
}
