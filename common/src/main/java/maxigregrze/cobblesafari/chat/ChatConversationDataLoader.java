package maxigregrze.cobblesafari.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import maxigregrze.cobblesafari.CobbleSafari;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads {@code data/<ns>/chat_conversation/*.json} into {@link ChatConversationRegistry}
 *.
 *
 * <p><b>Strict validation:</b> any structural error logs an explicit reason to the server log and
 * <em>aborts the loading of that specific JSON</em> (the file is skipped, others keep loading). No
 * partially-valid conversation is ever registered. Referenced advancements / loot tables / offer
 * templates are <em>not</em> checked here (resolved at runtime).
 */
public final class ChatConversationDataLoader {

    private ChatConversationDataLoader() {}

    private static final String DATA_DIR = "chat_conversation";
    private static final int CURRENT_VERSION = 1;
    private static final Pattern SAFE_ID = Pattern.compile("^[a-z0-9_]+$");

    public static void load(MinecraftServer server) {
        ChatConversationRegistry.clear();
        ResourceManager manager = server.getResourceManager();
        Map<ResourceLocation, Resource> resources =
                manager.listResources(DATA_DIR, id -> id.getPath().endsWith(".json"));

        int loaded = 0;
        int skipped = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open())) {
                ChatConversationDefinition def = parse(fileId, reader);
                if (def == null) {
                    skipped++;
                    continue;
                }
                ChatConversationRegistry.register(def);
                loaded++;
            } catch (Exception e) {
                CobbleSafari.LOGGER.error("[Chat] {} : failed to read/parse, skipped", fileId, e);
                skipped++;
            }
        }
        CobbleSafari.LOGGER.info("Loaded {} chat conversations ({} skipped)", loaded, skipped);
    }

    /** Logs the reason and returns {@code null} (= abort this JSON) on any structural error. */
    private static ChatConversationDefinition parse(ResourceLocation fileId, InputStreamReader reader) {
        JsonElement root = JsonParser.parseReader(reader);
        if (!root.isJsonObject()) {
            return reject(fileId, "not a JSON object");
        }
        JsonObject json = root.getAsJsonObject();

        // 2. schemaVersion
        if (!json.has("schemaVersion") || !json.get("schemaVersion").getAsJsonPrimitive().isNumber()) {
            return reject(fileId, "missing/invalid schemaVersion");
        }
        int schemaVersion = json.get("schemaVersion").getAsInt();
        if (schemaVersion > CURRENT_VERSION) {
            return reject(fileId, "unsupported schemaVersion " + schemaVersion);
        }

        // 3. id
        if (!json.has("id")) {
            return reject(fileId, "missing id");
        }
        String id = json.get("id").getAsString();
        if (id.isEmpty() || !SAFE_ID.matcher(id).matches()) {
            return reject(fileId, "invalid id '" + id + "' (expected ^[a-z0-9_]+$)");
        }

        // 4. displayName
        if (!json.has("displayName") || json.get("displayName").getAsString().isEmpty()) {
            return reject(fileId, "missing displayName");
        }
        String displayName = json.get("displayName").getAsString();

        // 5. displayPriority
        if (!json.has("displayPriority") || !json.get("displayPriority").getAsJsonPrimitive().isNumber()) {
            return reject(fileId, "missing/invalid displayPriority");
        }
        int displayPriority = json.get("displayPriority").getAsInt();

        // 6. textureFile
        if (!json.has("textureFile")) {
            return reject(fileId, "missing textureFile");
        }
        String textureFile = json.get("textureFile").getAsString();
        if (!SAFE_ID.matcher(textureFile).matches()) {
            return reject(fileId, "invalid textureFile '" + textureFile + "' (expected ^[a-z0-9_]+$)");
        }

        // 7-8. unlockedFromStart + unlockingAdvancement
        if (!json.has("unlockedFromStart") || !json.get("unlockedFromStart").getAsJsonPrimitive().isBoolean()) {
            return reject(fileId, "missing unlockedFromStart");
        }
        boolean unlockedFromStart = json.get("unlockedFromStart").getAsBoolean();
        String unlockingAdvancement =
                json.has("unlockingAdvancement") ? json.get("unlockingAdvancement").getAsString() : null;
        if (!unlockedFromStart) {
            if (unlockingAdvancement == null || ResourceLocation.tryParse(unlockingAdvancement) == null) {
                return reject(fileId, "locked conversation without valid unlockingAdvancement");
            }
        }

        // 9. steps
        if (!json.has("steps") || !json.get("steps").isJsonArray()) {
            return reject(fileId, "missing/invalid steps array");
        }
        JsonArray stepsArr = json.getAsJsonArray("steps");
        if (stepsArr.isEmpty()) {
            return reject(fileId, "empty steps");
        }

        List<ChatStepDefinition> steps = new ArrayList<>();
        for (int i = 0; i < stepsArr.size(); i++) {
            if (!stepsArr.get(i).isJsonObject()) {
                return reject(fileId, "step " + i + ": not a JSON object");
            }
            ChatStepDefinition step = parseStep(fileId, stepsArr.get(i).getAsJsonObject(), i, false);
            if (step == null) {
                return null; // reason already logged
            }
            steps.add(step);
        }

        // 10. repeatableStepsLists (optional)
        List<RepeatableSeriesDefinition> repeatables = new ArrayList<>();
        if (json.has("repeatableStepsLists")) {
            if (!json.get("repeatableStepsLists").isJsonArray()) {
                return reject(fileId, "repeatableStepsLists must be an array");
            }
            JsonArray repArr = json.getAsJsonArray("repeatableStepsLists");
            Set<String> seriesIds = new HashSet<>();
            for (int i = 0; i < repArr.size(); i++) {
                if (!repArr.get(i).isJsonObject()) {
                    return reject(fileId, "repeatableStepsLists[" + i + "] is not a JSON object");
                }
                RepeatableSeriesDefinition s = parseRepeatableSeries(fileId, repArr.get(i).getAsJsonObject(), i);
                if (s == null) {
                    return null; // reason already logged
                }
                if (!seriesIds.add(s.id())) {
                    return reject(fileId, "duplicate repeatable series id '" + s.id() + "'");
                }
                repeatables.add(s);
            }
            if (!validatePrerequisites(fileId, repeatables)) {
                return null; // reason already logged
            }
        }

        return new ChatConversationDefinition(schemaVersion, id, displayName, displayPriority,
                textureFile, unlockedFromStart, unlockingAdvancement, steps, repeatables);
    }

    /**
     * Checks every {@code prerequisite} once all series ids are known: the target must exist, must not be
     * the series itself, and the prerequisite graph must be acyclic — a cycle would make every series in
     * it permanently unrollable, silently emptying the pool.
     *
     * @return false (reason logged) if the conversation must be skipped
     */
    private static boolean validatePrerequisites(ResourceLocation fileId, List<RepeatableSeriesDefinition> series) {
        Map<String, String> prereqOf = new java.util.HashMap<>();
        Set<String> ids = new HashSet<>();
        for (RepeatableSeriesDefinition s : series) {
            ids.add(s.id());
        }
        for (RepeatableSeriesDefinition s : series) {
            if (!s.hasPrerequisite()) {
                continue;
            }
            if (s.prerequisite().equals(s.id())) {
                reject(fileId, "series '" + s.id() + "' has itself as prerequisite");
                return false;
            }
            if (!ids.contains(s.prerequisite())) {
                reject(fileId, "series '" + s.id() + "' has unknown prerequisite '" + s.prerequisite() + "'");
                return false;
            }
            prereqOf.put(s.id(), s.prerequisite());
        }
        for (String start : prereqOf.keySet()) {
            Set<String> seen = new HashSet<>();
            String cur = start;
            while (cur != null && seen.add(cur)) {
                cur = prereqOf.get(cur);
            }
            if (cur != null) {
                reject(fileId, "prerequisite cycle involving series '" + start + "'");
                return false;
            }
        }
        return true;
    }

    private static RepeatableSeriesDefinition parseRepeatableSeries(ResourceLocation fileId, JsonObject obj, int index) {
        if (!obj.has("id")) {
            return rejectSeries(fileId, index, "missing id");
        }
        String id = obj.get("id").getAsString();
        if (!SAFE_ID.matcher(id).matches()) {
            return rejectSeries(fileId, index, "invalid id '" + id + "' (expected ^[a-z0-9_]+$)");
        }
        if (!obj.has("weight") || !obj.get("weight").getAsJsonPrimitive().isNumber()) {
            return rejectSeries(fileId, index, "missing/invalid weight");
        }
        int weight = obj.get("weight").getAsInt();
        if (weight <= 0) {
            return rejectSeries(fileId, index, "weight must be > 0");
        }
        boolean isUnique = obj.has("isUnique") && obj.get("isUnique").getAsBoolean();
        boolean isTimed = obj.has("isTimed") && obj.get("isTimed").getAsBoolean();
        boolean doDisapear = obj.has("doDisapear") && obj.get("doDisapear").getAsBoolean();
        String failMessage = obj.has("failMessage") ? obj.get("failMessage").getAsString() : null;
        if (failMessage != null && failMessage.isEmpty()) {
            return rejectSeries(fileId, index, "empty failMessage");
        }
        // Existence / self-reference / cycles are checked once every series is known (validatePrerequisites).
        String prerequisite = obj.has("prerequisite") ? obj.get("prerequisite").getAsString() : null;
        if (prerequisite != null && !SAFE_ID.matcher(prerequisite).matches()) {
            return rejectSeries(fileId, index, "invalid prerequisite '" + prerequisite + "' (expected ^[a-z0-9_]+$)");
        }

        if (!obj.has("steps") || !obj.get("steps").isJsonArray()) {
            return rejectSeries(fileId, index, "missing/invalid steps array");
        }
        JsonArray stepsArr = obj.getAsJsonArray("steps");
        if (stepsArr.isEmpty()) {
            return rejectSeries(fileId, index, "empty steps");
        }
        List<ChatStepDefinition> steps = new ArrayList<>();
        for (int i = 0; i < stepsArr.size(); i++) {
            if (!stepsArr.get(i).isJsonObject()) {
                return rejectSeries(fileId, index, "step " + i + ": not a JSON object");
            }
            // Timed series forbid waitNextDay (it would contradict the one-reset deadline).
            ChatStepDefinition step = parseStep(fileId, stepsArr.get(i).getAsJsonObject(), i, isTimed);
            if (step == null) {
                return null;
            }
            steps.add(step);
        }
        // Guardrail: advancements complete only once, so an advancement-gated step in a non-unique
        // (rerunnable) series auto-completes on every rerun. Allowed (valid for one-shot/unique series),
        // but warn so authors use a 'statistic' task for repeatable missions.
        if (!isUnique) {
            for (ChatStepDefinition st : steps) {
                if (!st.isStatGated()) {
                    CobbleSafari.LOGGER.warn("[Chat] {} : repeatable series '{}' is not unique but has an "
                            + "advancement-gated step; advancements complete only once, so it will auto-complete on "
                            + "every rerun — use a 'statistic' task instead", fileId, id);
                    break;
                }
            }
        }
        return new RepeatableSeriesDefinition(id, weight, isUnique, isTimed, failMessage, doDisapear,
                prerequisite, steps);
    }

    private static ChatStepDefinition parseStep(ResourceLocation fileId, JsonObject obj, int index,
                                                boolean forbidWaitNextDay) {
        // message arrays (may be empty, must be present arrays of strings)
        List<String> before = readStringArray(obj, "messagesBefore");
        List<String> after = readStringArray(obj, "messagesAfter");
        if (before == null || after == null) {
            return rejectStep(fileId, index, "missing messagesBefore/messagesAfter (must be string arrays)");
        }
        // Optional: shown instead of messagesAfter when a tag reward fell back to the loot pool.
        List<String> fallbackAfter = List.of();
        if (obj.has("fallbackMessagesAfter")) {
            fallbackAfter = readStringArray(obj, "fallbackMessagesAfter");
            if (fallbackAfter == null) {
                return rejectStep(fileId, index, "fallbackMessagesAfter must be a string array");
            }
        }

        // Optional visibility gate: the step stays hidden until the player owns this advancement.
        String unlockingAdvancement =
                obj.has("unlockingAdvancement") ? obj.get("unlockingAdvancement").getAsString() : null;
        if (unlockingAdvancement != null
                && (unlockingAdvancement.isEmpty() || ResourceLocation.tryParse(unlockingAdvancement) == null)) {
            return rejectStep(fileId, index, "invalid unlockingAdvancement id '" + unlockingAdvancement + "'");
        }

        String advancement = obj.has("advancement") ? obj.get("advancement").getAsString() : null;
        String statistic = obj.has("statistic") ? obj.get("statistic").getAsString() : null;
        int statisticAmount = obj.has("statisticAmount") ? obj.get("statisticAmount").getAsInt() : 0;

        // Optional item-gathering objective (highest-priority gating).
        List<ChatStepDefinition.ItemReq> requiredItems = List.of();
        if (obj.has("requiredItems")) {
            requiredItems = parseRequiredItems(fileId, index, obj.get("requiredItems"));
            if (requiredItems == null) {
                return null; // reason already logged
            }
        }
        boolean itemGated = !requiredItems.isEmpty();

        // Gating priority: requiredItems > statistic > advancement (mutually exclusive).
        if (itemGated) {
            if (statistic != null && !statistic.isEmpty()) {
                return rejectStep(fileId, index, "requiredItems and statistic are mutually exclusive");
            }
            if (advancement != null && !advancement.isEmpty()) {
                return rejectStep(fileId, index, "requiredItems and advancement are mutually exclusive");
            }
        } else if (statistic != null && !statistic.isEmpty()) {
            if (ResourceLocation.tryParse(statistic) == null) {
                return rejectStep(fileId, index, "invalid 'statistic' id '" + statistic + "'");
            }
            if (statisticAmount <= 0) {
                return rejectStep(fileId, index, "statistic-gated step requires 'statisticAmount' > 0");
            }
        } else {
            if (advancement == null || ResourceLocation.tryParse(advancement) == null) {
                return rejectStep(fileId, index, "missing/invalid 'advancement'");
            }
        }

        // 14. rewardItems, if present, must be a parseable loot-table id (a ResourceLocation).
        String rewardItems = obj.has("rewardItems") ? obj.get("rewardItems").getAsString() : null;
        if (rewardItems != null && ResourceLocation.tryParse(rewardItems) == null) {
            return rejectStep(fileId, index, "invalid rewardItems id '" + rewardItems + "'");
        }
        // rewardPersonalTrade is a free GTS unique-offer template id (NOT a ResourceLocation),
        // so only reject an explicitly empty value.
        String rewardTrade = obj.has("rewardPersonalTrade") ? obj.get("rewardPersonalTrade").getAsString() : null;
        if (rewardTrade != null && rewardTrade.isEmpty()) {
            return rejectStep(fileId, index, "empty rewardPersonalTrade id");
        }
        String rewardTradeTag = obj.has("rewardPersonalTradeTag") ? obj.get("rewardPersonalTradeTag").getAsString() : null;
        if (rewardTradeTag != null && rewardTradeTag.isEmpty()) {
            return rejectStep(fileId, index, "empty rewardPersonalTradeTag");
        }
        if (rewardTrade != null && rewardTradeTag != null) {
            return rejectStep(fileId, index, "rewardPersonalTrade and rewardPersonalTradeTag are mutually exclusive");
        }

        // unlockApp / rewardSkin / rewardSkinTag are free ids resolved at runtime; only reject
        // explicitly-empty values, and rewardSkin/rewardSkinTag are mutually exclusive.
        String unlockApp = obj.has("unlockApp") ? obj.get("unlockApp").getAsString() : null;
        if (unlockApp != null && unlockApp.isEmpty()) {
            return rejectStep(fileId, index, "empty unlockApp id");
        }
        String rewardSkin = obj.has("rewardSkin") ? obj.get("rewardSkin").getAsString() : null;
        if (rewardSkin != null && rewardSkin.isEmpty()) {
            return rejectStep(fileId, index, "empty rewardSkin id");
        }
        String rewardSkinTag = obj.has("rewardSkinTag") ? obj.get("rewardSkinTag").getAsString() : null;
        if (rewardSkinTag != null && rewardSkinTag.isEmpty()) {
            return rejectStep(fileId, index, "empty rewardSkinTag");
        }
        if (rewardSkin != null && rewardSkinTag != null) {
            return rejectStep(fileId, index, "rewardSkin and rewardSkinTag are mutually exclusive");
        }

        // fallbackReward, if present, must be a parseable loot-table id.
        String fallbackReward = obj.has("fallbackReward") ? obj.get("fallbackReward").getAsString() : null;
        if (fallbackReward != null && (fallbackReward.isEmpty() || ResourceLocation.tryParse(fallbackReward) == null)) {
            return rejectStep(fileId, index, "invalid fallbackReward id '" + fallbackReward + "'");
        }

        // The fallback only ever fires for tag rewards, so fallback messages are dead weight without one.
        if (!fallbackAfter.isEmpty() && rewardSkinTag == null && rewardTradeTag == null) {
            CobbleSafari.LOGGER.warn("[Chat] {} : step {}: fallbackMessagesAfter has no effect without "
                    + "rewardSkinTag/rewardPersonalTradeTag", fileId, index);
        }

        boolean waitNextDay = obj.has("waitNextDay") && obj.get("waitNextDay").getAsBoolean();
        if (waitNextDay && forbidWaitNextDay) {
            return rejectStep(fileId, index, "waitNextDay is not allowed inside a timed repeatable series");
        }

        return new ChatStepDefinition(before, after, fallbackAfter, unlockingAdvancement,
                advancement, statistic, statisticAmount,
                rewardItems, rewardTrade, rewardTradeTag, unlockApp, rewardSkin, rewardSkinTag,
                fallbackReward, requiredItems, waitNextDay);
    }

    /**
     * Parses an item-gathering objective. Each entry must be {@code { "item": <existing item id>,
     * "count": >0 }}; ids must resolve to a registered item (not air) and be unique across the list
     * (duplicate ids would make the per-item count check double-count). Returns {@code null} (reason
     * logged) on any error, or a non-empty list on success.
     */
    private static List<ChatStepDefinition.ItemReq> parseRequiredItems(ResourceLocation fileId, int index,
                                                                       JsonElement el) {
        if (!el.isJsonArray()) {
            rejectStep(fileId, index, "requiredItems must be an array");
            return null;
        }
        JsonArray arr = el.getAsJsonArray();
        if (arr.isEmpty()) {
            rejectStep(fileId, index, "requiredItems must not be empty");
            return null;
        }
        List<ChatStepDefinition.ItemReq> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < arr.size(); i++) {
            if (!arr.get(i).isJsonObject()) {
                rejectStep(fileId, index, "requiredItems[" + i + "] is not a JSON object");
                return null;
            }
            JsonObject o = arr.get(i).getAsJsonObject();
            if (!o.has("item") || !o.get("item").getAsJsonPrimitive().isString()) {
                rejectStep(fileId, index, "requiredItems[" + i + "] missing 'item'");
                return null;
            }
            String itemId = o.get("item").getAsString();
            ResourceLocation loc = ResourceLocation.tryParse(itemId);
            if (loc == null) {
                rejectStep(fileId, index, "requiredItems[" + i + "] invalid item id '" + itemId + "'");
                return null;
            }
            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(loc)
                    || itemId.equals("minecraft:air")) {
                rejectStep(fileId, index, "requiredItems[" + i + "] unknown item '" + itemId + "'");
                return null;
            }
            if (!seen.add(itemId)) {
                rejectStep(fileId, index, "requiredItems[" + i + "] duplicate item '" + itemId + "'");
                return null;
            }
            if (!o.has("count") || !o.get("count").getAsJsonPrimitive().isNumber()) {
                rejectStep(fileId, index, "requiredItems[" + i + "] missing/invalid 'count'");
                return null;
            }
            int count = o.get("count").getAsInt();
            if (count <= 0) {
                rejectStep(fileId, index, "requiredItems[" + i + "] count must be > 0");
                return null;
            }
            out.add(new ChatStepDefinition.ItemReq(itemId, count));
        }
        return out;
    }

    /** Returns null if the field is missing or not an array of strings. */
    private static List<String> readStringArray(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray(key)) {
            if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                return null;
            }
            out.add(el.getAsString());
        }
        return out;
    }

    private static ChatConversationDefinition reject(ResourceLocation fileId, String reason) {
        CobbleSafari.LOGGER.error("[Chat] {} : {}, skipped", fileId, reason);
        return null;
    }

    private static ChatStepDefinition rejectStep(ResourceLocation fileId, int index, String reason) {
        CobbleSafari.LOGGER.error("[Chat] {} : step {}: {}, skipped", fileId, index, reason);
        return null;
    }

    private static RepeatableSeriesDefinition rejectSeries(ResourceLocation fileId, int index, String reason) {
        CobbleSafari.LOGGER.error("[Chat] {} : repeatableStepsLists[{}]: {}, skipped", fileId, index, reason);
        return null;
    }
}
