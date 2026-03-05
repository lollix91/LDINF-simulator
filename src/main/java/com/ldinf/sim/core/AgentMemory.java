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
    
    private final List<Atom> groupGoals = new ArrayList<>();
    // H(i,w): set of physical actions that agent i is enabled by its group to perform
    private final Set<String> enabledActions = new HashSet<>();

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
    public void addGroupGoal(Atom goal) {
        if (!groupGoals.contains(goal)) {
            this.groupGoals.add(goal);
        }
    }
    public List<Atom> getGroupGoals() {
        return groupGoals;
    }

    // --- H (Roles/Enabling) ---
    public void addEnabledAction(String actionName) {
        enabledActions.add(actionName);
    }
    public Set<String> getEnabledActions() { return enabledActions; }
    /**
     * Checks if an action is enabled for this agent: H(i,w).
     * If no enabled_actions are configured, all actions are enabled by default.
     */
    public boolean isActionEnabled(String actionName) {
        if (enabledActions.isEmpty()) return true; // default: all enabled
        return enabledActions.contains(actionName);
    }

    // --- NUOVI GETTER NECESSARI PER LA COPIA ---
    public List<Equivalence> getEquivalences() { return equivalences; }
    public Map<String, Integer> getPreferencesMap() { return preferences; }
}