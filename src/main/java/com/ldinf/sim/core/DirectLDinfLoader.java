package com.ldinf.sim.core;

import com.ldinf.sim.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Carica direttamente un file .ldinf (sintassi paper L-DINF) e costruisce
 * gli oggetti Group, Agent, Budget, AgentMemory senza file intermedi.
 *
 * Sintassi supportata (notazione paper):
 *   GROUP <id> { ... }             - Gruppo G
 *   B1: <n>                        - Budget mentale
 *   B2: <risorsa:n, ...>           - Budget fisico
 *   Ki: <premises> ---> <concl>    - Regola Knowledge Base
 *   Cl: <a> = <b>                  - Equivalenza
 *   C1: <n>                        - Costo inferenza mentale
 *   C2: <action> [res:n, ...]      - Costo azione fisica
 *   P: <action> = <n>              - Preferenza
 *   G: <atom>                      - Goal comune
 *   LENDING: enabled|disabled      - Prestito agenti (estensione)
 *   AGENT <id> { ... }             - Agente i
 *     Bi: <atom>                   - Fatto Working Memory
 *     Hi: <action>                 - Azione abilitata H(i,w)
 *     Gi: <atom>                   - Goal personale
 */
public class DirectLDinfLoader {

    /**
     * Risultato del caricamento: lista di gruppi con i loro agenti già pronti.
     */
    public static class LoadResult {
        public final List<Group> groups = new ArrayList<>();
        public final List<Agent> allAgents = new ArrayList<>();
        public final CommunicationChannel channel = new CommunicationChannel();
    }

    // Dati intermedi durante il parsing di un gruppo
    private static class GroupData {
        String groupId;
        int mentalBudget = 0;
        Map<String, Integer> physicalResources = new HashMap<>();
        AgentMemory sharedMemory = new AgentMemory();
        List<Atom> commonGoals = new ArrayList<>();
        boolean lendingEnabled = true; // default: abilitato
        List<AgentData> agents = new ArrayList<>();
    }

    // Dati intermedi durante il parsing di un agente
    private static class AgentData {
        String agentId;
        List<String> workingMemoryLines = new ArrayList<>();
        List<String> enabledActions = new ArrayList<>();
        String personalGoal = null;
    }

    /**
     * Carica un file .ldinf e costruisce tutti gli oggetti della simulazione.
     */
    public static LoadResult load(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("File .ldinf non trovato: " + path.toAbsolutePath());
        }

        System.out.println("--- L-DINF LOADER ---");
        System.out.println("File: " + path.toAbsolutePath());

        List<String> allLines = Files.readAllLines(path);
        List<GroupData> groupDataList = parseFile(allLines);

        System.out.println("Gruppi trovati: " + groupDataList.size());

        LoadResult result = new LoadResult();

