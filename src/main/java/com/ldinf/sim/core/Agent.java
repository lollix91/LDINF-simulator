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
        List<Atom> groupGoals = memory.getGroupGoals();
        for (Atom targetGoal : groupGoals) {
            // Se il goal non è ancora stato raggiunto (non è in working memory)
            if (!memory.getWorkingMemory().contains(targetGoal)) {
                // Proviamo a soddisfarlo
                if (performBestActionFor(targetGoal)) {
                    actionTaken = true;
                    comms.broadcast(id, targetGoal);
                    
                    // Opzionale: se un agente può fare solo un'azione per turno, 
                    // aggiungi 'break' qui per fermare l'agente dopo il primo successo.
                    break; 
                }
            }
        }
        return actionTaken;
    }

    private boolean performBestActionFor(Atom targetGoal) {
        // 1. Trova tutte le regole che CONCLUDONO con il goal target
        List<Implication> potentialRules = memory.getKnowledgeBase().stream()
                .filter(impl -> impl.conclusion().equals(targetGoal))
                .collect(Collectors.toList());

        if (potentialRules.isEmpty()) return false;

        List<ActionCandidate> candidates = new ArrayList<>();

        for (Implication rule : potentialRules) {
            // Dividiamo le premesse in soddifatte e mancanti
            List<Atom> missingPremises = rule.premises().stream()
                    .filter(p -> !memory.getWorkingMemory().contains(p))
                    .collect(Collectors.toList());

            // CASO A: Tutte le premesse sono soddisfatte (es. has(ingr) -> cook)
            // L'azione da fare è direttamente la conclusione della regola
            if (missingPremises.isEmpty()) {
                String baseActionName = extractActionName(rule.conclusion());
                generateCandidatesForAction(baseActionName, candidates);
            }
            
            // CASO B: Mancano delle premesse (es. ... & done(clear_table) -> clean)
            // Controlliamo se una delle premesse mancanti è un'azione che possiamo fare
            else {
                for (Atom missing : missingPremises) {
                    String missingActionName = extractActionName(missing);
                    // Se la premessa mancante è un'azione fattibile (ha un costo o è equivalente a una che lo ha)
                    // Allora proviamo a eseguire QUELLA azione per sbloccare la regola.
                    generateCandidatesForAction(missingActionName, candidates);
                }
            }
        }

        if (candidates.isEmpty()) {
            return false;
        }

        // C. Selezione (Funzione F) - Invariata
        candidates.sort(Comparator
                .comparingInt(ActionCandidate::preference).reversed()
                .thenComparingInt(c -> sumResources(c.cost().physicalResources()))
        );

        ActionCandidate best = candidates.get(0);

        // Esecuzione
        if (best.isPhysical()) {
            System.out.println("AGENTE " + id + ": Selezionata azione fisica '" + best.name() + "' (Pref: " + best.preference() + ")");
            budget.consume(best.cost().mentalCost(), best.cost().physicalResources());
            
            // NOTA: Qui costruiamo l'atomo di risultato. 
            // Se l'azione era un sotto-goal (es. clear_table), l'atomo prodotto è done(clear_table).
            // Se era un goal diretto (cook_main_dish), è done(cook_main_dish).
            Atom resultAtom = Atom.of("done", best.name()); // Assumiamo convenzione done(X)
            
            System.out.println("AGENTE " + id + ": *** ESEGUITO *** " + best.name() + " -> " + resultAtom);
            memory.getWorkingMemory().add(resultAtom);
            comms.broadcast(id, resultAtom);
            
            // IMPORTANTE: Se abbiamo agito per un sotto-goal, il goal principale (targetGoal)
            // non è ancora raggiunto, lo sarà per inferenza al prossimo step.
            // Se invece era un'azione diretta (Caso A), aggiungiamo anche il targetGoal.
            if (ruleConclusionMatches(best.name(), targetGoal)) {
                 memory.getWorkingMemory().add(targetGoal);
            }

        } else {
            // Inferenza pura
            System.out.println("AGENTE " + id + ": Inferenza logica '" + best.name() + "' (Costo Energy: " + best.cost().mentalCost() + ")");
            budget.consume(best.cost().mentalCost(), Collections.emptyMap());
            memory.getWorkingMemory().add(targetGoal); // L'inferenza produce direttamente il goal
        }
        
        return true;
    }

    // Metodo helper per generare candidati dato un nome azione (astrae la logica di equivalenza)
    private void generateCandidatesForAction(String actionName, List<ActionCandidate> candidates) {
        Set<String> equivalentActions = memory.getEquivalentActions(actionName);
        for (String actName : equivalentActions) {
            ActionCost cost = memory.getActionCosts().get(actName);
            
            // 1. Azione Fisica
            if (cost != null) {
                if (budget.canAfford(cost.mentalCost(), cost.physicalResources())) {
                    int pref = memory.getPreference(actName);
                    candidates.add(new ActionCandidate(actName, cost, pref, true));
                }
            } 
            // 2. Inferenza (Azione Mentale)
            else {
                // Verifichiamo che non sia un'astrazione di un fisico (es. cook_main_dish)
                boolean isAbstractForPhysical = equivalentActions.stream()
                        .anyMatch(eq -> memory.getActionCosts().containsKey(eq));

                if (!isAbstractForPhysical) {
                    int mentalCost = memory.getBaseInferenceCost();
                    if (budget.canAfford(mentalCost, Collections.emptyMap())) {
                        ActionCost infCost = new ActionCost(actName, mentalCost, Collections.emptyMap());
                        candidates.add(new ActionCandidate(actName, infCost, 0, false));
                    }
                }
            }
        }
    }

    // Helper per verificare se l'azione eseguita soddisfa direttamente il goal richiesto
    private boolean ruleConclusionMatches(String actionName, Atom targetGoal) {
        String targetName = extractActionName(targetGoal);
        return memory.getEquivalentActions(actionName).contains(targetName);
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