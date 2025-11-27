package com.ldinf.sim.core;

import com.ldinf.sim.model.*;
import java.util.*;

public class AgentMemory {
    private final List<Implication> knowledgeBase = new ArrayList<>();
    private final Set<Atom> workingMemory = new HashSet<>();
    private final Map<String, ActionCost> actionCosts = new HashMap<>();
    
    private final Map<String, Integer> preferences = new HashMap<>(); 
    private final List<Equivalence> equivalences = new ArrayList<>(); 
    private int baseInferenceCost = 1; 
    
    private Atom groupGoal;

    public void addKnowledge(Formula f) {
        if (f == null) return;
        if (f instanceof Implication i) knowledgeBase.add(i);
        else if (f instanceof ActionCost c) actionCosts.put(c.actionName(), c);
        else if (f instanceof Atom a) workingMemory.add(a);
        else if (f instanceof Preference p) preferences.put(p.actionName(), p.value());
        else if (f instanceof Equivalence e) equivalences.add(e);
        else if (f instanceof InferenceCost ic) baseInferenceCost = ic.mentalCost();
    }

    public Set<String> getEquivalentActions(String action) {
        Set<String> result = new HashSet<>();
        result.add(action);
        for (Equivalence eq : equivalences) {
            if (eq.action1().equals(action)) result.add(eq.action2());
            else if (eq.action2().equals(action)) result.add(eq.action1());
        }
        return result;
    }

    public int getPreference(String action) {
        return preferences.getOrDefault(action, 0); 
    }

    public int getBaseInferenceCost() { return baseInferenceCost; }

    // --- GETTER ESISTENTI ---
    public List<Implication> getKnowledgeBase() { return knowledgeBase; }
    public Set<Atom> getWorkingMemory() { return workingMemory; }
    public Map<String, ActionCost> getActionCosts() { return actionCosts; }
    public void setGroupGoal(Atom goal) { this.groupGoal = goal; }
    public Atom getGroupGoal() { return groupGoal; }

    // --- NUOVI GETTER NECESSARI PER LA COPIA ---
    public List<Equivalence> getEquivalences() { return equivalences; }
    public Map<String, Integer> getPreferencesMap() { return preferences; }
}