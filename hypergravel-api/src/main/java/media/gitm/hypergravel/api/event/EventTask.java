package media.gitm.hypergravel.api.event;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public interface EventTask {

    CompletableFuture<Void> execute();

    static EventTask async(Runnable work) {
        return () -> {
            CompletableFuture<Void> future = new CompletableFuture<>();
            Thread.ofVirtual().name("hypergravel-event-task").start(() -> {
                try {
                    work.run();
                    future.complete(null);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            return future;
        };
    }

    static EventTask resumeWhenComplete(CompletableFuture<?> future) {
        return () -> future.thenApply(ignored -> null);
    }

    static EventTask withFuture(Supplier<CompletableFuture<Void>> supplier) {
        return supplier::get;
    }
}
