import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.*;
import org.geysermc.mcprotocollib.network.factory.ClientNetworkSessionFactory;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.*;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.*;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomClickActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundPongPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.title.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.*;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.*;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Scriptable test bot for the PostMarkt test instance (offline mode, 127.0.0.1 only).
 *
 * <p>Reads one command per line from stdin (fed by ptest.sh through an append-only file) and logs everything
 * interesting to stdout: chat, action bar, titles, container contents (custom name + lore), own inventory,
 * dialogs. The command list is in testing/README.md and in {@link #HELP}.
 *
 * <p>Ported from the KaboofRevive/KaboofTotem/KaboofStats test bot; added for PostMarkt: configurable port (never
 * the live ports), dialog support (log the dialog packet, press its buttons with input values, raw custom clicks),
 * every container click type (number keys, F, Q, Ctrl+Q, double click, middle click, drag, outside clicks).
 */
public class QuestBot {
  static final String HELP = String.join("\n",
      "cmd <command>                 run a command (without /)",
      "tab <text>                    tab completion, '~' = trailing space",
      "click <slot> [mode]           container click; mode: left right shift shiftright num1..num9 offhand",
      "                              middle drop ctrldrop double outside outsideright (outside* ignore <slot>)",
      "drag <left|right|middle> <s1,s2,...>   drag the cursor stack over slots (quick craft)",
      "dump                          top inventory of the open container with names and lore",
      "close                         close the open container (like ESC)",
      "invdump                       own inventory as last sent by the server",
      "dialog                        print the last dialog packet (full NBT)",
      "dlgbtn <yes|no|exit|N> [k=v]  press a button of the last dialog; inputs default to their initial values;",
      "                              v: text, f:<float>, i:<int>, b:<0|1>, s:<text>, \"quoted text\", @repeat(n,c)",
      "customclick <ns:key> [k=v]    send a raw custom click action (forged dialog response)",
      "respawn | hotbar <0-8> | use | sneak on|off | move <x> <y> <z> | dig <x> <y> <z> [ms]",
      "place <x> <y> <z> [face]      use the held item on a block face",
      "creative <key> <slot>         send a stored stack (see invdump keys) as creative slot packet",
      "state                         container id/state, position, last dialog summary",
      "entities                      tracked entities with position; moves of non-players log as ENTITY~ lines",
      "rclick <id|type> [main|off]   right click an entity; type = last seen entity of that type, e.g. wandering_trader",
      "selecttrade <n>               choose offer n of the open merchant screen (MERCHANT lines log the offers)",
      "attack <id|type>              left click an entity",
      "rclickuse <id|type> [main|off] [main|off]   right click and use the held item in one tick, hands given separately",
      "air <x> <y> <z>               a position with nothing under the feet: a jump, a fall",
      "glide                         start gliding (wear an elytra and be in the air)",
      "useoff                        use the item in the off hand",
      "rclickplace <id|type> <x> <y> <z> [face]   right click, then use the held item on a block, in one write",
      "at <epochMillis> <command...>  run a command at a wall-clock moment (same-tick races across bots)",
      "clickid <cid> <slot> [mode]   click in an explicit container id (0 = own inventory: puts items on the cursor)",
      "border                        click on the window border (slot -1)",
      "bundlesel <slot> <idx>        select-bundle-item packet",
      "closeid <cid>                 raw container close",
      "fly <x> <y> <z>               move with onGround=false",
      "act drop|dropall|swap|release player action packets, they work while a menu is open",
      "fullinv                       every slot of the open view (top and bottom) with components",
      "spam <n> <slot> [mode]        n clicks in one burst; sweep <mode> <from> <to> [gapMs] one mode over a slot range",
      "cursor                        the cursor item the server last reported",
      "sleep <ms> | quit");

  static volatile int containerId = -1, stateId = 0, stateId0 = 0, seq = 1;
  static volatile ItemStack cursor;
  static volatile String title = "";
  static volatile ItemStack[] items = new ItemStack[0];
  static volatile double px, py, pz;
  static volatile int myId = -1;
  static volatile NbtMap lastDialog;
  /** Own inventory as the server sent it: "m<menu slot>" (container 0) and "i<inventory index>" (SetPlayerInventory). */
  static final Map<String, ItemStack> inv = new ConcurrentHashMap<>();
  /** One tracked entity as the server described it. */
  static final class Seen {
    final int id; final String type; final UUID uuid; double x, y, z; long lastLog;
    Seen(int id, String type, UUID uuid, double x, double y, double z) { this.id = id; this.type = type; this.uuid = uuid; this.x = x; this.y = y; this.z = z; }
  }
  static final Map<Integer, Seen> seen = new ConcurrentHashMap<>();
  /** Yaw and pitch of the position the bot still has to confirm with a move; sent on the next tick. */
  static final java.util.concurrent.atomic.AtomicReference<float[]> moveEcho = new java.util.concurrent.atomic.AtomicReference<>();

  static void added(ClientboundAddEntityPacket p) {
    String type = String.valueOf(p.getType()).toLowerCase(Locale.ROOT);
    seen.put(p.getEntityId(), new Seen(p.getEntityId(), type, p.getUuid(), p.getX(), p.getY(), p.getZ()));
    if (p.getEntityId() != myId) log("ENTITY+ id=" + p.getEntityId() + " type=" + type + " uuid=" + p.getUuid() + " at " + fmt(p.getX(), p.getY(), p.getZ()) + " dist=" + round(dist(p.getX(), p.getZ())));
  }

  static void moved(int id, Double x, Double y, Double z, boolean absolute) {
    Seen e = seen.get(id);
    if (e == null) return;
    if (absolute) { e.x = x; e.y = y; e.z = z; } else { e.x += x; e.y += y; e.z += z; }
    long now = System.currentTimeMillis();
    if (!e.type.equals("player") && now - e.lastLog >= 200) { e.lastLog = now; log("ENTITY~ id=" + id + " type=" + e.type + " at " + fmt(e.x, e.y, e.z) + " dist=" + round(dist(e.x, e.z))); }
  }

  static double dist(double x, double z) { return Math.hypot(x - px, z - pz); }
  static double round(double v) { return Math.round(v * 100) / 100.0; }
  static String fmt(double x, double y, double z) { return round(x) + "," + round(y) + "," + round(z); }

  /** The entity named by a numeric id or a type (the newest one of that type). */
  static Seen target(String which) {
    if (which.matches("\\d+")) { // an id this client never saw still works: a forged click on a hidden entity
      int id = Integer.parseInt(which);
      return seen.computeIfAbsent(id, k -> new Seen(k, "unseen", null, px, py, pz));
    }
    Seen last = null;
    for (Seen e : seen.values()) if (e.type.equals(which) && (last == null || e.id > last.id)) last = e;
    return last;
  }

  static Hand hand(String which) { return "off".equals(which) ? Hand.OFF_HAND : Hand.MAIN_HAND; }

  static void rightClick(Session s, Seen e, Hand h) {
    s.send(new ServerboundInteractPacket(e.id, h, Vector3d.from(0, 1, 0), false));
  }

  static final Map<Integer, Integer> DATA_PACKETS = new ConcurrentHashMap<>();
  /** PACK_MODE: accept (download + SHA-1 check, then DOWNLOADED/SUCCESSFULLY_LOADED), decline, fail (FAILED_DOWNLOAD), ignore. */
  static void packPush(Session ss, org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPushPacket rp) {
    // per-pack override: -Dpackmode.<pack uuid>=accept|decline|fail|ignore (e.g. the optional music pack)
    String mode = System.getProperty("packmode." + rp.getId(), System.getProperty("packmode", "accept"));
    log("PACK-PUSH id=" + rp.getId() + " url=" + rp.getUrl() + " hash=" + rp.getHash() + " required=" + rp.isRequired() + " prompt=" + plain(rp.getPrompt()) + " mode=" + mode);
    var R = org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus.class;
    java.util.function.Consumer<org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus> reply = st -> {
      ss.send(new org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundResourcePackPacket(rp.getId(), st)); log("PACK-REPLY " + st); };
    switch (mode) {
      case "ignore" -> { }
      case "decline" -> reply.accept(org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus.DECLINED);
      case "fail" -> { reply.accept(org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus.ACCEPTED); reply.accept(org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus.FAILED_DOWNLOAD); }
      default -> new Thread(() -> {
        reply.accept(org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus.ACCEPTED);
        try {
          long t0 = System.nanoTime();
          var client = java.net.http.HttpClient.newHttpClient();
          var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(rp.getUrl()))
            .header("X-Minecraft-Username", System.getProperty("ptest.bot", "bot")).header("X-Minecraft-Version", "26.3")
            .header("X-Minecraft-Pack-Format", "97").header("User-Agent", "Minecraft Java/26.3").build();
          var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
          byte[] body = resp.body();
          String sha = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(body));
          boolean ok = resp.statusCode() == 200 && sha.equalsIgnoreCase(rp.getHash());
          log("PACK-DOWNLOAD http=" + resp.statusCode() + " bytes=" + body.length + " sha1=" + sha + " match=" + ok + " in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
          if (!ok) { reply.accept(org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus.FAILED_DOWNLOAD); return; }
          reply.accept(org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus.DOWNLOADED);
          Thread.sleep(Long.getLong("packreloadms", 300));   // a real client reloads its resources here
          reply.accept(org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus.SUCCESSFULLY_LOADED);
        } catch (Exception e) {
          log("PACK-DOWNLOAD failed " + e);
          reply.accept(org.geysermc.mcprotocollib.protocol.data.game.ResourcePackStatus.FAILED_DOWNLOAD);
        }
      }, "pack-download").start();
    }
  }

  static void log(String s) {
    System.out.println("[" + java.time.LocalTime.now(java.time.ZoneOffset.UTC) + "Z] " + s);
    System.out.flush();
  }

  static int tabLogged = 0;
  /** -Dbot.clicks=true: chat lines show their click events. */
  static final boolean showClicks = true;

  static String plain(Component c) {
    if (c == null) return "null";
    StringBuilder b = new StringBuilder();
    flat(c, b);
    return b.toString();
  }

  static void flat(Component c, StringBuilder b) {
    if (c instanceof TextComponent t) b.append(t.content());
    else if (c instanceof TranslatableComponent t) {
      b.append('{').append(t.key());
      for (var a : t.arguments()) { b.append('|'); flat(a.asComponent(), b); }
      b.append('}');
    } else b.append(c.getClass().getSimpleName());
    if (showClicks && c.clickEvent() != null) b.append("[click:").append(c.clickEvent().action()).append(':').append(c.clickEvent().payload()).append(']');
    for (Component ch : c.children()) flat(ch, b);
  }

  /** Plain text of a text component that arrived as raw NBT (inside a dialog). */
  static String nbtText(Object o) {
    if (o == null) return "";
    if (o instanceof String s) return s;
    if (o instanceof NbtList<?> l) {
      StringBuilder b = new StringBuilder();
      for (Object e : l) b.append(nbtText(e));
      return b.toString();
    }
    if (o instanceof NbtMap m) {
      StringBuilder b = new StringBuilder();
      if (m.containsKey("")) b.append(m.getString("")); // plain string inside a mixed NBT list is wrapped as {"": text}
      if (m.containsKey("text")) b.append(m.getString("text"));
      if (m.containsKey("translate")) b.append('{').append(m.getString("translate")).append('}');
      if (m.containsKey("extra")) b.append(nbtText(m.get("extra")));
      return b.toString();
    }
    return String.valueOf(o);
  }

  static String describe(ItemStack it) {
    if (it == null) return "-";
    StringBuilder b = new StringBuilder("id=" + it.getId() + " x" + it.getAmount());
    DataComponents d = it.getDataComponentsPatch();
    if (d != null) {
      Component n = d.get(DataComponentTypes.CUSTOM_NAME);
      if (n != null) b.append(" name='").append(plain(n)).append("'");
      org.geysermc.mcprotocollib.protocol.data.game.item.component.CustomModelData cmd =
          d.get(DataComponentTypes.CUSTOM_MODEL_DATA);
      if (cmd != null && cmd.strings() != null && !cmd.strings().isEmpty()) b.append(" cmd=").append(cmd.strings());
      List<Component> lore = d.get(DataComponentTypes.LORE);
      if (lore != null) for (Component l : lore) b.append("\n        | ").append(plain(l));
    }
    return b.toString();
  }

  // ------------------------------------------------------------------------------------------------ dialogs

  static String stripType(String t) {
    return t == null ? "?" : t.replace("minecraft:", "");
  }

  /** One-line summary of a dialog packet: type, title, inputs and buttons with their actions. */
  static String dialogSummary(NbtMap d) {
    StringBuilder b = new StringBuilder("type=").append(stripType(d.getString("type", null)))
        .append(" title='").append(nbtText(d.get("title"))).append("'");
    Object body = d.get("body");
    if (body != null) b.append(" body='").append(bodyText(body)).append("'");
    b.append(" escape=").append(d.getBoolean("can_close_with_escape", true))
        .append(" after=").append(d.getString("after_action", "close"));
    for (NbtMap in : inputs(d)) {
      b.append("\n        input ").append(stripType(in.getString("type", null))).append(' ').append(in.getString("key", "?"))
          .append(" label='").append(nbtText(in.get("label"))).append("'");
      for (String k : List.of("initial", "max_length", "start", "end", "step", "width")) {
        if (in.containsKey(k)) b.append(' ').append(k).append('=').append(in.get(k));
      }
    }
    for (Map.Entry<String, NbtMap> e : buttons(d).entrySet()) {
      b.append("\n        button ").append(e.getKey()).append(" '").append(nbtText(e.getValue().get("label"))).append("' -> ")
          .append(actionText(e.getValue().getCompound("action", null)));
    }
    return b.toString();
  }

  static String bodyText(Object body) {
    if (body instanceof NbtList<?> l) {
      StringBuilder b = new StringBuilder();
      for (Object e : l) b.append(bodyText(e)).append(" / ");
      return b.toString();
    }
    if (body instanceof NbtMap m) {
      String t = stripType(m.getString("type", "plain_message"));
      if (t.equals("item")) return "[item " + m.getCompound("item", NbtMap.EMPTY).getString("id", "?") + "]";
      return nbtText(m.get("contents"));
    }
    return String.valueOf(body);
  }

  static String actionText(NbtMap a) {
    if (a == null) return "(no action)";
    String t = stripType(a.getString("type", null));
    return switch (t) {
      case "dynamic/custom" -> t + " " + a.getString("id", "?") + " additions=" + a.getCompound("additions", NbtMap.EMPTY);
      case "custom" -> t + " " + a.getString("id", "?") + " payload=" + a.get("payload");
      case "run_command" -> t + " /" + a.getString("command", "");
      case "dynamic/run_command" -> t + " template=" + a.getString("template", "");
      default -> t + " " + a;
    };
  }

  @SuppressWarnings("unchecked")
  static List<NbtMap> inputs(NbtMap d) {
    Object o = d.get("inputs");
    List<NbtMap> out = new ArrayList<>();
    if (o instanceof NbtList<?> l) for (Object e : l) if (e instanceof NbtMap m) out.add(m);
    return out;
  }

  /** Buttons by name: confirmation yes/no, notice action, multi_action 0..n, exit_action as "exit". */
  static LinkedHashMap<String, NbtMap> buttons(NbtMap d) {
    LinkedHashMap<String, NbtMap> out = new LinkedHashMap<>();
    for (String k : List.of("yes", "no", "action")) {
      if (d.get(k) instanceof NbtMap m) out.put(k, m);
    }
    if (d.get("actions") instanceof NbtList<?> l) {
      int i = 0;
      for (Object e : l) if (e instanceof NbtMap m) out.put(String.valueOf(i++), m);
    }
    if (d.get("exit_action") instanceof NbtMap m) out.put("exit", m);
    return out;
  }

  /** Splits "k=v k2=\"a b\"" into an ordered map; values keep their type prefix. */
  static LinkedHashMap<String, String> kv(String s) {
    LinkedHashMap<String, String> out = new LinkedHashMap<>();
    int i = 0, n = s.length();
    while (i < n) {
      while (i < n && s.charAt(i) == ' ') i++;
      int eq = s.indexOf('=', i);
      if (i >= n || eq < 0) break;
      String k = s.substring(i, eq).trim();
      int j = eq + 1;
      String v;
      if (j < n && s.charAt(j) == '"') {
        int end = s.indexOf('"', j + 1);
        if (end < 0) end = n;
        v = "s:" + s.substring(j + 1, end);
        i = Math.min(n, end + 1);
      } else {
        int end = s.indexOf(' ', j);
        if (end < 0) end = n;
        v = s.substring(j, end);
        i = end;
      }
      out.put(k, v);
    }
    return out;
  }

  static String expand(String v) {
    if (v.startsWith("@repeat(") && v.endsWith(")")) {
      String[] p = v.substring(8, v.length() - 1).split(",", 2);
      return (p.length > 1 ? p[1] : "x").repeat(Integer.parseInt(p[0].trim()));
    }
    return v;
  }

  /** Puts one value; the input's own type decides unless the value has an explicit type prefix. */
  static void putValue(NbtMapBuilder b, String key, String raw, String inputType) {
    if (raw.startsWith("f:")) b.putFloat(key, Float.parseFloat(raw.substring(2)));
    else if (raw.startsWith("i:")) b.putInt(key, Integer.parseInt(raw.substring(2)));
    else if (raw.startsWith("b:")) b.putByte(key, (byte) (raw.substring(2).equals("1") || raw.substring(2).equals("true") ? 1 : 0));
    else if (raw.startsWith("s:")) b.putString(key, expand(raw.substring(2)));
    else if ("number_range".equals(inputType)) b.putFloat(key, Float.parseFloat(raw));
    else if ("boolean".equals(inputType)) b.putByte(key, (byte) (raw.equals("1") || raw.equals("true") ? 1 : 0));
    else b.putString(key, expand(raw));
  }

  /** What a real client sends for an untouched input. */
  static void putDefault(NbtMapBuilder b, NbtMap in) {
    String key = in.getString("key", "?");
    switch (stripType(in.getString("type", null))) {
      case "text" -> b.putString(key, in.getString("initial", ""));
      case "number_range" -> {
        float start = in.getFloat("start", 0f), end = in.getFloat("end", 0f);
        b.putFloat(key, in.containsKey("initial") ? in.getFloat("initial") : (start + end) / 2f);
      }
      case "boolean" -> b.putByte(key, (byte) (in.getBoolean("initial", false) ? 1 : 0));
      case "single_option" -> {
        String chosen = null;
        if (in.get("options") instanceof NbtList<?> l) {
          for (Object e : l) {
            if (e instanceof NbtMap m) {
              if (chosen == null) chosen = m.getString("id", null);
              if (m.getBoolean("initial", false)) chosen = m.getString("id", null);
            } else if (e instanceof String s && chosen == null) chosen = s;
          }
        }
        b.putString(key, chosen == null ? "" : chosen);
      }
      default -> { }
    }
  }

  static void pressDialogButton(Session s, String which, String rest) {
    NbtMap d = lastDialog;
    if (d == null) { log("DLGBTN no dialog received yet"); return; }
    NbtMap button = buttons(d).get(which);
    if (button == null) { log("DLGBTN no button '" + which + "', have " + buttons(d).keySet()); return; }
    NbtMap action = button.getCompound("action", null);
    if (action == null) { log("DLGBTN button '" + which + "' has no action: a real client only closes the dialog"); return; }
    String type = stripType(action.getString("type", null));
    LinkedHashMap<String, String> given = kv(rest);
    switch (type) {
      case "dynamic/custom" -> {
        NbtMapBuilder b = action.getCompound("additions", NbtMap.EMPTY).toBuilder();
        Map<String, String> inputTypes = new HashMap<>();
        for (NbtMap in : inputs(d)) {
          inputTypes.put(in.getString("key", "?"), stripType(in.getString("type", null)));
          putDefault(b, in);
        }
        given.forEach((k, v) -> putValue(b, k, v, inputTypes.get(k)));
        NbtMap payload = b.build();
        String id = action.getString("id");
        s.send(new ServerboundCustomClickActionPacket(Key.key(id), payload));
        log("DLGBTN sent " + id + " payload=" + abbreviate(payload.toString()));
      }
      case "custom" -> {
        String id = action.getString("id");
        NbtMap payload = action.get("payload") instanceof NbtMap m ? m : NbtMap.EMPTY;
        s.send(new ServerboundCustomClickActionPacket(Key.key(id), payload));
        log("DLGBTN sent static " + id + " payload=" + abbreviate(payload.toString()));
      }
      case "run_command" -> {
        String c = action.getString("command", "").replaceFirst("^/", "");
        s.send(new ServerboundChatCommandPacket(c));
        log("DLGBTN ran /" + c);
      }
      default -> log("DLGBTN action type " + type + " not supported by the bot: " + action);
    }
  }

  static String abbreviate(String s) {
    return s.length() > 300 ? s.substring(0, 300) + "...(" + s.length() + " chars)" : s;
  }

  // ------------------------------------------------------------------------------------------------ main

  public static void main(String[] a) throws Exception {
    String name = a[0];
    int port = Integer.getInteger("port", 25572);
    if (port == 25565 || port == 25575) { // the LIVE server's game and RCON ports: never
      log("REFUSED port " + port + " belongs to the live server");
      System.exit(2);
    }
    var s = ClientNetworkSessionFactory.factory().setAddress("127.0.0.1", port).setProtocol(new MinecraftProtocol(name)).create();
    var ex = Executors.newSingleThreadScheduledExecutor();
    s.addListener(new SessionAdapter() {
      @Override public void packetReceived(Session ss, Packet p) {
        if (p instanceof org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPushPacket rp) {
          packPush(ss, rp); return;
        } else if (p instanceof org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundResourcePackPopPacket pop) {
          log("PACK-POP " + pop); return;
        } else if (p instanceof org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket) {
          log("CONFIG-FINISH (server ends configuration)"); return;
        } else if (p instanceof ClientboundSetEntityDataPacket md) {
          int n = DATA_PACKETS.merge(md.getEntityId(), 1, Integer::sum);
          if (n <= 3 || n % 20 == 0) log("ENTITY-DATA id=" + md.getEntityId() + " #" + n + " " + abbreviate(java.util.Arrays.toString(md.getMetadata())));
          return;
        }
        if (p instanceof ClientboundLoginPacket lp) {
          myId = lp.getEntityId(); log("LOGIN ok entityId=" + lp.getEntityId()); ss.send(ServerboundPlayerLoadedPacket.INSTANCE);
          ex.scheduleAtFixedRate(() -> { if (ss.isConnected()) { // one position packet per tick, like a client: a second one is a kick
            float[] echo = moveEcho.getAndSet(null);
            if (echo != null) ss.send(new ServerboundMovePlayerPosRotPacket(true, false, px, py, pz, echo[0], echo[1]));
            ss.send(ServerboundClientTickEndPacket.INSTANCE);
          } }, 50, 50, TimeUnit.MILLISECONDS);
        } else if (p instanceof ClientboundPingPacket pp) { ss.send(new ServerboundPongPacket(pp.getId()));
        } else if (p instanceof ClientboundPlayerPositionPacket pp) {
          px = pp.getPosition().getX(); py = pp.getPosition().getY(); pz = pp.getPosition().getZ();
          log("TELEPORT " + pp.getPosition());
          ss.send(new ServerboundAcceptTeleportationPacket(pp.getId(), px, py, pz, pp.getYRot(), pp.getXRot()));
          moveEcho.set(new float[] {pp.getYRot(), pp.getXRot()});
        } else if (p instanceof ClientboundSystemChatPacket c) { log((c.isOverlay() ? "ACTIONBAR " : "CHAT ") + plain(c.getContent()));
        } else if (p instanceof org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRecipeBookAddPacket rb) {
          for (var e : rb.getEntries()) { // the recipe book: only the ids of the casino's shop counter are of interest
            String d = e.contents().toString();
            if (d.contains("chip_shop") || d.contains("outfit_")) { var tag = java.util.regex.Pattern.compile("outfit_[a-z]+_[a-z]+").matcher(d); log("RECIPE id=" + e.contents().id() + " " + (tag.find() ? tag.group() + " " : "") + d.substring(0, Math.min(900, d.length()))); }
          }
        } else if (p instanceof ClientboundSetActionBarTextPacket c) { log("ACTIONBAR " + plain(c.getText()));
        } else if (p instanceof ClientboundSetTitleTextPacket c) { log("TITLE " + plain(c.getText()));
        } else if (p instanceof ClientboundTabListPacket t) { if (tabLogged++ < 1) log("TAB\n" + plain(t.getHeader()) + "\n----\n" + plain(t.getFooter()));
        } else if (p instanceof ClientboundCooldownPacket c) { log("COOLDOWN " + c.getCooldownGroup().asString() + " ticks=" + c.getCooldownTicks());
        } else if (p instanceof ClientboundRespawnPacket r) { log("RESPAWN " + r.getCommonPlayerSpawnInfo().getWorldName()); ss.send(ServerboundPlayerLoadedPacket.INSTANCE);
        } else if (p instanceof ClientboundSetHealthPacket h) { log("HEALTH " + h.getHealth() + " food=" + h.getFood());
        } else if (p instanceof ClientboundPlayerCombatKillPacket k) { log("DIED " + plain(k.getMessage()));
        } else if (p instanceof ClientboundMerchantOffersPacket mo) {
          log("MERCHANT container=" + mo.getContainerId() + " level=" + mo.getVillagerLevel() + " xp=" + mo.getVillagerXp() + " progress=" + mo.isShowProgress() + " restock=" + mo.isCanRestock() + " offers=" + mo.getOffers());
        } else if (p instanceof ClientboundOpenScreenPacket o) {
          containerId = o.getContainerId(); title = plain(o.getTitle()); log("OPEN id=" + containerId + " type=" + o.getType() + " title='" + title + "'");
        } else if (p instanceof ClientboundContainerSetContentPacket c) {
          if (c.getContainerId() == containerId) { items = c.getItems(); stateId = c.getStateId(); cursor = c.getCarriedItem(); log("CONTENT id=" + containerId + " state=" + stateId + " n=" + items.length + " carried=" + (cursor == null ? "empty" : describe(cursor).replace("\n", " "))); }
          else if (c.getContainerId() == 0) { stateId0 = c.getStateId(); for (int i = 0; i < c.getItems().length; i++) { if (c.getItems()[i] != null) inv.put("m" + i, c.getItems()[i]); else inv.remove("m" + i); } log("CONTENT0 n=" + c.getItems().length); }
        } else if (p instanceof ClientboundSetPlayerInventoryPacket c) {
          if (c.getContents() != null) inv.put("i" + c.getSlot(), c.getContents()); else inv.remove("i" + c.getSlot());
          log("INV i" + c.getSlot() + " " + (c.getContents() == null ? "empty" : describe(c.getContents())));
        } else if (p instanceof ClientboundContainerSetSlotPacket c && c.getContainerId() == 0) {
          stateId0 = c.getStateId();
          if (c.getItem() != null) inv.put("m" + c.getSlot(), c.getItem()); else inv.remove("m" + c.getSlot());
          log("INV m" + c.getSlot() + " " + (c.getItem() == null ? "empty" : describe(c.getItem())));
        } else if (p instanceof ClientboundContainerSetSlotPacket c) {
          if (c.getContainerId() == containerId) {
            stateId = c.getStateId();
            if (c.getSlot() >= 0 && c.getSlot() < items.length) items[c.getSlot()] = c.getItem();
            log("SLOT id=" + containerId + " slot=" + c.getSlot() + " " + (c.getItem() == null ? "empty" : describe(c.getItem()).replace("\n", " ")));
          }
        } else if (p instanceof ClientboundSetCursorItemPacket c) { cursor = c.getContents(); log("CURSOR " + (cursor == null ? "empty" : describe(cursor).replace("\n", " ")));
        } else if (p instanceof ClientboundContainerClosePacket c) { log("CLOSE " + c.getContainerId()); containerId = -1;
        } else if (p instanceof ClientboundShowDialogGamePacket d) {
          if (d.getDialog().isCustom()) { lastDialog = d.getDialog().custom(); log("DIALOG " + dialogSummary(lastDialog)); }
          else log("DIALOG registry id=" + d.getDialog().id());
        } else if (p instanceof ClientboundClearDialogPacket) { log("DIALOG-CLEAR");
        } else if (p instanceof org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket e) {
          if (e.getEntityId() == myId) log("EFFECT+ " + e.getEffect() + " amp=" + e.getAmplifier() + " dur=" + e.getDuration());
        } else if (p instanceof org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveMobEffectPacket e) {
          if (e.getEntityId() == myId) log("EFFECT- " + e.getEffect());
        } else if (p instanceof ClientboundCommandSuggestionsPacket t) {
          log("SUGGEST id=" + t.getTransactionId() + " " + String.join(",", t.getMatches()));
        } else if (p instanceof ClientboundAddEntityPacket e) { added(e);
        } else if (p instanceof ClientboundRemoveEntitiesPacket r) {
          for (int id : r.getEntityIds()) { Seen e = seen.remove(id); log("ENTITY- id=" + id + " type=" + (e == null ? "?" : e.type) + (e == null ? "" : " at " + fmt(e.x, e.y, e.z))); }
        } else if (p instanceof ClientboundMoveEntityPosPacket m) {
          if (m.getStepCount() > 0) m.getSteps().forEach(d -> moved(m.getEntityId(), d.moveX(), d.moveY(), d.moveZ(), false));
          else moved(m.getEntityId(), m.getMoveX(), m.getMoveY(), m.getMoveZ(), false);
        } else if (p instanceof ClientboundMoveEntityPosRotPacket m) {
          if (m.getStepCount() > 0) m.getSteps().forEach(d -> moved(m.getEntityId(), d.moveX(), d.moveY(), d.moveZ(), false));
          else moved(m.getEntityId(), m.getMoveX(), m.getMoveY(), m.getMoveZ(), false);
        } else if (p instanceof ClientboundTeleportEntityPacket t) { moved(t.getId(), t.getPosition().getX(), t.getPosition().getY(), t.getPosition().getZ(), true);
        } else if (p instanceof ClientboundEntityPositionSyncPacket t) {
          Vector3d at = t.isStepped() ? t.getSteps().getLast().position() : t.getEndPosition();
          moved(t.getId(), at.getX(), at.getY(), at.getZ(), true);
        } else if (p instanceof ClientboundSoundPacket a) { log("SOUND " + a.getSound().getName() + " cat=" + a.getCategory() + " at " + fmt(a.getX(), a.getY(), a.getZ()));
        } else if (p instanceof org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStopSoundPacket st) { log("STOP-SOUND " + st.getSound() + " cat=" + st.getCategory());
        } else if (p instanceof ClientboundSoundEntityPacket a) { log("SOUND-ENTITY " + a.getSound().getName() + " entity=" + a.getEntityId());
        } else if (p instanceof ClientboundLevelParticlesPacket a) { log("PARTICLE " + a.getParticle().getType() + " n=" + a.getAmount() + " at " + fmt(a.getX(), a.getY(), a.getZ()));
        } else if (p instanceof ClientboundBlockUpdatePacket b) { log("BLOCK " + b.getEntry().getPosition() + " -> " + b.getEntry().getBlock());
        } else if (p instanceof ClientboundSectionBlocksUpdatePacket b) { // several blocks in one tick, e.g. a tripwire and its hooks
          for (var e : b.getEntries()) log("BLOCK " + e.getPosition() + " -> " + e.getBlock());
        } else if (p instanceof ClientboundSetPlayerTeamPacket t) {
          log("TEAM " + t.getTeamName() + " " + t.getAction() + " collision=" + t.getCollisionRule() + " players=" + (t.getPlayers() == null ? "-" : Arrays.toString(t.getPlayers())));
        } else if (p instanceof ClientboundDisconnectPacket d) { log("DISCONNECT " + plain(d.getReason()));
        } else if (p.getClass().getSimpleName().contains("Command")) { log("PACKET " + p.getClass().getSimpleName()); }
      }
      @Override public void disconnected(DisconnectedEvent e) { log("DISCONNECTED " + plain(e.getReason()) + " " + e.getCause()); System.exit(0); }
    });
    s.connect(true);
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line = in.readLine()) != null) {
      line = line.trim();
      if (line.isEmpty() || line.startsWith("#")) continue;
      String[] w = line.split("\\s+");
      log(">> " + abbreviate(line));
      if (w[0].equals("at") && w.length > 2) { // at <epochMillis> <command...>: wall-clock start, for same-tick races
        long due = Long.parseLong(w[1]);
        long left;
        while ((left = due - System.currentTimeMillis()) > 0) { if (left > 2) Thread.sleep(left - 1); else Thread.onSpinWait(); }
        line = line.substring(line.indexOf(w[2], line.indexOf(w[1]) + w[1].length()));
        w = line.split("\\s+");
      }
      try {
        switch (w[0]) {
          case "cmd" -> s.send(new ServerboundChatCommandPacket(line.substring(4).replaceFirst("^/", "")));
          case "say" -> s.send(new org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket(line.substring(4), System.currentTimeMillis(), 0L, null, 0, new java.util.BitSet(), 0)); // unsigned chat line (offline mode)
          case "tab" -> s.send(new ServerboundCommandSuggestionPacket(seq++, "/" + line.substring(4).replaceFirst("^/", "").replace("~", " ")));
          case "click" -> click(s, Integer.parseInt(w[1]), w.length > 2 ? w[2] : "left");
          case "placerecipe" -> s.send(new org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundPlaceRecipePacket(containerId, Integer.parseInt(w[1]), w.length > 2)); // a click on a recipe-book entry
          case "clickid" -> { // clickid <container id> <slot> [mode]: click in an explicit container (0 = own inventory screen)
            int saved = containerId, savedState = stateId;
            int cid = Integer.parseInt(w[1]);
            containerId = cid; if (cid == 0) stateId = stateId0;
            click(s, Integer.parseInt(w[2]), w.length > 3 ? w[3] : "left");
            containerId = saved; stateId = savedState;
          }
          case "border" -> s.send(new ServerboundContainerClickPacket(containerId, stateId, -1, ContainerActionType.CLICK_ITEM, ClickItemAction.LEFT_CLICK, null, new HashMap<>()));
          case "bundlesel" -> s.send(new ServerboundSelectBundleItemPacket(Integer.parseInt(w[1]), Integer.parseInt(w[2])));
          case "closeid" -> s.send(new ServerboundContainerClosePacket(Integer.parseInt(w[1])));
          case "look" -> s.send(new ServerboundMovePlayerPosRotPacket(true, false, px, py, pz, Float.parseFloat(w[1]), Float.parseFloat(w[2])));
          case "fly" -> { // fly <x> <y> <z>: move with onGround=false
            px = Double.parseDouble(w[1]); py = Double.parseDouble(w[2]); pz = Double.parseDouble(w[3]);
            s.send(new ServerboundMovePlayerPosRotPacket(false, false, px, py, pz, 0f, 90f));
          }
          case "act" -> { // act drop|dropall|swap|release: PlayerAction packets (work while a menu is open)
            PlayerAction a2 = switch (w[1]) {
              case "drop" -> PlayerAction.DROP_ITEM;
              case "dropall" -> PlayerAction.DROP_ITEM_STACK;
              case "swap" -> PlayerAction.SWAP_HANDS;
              default -> PlayerAction.RELEASE_USE_ITEM;
            };
            s.send(new ServerboundPlayerActionPacket(a2, Vector3i.ZERO, Direction.DOWN, seq++));
          }
          case "cursor" -> log("CURSOR-NOW " + (cursor == null ? "empty" : describe(cursor).replace("\n", " ")));
          case "fullinv" -> { // every slot of the open view, also the bottom part, with the component patch
            log("FULLINV id=" + containerId + " n=" + items.length);
            for (int i = 0; i < items.length; i++) if (items[i] != null) System.out.println("  fslot " + i + ": " + describe(items[i]).replace("\n", " ") + " comps=" + items[i].getDataComponentsPatch());
            System.out.flush();
          }
          case "spam" -> { // spam <n> <slot> [mode]: n clicks in one burst (usually the same tick)
            int n = Integer.parseInt(w[1]);
            for (int i = 0; i < n; i++) click(s, Integer.parseInt(w[2]), w.length > 3 ? w[3] : "left");
          }
          case "sweep" -> { // sweep <mode> <from> <to> [gapMs]: one mode over a slot range without per-line logging
            int from = Integer.parseInt(w[2]), to = Integer.parseInt(w[3]); long gap = w.length > 4 ? Long.parseLong(w[4]) : 5;
            for (int i = from; i <= to; i++) { click(s, i, w[1]); if (gap > 0) Thread.sleep(gap); }
            log("SWEEP-DONE " + w[1] + " " + from + ".." + to);
          }
          case "drag" -> {
            boolean right = w[1].equals("right"), middle = w[1].equals("middle");
            SpreadItemAction begin = middle ? SpreadItemAction.MIDDLE_MOUSE_BEGIN_DRAG : right ? SpreadItemAction.RIGHT_MOUSE_BEGIN_DRAG : SpreadItemAction.LEFT_MOUSE_BEGIN_DRAG;
            SpreadItemAction add = middle ? SpreadItemAction.MIDDLE_MOUSE_ADD_SLOT : right ? SpreadItemAction.RIGHT_MOUSE_ADD_SLOT : SpreadItemAction.LEFT_MOUSE_ADD_SLOT;
            SpreadItemAction end = middle ? SpreadItemAction.MIDDLE_MOUSE_END_DRAG : right ? SpreadItemAction.RIGHT_MOUSE_END_DRAG : SpreadItemAction.LEFT_MOUSE_END_DRAG;
            s.send(new ServerboundContainerClickPacket(containerId, stateId, -999, ContainerActionType.SPREAD_ITEM, begin, null, new HashMap<>()));
            for (String slot : w[2].split(",")) {
              s.send(new ServerboundContainerClickPacket(containerId, stateId, Integer.parseInt(slot), ContainerActionType.SPREAD_ITEM, add, null, new HashMap<>()));
            }
            s.send(new ServerboundContainerClickPacket(containerId, stateId, -999, ContainerActionType.SPREAD_ITEM, end, null, new HashMap<>()));
          }
          case "dump" -> {
            int top = items.length - 36;
            log("DUMP id=" + containerId + " title='" + title + "' top=" + top);
            for (int i = 0; i < Math.max(0, top); i++) {
              ItemStack it = items[i];
              if (it == null) continue;
              System.out.println("  slot " + i + ": " + describe(it));
            }
            System.out.flush();
            log("DUMP-END");
          }
          case "close" -> { s.send(new ServerboundContainerClosePacket(containerId)); containerId = -1; }
          case "respawn" -> s.send(new ServerboundClientCommandPacket(ClientCommand.PERFORM_RESPAWN));
          case "hotbar" -> s.send(new ServerboundSetCarriedItemPacket(Integer.parseInt(w[1])));
          case "dig" -> {
            Vector3i pos = Vector3i.from(Integer.parseInt(w[1]), Integer.parseInt(w[2]), Integer.parseInt(w[3]));
            long ms = w.length > 4 ? Long.parseLong(w[4]) : 1500;
            s.send(new ServerboundPlayerActionPacket(PlayerAction.START_DIGGING, pos, Direction.UP, seq++));
            Thread.sleep(ms);
            s.send(new ServerboundPlayerActionPacket(PlayerAction.FINISH_DIGGING, pos, Direction.UP, seq++));
          }
          case "place" -> {
            Vector3i pos = Vector3i.from(Integer.parseInt(w[1]), Integer.parseInt(w[2]), Integer.parseInt(w[3]));
            Direction face = Direction.valueOf(w.length > 4 ? w[4].toUpperCase() : "UP");
            s.send(new ServerboundUseItemOnPacket(pos, face, Hand.MAIN_HAND, 0.5f, 1f, 0.5f, false, false, seq++));
          }
          case "move" -> {
            px = Double.parseDouble(w[1]); py = Double.parseDouble(w[2]); pz = Double.parseDouble(w[3]);
            s.send(new ServerboundMovePlayerPosRotPacket(true, false, px, py, pz, 0f, 90f));
          }
          case "air" -> { // a position with nothing under the feet: a jump, a fall, the start of a glide
            px = Double.parseDouble(w[1]); py = Double.parseDouble(w[2]); pz = Double.parseDouble(w[3]);
            s.send(new ServerboundMovePlayerPosRotPacket(false, false, px, py, pz, 0f, 90f));
          }
          case "selecttrade" -> s.send(new ServerboundSelectTradePacket(Integer.parseInt(w[1]))); // selecttrade <offer index>: a merchant screen's offer
          case "glide" -> s.send(new ServerboundPlayerCommandPacket(myId, org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState.START_ELYTRA_FLYING));
          case "sneak" -> s.send(new org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket(false, false, false, false, false, w[1].equals("on"), false));
          case "use" -> s.send(new ServerboundUseItemPacket(Hand.MAIN_HAND, seq++, 0f, 90f));
          case "useoff" -> s.send(new ServerboundUseItemPacket(Hand.OFF_HAND, seq++, 0f, 90f));
          case "entities" -> seen.values().forEach(e -> log("ENTITY id=" + e.id + " type=" + e.type + " at " + fmt(e.x, e.y, e.z) + " dist=" + round(dist(e.x, e.z))));
          case "rclick" -> {
            Seen e = target(w[1]);
            log("RCLICK " + (e == null ? "NOTHING " + w[1] : e.id + " " + e.type + " dist=" + round(dist(e.x, e.z))));
            if (e != null) rightClick(s, e, hand(w.length > 2 ? w[2] : "main"));
          }
          case "attack" -> {
            Seen e = target(w[1]);
            log("ATTACK " + (e == null ? "NOTHING " + w[1] : e.id + " " + e.type + " dist=" + round(dist(e.x, e.z))));
            if (e != null) s.send(new ServerboundAttackPacket(e.id));
          }
          case "rclickplace" -> {
            Seen e = target(w[1]);
            log("RCLICKPLACE " + (e == null ? "NOTHING " + w[1] : e.id + " " + e.type));
            if (e != null) {
              rightClick(s, e, Hand.MAIN_HAND);
              s.send(new ServerboundUseItemOnPacket(Vector3i.from(Integer.parseInt(w[2]), Integer.parseInt(w[3]), Integer.parseInt(w[4])),
                  Direction.valueOf(w.length > 5 ? w[5].toUpperCase() : "UP"), Hand.MAIN_HAND, 0.5f, 1f, 0.5f, false, false, seq++));
            }
          }
          case "rclickuse" -> { // the worst case of the item lock: click and use right behind it, in one write
            Seen e = target(w[1]);
            log("RCLICKUSE " + (e == null ? "NOTHING " + w[1] : e.id + " " + e.type + " dist=" + round(dist(e.x, e.z))));
            if (e != null) {
              rightClick(s, e, hand(w.length > 2 ? w[2] : "main"));
              s.send(new ServerboundUseItemPacket(hand(w.length > 3 ? w[3] : "main"), seq++, 0f, 90f));
            }
          }
          case "invdump" -> inv.forEach((k, v) -> log("HAVE " + k + " " + describe(v) + "\n        components=" + v.getDataComponentsPatch()));
          case "creative" -> { // creative <stored key> <menu slot>: put a copy of a stored stack into a slot, like a client-built creative stack
            ItemStack it = inv.get(w[1]);
            log("CREATIVE " + w[1] + " -> m" + w[2] + " " + (it == null ? "NOTHING STORED" : describe(it)));
            if (it != null) s.send(new ServerboundSetCreativeModeSlotPacket((short) Integer.parseInt(w[2]), it));
          }
          case "dialog" -> log("DIALOG-NBT " + (lastDialog == null ? "none" : lastDialog));
          case "dlgbtn" -> pressDialogButton(s, w[1], line.substring(line.indexOf(w[1]) + w[1].length()));
          case "customclick" -> {
            String rest = line.substring(line.indexOf(w[1]) + w[1].length());
            NbtMapBuilder b = NbtMap.builder();
            kv(rest).forEach((k, v) -> putValue(b, k, v, null));
            NbtMap payload = b.build();
            s.send(new ServerboundCustomClickActionPacket(Key.key(w[1]), payload));
            log("CUSTOMCLICK sent " + w[1] + " payload=" + abbreviate(payload.toString()));
          }
          case "customclick0" -> { s.send(new ServerboundCustomClickActionPacket(Key.key(w[1]), null)); log("CUSTOMCLICK0 sent " + w[1]); }
          case "state" -> log("STATE container=" + containerId + " state=" + stateId + " title='" + title + "' pos=" + px + "," + py + "," + pz
              + (lastDialog == null ? "" : "\n        last dialog: " + dialogSummary(lastDialog)));
          case "help" -> log("HELP\n" + HELP);
          case "sleep" -> Thread.sleep(Long.parseLong(w[1]));
          case "quit" -> { s.disconnect("bye"); Thread.sleep(500); System.exit(0); }
          default -> log("unknown command " + w[0] + " (try help)");
        }
      } catch (Exception e) { log("ERR " + e); }
    }
    // stdin closed: keep running until killed
    Thread.sleep(Long.MAX_VALUE);
  }

  static void click(Session s, int slot, String mode) {
    ContainerActionType type;
    ContainerAction act;
    switch (mode) {
      case "right" -> { type = ContainerActionType.CLICK_ITEM; act = ClickItemAction.RIGHT_CLICK; }
      case "shift" -> { type = ContainerActionType.SHIFT_CLICK_ITEM; act = ShiftClickItemAction.LEFT_CLICK; }
      case "shiftright" -> { type = ContainerActionType.SHIFT_CLICK_ITEM; act = ShiftClickItemAction.RIGHT_CLICK; }
      case "offhand" -> { type = ContainerActionType.MOVE_TO_HOTBAR_SLOT; act = MoveToHotbarAction.OFF_HAND; }
      case "middle" -> { type = ContainerActionType.CREATIVE_GRAB_MAX_STACK; act = CreativeGrabAction.GRAB; }
      case "drop" -> { type = ContainerActionType.DROP_ITEM; act = DropItemAction.DROP_FROM_SELECTED; }
      case "ctrldrop" -> { type = ContainerActionType.DROP_ITEM; act = DropItemAction.DROP_SELECTED_STACK; }
      case "double" -> { type = ContainerActionType.FILL_STACK; act = FillStackAction.FILL; }
      case "outside" -> { type = ContainerActionType.CLICK_ITEM; act = ClickItemAction.LEFT_CLICK; slot = -999; }
      case "outsideright" -> { type = ContainerActionType.CLICK_ITEM; act = ClickItemAction.RIGHT_CLICK; slot = -999; }
      default -> {
        if (mode.matches("num[1-9]")) {
          type = ContainerActionType.MOVE_TO_HOTBAR_SLOT;
          act = MoveToHotbarAction.values()[Integer.parseInt(mode.substring(3)) - 1];
        } else {
          type = ContainerActionType.CLICK_ITEM;
          act = ClickItemAction.LEFT_CLICK;
        }
      }
    }
    s.send(new ServerboundContainerClickPacket(containerId, stateId, slot, type, act, null, new HashMap<>()));
  }
}
