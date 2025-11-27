package com.ldinf.sim.core;

import com.ldinf.sim.model.*;
import java.util.*;

public class AgentMemory {
    private final List<Implication> knowledgeBase = new ArrayList<>();
    private final Set<Atom> workingMemory = new HashSet<>();
    private final Map<String, ActionCost> actionCosts = new HashMap<>();
    
    // NUOVO: Goal Comune del gruppo
    private Atom groupGoal;

    public void addKnowledge(Formula f) {
        if (f instanceof Implication i) knowledgeBase.add(i);
        else if (f instanceof ActionCost c) actionCosts.put(c.actionName(), c);
        else if (f instanceof Atom a) workingMemory.add(a); 
    }

    public void setGroupGoal(Atom goal) {
        this.groupGoal = goal;
    }

    public Atom getGroupGoal() {
        return groupGoal;
    }

    // ... (metodi getter precedenti: getKnowledgeBase, getWorkingMemory, getActionCosts)
    public List<Implication> getKnowledgeBase() { return knowledgeBase; }
    public Set<Atom> getWorkingMemory() { return workingMemory; }
    public Map<String, ActionCost> getActionCosts() { return actionCosts; }
}