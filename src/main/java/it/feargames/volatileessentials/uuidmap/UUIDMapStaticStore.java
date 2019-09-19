package it.feargames.volatileessentials.uuidmap;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public final class UUIDMapStaticStore {

    private UUIDMapStaticStore() {
    }

    public static boolean UUID_MAP_PENDING_WRITE = false;
    public static boolean UUID_MAP_LOADING = false;
    public static final ScheduledExecutorService UUID_MAP_SCHEDULER = Executors.newScheduledThreadPool(1);
}