        for (GroupData gd : groupDataList) {
            System.out.println("  -> " + gd.groupId + " (" + gd.agents.size() + " agenti, "
                    + gd.sharedMemory.getKnowledgeBase().size() + " regole Ki, "
                    + gd.commonGoals.size() + " goal G"
                    + ", lending=" + (gd.lendingEnabled ? "enabled" : "disabled") + ")");

            Budget totalBudget = new Budget(gd.mentalBudget, gd.physicalResources);
            Group group = new Group(gd.groupId, totalBudget, gd.sharedMemory);
            group.setLendingEnabled(gd.lendingEnabled);
            gd.commonGoals.forEach(group::addCommonGoal);

            int numAgents = gd.agents.size();
            if (numAgents == 0) continue;

            // Dividi budget tra agenti
            int energyPerAgent = gd.mentalBudget / numAgents;
            Map<String, Integer> resPerAgent = new HashMap<>();
            gd.physicalResources.forEach((k, v) -> resPerAgent.put(k, v / numAgents));

            System.out.println("     Budget per agente: B1=" + energyPerAgent);

            for (AgentData ad : gd.agents) {
                String agentId = gd.groupId + "_" + ad.agentId;

                // Clona memoria condivisa per l'agente
                AgentMemory myMemory = new AgentMemory();

                // A. Copia Regole Logiche (Ki)
                gd.sharedMemory.getKnowledgeBase().forEach(k -> myMemory.addKnowledge(k));

                // B. Copia Costi Azioni (C2)
                gd.sharedMemory.getActionCosts().values().forEach(c -> myMemory.addKnowledge(c));

                // C. Copia Equivalenze (Cl)
                gd.sharedMemory.getEquivalences().forEach(e -> myMemory.addKnowledge(e));

                // D. Copia Preferenze (P)
                gd.sharedMemory.getPreferencesMap().forEach((act, val) ->
                    myMemory.addKnowledge(new Preference(act, val)));

                // E. Copia Costo Base Inferenza (C1)
                myMemory.addKnowledge(new InferenceCost(gd.sharedMemory.getBaseInferenceCost()));

                // F. Copia Goal Comune (G)
                for (Atom g : gd.sharedMemory.getGroupGoals()) {
                    myMemory.addGroupGoal(g);
                }

                // G. Working Memory specifica (Bi)
                for (String wmLine : ad.workingMemoryLines) {
                    Formula f = LDinfParser.parse(wmLine);
                    if (f != null) myMemory.addKnowledge(f);
                }

                // H. Azioni abilitate H(i,w)
                for (String action : ad.enabledActions) {
                    myMemory.addEnabledAction(action);
                }
                System.out.println("     " + agentId + " Hi=" + myMemory.getEnabledActions());

                Budget myBudget = new Budget(energyPerAgent, resPerAgent);
                Agent agent = new Agent(agentId, myMemory, myBudget, result.channel);
                agent.setGroupId(gd.groupId);
                agent.setGroupSize(numAgents);
                agent.setGroup(group);

                // Goal personale (Gi)
                if (ad.personalGoal != null) {
                    agent.setGoal(ad.personalGoal);
                }

                result.allAgents.add(agent);
                group.addAgent(agent);
                result.channel.register(agent);
            }

            result.groups.add(group);
        }

