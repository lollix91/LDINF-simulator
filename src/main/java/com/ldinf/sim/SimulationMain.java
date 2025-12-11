package com.ldinf.sim;

import com.ldinf.sim.core.*;
import com.ldinf.sim.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class SimulationMain {

    private static final String CONFIG_ROOT = "config";

    public static void main(String[] args) {
        System.out.println("--- L-DINF SIMULATION (Group Goals & Energy) ---");

        CommunicationChannel channel = new CommunicationChannel();
        List<Agent> allAgents = new ArrayList<>();
        // Mappa per tenere traccia del goal comune per ogni gruppo
        Map<String, List<Atom>> groupGoalsMap = new HashMap<>();
        
        try {
            // 1. Scan for Group Folders
            List<Path> groupDirs = Files.list(Paths.get(CONFIG_ROOT))
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("group_"))
                    .toList();

            for (Path groupPath : groupDirs) {
                String groupId = groupPath.getFileName().toString();
                System.out.println("Loading Group: " + groupId);
                
                // 2. Load Shared Memory Template (Knowledge Base)
                AgentMemory sharedMemoryTemplate = new AgentMemory();
                List<String> kbLines = FileLoader.readRealFile(groupPath.resolve("knowledge_base.ldinf").toString());
                kbLines.forEach(line -> sharedMemoryTemplate.addKnowledge(LDinfParser.parse(line)));

                // 3. Load Common Goal
                Path commonGoalPath = groupPath.resolve("common_goal.ldinf");
                if (Files.exists(commonGoalPath)) {
                    List<String> lines = FileLoader.readRealFile(commonGoalPath.toString());
                    for (String line : lines) {
                        Formula f = LDinfParser.parse(line);
                        if (f instanceof Atom a) {
                            sharedMemoryTemplate.addGroupGoal(a); // Usa il nuovo metodo
                            // Nota: groupGoalsMap ora dovrebbe mappare String -> List<Atom> 
                            groupGoalsMap.computeIfAbsent(groupId, k -> new ArrayList<>()).add(a);
                            System.out.println("  -> Added Common Goal: " + a);
                        }
                    }
                }

                // 4. Load Budget (Energy & Physical Resources)
                List<String> budgetLines = FileLoader.readRealFile(groupPath.resolve("budget.ldinf").toString());
                if (budgetLines.isEmpty()) continue;
                Budget totalGroupBudget = parseTotalBudget(budgetLines.get(0));

                // 5. Detect Agents
                List<Path> agentDirs = Files.list(groupPath)
                        .filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().startsWith("agent_"))
                        .toList();

                int numAgents = agentDirs.size();
                if (numAgents == 0) continue;

                // 6. Divide Budget
                int energyPerAgent = totalGroupBudget.getMentalBudget() / numAgents;
                Map<String, Integer> resPerAgent = new HashMap<>();
                totalGroupBudget.getPhysicalResources().forEach((k, v) -> resPerAgent.put(k, v / numAgents));
                
                System.out.println("  -> Budget per agent: ENERGY:" + energyPerAgent);

                // 7. Instantiate Agents
                for (Path agentPath : agentDirs) {
                    String agentId = groupId + "_" + agentPath.getFileName();
                    
                    // --- CLONE MEMORY (CRITICAL FIX APPLIED HERE) ---
                    AgentMemory myMemory = new AgentMemory();
                    
                    // A. Copia Regole Logiche (KB)
                    sharedMemoryTemplate.getKnowledgeBase().forEach(k -> myMemory.addKnowledge(k));
                    
                    // B. Copia Costi Azioni (C1/C2)
                    sharedMemoryTemplate.getActionCosts().values().forEach(c -> myMemory.addKnowledge(c));
                    
                    // C. Copia Equivalenze (Q/Cl) - FONDAMENTALE per riconoscere cook_steak come cook_main_dish
                    sharedMemoryTemplate.getEquivalences().forEach(e -> myMemory.addKnowledge(e));

                    // D. Copia Preferenze (P) - FONDAMENTALE per scegliere la bistecca
                    sharedMemoryTemplate.getPreferencesMap().forEach((act, val) -> 
                        myMemory.addKnowledge(new Preference(act, val)));
                    
                    // E. Copia Costo Base Inferenza
                    myMemory.addKnowledge(new InferenceCost(sharedMemoryTemplate.getBaseInferenceCost()));

                    // F. Copia Goal Comune
                    for (Atom g : sharedMemoryTemplate.getGroupGoals()) {
                        myMemory.addGroupGoal(g);
                    }
                    
                    // G. Carica Working Memory Specifica (Bi)
                    List<String> wm = FileLoader.readRealFile(agentPath.resolve("working_memory.ldinf").toString());
                    wm.forEach(line -> myMemory.addKnowledge(LDinfParser.parse(line)));
                    // ------------------------------------------------

                    Budget myBudget = new Budget(energyPerAgent, resPerAgent);
                    Agent agent = new Agent(agentId, myMemory, myBudget, channel);

                    // Load Personal Goal
                    List<String> goalLines = FileLoader.readRealFile(agentPath.resolve("personal_goal.ldinf").toString());
                    if (!goalLines.isEmpty()) agent.setGoal(goalLines.get(0));

                    allAgents.add(agent);
                    channel.register(agent);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // 8. Simulation Loop
        System.out.println("\n--- STARTING SIMULATION ---");
        boolean commonGoalMet = false;
        int maxSteps = 10;
        int step = 0;

        while (!commonGoalMet && step < maxSteps) {
            step++;
            System.out.println("\n>>> STEP " + step);
            boolean anyoneDidAnything = false;
            
            for (Agent a : allAgents) {
                // reasonAndAct ritorna true se l'agente ha compiuto una NUOVA azione/inferenza
                boolean acted = a.reasonAndAct(); 
                if (acted) anyoneDidAnything = true;
            }
            
            // Check Common Goals
            for (String groupId : groupGoalsMap.keySet()) {
                List<Atom> goals = groupGoalsMap.get(groupId);
                // Verifica se TUTTI i goal di questo gruppo sono soddisfatti
                boolean allGoalsMet = goals.stream()
                        .allMatch(g -> checkGroupGoal(groupId, g, allAgents));
                        
                if (allGoalsMet) {
                    commonGoalMet = true; // Questo ferma la simulazione
                    // System.out.println(">>> SUCCESS: Group " + groupId + " achieved ALL goals at step " + step);
                }
            }

            if (!anyoneDidAnything && !commonGoalMet) {
                System.out.println(">>> STALL: No agents performed actions this step.");
                break;
            }
        }
        
        // 9. Final Report
        System.out.println("\n--- FINAL REPORT ---");
        for (String groupId : groupGoalsMap.keySet()) {
            List<Atom> goals = groupGoalsMap.get(groupId);
            System.out.println("Group: " + groupId);
            
            for (Atom commonGoal : goals) {
                boolean isCommonGoalMet = checkGroupGoal(groupId, commonGoal, allAgents);
                System.out.println("  Goal (" + commonGoal + ") met? " + (isCommonGoalMet ? "YES" : "NO"));
                
                if (isCommonGoalMet) {
                    allAgents.stream()
                        .filter(a -> a.toString().startsWith(groupId)) // Filtra per gruppo
                        .filter(a -> a.getMemory().getWorkingMemory().contains(commonGoal))
                        .forEach(a -> System.out.println("     -> Known by " + a));
                }
            }
        }
    }

    private static boolean checkGroupGoal(String groupId, Atom goal, List<Agent> agents) {
        return agents.stream()
            .filter(a -> a.toString().startsWith(groupId)) 
            .anyMatch(a -> checkMemoryForAtom(a, goal));
    }

    private static boolean checkMemoryForAtom(Agent a, Atom goal) {
        return a.getMemory().getWorkingMemory().contains(goal);
    }

    private static Budget parseTotalBudget(String line) {
        String[] parts = line.split(",");
        int mental = 0;
        Map<String, Integer> res = new HashMap<>();
        for (String p : parts) {
            String[] kv = p.trim().split(":");
            if (kv[0].trim().equalsIgnoreCase("ENERGY") || kv[0].trim().equalsIgnoreCase("MENTAL")) 
                mental = Integer.parseInt(kv[1].trim());
            else 
                res.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
        }
        return new Budget(mental, res);
    }
}