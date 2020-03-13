package it.feargames.volatileessentials.userconf;

import com.earth2me.essentials.EssentialsConf;
import com.earth2me.essentials.EssentialsUserConf;
import it.feargames.volatileessentials.VolatileEssentials;
import me.yamakaja.runtimetransformer.annotation.Inject;
import me.yamakaja.runtimetransformer.annotation.InjectionType;
import me.yamakaja.runtimetransformer.annotation.Transform;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

@Transform(EssentialsConf.class)
public abstract class EssentialsConfModifier extends EssentialsConf {

    private static final ExecutorService EXECUTOR_SERVICE = null;
    private final AtomicInteger pendingDiskWrites = null;

    EssentialsConfModifier(File configFile) {
        super(configFile);
    }

    @Override
    @Inject(InjectionType.INSERT)
    public synchronized void load() {
        Object instance = this;
        if (instance instanceof EssentialsUserConf) {
            if(VolatileEssentials.getInstance().getDatabase() == null) {
                // Ignore silently if persistence is disabled
                return;
            }
            EssentialsUserConf userConf = (EssentialsUserConf) instance;
            VolatileEssentials.logger().info("Loading '" + userConf.username + "' (" + userConf.uuid + ") from the database!");
            if (this.pendingDiskWrites.get() != 0) {
                VolatileEssentials.logger().log(Level.INFO, "Data of user {0} ({1}) not read, because it''s not yet written to the database.", new String[]{userConf.username, userConf.uuid.toString()});
            } else {
                if (Bukkit.isPrimaryThread()) {
                    VolatileEssentials.logger().severe("Blocking mongodb call detected on the main thread! (sync data load)");
                    new Throwable().printStackTrace();
                }
                VolatileEssentials.getInstance().loadUserConf(userConf);
            }
            return;
        }
        throw null;
    }

    @Override
    @Inject(InjectionType.OVERRIDE)
    public synchronized void forceSave() {
        if (Bukkit.isPrimaryThread()) {
            VolatileEssentials.logger().severe("Blocking mongodb call detected on the main thread! (forced data save)");
            new Throwable().printStackTrace();
        }
        try {
            Future<?> future = this.delayedSave(this.configFile);
            if(future != null) {
                future.get();
            }
        } catch (ExecutionException | InterruptedException var2) {
            LOGGER.log(Level.SEVERE, var2.getMessage(), var2);
        }
    }

    @Inject(InjectionType.INSERT)
    private Future<?> delayedSave(File file) {
        Object instance = this;
        if (instance instanceof EssentialsUserConf) {
            if(VolatileEssentials.getInstance().getDatabase() == null) {
                // Ignore silently if persistence is disabled
                return null;
            }
            EssentialsUserConf userConf = (EssentialsUserConf) instance;
            VolatileEssentials.logger().info("Saving '" + userConf.username + "' (" + userConf.uuid + ") to the database!");
            UUID uniqueId = userConf.uuid;
            Map<String, Object> values = userConf.getValues(true);
            this.pendingDiskWrites.incrementAndGet();
            final AtomicInteger pendingDiskWrites = this.pendingDiskWrites;
            return EXECUTOR_SERVICE.submit(new Runnable() {
                @Override
                public void run() {
                    if (pendingDiskWrites.get() > 1) {
                        pendingDiskWrites.decrementAndGet();
                    } else {
                        try {
                            VolatileEssentials.getInstance().saveUserConf(uniqueId, values);
                        } finally {
                            pendingDiskWrites.decrementAndGet();
                        }
                    }
                }
            });
        }
        throw null;
    }
}
