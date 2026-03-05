package com.ldinf.sim.core;

import com.ldinf.sim.model.Atom;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

// Canale di comunicazione locale (group-aware)
public class CommunicationChannel {
    private final List<Agent> subscribers = new ArrayList<>();

    public void register(Agent a) { subscribers.add(a); }

    /**
     * Broadcast all'interno dello stesso gruppo dell'agente mittente.
     * Un agente non invia messaggi a se stesso.
     */
    public void broadcast(String senderId, Atom info) {
        // Ricava il groupId dal senderId (formato: "group_X_agent_Y")
        String senderGroup = extractGroupId(senderId);
        
        for (Agent a : subscribers) {
            if (!a.toString().equals(senderId) && senderGroup.equals(a.getGroupId())) {
                a.receiveMessage(info);
            }
        }
    }

    /**
     * Broadcast a TUTTI gli agenti di un gruppo specifico (usato durante il prestito).
     */
    public void broadcastToGroup(String groupId, String senderId, Atom info) {
        for (Agent a : subscribers) {
            if (!a.toString().equals(senderId) && groupId.equals(a.getGroupId())) {
                a.receiveMessage(info);
            }
        }
    }

    private String extractGroupId(String agentId) {
        // agentId ha formato "group_X_agent_Y" -> estraiamo "group_X"
        int idx = agentId.indexOf("_agent_");
        return idx > 0 ? agentId.substring(0, idx) : agentId;
    }
}