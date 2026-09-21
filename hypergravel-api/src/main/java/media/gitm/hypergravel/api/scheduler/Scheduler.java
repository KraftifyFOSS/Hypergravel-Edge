package media.gitm.hypergravel.api.scheduler;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface Scheduler {

    ScheduledTask run(Object owner, Runnable task);

    ScheduledTask delay(Object owner, Duration delay, Runnable task);

    ScheduledTask repeat(Object owner, Duration initialDelay, Duration period, Runnable task);

    <T> CompletableFuture<T> supply(Object owner, Supplier<T> supplier);

    void cancelAll(Object owner);
}
