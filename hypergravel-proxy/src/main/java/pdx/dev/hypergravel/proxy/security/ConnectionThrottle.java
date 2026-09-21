package pdx.dev.hypergravel.proxy.security;

import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

public final class ConnectionThrottle {

    private final LoadingCache<InetAddress, Bucket> buckets;
    private final int perSecond;
    private final int maxConcurrent;
    private final LongAdder rejected = new LongAdder();

    public ConnectionThrottle(int perSecond, int maxConcurrent) {
        this.perSecond = perSecond;
        this.maxConcurrent = maxConcurrent;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(1))

                .maximumSize(1_000_000)
                .build(ignored -> new Bucket());
    }

    public boolean tryAcquire(InetAddress address) {
        if (perSecond <= 0 && maxConcurrent <= 0) {
            return true;
        }
        Bucket bucket = buckets.get(address);
        boolean allowed = bucket.tryAcquire(perSecond, maxConcurrent);
        if (!allowed) {
            rejected.increment();
        }
        return allowed;
    }

    public void release(InetAddress address) {
        if (maxConcurrent <= 0) {
            return;
        }
        Bucket bucket = buckets.getIfPresent(address);
        if (bucket != null) {
            bucket.release();
        }
    }

    public int concurrentFor(InetAddress address) {
        Bucket bucket = buckets.getIfPresent(address);
        return bucket == null ? 0 : bucket.concurrent.get();
    }

    public long rejectedCount() {
        return rejected.sum();
    }

    public long trackedAddresses() {
        return buckets.estimatedSize();
    }

    private static final class Bucket {

        private final AtomicInteger concurrent = new AtomicInteger();

        private final java.util.concurrent.atomic.AtomicLong window =
                new java.util.concurrent.atomic.AtomicLong();

        boolean tryAcquire(int perSecond, int maxConcurrent) {
            if (perSecond > 0 && !allowRate(perSecond)) {
                return false;
            }
            if (maxConcurrent > 0) {
                int current = concurrent.incrementAndGet();
                if (current > maxConcurrent) {
                    concurrent.decrementAndGet();
                    return false;
                }
            }
            return true;
        }

        private boolean allowRate(int perSecond) {
            long nowSecond = System.nanoTime() / 1_000_000_000L;
            while (true) {
                long packed = window.get();
                long second = packed >>> 20;
                int count = (int) (packed & 0xFFFFF);

                long next;
                if (second != nowSecond) {
                    next = (nowSecond << 20) | 1;
                } else {
                    if (count >= perSecond) {
                        return false;
                    }
                    next = (second << 20) | (count + 1);
                }
                if (window.compareAndSet(packed, next)) {
                    return true;
                }
            }
        }

        void release() {
            concurrent.updateAndGet(current -> Math.max(0, current - 1));
        }
    }
}
