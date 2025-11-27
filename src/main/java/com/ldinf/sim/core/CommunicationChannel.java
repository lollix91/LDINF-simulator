package com.ldinf.sim.core;

import com.ldinf.sim.model.Atom;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

// Canale di comunicazione locale
public class CommunicationChannel {
    private final List<Agent> subscribers = new ArrayList<>();

    public void register(Agent a) { subscribers.add(a); }

    public void broadcast(String senderId, Atom info) {
        for (Agent a : subscribers) {
            // Un agente non invia messaggi a se stesso
            if (!a.toString().contains(senderId)) { 
                a.receiveMessage(info);
            }
        }
    }
}