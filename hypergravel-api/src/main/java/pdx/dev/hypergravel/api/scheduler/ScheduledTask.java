package pdx.dev.hypergravel.api.scheduler;

public interface ScheduledTask {

    Object owner();

    boolean cancelled();

    void cancel();
}
