package ai.agentican.framework.util;

import java.security.SecureRandom;

public class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {}

    public static String generate() {

        var bytes = new byte[4];

        RANDOM.nextBytes(bytes);

        var sb = new StringBuilder(8);

        for (var b : bytes) sb.append(String.format("%02x", b));

        return sb.toString();
    }
}
