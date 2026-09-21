package media.gitm.hypergravel.api.event;

import media.gitm.hypergravel.api.ProxyServer;

public record ProxyShutdownEvent(ProxyServer proxy) {}
