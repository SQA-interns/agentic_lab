package si.konferenca.registration.acceptance.support;

import java.util.concurrent.atomic.AtomicInteger;

/** Distinct client addresses so that rate-limit buckets of different tests never mix. */
public final class Clients {

  private static final AtomicInteger COUNTER = new AtomicInteger(1);

  private Clients() {}

  public static String next() {
    int n = COUNTER.getAndIncrement();
    return "10." + ((n >> 16) & 0xff) + "." + ((n >> 8) & 0xff) + "." + (n & 0xff);
  }
}
