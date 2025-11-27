package com.ldinf.sim.core;

import java.util.*;

public class Budget {
    private int mentalBudget; // ENERGY [cite: 41]
    private Map<String, Integer> physicalResources;

    public Budget(int mental, Map<String, Integer> physical) {
        this.mentalBudget = mental;
        this.physicalResources = new HashMap<>(physical);
    }

    public int getMentalBudget() { return mentalBudget; }
    public Map<String, Integer> getPhysicalResources() { return physicalResources; }

    // Check preventivo (per il selettore F)
    public boolean canAfford(int mentalCost, Map<String, Integer> physicalCosts) {
        if (this.mentalBudget < mentalCost) return false;
        for (var entry : physicalCosts.entrySet()) {
            if (this.physicalResources.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    // Consumo effettivo (dopo la selezione)
    public void consume(int mentalCost, Map<String, Integer> physicalCosts) {
        this.mentalBudget -= mentalCost;
        physicalCosts.forEach((res, amount) -> 
            this.physicalResources.merge(res, -amount, Integer::sum));
    }
    
    // Manteniamo tryConsume per retrocompatibilità se serve, o lo rimuoviamo
}