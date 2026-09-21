package media.gitm.hypergravel.proxy.scheduler;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import media.gitm.hypergravel.api.scheduler.ScheduledTask;
import media.gitm.hypergravel.api.scheduler.Scheduler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class HyperGravelScheduler implements Scheduler {

    private static final Logger LOGGER = LogManager.getLogger(HyperGravelScheduler.class);

    private final ScheduledExecutorService timer;
    private final ExecutorService workers;
    private final Set<Task> tasks = ConcurrentHashMap.newKeySet();

    public HyperGravelScheduler() {
        this.timer = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "hypergravel-scheduler-timer");
            thread.setDaemon(true);
            return thread;
        });
        this.workers = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public ScheduledTask run(Object owner, Runnable body) {
        Task task = new Task(owner);
        workers.execute(() -> runGuarded(task, body));
        return task;
    }

    @Override
    public ScheduledTask delay(Object owner, Duration delay, Runnable body) {
        Task task = new Task(owner);
        task.attach(timer.schedule(
                () -> workers.execute(() -> runGuarded(task, body)),
                delay.toMillis(), TimeUnit.MILLISECONDS));
        tasks.add(task);
        return task;
    }

    @Override
    public ScheduledTask repeat(Object owner, Duration initialDelay, Duration period,
                                Runnable body) {
        Task task = new Task(owner);

        AtomicBoolean running = new AtomicBoolean();
        task.attach(timer.scheduleAtFixedRate(() -> {
            if (task.cancelled() || !running.compareAndSet(false, true)) {
                return;
            }
            workers.execute(() -> {
                try {
                    runGuarded(task, body);
                } finally {
                    running.set(false);
                }
            });
        }, initialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS));
        tasks.add(task);
        return task;
    }

    @Override
    public <T> CompletableFuture<T> supply(Object owner, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        workers.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    @Override
    public void cancelAll(Object owner) {
        for (Task task : tasks) {
            if (task.owner() == owner) {
                task.cancel();
            }
        }
    }

    private void runGuarded(Task task, Runnable body) {
        if (task.cancelled()) {
            return;
        }
        try {
            body.run();
        } catch (Throwable t) {
            LOGGER.error("scheduled task from {} threw", describe(task.owner()), t);
        }
    }

    private static String describe(Object owner) {
        return owner == null ? "unknown" : owner.getClass().getName();
    }

    public void shutdown(Duration grace) {
        timer.shutdown();
        workers.shutdown();
        try {
            if (!workers.awaitTermination(grace.toMillis(), TimeUnit.MILLISECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        }
    }

    private final class Task implements ScheduledTask {

        private final Object owner;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile ScheduledFuture<?> future;

        Task(Object owner) {
            this.owner = owner;
        }

        void attach(ScheduledFuture<?> future) {
            this.future = future;
            if (cancelled.get()) {
                future.cancel(false);
            }
        }

        @Override
        public Object owner() {
            return owner;
        }

        @Override
        public boolean cancelled() {
            return cancelled.get();
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true)) {
                ScheduledFuture<?> scheduled = future;
                if (scheduled != null) {

                    scheduled.cancel(false);
                }
                tasks.remove(this);
            }
        }
    }
}
