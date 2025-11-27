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
        Map<String, Atom> groupGoalsMap = new HashMap<>(); 

        try {
            List<Path> groupDirs = Files.list(Paths.get(CONFIG_ROOT))
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("group_"))
                    .toList();

            for (Path groupPath : groupDirs) {
                String groupId = groupPath.getFileName().toString();
                System.out.println("Loading Group: " + groupId);
                
                // 1. Shared Memory
                AgentMemory sharedMemoryTemplate = new AgentMemory();
                List<String> kbLines = FileLoader.readRealFile(groupPath.resolve("knowledge_base.ldinf").toString());
                kbLines.forEach(line -> sharedMemoryTemplate.addKnowledge(LDinfParser.parse(line)));

                // 2. NEW: Load Common Goal
                Path commonGoalPath = groupPath.resolve("common_goal.ldinf");
                if (Files.exists(commonGoalPath)) {
                    List<String> lines = FileLoader.readRealFile(commonGoalPath.toString());
                    if (!lines.isEmpty()) {
                        Formula f = LDinfParser.parse(lines.get(0));
                        if (f instanceof Atom a) {
                            sharedMemoryTemplate.setGroupGoal(a); // Setta nel template
                            groupGoalsMap.put(groupId, a);
                            System.out.println("  -> Common Goal: " + a);
                        }
                    }
                }

                // 3. Budget (Energy & Physical Resources)
                List<String> budgetLines = FileLoader.readRealFile(groupPath.resolve("budget.ldinf").toString());
                if (budgetLines.isEmpty()) continue;
                Budget totalGroupBudget = parseTotalBudget(budgetLines.get(0));

                // 4. Load Agents
                List<Path> agentDirs = Files.list(groupPath)
                        .filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().startsWith("agent_"))
                        .toList();

                int numAgents = agentDirs.size();
                if (numAgents == 0) continue;

                // Divide Budget
                int energyPerAgent = totalGroupBudget.getMentalBudget() / numAgents;
                Map<String, Integer> resPerAgent = new HashMap<>();
                totalGroupBudget.getPhysicalResources().forEach((k, v) -> resPerAgent.put(k, v / numAgents));
                
                System.out.println("  -> Budget per agent: ENERGY:" + energyPerAgent);

                for (Path agentPath : agentDirs) {
                    String agentId = groupId + "_" + agentPath.getFileName();
                    
                    // Clone Memory & Goal
                    AgentMemory myMemory = new AgentMemory();
                    sharedMemoryTemplate.getKnowledgeBase().forEach(k -> myMemory.addKnowledge(k));
                    sharedMemoryTemplate.getActionCosts().values().forEach(c -> myMemory.addKnowledge(c));
                    if(sharedMemoryTemplate.getGroupGoal() != null) 
                        myMemory.setGroupGoal(sharedMemoryTemplate.getGroupGoal());
                    
                    List<String> wm = FileLoader.readRealFile(agentPath.resolve("working_memory.ldinf").toString());
                    wm.forEach(line -> myMemory.addKnowledge(LDinfParser.parse(line)));

                    Budget myBudget = new Budget(energyPerAgent, resPerAgent);
                    Agent agent = new Agent(agentId, myMemory, myBudget, channel);

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

      // 5. Execution
        System.out.println("\n--- STARTING SIMULATION ---");
        boolean commonGoalMet = false;
        int maxSteps = 10;
        int step = 0;

        // Il ciclo continua finché il goal comune NON è raggiunto e non superiamo i passi massimi
        while (!commonGoalMet && step < maxSteps) {
            step++;
            System.out.println("\n>>> STEP " + step);
            boolean anyoneDidAnything = false;
            
            for (Agent a : allAgents) {
                // reasonAndAct ora ritorna true SOLO se ha compiuto una NUOVA azione/inferenza
                boolean acted = a.reasonAndAct(); 
                if (acted) anyoneDidAnything = true;
            }
            
            // Verifica se il goal comune è stato raggiunto in questo step
            for (String groupId : groupGoalsMap.keySet()) {
                Atom goal = groupGoalsMap.get(groupId);
                if (checkGroupGoal(groupId, goal, allAgents)) {
                    commonGoalMet = true;
                    System.out.println(">>> SUCCESS: Group " + groupId + " achieved goal " + goal + " at step " + step);
                }
            }

            // Se nessuno ha fatto nulla e il goal non è raggiunto, inutile continuare
            if (!anyoneDidAnything && !commonGoalMet) {
                System.out.println(">>> STALL: No agents performed actions this step.");
                break;
            }
        }
        
        // 6. Final Report
        System.out.println("\n--- FINAL REPORT ---");
        for (String groupId : groupGoalsMap.keySet()) {
            Atom commonGoal = groupGoalsMap.get(groupId);
            boolean isCommonGoalMet = checkGroupGoal(groupId, commonGoal, allAgents);
            System.out.println("Group " + groupId + " Common Goal (" + commonGoal + ") met? " + isCommonGoalMet);
            // Visualizza il ragionamento (chi lo sa?)
            if (isCommonGoalMet) {
                allAgents.stream()
                    .filter(a -> a.getMemory().getWorkingMemory().contains(commonGoal))
                    .forEach(a -> System.out.println("   -> Known by " + a));
            }
        }
    }

    // Verifica se il goal comune è nella working memory di ALMENO UN agente del gruppo
    private static boolean checkGroupGoal(String groupId, Atom goal, List<Agent> agents) {
        return agents.stream()
            .filter(a -> a.toString().startsWith(groupId)) 
            .anyMatch(a -> checkMemoryForAtom(a, goal));
    }

    // --- CORREZIONE APPLICATA QUI SOTTO ---
    private static boolean checkMemoryForAtom(Agent a, Atom goal) {
        // Ora accediamo davvero alla memoria dell'agente tramite il getter
        return a.getMemory().getWorkingMemory().contains(goal);
    }

    private static Budget parseTotalBudget(String line) {
        String[] parts = line.split(",");
        int mental = 0;
        Map<String, Integer> res = new HashMap<>();
        for (String p : parts) {
            String[] kv = p.trim().split(":");
            // PAPER COMPLIANCE: ENERGY keyword
            if (kv[0].trim().equalsIgnoreCase("ENERGY") || kv[0].trim().equalsIgnoreCase("MENTAL")) 
                mental = Integer.parseInt(kv[1].trim());
            else 
                res.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
        }
        return new Budget(mental, res);
    }
}