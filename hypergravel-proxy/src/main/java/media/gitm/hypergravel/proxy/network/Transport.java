package media.gitm.hypergravel.proxy.network;

import java.util.concurrent.ThreadFactory;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollIoHandler;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Transport {

    private static final Logger LOGGER = LogManager.getLogger(Transport.class);

    private final String name;
    private final Class<? extends ServerChannel> serverChannelType;
    private final Class<? extends Channel> channelType;
    private final IoHandlerFactoryHolder handlerFactory;

    private Transport(String name, Class<? extends ServerChannel> serverChannelType,
                      Class<? extends Channel> channelType,
                      IoHandlerFactoryHolder handlerFactory) {
        this.name = name;
        this.serverChannelType = serverChannelType;
        this.channelType = channelType;
        this.handlerFactory = handlerFactory;
    }

    public static Transport best(boolean preferIoUring) {
        if (preferIoUring) {

            LOGGER.warn("prefer-io-uring is set but the io_uring transport is not wired up yet; "
                    + "falling back to the next best transport");
        }
        if (Epoll.isAvailable()) {
            LOGGER.info("using the epoll transport");
            return new Transport("epoll", EpollServerSocketChannel.class,
                    EpollSocketChannel.class, EpollIoHandler::newFactory);
        }
        LOGGER.info("using the NIO transport ({})",
                Epoll.unavailabilityCause() == null
                        ? "epoll not present"
                        : "epoll unavailable: " + Epoll.unavailabilityCause());
        return new Transport("nio", NioServerSocketChannel.class,
                NioSocketChannel.class, NioIoHandler::newFactory);
    }

    public String name() {
        return name;
    }

    public Class<? extends ServerChannel> serverChannelType() {
        return serverChannelType;
    }

    public Class<? extends Channel> channelType() {
        return channelType;
    }

    public EventLoopGroup createGroup(int threads, String threadNamePrefix) {
        ThreadFactory factory = new ThreadFactory() {
            private int counter;

            @Override
            public synchronized Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, threadNamePrefix + "-" + counter++);
                thread.setDaemon(true);
                return thread;
            }
        };
        return new MultiThreadIoEventLoopGroup(threads, factory, handlerFactory.create());
    }

    @FunctionalInterface
    private interface IoHandlerFactoryHolder {
        io.netty.channel.IoHandlerFactory create();
    }
}
