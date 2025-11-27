package com.ldinf.sim.core;

import java.util.*;

public class Budget {
    private int mentalBudget; // Energia
    private Map<String, Integer> physicalResources; 

    // --- COSTRUTTORE MANUALE ---
    public Budget(int mental, Map<String, Integer> physical) {
        this.mentalBudget = mental;
        this.physicalResources = new HashMap<>(physical);
    }

    // --- GETTER E SETTER MANUALI ---
    public int getMentalBudget() {
        return mentalBudget;
    }

    public void setMentalBudget(int mentalBudget) {
        this.mentalBudget = mentalBudget;
    }

    public Map<String, Integer> getPhysicalResources() {
        return physicalResources;
    }

    public void setPhysicalResources(Map<String, Integer> physicalResources) {
        this.physicalResources = physicalResources;
    }

    // Verifica e consuma risorse (Transazionale)
    public boolean tryConsume(int mentalCost, Map<String, Integer> physicalCosts) {
        if (this.mentalBudget < mentalCost) return false;
        
        for (var entry : physicalCosts.entrySet()) {
            if (this.physicalResources.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        
        // Consuma
        this.mentalBudget -= mentalCost;
        physicalCosts.forEach((res, amount) -> 
            this.physicalResources.merge(res, -amount, Integer::sum));
        return true;
    }
}