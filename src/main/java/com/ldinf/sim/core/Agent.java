package com.ldinf.sim.core;

import com.ldinf.sim.model.*;
import java.util.*;

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
        this.personalGoal = LDinfParser.parse(goalPredicate) instanceof Atom a ? a : null;
    }
    
    // Serve al Main per controllare lo stato finale
    public AgentMemory getMemory() { 
        return memory; 
    }

    // Ciclo di ragionamento
    public boolean reasonAndAct() {
        boolean actionTaken = false;

        // 1. Prima priorità: Raggiungere il Goal Personale
        if (personalGoal != null && !memory.getWorkingMemory().contains(personalGoal)) {
            if (attemptToDerive(personalGoal)) {
                actionTaken = true;
            }
        }

        // 2. Seconda priorità: Dedurre il Goal di Gruppo (se esiste e non è ancora noto)
        Atom groupGoal = memory.getGroupGoal();
        if (groupGoal != null && !memory.getWorkingMemory().contains(groupGoal)) {
            // Provo a dedurlo solo se ho già soddisfatto il personale o se non posso fare altro
            // (Nel paper gli agenti sono cooperativi, quindi ci provano sempre)
            System.out.println("AGENTE " + id + ": Controlla se può dedurre il goal comune " + groupGoal);
            if (attemptToDerive(groupGoal)) {
                actionTaken = true;
                // Importante: Comunica agli altri che il goal comune è raggiunto!
                comms.broadcast(id, groupGoal);
            }
        }

        return actionTaken;
    }

    // Logica di inferenza generica (valida per qualsiasi goal)
    private boolean attemptToDerive(Atom targetGoal) {
        // Cerca una regola che concluda con il targetGoal
        Optional<Implication> rule = memory.getKnowledgeBase().stream()
                .filter(impl -> impl.conclusion().equals(targetGoal))
                .findFirst();

        if (rule.isPresent()) {
            Implication i = rule.get();
            
            // Verifica se le premesse sono soddisfatte nella Working Memory
            if (checkPremises(i.premises())) {
                
                // Determina il costo
                String actionName = i.conclusion().predicate(); // Usa il predicato come nome azione
                if (!i.conclusion().args().isEmpty()) {
                    actionName = i.conclusion().args().get(0); // O l'argomento (es. "cook" in done(cook))
                }
                
                ActionCost cost = memory.getActionCosts().get(actionName);
                
                // Caso A: Azione Fisica (ha un costo definito esplicitamente nel file)
                if (cost != null) {
                    System.out.println("AGENTE " + id + ": Tenta azione " + actionName + " (Costo Energy: " + cost.mentalCost() + ")");
                    if (budget.tryConsume(cost.mentalCost(), cost.physicalResources())) {
                        System.out.println("AGENTE " + id + ": *** AZIONE ESEGUITA *** " + targetGoal);
                        memory.getWorkingMemory().add(targetGoal);
                        comms.broadcast(id, targetGoal);
                        return true;
                    } else {
                        System.out.println("AGENTE " + id + ": Budget insufficiente per " + actionName);
                    }
                } 
                // Caso B: Inferenza Mentale Pura (es. done(A) & done(B) -> done(C))
                // Nel paper L-DINF anche le inferenze costano energia. Assumiamo costo 1 di default se non specificato.
                else {
                    // Verifichiamo se abbiamo budget per pensare (Costo fisso 1 Energy per inferenza semplice)
                    if (budget.tryConsume(1, Map.of())) {
                        System.out.println("AGENTE " + id + ": Inferenza logica riuscita -> " + targetGoal);
                        memory.getWorkingMemory().add(targetGoal);
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    private boolean checkPremises(List<Atom> premises) {
        return premises.stream().allMatch(p -> memory.getWorkingMemory().contains(p));
    }

    public void receiveMessage(Atom info) {
        if (!memory.getWorkingMemory().contains(info)) {
            // System.out.println("AGENTE " + id + ": Ricevuto info " + info); // Decommenta per debug verboso
            memory.getWorkingMemory().add(info);
        }
    }
    
    @Override
    public String toString() {
        return id;
    }
}