        System.out.println("--- FINE CARICAMENTO ---\n");
        return result;
    }

    // --- PARSER ---

    private static List<GroupData> parseFile(List<String> lines) {
        List<GroupData> groups = new ArrayList<>();
        int i = 0;

        while (i < lines.size()) {
            String line = lines.get(i).trim();
            i++;
            if (line.isEmpty() || line.startsWith("#")) continue;

            Matcher groupMatcher = Pattern.compile("^GROUP\\s+(\\S+)\\s*\\{").matcher(line);
            if (groupMatcher.find()) {
                GroupData gd = new GroupData();
                gd.groupId = groupMatcher.group(1);
                i = parseGroupBody(lines, i, gd);
                groups.add(gd);
            }
        }

        return groups;
    }

    private static int parseGroupBody(List<String> lines, int startIdx, GroupData gd) {
        int i = startIdx;
        int braceDepth = 1;

        while (i < lines.size() && braceDepth > 0) {
            String line = lines.get(i).trim();
            i++;

            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.equals("}")) { braceDepth--; if (braceDepth == 0) break; continue; }

            // B1: <n> (Budget mentale)
            if (line.startsWith("B1:")) {
                gd.mentalBudget = Integer.parseInt(line.substring(3).trim());
                continue;
            }

            // B2: <risorsa:n, ...> (Budget fisico)
            if (line.startsWith("B2:")) {
                parsePhysicalBudget(line.substring(3).trim(), gd.physicalResources);
                continue;
            }

            // Ki: <premises> ---> <conclusion> (Knowledge Base)
            if (line.startsWith("Ki:")) {
                String ruleText = line.substring(3).trim().replace("--->", "->");
                Formula f = LDinfParser.parse(ruleText);
                if (f != null) gd.sharedMemory.addKnowledge(f);
                continue;
            }

            // Cl: <a> = <b> (Equivalenza)
            if (line.startsWith("Cl:")) {
                String eqText = line.substring(3).trim();
                String[] parts = eqText.split("=");
                String equiv = "equiv(" + parts[0].trim() + ", " + parts[1].trim() + ")";
                Formula f = LDinfParser.parse(equiv);
                if (f != null) gd.sharedMemory.addKnowledge(f);
                continue;
            }

            // C1: <n> (Costo inferenza mentale)
            if (line.startsWith("C1:")) {
                String costText = "inference_cost(" + line.substring(3).trim() + ")";
                Formula f = LDinfParser.parse(costText);
                if (f != null) gd.sharedMemory.addKnowledge(f);
                continue;
            }

            // C2: <action> [res:n, ...] (Costo azione fisica)
            if (line.startsWith("C2:")) {
                String costText = line.substring(3).trim();
                Matcher m = Pattern.compile("^(\\S+)\\s*\\[(.*)\\]").matcher(costText);
                if (m.find()) {
                    String parsed = "cost(" + m.group(1) + ", " + m.group(2).trim() + ")";
                    Formula f = LDinfParser.parse(parsed);
                    if (f != null) gd.sharedMemory.addKnowledge(f);
                }
                continue;
            }

            // P: <action> = <value> (Preferenza)
            if (line.startsWith("P:")) {
                String prefText = line.substring(2).trim();
                String[] parts = prefText.split("=");
                String parsed = "pref(" + parts[0].trim() + ", " + parts[1].trim() + ")";
                Formula f = LDinfParser.parse(parsed);
                if (f != null) gd.sharedMemory.addKnowledge(f);
                continue;
            }

            // G: <atom> (Goal comune) — deve venire PRIMA del check AGENT per evitare conflitto
            if (line.startsWith("G:") && !line.startsWith("Gi:")) {
                String goalText = line.substring(2).trim();
                Formula f = LDinfParser.parse(goalText);
                if (f instanceof Atom a) {
                    gd.commonGoals.add(a);
                    gd.sharedMemory.addGroupGoal(a);
                }
                continue;
            }

            // LENDING: enabled|disabled
            if (line.startsWith("LENDING:")) {
                String val = line.substring(8).trim().toLowerCase();
                gd.lendingEnabled = val.equals("enabled");
                continue;
            }

            // AGENT <id> {
            Matcher agentMatcher = Pattern.compile("^AGENT\\s+(\\S+)\\s*\\{").matcher(line);
            if (agentMatcher.find()) {
                AgentData ad = new AgentData();
                ad.agentId = agentMatcher.group(1);
                i = parseAgentBody(lines, i, ad);
                gd.agents.add(ad);
            }
        }

        return i;
    }

    private static int parseAgentBody(List<String> lines, int startIdx, AgentData ad) {
        int i = startIdx;

        while (i < lines.size()) {
            String line = lines.get(i).trim();
            i++;

            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.equals("}")) break;

            // Bi: <atom> (Working Memory)
            if (line.startsWith("Bi:")) {
                ad.workingMemoryLines.add(line.substring(3).trim());
                continue;
            }

            // Hi: <action> (Azione abilitata)
            if (line.startsWith("Hi:")) {
                ad.enabledActions.add(line.substring(3).trim());
                continue;
            }

            // Gi: <atom> (Goal personale)
            if (line.startsWith("Gi:")) {
                ad.personalGoal = line.substring(3).trim();
            }
        }

        return i;
    }

    private static void parsePhysicalBudget(String text, Map<String, Integer> resources) {
        String[] parts = text.split(",");
        for (String p : parts) {
            String[] kv = p.trim().split(":");
            if (kv.length == 2) {
                resources.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
            }
        }
    }
}
