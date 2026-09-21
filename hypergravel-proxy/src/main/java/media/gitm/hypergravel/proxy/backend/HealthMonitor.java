package media.gitm.hypergravel.proxy.backend;

import java.time.Duration;

import media.gitm.hypergravel.api.server.ServerHealth;
import media.gitm.hypergravel.proxy.HyperGravelProxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class HealthMonitor {

    private static final Logger LOGGER = LogManager.getLogger(HealthMonitor.class);

    private static final int SUCCESSES_TO_RECOVER = 2;

    private final HyperGravelProxy proxy;

    public HealthMonitor(HyperGravelProxy proxy) {
        this.proxy = proxy;
    }

    public void start() {
        Duration interval = proxy.config().ops().healthCheckInterval();
        proxy.scheduler().repeat(this, Duration.ZERO, interval, this::checkAll);
        LOGGER.info("health monitor started ({}s interval)", interval.toSeconds());
    }

    private void checkAll() {
        for (HyperGravelServer server : proxy.serverRegistry().all()) {
            check(server);
        }
    }

    private void check(HyperGravelServer server) {
        long startNanos = System.nanoTime();
        server.ping().whenComplete((ping, error) -> {
            if (error != null) {
                onFailure(server);
            } else {
                server.recordLatency((System.nanoTime() - startNanos) / 1_000_000L);
                onSuccess(server);
            }
        });
    }

    private void onSuccess(HyperGravelServer server) {
        int successes = server.recordSuccess();
        ServerHealth current = server.health();

        if (current == ServerHealth.UP) {
            return;
        }
        if (successes >= SUCCESSES_TO_RECOVER) {
            ServerHealth previous = server.setHealth(ServerHealth.UP);
            proxy.onHealthChange(server, previous, ServerHealth.UP);
        }
    }

    private void onFailure(HyperGravelServer server) {
        int failures = server.recordFailure();
        ServerHealth current = server.health();

        if (current == ServerHealth.DOWN) {
            return;
        }
        int threshold = proxy.config().ops().healthCheckFailuresToDown();
        if (failures >= threshold) {

            ServerHealth previous = server.setHealth(ServerHealth.DOWN);
            server.invalidateAddress();
            proxy.onHealthChange(server, previous, ServerHealth.DOWN);
        }
    }

    public void beginDrain(HyperGravelServer server) {
        ServerHealth previous = server.setHealth(ServerHealth.DRAINING);
        LOGGER.info("{} is draining on request", server.name());
        proxy.onHealthChange(server, previous, ServerHealth.DRAINING);
    }
}
