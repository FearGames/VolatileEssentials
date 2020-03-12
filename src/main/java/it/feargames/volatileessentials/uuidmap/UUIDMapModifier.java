package it.feargames.volatileessentials.uuidmap;

import com.earth2me.essentials.UUIDMap;
import com.mongodb.client.MongoCollection;
import it.feargames.volatileessentials.VolatileEssentials;
import me.yamakaja.runtimetransformer.annotation.Inject;
import me.yamakaja.runtimetransformer.annotation.InjectionType;
import me.yamakaja.runtimetransformer.annotation.Transform;
import net.ess3.api.IEssentials;
import org.apache.commons.lang.reflect.FieldUtils;
import org.bson.Document;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

@Transform(UUIDMap.class)
public abstract class UUIDMapModifier extends UUIDMap {
    private transient IEssentials ess;
    private static final ScheduledExecutorService writeScheduler = null;
    private Runnable writeTaskRunnable;

    public UUIDMapModifier(IEssentials ess) {
        super(ess);
    }

    @Inject(InjectionType.APPEND)
    public void _init_(final net.ess3.api.IEssentials ess) {
        // Use custom static flags to avoid weird transformer errors
        UUIDMapStaticStore.UUID_MAP_PENDING_WRITE = false;
        UUIDMapStaticStore.UUID_MAP_LOADING = false;
        // Destroy the current1 scheduler (running the original task)
        writeScheduler.shutdownNow();
        // Define the new task
        writeTaskRunnable = new WriteUUIDMapTask(new Supplier<ConcurrentSkipListMap<String, UUID>>() {
            ConcurrentSkipListMap<String, UUID> cached = null;

            @Override
            public ConcurrentSkipListMap<String, UUID> get() {
                // Provided in a weird way to avoid security exceptions
                if (cached != null) {
                    return cached;
                }
                try {
                    cached = (ConcurrentSkipListMap<String, UUID>) FieldUtils.readDeclaredField(ess.getUserMap(), "names", true);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
                return cached;
            }
        });
        UUIDMapStaticStore.UUID_MAP_SCHEDULER.scheduleWithFixedDelay(writeTaskRunnable, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    @Inject(InjectionType.OVERRIDE)
    public void loadAllUsers(final ConcurrentSkipListMap<String, UUID> names, final ConcurrentSkipListMap<UUID, ArrayList<String>> history) {
        try {
            if (UUIDMapStaticStore.UUID_MAP_LOADING) {
                return;
            }
            VolatileEssentials.logger().info("Loading uuid mappings from the database!");

            names.clear();
            history.clear();

            if(VolatileEssentials.getInstance().getDatabase() != null) {
                UUIDMapStaticStore.UUID_MAP_LOADING = true;

                MongoCollection<Document> collection = VolatileEssentials.getInstance().getDatabase().getCollection("uuids");
                collection.find().forEach(new Consumer<Document>() {
                    @Override
                    public void accept(Document document) {
                        String name = document.getString("_id");
                        UUID uuid = UUID.fromString(document.getString("uuid"));
                        names.put(name, uuid);
                        if (!history.containsKey(uuid)) {
                            final ArrayList<String> list = new ArrayList<>();
                            list.add(name);
                            history.put(uuid, list);
                        } else {
                            final ArrayList<String> list = history.get(uuid);
                            if (!list.contains(name)) {
                                list.add(name);
                            }
                        }
                    }
                });
                UUIDMapStaticStore.UUID_MAP_LOADING = false;
            }
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.SEVERE, ex.getMessage(), ex);
        }
    }

    @Override
    @Inject(InjectionType.OVERRIDE)
    public void writeUUIDMap() {
        UUIDMapStaticStore.UUID_MAP_PENDING_WRITE = true;
    }

    @Override
    @Inject(InjectionType.OVERRIDE)
    public void forceWriteUUIDMap() {
        if (ess.getSettings().isDebug()) {
            ess.getLogger().log(Level.INFO, "Forcing usermap write to disk");
        }
        UUIDMapStaticStore.UUID_MAP_PENDING_WRITE = true;
        writeTaskRunnable.run();
    }

    @Override
    @Inject(InjectionType.OVERRIDE)
    public void shutdown() {
        UUIDMapStaticStore.UUID_MAP_SCHEDULER.submit(writeTaskRunnable);
        UUIDMapStaticStore.UUID_MAP_SCHEDULER.shutdown();
    }
}
