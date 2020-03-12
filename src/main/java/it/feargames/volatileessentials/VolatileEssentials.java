package it.feargames.volatileessentials;

import com.earth2me.essentials.*;
import com.google.common.cache.Cache;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import it.feargames.volatileessentials.userconf.EssentialsConfModifier;
import it.feargames.volatileessentials.userconf.EssentialsUserConfModifier;
import it.feargames.volatileessentials.uuidmap.UUIDMapModifier;
import me.yamakaja.runtimetransformer.RuntimeTransformer;
import org.apache.commons.lang.reflect.FieldUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.configuration.MemorySection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class VolatileEssentials extends JavaPlugin implements Listener {

    private static VolatileEssentials instance;

    private Essentials essentials;
    private Cache<String, User> userCache;

    private MongoClient mongoClient;
    private MongoDatabase database;

    @Override
    public void onLoad() {
        instance = this;
        new RuntimeTransformer(EssentialsConfModifier.class, EssentialsUserConfModifier.class, UserMapModifier.class, UUIDMapModifier.class);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if(getConfig().getString("persistence").equalsIgnoreCase("mongodb")) {
            mongoClient = new MongoClient(getConfig().getString("mongodb.host"), getConfig().getInt("mongodb.port"));
            database = mongoClient.getDatabase(getConfig().getString("mongodb.database"));
        }

        essentials = (Essentials) getServer().getPluginManager().getPlugin("Essentials");
        try {
            userCache = (Cache<String, User>) FieldUtils.readDeclaredField(essentials.getUserMap(), "users", true);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if(mongoClient != null) {
            mongoClient.close();
        }
    }

    /*
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        new BukkitRunnable() {

            @Override
            public void run() {
                if(getServer().getPlayer(event.getPlayer().getUniqueId()) != null) {
                    return;
                }
                userCache.invalidate(event.getPlayer().getUniqueId().toString());
            }
        }.runTaskLater(this, 100);
    }
    */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            String uuidString = event.getUniqueId().toString();
            User user = essentials.getUserMap().load(uuidString + "!create");
            userCache.put(uuidString, user);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerLogin(PlayerLoginEvent event) {
        String uuidString = event.getPlayer().getUniqueId().toString();
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            userCache.invalidate(uuidString);
            return;
        }
        try {
            User user = userCache.getIfPresent(uuidString);
            if (user == null) {
                return;
            }
            if (user.getBase() instanceof OfflinePlayer) {
                user.setBase(event.getPlayer());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveUserConf(UUID uniqueId, Map<String, Object> values) {
        if(database == null) {
            return;
        }
        MongoCollection<Document> collection = database.getCollection("users");
        Bson filter = Filters.eq("_id", uniqueId.toString());
        Document data = new Document();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() instanceof MemorySection) {
                continue;
            }
            //System.out.println(entry.getKey() + ": " + entry.getValue());
            data.append(entry.getKey().replace(".", "$"), entry.getValue());
        }
        Bson update = new Document("$set", data);
        UpdateOptions options = new UpdateOptions().upsert(true);
        collection.updateOne(filter, update, options);
    }

    public void loadUserConf(EssentialsUserConf userConf) {
        if (database == null) {
            return;
        }
        MongoCollection<Document> collection = database.getCollection("users");
        Bson filter = Filters.eq("_id", userConf.uuid.toString());
        Document result = collection.find(filter).first();
        if (result == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            userConf.set(entry.getKey().replace("$", "."), entry.getValue());
        }
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public static VolatileEssentials getInstance() {
        return instance;
    }

    public static Logger logger() {
        return getInstance().getLogger();
    }
}
