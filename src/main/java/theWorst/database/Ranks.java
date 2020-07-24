package theWorst.database;

import arc.graphics.Color;
import arc.util.Log;
import mindustry.content.Items;
import theWorst.Global;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import static theWorst.Tools.Commands.logInfo;
import static theWorst.Tools.General.enumContains;
import static theWorst.Tools.Json.loadSimpleHashmap;
import static theWorst.Tools.Json.saveSimple;

public class Ranks {
    static final String rankFile = Global.configDir + "specialRanks.json";
    public static HashMap<String, Rank> buildIn = new HashMap<String, Rank>() {{
        put("griefer", new Rank(true, false, "griefer", "#" + Items.thorium.color.toString(),
                new HashMap<String, String>() {{
                    put("default", "Rank for griefers you ll see what it does when you get it.");
                }}, 0, null, null, null, null));
        put("newcomer", new Rank(false, false, "newcomer", "#" + Items.copper.color.toString(),
                new HashMap<String, String>() {{
                    put("default", "This is first ran you will get.");
                }}, 0, new HashSet<>(Collections.singletonList(Perm.normal.name())), null, null, null));
        put("verified", new Rank(false, false, "verified", "#" + Items.titanium.color.toString(),
                new HashMap<String, String>() {{
                    put("default", "pass the test and you ll get this. Protects your blocks against newcomers.");
                }}, 0, new HashSet<>(Collections.singletonList(Perm.high.name())), null, null, null));
        put("candidate", new Rank(true, false, "candidate", "#" + Items.plastanium.color.toString(),
                new HashMap<String, String>() {{
                    put("default", "This is middle step between normal player and admin.");
                }}, 0, new HashSet<>(Collections.singletonList(Perm.higher.name())), null, null, null));
        put("admin", new Rank(true, true, "admin", "#" + Items.surgealloy.color.toString(),
                new HashMap<String, String>() {{
                    put("default", "You have power to protect others.");
                }}, 0, new HashSet<>(Collections.singletonList(Perm.highest.name())), null, null, null));
    }};
    public static HashMap<String, Rank> special = new HashMap<>();
    public static HashMap<String, Rank> donation = new HashMap<>();
    public static Rank griefer, newcomer, verified, candidate, admin;
    public static Rank error = new Rank(true, false, "error", "red", new HashMap<String, String>() {{
        put("default", "When your special rank disappears you have chance to get this super rare rank");
    }}, 0, null, null, null, null);

    public static Rank getRank(String name, RankType type) {
        switch (type) {
            case rank:
                return buildIn.getOrDefault(name, newcomer);
            case specialRank:
                return special.getOrDefault(name, error);
            default:
                return donation.getOrDefault(name, error);
        }
    }
    public static void loadBuildIn() {
        griefer = buildIn.get("griefer");
        newcomer = buildIn.get("newcomer");
        verified = buildIn.get("verified");
        candidate = buildIn.get("candidate");
        admin = buildIn.get("admin");
    }

    public static void loadRanks() {
        special.clear();
        donation.clear();
        HashMap<String, Rank[]> ranks = loadSimpleHashmap(rankFile, Rank[].class, Ranks::defaultRanks);
        if (ranks == null) return;
        Rank[] srs = ranks.get("ranks");
        if (srs == null) return;
        boolean invalid = false;
        for (Rank r : srs) {
            if(buildIn.containsKey(r.name)) {
                buildIn.put(r.name, r);
                continue;
            }

            if (r.quests != null) {
                for (String s : r.quests.keySet()) {
                    if (!enumContains(Stat.values(), s)) {
                        logInfo("special-error-invalid-stat", r.name, s);
                        invalid = true;
                    }
                    for (String l : r.quests.get(s).keySet()) {
                        if (!enumContains(Rank.Mod.values(), l)) {
                            logInfo("special-error-invalid-stat-property", r.name, s, l);
                            invalid = true;
                        }
                    }
                }
            } else {
                donation.put(r.name, r);
                continue;
            }

            if (r.linked != null) {
                for (String l : r.linked) {
                    if (!ranks.containsKey(l)) {
                        logInfo("special-rank-error-missing-rank", l, r.name);
                        invalid = true;
                    }

                }
            }
            special.put(r.name, r);
        }
        if (invalid) {
            ranks.clear();
            logInfo("special-rank-file-invalid");
        }

    }

    public static void defaultRanks() {

        ArrayList<Rank> arr = new ArrayList<>();
        arr.add(new Rank() {
            {
                name = "kamikaze";
                color = "scarlet";
                description = new HashMap<String, String>() {{
                    put("default", "put your description here.");
                    put("en_US", "Put translation like this.");
                }};
                value = 1;
                permissions = new HashSet<String>() {{
                    add(Perm.suicide.name());
                }};
                quests = new HashMap<String, HashMap<String, Integer>>() {{
                    put(Stat.deaths.name(), new HashMap<String, Integer>() {{
                        put(Mod.best.name(), 10);
                        put(Mod.required.name(), 100);
                        put(Mod.frequency.name(), 20);
                    }});
                }};
            }
        });
        arr.add(new Rank() {{
            name = "donor";
            color = "#" + Color.gold.toString();
            description = new HashMap<String, String>() {{
                put("default", "For people who support server financially.");
            }};
            permissions = new HashSet<String>() {{
                add(Perm.colorCombo.name());
                add(Perm.suicide.name());
            }};
            pets = new ArrayList<String>() {{
                add("fire-pet");
                add("fire-pet");
            }};
        }});
        HashMap<String, ArrayList<Rank>> ranks = new HashMap<>();
        ranks.put("ranks", arr);
        saveSimple(rankFile, ranks, "special ranks");
        for (Rank sr : arr) {
            special.put(sr.name, sr);
        }
    }
}
