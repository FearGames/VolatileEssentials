package com.earth2me.essentials;

import com.earth2me.essentials.utils.StringUtil;
import com.google.common.cache.Cache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.mongodb.client.model.Filters;
import it.feargames.volatileessentials.VolatileEssentials;
import it.feargames.volatileessentials.usermap.LoadAllUsersTask;
import me.yamakaja.runtimetransformer.annotation.Inject;
import me.yamakaja.runtimetransformer.annotation.InjectionType;
import me.yamakaja.runtimetransformer.annotation.Transform;
import net.ess3.api.IEssentials;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Transform(UserMap.class)
public abstract class UserMapModifier extends UserMap {
    private final transient IEssentials ess = null;
    private final transient ConcurrentSkipListSet<UUID> keys = null;
    private final transient ConcurrentSkipListMap<String, UUID> names = null;
    private final transient Cache<String, User> users = null;
    private final transient ConcurrentSkipListMap<UUID, ArrayList<String>> history = null;
    private UUIDMap uuidMap = null;

    public UserMapModifier(IEssentials ess) {
        super(ess);
    }

    @Inject(InjectionType.OVERRIDE)
    private void loadAllUsersAsync(final IEssentials ess) {
        VolatileEssentials.logger().info("Loading user names from the database!");
        this.ess.runTaskAsynchronously(new LoadAllUsersTask(keys, names, users, history, uuidMap));
    }

    @Override
    @Inject(InjectionType.OVERRIDE)
    public User getUser(final String name) {
        //VolatileEssentials.logger().info("Loading user by name '" + name + "' from database!");
        try {
            final String sanitizedName = StringUtil.safeString(name);
            if (names.containsKey(sanitizedName)) {
                final UUID uuid = names.get(sanitizedName);
                return getUser(uuid);
            }
            /* Remove useless FS check
            final File userFile = getUserFileFromString(sanitizedName);
            if (userFile.exists()) {
                ess.getLogger().info("Importing user " + name + " to usermap.");
                User user = new User(new OfflinePlayer(sanitizedName, ess.getServer()), ess);
                trackUUID(user.getBase().getUniqueId(), user.getName(), true);
                return user;
            }
            */
            return null;
        } catch (UncheckedExecutionException ex) {
            return null;
        }
    }

    @Override
    @Inject(InjectionType.OVERRIDE)
    public User load(final String stringUUID) throws Exception {
        VolatileEssentials.logger().info("Loading user by uuid '" + stringUUID + "' from database!");
        boolean create = stringUUID.endsWith("!create");
        UUID uuid = UUID.fromString(create ? stringUUID.split("!create")[0] : stringUUID);
        Player player = ess.getServer().getPlayer(uuid);
        if (player != null) {
            final User user = new User(player, ess);
            trackUUID(uuid, user.getName(), true);
            return user;
        }

        if (!create && Bukkit.isPrimaryThread()) {
            VolatileEssentials.logger().severe("Blocking mongodb call detected on the main thread! (check if user exists due to name user lookup)");
            new Throwable().printStackTrace();
        }

        if (create
                || VolatileEssentials.getInstance().getDatabase() == null
                || VolatileEssentials.getInstance().getDatabase().getCollection("users").find(Filters.eq("_id", uuid.toString())).first() != null) {
            player = new OfflinePlayer(uuid, ess.getServer());
            final User user = new User(player, ess);
            ((OfflinePlayer) player).setName(user.getLastAccountName());
            trackUUID(uuid, user.getName(), false);
            return user;
        }

        throw new Exception("User not found!");
    }
}
