package media.gitm.hypergravel.proxy.security;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CidrTrustList {

    public static final CidrTrustList DENY_ALL = new CidrTrustList(List.of());

    private final List<Entry> entries;

    private CidrTrustList(List<Entry> entries) {
        this.entries = entries;
    }

    public static CidrTrustList parse(List<String> cidrs) {
        List<Entry> entries = new ArrayList<>(cidrs.size());
        for (String cidr : cidrs) {
            entries.add(Entry.parse(cidr));
        }
        return new CidrTrustList(List.copyOf(entries));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public boolean contains(InetAddress address) {
        if (address == null) {
            return false;
        }

        InetAddress normalised = normalise(address);
        byte[] bytes = normalised.getAddress();
        for (Entry entry : entries) {
            if (entry.matches(bytes)) {
                return true;
            }
        }
        return false;
    }

    private static InetAddress normalise(InetAddress address) {
        if (address instanceof Inet6Address v6) {
            byte[] bytes = v6.getAddress();
            if (isV4Mapped(bytes)) {
                try {
                    return InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16));
                } catch (UnknownHostException e) {
                    return address;
                }
            }
        }
        return address;
    }

    private static boolean isV4Mapped(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }

    private record Entry(byte[] network, int prefixBits) {

        static Entry parse(String raw) {
            String text = raw.trim();
            int slash = text.indexOf('/');
            String host = slash < 0 ? text : text.substring(0, slash);

            InetAddress address;
            try {

                if (!isIpLiteral(host)) {
                    throw new IllegalArgumentException(
                            "proxy-protocol.trusted must contain IP literals, not hostnames: " + raw);
                }
                address = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("invalid address in trust list: " + raw, e);
            }

            byte[] bytes = address.getAddress();
            int maxBits = bytes.length * 8;
            int prefix = maxBits;
            if (slash >= 0) {
                try {
                    prefix = Integer.parseInt(text.substring(slash + 1));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("invalid CIDR prefix: " + raw, e);
                }
                if (prefix < 0 || prefix > maxBits) {
                    throw new IllegalArgumentException(
                            "CIDR prefix out of range for this address family: " + raw);
                }
            }
            return new Entry(bytes, prefix);
        }

        boolean matches(byte[] candidate) {
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixBits % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }

    private static boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            return true;
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    static boolean isV4(InetAddress address) {
        return address instanceof Inet4Address;
    }
}
