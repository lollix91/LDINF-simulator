package com.ldinf.sim;

import com.ldinf.sim.core.*;
import com.ldinf.sim.model.*;
import java.io.IOException;
import java.util.*;

public class SimulationMain {

    public static void main(String[] args) {
        System.out.println("--- L-DINF SIMULATION ---");

        if (args.length == 0) {
            System.err.println("Uso: java com.ldinf.sim.SimulationMain <file.ldinf>");
            System.err.println("Esempio: java com.ldinf.sim.SimulationMain examples/banquet.ldinf");
            return;
        }

        // 1. Caricamento diretto dal file .ldinf
        DirectLDinfLoader.LoadResult loaded;
        try {
            loaded = DirectLDinfLoader.load(args[0]);
        } catch (IOException e) {
            System.err.println("ERRORE nel caricamento del file .ldinf: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        List<Agent> allAgents = loaded.allAgents;
        List<Group> allGroups = loaded.groups;

        // 2. Simulation Loop
        System.out.println("--- STARTING SIMULATION ---");
        boolean allGroupsDone = false;
        int maxSteps = 15;
        int step = 0;

        while (!allGroupsDone && step < maxSteps) {
            step++;
            System.out.println("\n>>> STEP " + step);
            boolean anyoneDidAnything = false;
            
            // 2a. Ogni agente ragiona e agisce
            for (Agent a : allAgents) {
                boolean acted = a.reasonAndAct(); 
                if (acted) anyoneDidAnything = true;
            }
            
            // 2b. Controlla se qualche gruppo è in stallo e tenta il PRESTITO (LENDING)
            for (Group group : allGroups) {
                if (!group.isLendingEnabled()) continue;
                if (!group.allGoalsMet() && group.isStalled()) {
                    System.out.println(">>> STALL DETECTED: Gruppo " + group.getGroupId() 
                            + " non riesce a progredire. Tentativo di prestito agente...");
                    
                    List<Group> lendingGroups = allGroups.stream()
                            .filter(g -> g != group && g.isLendingEnabled())
                            .toList();
                    
                    boolean borrowed = group.tryBorrowAgent(lendingGroups);
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

            // 2c. Verifica se tutti i gruppi hanno raggiunto i loro goal
            allGroupsDone = allGroups.stream().allMatch(Group::allGoalsMet);
            if (allGroupsDone) {
                System.out.println(">>> ALL GROUPS ACHIEVED THEIR GOALS at step " + step);
            }

            if (!anyoneDidAnything && !allGroupsDone) {
                System.out.println(">>> GLOBAL STALL: No agents performed actions this step.");
                break;
            }
        }
        
        // 3. Final Report
        System.out.println("\n--- FINAL REPORT ---");
        for (Group group : allGroups) {
            System.out.println("Group: " + group.getGroupId() 
                    + " (lending=" + (group.isLendingEnabled() ? "enabled" : "disabled") + ")");
            
            for (Atom commonGoal : group.getCommonGoals()) {
                boolean isGoalMet = group.isGoalMet(commonGoal);
                System.out.println("  G(" + commonGoal + ") met? " + (isGoalMet ? "YES" : "NO"));
                
                if (isGoalMet) {
                    group.getAgents().stream()
                        .filter(a -> a.getMemory().getWorkingMemory().contains(commonGoal))
                        .forEach(a -> System.out.println("     -> Known by " + a));
                }
            }
            
            // Report budget residuo
            System.out.println("  Agent budgets:");
            for (Agent a : group.getAgents()) {
                System.out.println("     " + a 
                        + " - B1(remaining)=" + a.getBudget().getMentalBudget()
                        + ", done=" + a.getMemory().getWorkingMemory().stream()
                            .filter(atom -> atom.predicate().equals("done"))
                            .toList());
            }
        }
    }
}