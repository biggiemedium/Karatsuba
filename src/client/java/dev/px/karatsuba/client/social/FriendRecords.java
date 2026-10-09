package dev.px.karatsuba.client.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.px.core.Core;
import dev.px.core.config.ConfigSection;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.social.SocialService;
import dev.px.karatsuba.adapter.game.player.PlayerList;
import dev.px.karatsuba.client.config.AutoSave;

import java.util.HashMap;
import java.util.Map;

/**
 * What the client remembers about each friend beyond Core's name and alias: a
 * note, when they were added, and when and where they were last seen.
 *
 * <p>Kept here rather than in Core's {@code Friend}, which is the shared library's
 * and stays as small as a targeting check needs. Saved beside the friends list,
 * shared by every profile, since who a player is does not change with the
 * settings you play on. Keyed like Core's list, by the lowercased name.
 *
 * <p>"Last seen" is the server's player list, read every two seconds: a friend on
 * the same server counts as seen, wherever they are on it.
 */
public final class FriendRecords implements ConfigSection {

    public static final String ID = "friend-records";

    /** Ticks between looks at the player list. */
    private static final int LOOK_EVERY = 40;

    private static final Map<String, Record> RECORDS = new HashMap<>();
    private static int ticks;

    /** One friend's extra data. Times are epoch milliseconds, zero when unknown. */
    public static final class Record {

        private String note = "";
        private long added;
        private long lastSeen;
        private String lastServer = "";

        public String note() {
            return note;
        }

        public void note(String text) {
            this.note = text == null ? "" : text;
            AutoSave.markDirty();
        }

        public long added() {
            return added;
        }

        public long lastSeen() {
            return lastSeen;
        }

        public String lastServer() {
            return lastServer;
        }
    }

    private FriendRecords() {
    }

    /** Registers the section and starts watching the player list. Before {@code Core.start()}. */
    public static void install() {
        Core.config().register(new FriendRecords());
        Core.bus().on(TickEvent.class, event -> {
            if (++ticks % LOOK_EVERY == 0) {
                look();
            }
        });
    }

    /** @return the record for a player, made empty if there is none yet */
    public static Record of(String name) {
        return RECORDS.computeIfAbsent(SocialService.key(name), key -> new Record());
    }

    /** Marks a player as added now: from the GUI or a command. */
    public static void added(String name) {
        Record record = of(name);
        if (record.added == 0L) {
            record.added = System.currentTimeMillis();
            AutoSave.markDirty();
        }
    }

    /** Forgets a removed friend's record, unless it is put back (undo hands it back). */
    public static Record forget(String name) {
        Record removed = RECORDS.remove(SocialService.key(name));
        AutoSave.markDirty();
        return removed;
    }

    /** Restores a record taken by {@link #forget}, for an undone removal. */
    public static void restore(String name, Record record) {
        if (record != null) {
            RECORDS.put(SocialService.key(name), record);
            AutoSave.markDirty();
        }
    }

    /** Marks every friend on the server's player list as seen now, here. */
    private static void look() {
        if (!Core.platform().isInGame()) {
            return;
        }
        String host = Core.server().getHost();
        String server = host == null ? "" : host;
        long now = System.currentTimeMillis();
        for (String name : PlayerList.names()) {
            if (!Core.social().isFriend(name)) {
                continue;
            }
            Record record = of(name);
            boolean moved = !server.equals(record.lastServer) || record.lastSeen == 0L;
            record.lastSeen = now;
            record.lastServer = server;
            // A new sighting is worth a save; the clock ticking on is saved with the next one.
            if (moved) {
                AutoSave.markDirty();
            }
        }
    }

    // ---------------------------------------------------------------- config

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public JsonObject save() {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Record> entry : RECORDS.entrySet()) {
            Record record = entry.getValue();
            if (!Core.social().isFriend(entry.getKey())) {
                continue;
            }
            JsonObject node = new JsonObject();
            if (!record.note.isEmpty()) {
                node.addProperty("note", record.note);
            }
            node.addProperty("added", record.added);
            node.addProperty("lastSeen", record.lastSeen);
            if (!record.lastServer.isEmpty()) {
                node.addProperty("lastServer", record.lastServer);
            }
            json.add(entry.getKey(), node);
        }
        return json;
    }

    @Override
    public void load(JsonObject json) {
        RECORDS.clear();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject node = entry.getValue().getAsJsonObject();
            Record record = new Record();
            record.note = node.has("note") ? node.get("note").getAsString() : "";
            record.added = node.has("added") ? node.get("added").getAsLong() : 0L;
            record.lastSeen = node.has("lastSeen") ? node.get("lastSeen").getAsLong() : 0L;
            record.lastServer = node.has("lastServer") ? node.get("lastServer").getAsString() : "";
            RECORDS.put(entry.getKey(), record);
        }
    }
}
