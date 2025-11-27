package com.ldinf.sim.core;

import com.ldinf.sim.model.*;
import java.util.*;
import java.util.stream.Collectors;

public class Agent {
    private final String id;
    private final AgentMemory memory;
    private final Budget budget;
    private final CommunicationChannel comms;
    private Atom personalGoal;

    public Agent(String id, AgentMemory memory, Budget budget, CommunicationChannel comms) {
        this.id = id;
        this.memory = memory;
        this.budget = budget;
        this.comms = comms;
    }

    public void setGoal(String goalPredicate) {
        Formula f = LDinfParser.parse(goalPredicate);
        if (f instanceof Atom a) {
            this.personalGoal = a;
        }
    }
    
    public AgentMemory getMemory() { 
        return memory; 
    }

    public boolean reasonAndAct() {
        boolean actionTaken = false;
        
        // 1. Goal Personale
        if (personalGoal != null && !memory.getWorkingMemory().contains(personalGoal)) {
            if (performBestActionFor(personalGoal)) {
                actionTaken = true;
            }
        }

        // 2. Goal di Gruppo
        Atom groupGoal = memory.getGroupGoal();
        if (groupGoal != null && !memory.getWorkingMemory().contains(groupGoal)) {
            if (performBestActionFor(groupGoal)) {
                actionTaken = true;
                comms.broadcast(id, groupGoal);
            }
        }
        return actionTaken;
    }

    // IMPLEMENTAZIONE DEL SELETTORE F (Sezione 2.2 del paper)
    private boolean performBestActionFor(Atom targetGoal) {
        // A. Trova tutte le regole che portano al goal
        List<Implication> candidateRules = memory.getKnowledgeBase().stream()
                .filter(impl -> impl.conclusion().equals(targetGoal))
                .filter(impl -> checkPremises(impl.premises())) 
                .collect(Collectors.toList());

        if (candidateRules.isEmpty()) {
            return false;
        }

        // B. Costruisci l'insieme delle azioni "Candidabili"
        List<ActionCandidate> candidates = new ArrayList<>();

        for (Implication rule : candidateRules) {
            String baseActionName = extractActionName(rule.conclusion());
            
            // Trova classe di equivalenza (Cl) [cite: 99]
            Set<String> equivalentActions = memory.getEquivalentActions(baseActionName);

            for (String actName : equivalentActions) {
                ActionCost cost = memory.getActionCosts().get(actName);
                
                // CASO 1: Azione Fisica (ha un costo esplicito)
                if (cost != null) {
                    // Controlla Budget (B1 e B2) [cite: 251]
                    if (budget.canAfford(cost.mentalCost(), cost.physicalResources())) {
                        int pref = memory.getPreference(actName); // P(i,w,A) [cite: 183]
                        candidates.add(new ActionCandidate(actName, cost, pref, true));
                    }
                } 
                // CASO 2: Azione Mentale / Astratta
                else {
                    // FIX: Verifica se è un'azione astratta che ha corrispondenti fisici
                    // (stream catena corretta con parentesi e punto e virgola)
                    boolean isAbstractForPhysical = equivalentActions.stream()
                        .anyMatch(eq -> memory.getActionCosts().containsKey(eq));

                    if (!isAbstractForPhysical) {
                        int mentalCost = memory.getBaseInferenceCost();
                        if (budget.canAfford(mentalCost, Collections.emptyMap())) {
                            // Creiamo un costo fittizio per l'inferenza
                            ActionCost infCost = new ActionCost(actName, mentalCost, Collections.emptyMap());
                            candidates.add(new ActionCandidate(actName, infCost, 0, false));
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return false;
        }

        // C. Selezione (Funzione F) 
        // Criterio: Massima Preferenza, a parità di preferenza Minimo Costo
        // NOTA: Qui ho corretto c.cost in c.cost() perché è un record
        candidates.sort(Comparator
                .comparingInt(ActionCandidate::preference).reversed() // P desc
                .thenComparingInt(c -> sumResources(c.cost().physicalResources())) // Cost asc
        );

        ActionCandidate best = candidates.get(0);
        
        // Esecuzione
        if (best.isPhysical()) {
            System.out.println("AGENTE " + id + ": Selezionata azione fisica '" + best.name() + "' (Pref: " + best.preference() + ")");
            budget.consume(best.cost().mentalCost(), best.cost().physicalResources());
            System.out.println("AGENTE " + id + ": *** ESEGUITO *** " + best.name() + " -> " + targetGoal);
        } else {
            System.out.println("AGENTE " + id + ": Inferenza logica '" + best.name() + "' (Costo Energy: " + best.cost().mentalCost() + ")");
            budget.consume(best.cost().mentalCost(), Collections.emptyMap());
        }

        memory.getWorkingMemory().add(targetGoal);
        
        if(best.isPhysical()) {
            comms.broadcast(id, targetGoal);
        }
        
        return true;
    }

    private int sumResources(Map<String, Integer> res) {
        if (res == null) return 0;
        return res.values().stream().mapToInt(Integer::intValue).sum();
    }

    private String extractActionName(Atom atom) {
        return atom.args().isEmpty() ? atom.predicate() : atom.args().get(0);
    }

    private boolean checkPremises(List<Atom> premises) {
        return premises.stream().allMatch(p -> memory.getWorkingMemory().contains(p));
    }

    public void receiveMessage(Atom info) {
        if (!memory.getWorkingMemory().contains(info)) {
            memory.getWorkingMemory().add(info);
        }
    }
    
    @Override 
    public String toString() { 
        return id; 
    }

    // Record interno per la selezione
    private record ActionCandidate(String name, ActionCost cost, int preference, boolean isPhysical) {}
}