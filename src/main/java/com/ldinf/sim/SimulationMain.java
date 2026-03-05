package com.ldinf.sim;

import com.ldinf.sim.core.*;
import com.ldinf.sim.model.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class SimulationMain {

    private static final String CONFIG_ROOT = "config";

    public static void main(String[] args) {
        System.out.println("--- L-DINF SIMULATION (Group Goals, Energy & Agent Lending) ---");

        CommunicationChannel channel = new CommunicationChannel();
        List<Agent> allAgents = new ArrayList<>();
        List<Group> allGroups = new ArrayList<>();
        
        try {
            // 1. Scan for Group Folders
            List<Path> groupDirs = Files.list(Paths.get(CONFIG_ROOT))
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("group_"))
                    .sorted()
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
                List<Atom> commonGoals = new ArrayList<>();
                if (Files.exists(commonGoalPath)) {
                    List<String> lines = FileLoader.readRealFile(commonGoalPath.toString());
                    for (String line : lines) {
                        Formula f = LDinfParser.parse(line);
                        if (f instanceof Atom a) {
                            sharedMemoryTemplate.addGroupGoal(a);
                            commonGoals.add(a);
                            System.out.println("  -> Added Common Goal: " + a);
                        }
                    }
                }

                // 4. Load Budget (Energy & Physical Resources)
                List<String> budgetLines = FileLoader.readRealFile(groupPath.resolve("budget.ldinf").toString());
                if (budgetLines.isEmpty()) continue;
                Budget totalGroupBudget = parseTotalBudget(budgetLines.get(0));

                // Crea oggetto Group
                Group group = new Group(groupId, totalGroupBudget, sharedMemoryTemplate);
                commonGoals.forEach(group::addCommonGoal);

                // 5. Detect Agents
                List<Path> agentDirs = Files.list(groupPath)
                        .filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().startsWith("agent_"))
                        .sorted()
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
                    
                    // H. Carica Azioni Abilitate - H(i,w) (Ruoli)
                    Path enabledPath = agentPath.resolve("enabled_actions.ldinf");
                    if (Files.exists(enabledPath)) {
                        List<String> enabledLines = FileLoader.readRealFile(enabledPath.toString());
                        enabledLines.forEach(line -> myMemory.addEnabledAction(line.trim()));
                        System.out.println("  -> " + agentId + " enabled actions (H): " + myMemory.getEnabledActions());
                    }

                    Budget myBudget = new Budget(energyPerAgent, resPerAgent);
                    Agent agent = new Agent(agentId, myMemory, myBudget, channel);
                    agent.setGroupId(groupId);
                    agent.setGroupSize(numAgents); // |G| for mental cost sharing C1/|G|

                    // Load Personal Goal
                    List<String> goalLines = FileLoader.readRealFile(agentPath.resolve("personal_goal.ldinf").toString());
                    if (!goalLines.isEmpty()) agent.setGoal(goalLines.get(0));

                    allAgents.add(agent);
                    group.addAgent(agent);
                    channel.register(agent);
                }
                
                allGroups.add(group);
            }

        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // 8. Simulation Loop
        System.out.println("\n--- STARTING SIMULATION ---");
        boolean allGroupsDone = false;
        int maxSteps = 15;
        int step = 0;

        while (!allGroupsDone && step < maxSteps) {
            step++;
            System.out.println("\n>>> STEP " + step);
            boolean anyoneDidAnything = false;
            
            // 8a. Ogni agente ragiona e agisce
            for (Agent a : allAgents) {
                boolean acted = a.reasonAndAct(); 
                if (acted) anyoneDidAnything = true;
            }
            
            // 8b. Controlla se qualche gruppo è in stallo e tenta il PRESTITO
            for (Group group : allGroups) {
                if (!group.allGoalsMet() && group.isStalled()) {
                    System.out.println(">>> STALL DETECTED: Gruppo " + group.getGroupId() 
                            + " non riesce a progredire. Tentativo di prestito agente...");
                    
                    List<Group> otherGroups = allGroups.stream()
                            .filter(g -> g != group)
                            .toList();
                    
                    boolean borrowed = group.tryBorrowAgent(otherGroups);
                    if (borrowed) {
                        anyoneDidAnything = true;
                        System.out.println(">>> LENDING SUCCESS: Un agente prestato ha aiutato il gruppo " 
                                + group.getGroupId());
                    } else {
                        System.out.println(">>> LENDING FAILED: Nessun agente disponibile da prestare al gruppo " 
                                + group.getGroupId());
                    }
                }
            }

            // 8c. Verifica se tutti i gruppi hanno raggiunto i loro goal
            allGroupsDone = allGroups.stream().allMatch(Group::allGoalsMet);
            if (allGroupsDone) {
                System.out.println(">>> ALL GROUPS ACHIEVED THEIR GOALS at step " + step);
            }

            if (!anyoneDidAnything && !allGroupsDone) {
                System.out.println(">>> GLOBAL STALL: No agents performed actions this step.");
                break;
            }
        }
        
        // 9. Final Report
        System.out.println("\n--- FINAL REPORT ---");
        for (Group group : allGroups) {
            System.out.println("Group: " + group.getGroupId());
            
            for (Atom commonGoal : group.getCommonGoals()) {
                boolean isGoalMet = group.isGoalMet(commonGoal);
                System.out.println("  Goal (" + commonGoal + ") met? " + (isGoalMet ? "YES" : "NO"));
                
                if (isGoalMet) {
                    group.getAgents().stream()
                        .filter(a -> a.getMemory().getWorkingMemory().contains(commonGoal))
                        .forEach(a -> System.out.println("     -> Known by " + a));
                }
            }
            
            // Report budget residuo
            System.out.println("  Agent budgets:");
            for (Agent a : group.getAgents()) {
                // Mostra info basilari - il budget dettagliato è nell'oggetto Budget
                System.out.println("     " + a + " - completed goals in WM: " 
                        + a.getMemory().getWorkingMemory().stream()
                            .filter(atom -> atom.predicate().equals("done"))
                            .toList());
            }
        }
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