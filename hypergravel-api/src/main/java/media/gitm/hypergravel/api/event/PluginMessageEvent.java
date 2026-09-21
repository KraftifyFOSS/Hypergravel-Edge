package media.gitm.hypergravel.api.event;

import java.util.Objects;

public final class PluginMessageEvent {

    private final Object source;
    private final Object target;
    private final String channel;
    private final byte[] data;
    private boolean handled;

    public PluginMessageEvent(Object source, Object target, String channel, byte[] data) {
        this.source = source;
        this.target = target;
        this.channel = Objects.requireNonNull(channel);
        this.data = Objects.requireNonNull(data);
    }

    public Object source() {
        return source;
    }

    public Object target() {
        return target;
    }

    public String channel() {
        return channel;
    }

    public byte[] data() {
        return data;
    }

    public boolean handled() {
        return handled;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
    }
}
