package com.ldinf.sim.core;

import com.ldinf.sim.model.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Rappresenta un gruppo di agenti con goal comuni, budget condiviso e knowledge base.
 * Gestisce anche il prestito di agenti da/verso altri gruppi.
 */
public class Group {
    private final String groupId;
    private final List<Agent> agents = new ArrayList<>();
    private final List<Atom> commonGoals = new ArrayList<>();
    private final Budget totalBudget;
    private final AgentMemory sharedMemoryTemplate;

    // LENDING: controlla se il gruppo partecipa al prestito agenti
    private boolean lendingEnabled = true;

    // Agenti attualmente prestati AD altri gruppi
    private final Set<Agent> lentOut = new HashSet<>();
    // Agenti attualmente presi IN PRESTITO da altri gruppi
    private final List<Agent> borrowed = new ArrayList<>();

    public Group(String groupId, Budget totalBudget, AgentMemory sharedMemoryTemplate) {
        this.groupId = groupId;
        this.totalBudget = totalBudget;
        this.sharedMemoryTemplate = sharedMemoryTemplate;
    }

    public void addAgent(Agent agent) {
        agents.add(agent);
    }

    public void addCommonGoal(Atom goal) {
        commonGoals.add(goal);
    }

    // --- LENDING (Prestito) ---

