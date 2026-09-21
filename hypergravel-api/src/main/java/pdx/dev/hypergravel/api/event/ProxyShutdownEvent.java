package pdx.dev.hypergravel.api.event;

import pdx.dev.hypergravel.api.ProxyServer;

public record ProxyShutdownEvent(ProxyServer proxy) {}
