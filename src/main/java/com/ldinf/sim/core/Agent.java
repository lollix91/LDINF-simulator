package com.ldinf.sim.core;

import com.ldinf.sim.model.*;
import java.util.*;

public class Agent {
    private final String id;
    private final AgentMemory memory;
    private final Budget budget;
    private final CommunicationChannel comms;
    
    // Goal Personale
    private Atom personalGoal;
    private boolean goalAchieved = false;

    // --- COSTRUTTORE MANUALE (Sostituisce @RequiredArgsConstructor) ---
    public Agent(String id, AgentMemory memory, Budget budget, CommunicationChannel comms) {
        this.id = id;
        this.memory = memory;
        this.budget = budget;
        this.comms = comms;
    }

    public void setGoal(String goalPredicate) {
        this.personalGoal = LDinfParser.parse(goalPredicate) instanceof Atom a ? a : null;
    }

    // Ciclo di ragionamento (Simulazione step)
    public boolean reasonAndAct() {
        if (goalAchieved) return true;
        
        // Uso i getter espliciti ora presenti in AgentMemory
        if (memory.getWorkingMemory().contains(personalGoal)) {
            goalAchieved = true;
            System.out.println("AGENTE " + id + ": Goal raggiunto! -> " + personalGoal);
            return true;
        }

        System.out.println("AGENTE " + id + ": Ragiona su come ottenere " + personalGoal);

        Optional<Implication> rule = memory.getKnowledgeBase().stream()
                .filter(impl -> impl.conclusion().equals(personalGoal))
                .findFirst();

        if (rule.isPresent()) {
            Implication i = rule.get();
            System.out.println("AGENTE " + id + ": Trovata regola utile: " + i);
            
            boolean premisesMet = checkPremises(i.premises());
            
            if (premisesMet) {
                String actionName = i.conclusion().args().isEmpty() ? i.conclusion().predicate() : i.conclusion().args().get(0);
                ActionCost cost = memory.getActionCosts().get(actionName);
                
                if (cost != null) {
                    System.out.println("AGENTE " + id + ": Tenta azione fisica " + actionName + " Costo M:" + cost.mentalCost());
                    if (budget.tryConsume(cost.mentalCost(), cost.physicalResources())) {
                        System.out.println("AGENTE " + id + ": *** AZIONE ESEGUITA *** " + actionName);
                        memory.getWorkingMemory().add(personalGoal);
                        comms.broadcast(id, personalGoal); 
                        return true;
                    } else {
                        System.out.println("AGENTE " + id + ": Budget insufficiente per " + actionName);
                    }
                } else {
                    if (budget.tryConsume(1, Map.of())) {
                        System.out.println("AGENTE " + id + ": Inferenza logica eseguita.");
                        memory.getWorkingMemory().add(personalGoal);
                        return true;
                    }
                }
            } else {
                 System.out.println("AGENTE " + id + ": Precondizioni non soddisfatte per la regola.");
            }
        }
        
        return false;
    }
    
    private boolean checkPremises(List<Atom> premises) {
        return premises.stream().allMatch(p -> memory.getWorkingMemory().contains(p));
    }

    public void receiveMessage(Atom info) {
        if (!memory.getWorkingMemory().contains(info)) {
            System.out.println("AGENTE " + id + ": Ricevuto info " + info);
            memory.getWorkingMemory().add(info);
        }
    }
    
    @Override
    public String toString() {
        return "Agent{" + "id='" + id + '\'' + '}';
    }
    
    public AgentMemory getMemory() { return memory; }
}