    /**
     * Verifica se il gruppo è in stallo: nessun agente disponibile (non prestato)
     * riesce a fare progressi sui goal comuni non ancora raggiunti.
     */
    public boolean isStalled() {
        List<Atom> unmetGoals = getUnmetGoals();
        if (unmetGoals.isEmpty()) return false;

        for (Agent a : getAvailableAgents()) {
            for (Atom goal : unmetGoals) {
                if (a.canMakeProgressOn(goal)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Restituisce i goal comuni non ancora raggiunti da nessun agente del gruppo.
     */
    public List<Atom> getUnmetGoals() {
        return commonGoals.stream()
                .filter(goal -> !isGoalMet(goal))
                .collect(Collectors.toList());
    }

    /**
     * Verifica se un goal è stato raggiunto da almeno un agente del gruppo
     * (inclusi quelli in prestito).
     */
    public boolean isGoalMet(Atom goal) {
        return getAllActiveAgents().stream()
                .anyMatch(a -> a.getMemory().getWorkingMemory().contains(goal));
    }

    /**
     * Verifica se TUTTI i goal comuni sono stati raggiunti.
     */
    public boolean allGoalsMet() {
        return commonGoals.stream().allMatch(this::isGoalMet);
    }

    /**
     * Agenti propri del gruppo che NON sono attualmente prestati ad altri.
     */
    public List<Agent> getAvailableAgents() {
        return agents.stream()
                .filter(a -> !lentOut.contains(a))
                .collect(Collectors.toList());
    }

    /**
     * Tutti gli agenti attivi nel gruppo: propri (non prestati) + quelli presi in prestito.
     */
    public List<Agent> getAllActiveAgents() {
        List<Agent> active = new ArrayList<>(getAvailableAgents());
        active.addAll(borrowed);
        return active;
    }

    /**
     * Tenta di prendere in prestito un agente da un altro gruppo per soddisfare
     * un goal non raggiungibile internamente.
     * 
     * La logica decompone i goal non raggiunti nelle loro premesse mancanti,
     * poi cerca agenti in altri gruppi capaci di soddisfare quelle premesse specifiche.
     * 
     * @param otherGroups lista di tutti gli altri gruppi
     * @return true se è stato preso in prestito un agente e ha agito con successo
     */
    public boolean tryBorrowAgent(List<Group> otherGroups) {
        List<Atom> unmetGoals = getUnmetGoals();
        if (unmetGoals.isEmpty()) return false;

        // Raccogli tutte le premesse mancanti (sotto-goal) dai goal non raggiunti
        Set<Atom> missingSubGoals = new LinkedHashSet<>();
        for (Atom goal : unmetGoals) {
            missingSubGoals.addAll(findMissingPremises(goal));
        }
        
        // Se non ci sono sotto-goal specifici, prova direttamente con i goal
        if (missingSubGoals.isEmpty()) {
            missingSubGoals.addAll(unmetGoals);
        }

        System.out.println(">>> LENDING ANALYSIS [" + groupId + "]: Sotto-goal mancanti: " + missingSubGoals);

        for (Atom subGoal : missingSubGoals) {
            // Cerca in ogni altro gruppo un agente che possa aiutare
            for (Group otherGroup : otherGroups) {
                if (otherGroup == this) continue;

                for (Agent candidate : otherGroup.getAvailableAgents()) {
                    if (candidate.canMakeProgressOn(subGoal)) {
                        System.out.println(">>> LENDING: Gruppo " + groupId 
                                + " prende in prestito " + candidate 
                                + " dal gruppo " + otherGroup.getGroupId()
                                + " per il sotto-goal " + subGoal);

                        // Segna il prestito
                        otherGroup.lentOut.add(candidate);
                        this.borrowed.add(candidate);

                        // L'agente prestato agisce per il sotto-goal
                        boolean acted = candidate.performBestActionFor(subGoal);
                        
                        if (acted) {
                            System.out.println(">>> LENDING: " + candidate 
                                    + " ha completato l'azione per il gruppo " + groupId);
                            
                            // Condividi il risultato con gli agenti del gruppo richiedente
                            for (Agent a : getAvailableAgents()) {
                                for (Atom fact : candidate.getMemory().getWorkingMemory()) {
                                    a.receiveMessage(fact);
                                }
                            }
                        }

                        // Restituisci l'agente al gruppo originale
                        otherGroup.lentOut.remove(candidate);
                        this.borrowed.remove(candidate);

                        if (acted) return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Trova le premesse mancanti per un goal analizzando la knowledge base del gruppo.
     * Esempio: se il goal è done(organize_event) e la regola è
     *   done(decorate_venue) & done(cook_main_dish) -> done(organize_event)
     * e done(decorate_venue) è già nella WM, restituisce [done(cook_main_dish)].
     */
    private Set<Atom> findMissingPremises(Atom goal) {
        Set<Atom> missing = new LinkedHashSet<>();
        
        // Usa la KB condivisa del gruppo per trovare le regole
        for (Implication rule : sharedMemoryTemplate.getKnowledgeBase()) {
            if (rule.conclusion().equals(goal)) {
                for (Atom premise : rule.premises()) {
                    // Controlla se qualche agente del gruppo ha già questa premessa
                    boolean anyoneHasIt = getAllActiveAgents().stream()
                            .anyMatch(a -> a.getMemory().getWorkingMemory().contains(premise));
                    if (!anyoneHasIt) {
                        missing.add(premise);
                    }
                }
            }
        }
        
        // Ricorsione: decomponi ulteriormente le premesse mancanti
        Set<Atom> deepMissing = new LinkedHashSet<>();
        for (Atom m : missing) {
            Set<Atom> subMissing = findMissingPremises(m);
            if (!subMissing.isEmpty()) {
                deepMissing.addAll(subMissing);
            } else {
                deepMissing.add(m); // Foglia: non decomponibile ulteriormente
            }
        }
        
        return deepMissing;
    }

    // --- MENTAL COST SHARING (Paper: enabled_w, doer_w, B1 update) ---

    /**
     * Returns min_{h∈G} B1(h,w): the minimum mental budget across all available agents.
     * Used for the enabled_w condition from the paper.
     */
    public int getMinMentalBudget() {
        return getAvailableAgents().stream()
                .mapToInt(a -> a.getBudget().getMentalBudget())
                .min()
                .orElse(0);
    }

    /**
     * Deducts sharedCost = C1/|G| from ALL agents in the group.
     * Paper: B1^{[G:α]}(i,w) = B1(i,w) - C1(i,α,w)/|G| for each i ∈ G.
     * The doer agent has already paid via budget.consume(); we deduct from the others.
     */
    public void deductMentalCostFromAll(int sharedCost, Agent doer) {
        for (Agent a : getAvailableAgents()) {
            if (a != doer) {
                a.getBudget().consume(sharedCost, Collections.emptyMap());
            }
        }
    }

    // --- GETTER ---
    public String getGroupId() { return groupId; }
    public List<Agent> getAgents() { return agents; }
    public List<Atom> getCommonGoals() { return commonGoals; }
    public Budget getTotalBudget() { return totalBudget; }
    public AgentMemory getSharedMemoryTemplate() { return sharedMemoryTemplate; }
    public boolean isLendingEnabled() { return lendingEnabled; }
    public void setLendingEnabled(boolean lendingEnabled) { this.lendingEnabled = lendingEnabled; }

    @Override
    public String toString() {
        return "Group{" + groupId + ", agents=" + agents.size() + ", goals=" + commonGoals + "}";
    }
}
