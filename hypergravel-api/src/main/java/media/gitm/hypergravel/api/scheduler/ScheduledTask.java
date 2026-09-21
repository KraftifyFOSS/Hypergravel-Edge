package media.gitm.hypergravel.api.scheduler;

public interface ScheduledTask {

    Object owner();

    boolean cancelled();

    void cancel();
}
