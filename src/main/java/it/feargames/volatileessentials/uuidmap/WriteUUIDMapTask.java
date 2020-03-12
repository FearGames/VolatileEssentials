package it.feargames.volatileessentials.uuidmap;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import it.feargames.volatileessentials.VolatileEssentials;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;

public class WriteUUIDMapTask implements Runnable {

    private final Supplier<ConcurrentSkipListMap<String, UUID>> namesSupplier;

    public WriteUUIDMapTask(Supplier<ConcurrentSkipListMap<String, UUID>> namesSupplier) {
        this.namesSupplier = namesSupplier;
    }

    @Override
    public void run() {
        if(VolatileEssentials.getInstance().getDatabase() == null) {
            return;
        }
        if (UUIDMapStaticStore.UUID_MAP_PENDING_WRITE) {
            VolatileEssentials.logger().info("Writing uuid map to the database!");
            try {
                UUIDMapStaticStore.UUID_MAP_PENDING_WRITE = false;
                ConcurrentSkipListMap<String, UUID> names = namesSupplier.get();
                if (UUIDMapStaticStore.UUID_MAP_LOADING || names.isEmpty()) {
                    return;
                }
                //Map<String, UUID> names = new HashMap<>(this.names);
                MongoCollection<Document> collection = VolatileEssentials.getInstance().getDatabase().getCollection("uuids");
                for (Map.Entry<String, UUID> entry : names.entrySet()) {
                    Bson filter = Filters.eq("_id", entry.getKey());
                    Document data = new Document();
                    data.append("uuid", entry.getValue().toString());
                    Bson update = new Document("$set", data);
                    UpdateOptions options = new UpdateOptions().upsert(true);
                    collection.updateOne(filter, update, options);
                }
            } catch (Throwable t) { // bad code to prevent task from being suppressed
                t.printStackTrace();
            }
        }
    }
}
