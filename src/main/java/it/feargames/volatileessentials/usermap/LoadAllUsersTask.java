package it.feargames.volatileessentials.usermap;

import com.earth2me.essentials.UUIDMap;
import com.earth2me.essentials.User;
import com.google.common.cache.Cache;
import com.mongodb.client.MongoCollection;
import it.feargames.volatileessentials.VolatileEssentials;
import org.bson.Document;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Consumer;

public class LoadAllUsersTask implements Runnable {
    private final ConcurrentSkipListSet<UUID> keys;
    private final ConcurrentSkipListMap<String, UUID> names;
    private final Cache<String, User> users;
    private final ConcurrentSkipListMap<UUID, ArrayList<String>> history;
    private final UUIDMap uuidMap;


    public LoadAllUsersTask(ConcurrentSkipListSet<UUID> keys,
                            ConcurrentSkipListMap<String, UUID> names,
                            Cache<String, User> users,
                            ConcurrentSkipListMap<UUID, ArrayList<String>> history,
                            UUIDMap uuidMap) {
        this.keys = keys;
        this.names = names;
        this.users = users;
        this.history = history;
        this.uuidMap = uuidMap;
    }

    @Override
    public void run() {
        synchronized (users) {
            keys.clear();
            users.invalidateAll();
            MongoCollection<Document> collection = VolatileEssentials.getInstance().getDatabase().getCollection("users");
            collection.find().forEach((Consumer<Document>) document -> keys.add(UUID.fromString(document.getString("_id"))));
            uuidMap.loadAllUsers(names, history);
        }
    }
}
