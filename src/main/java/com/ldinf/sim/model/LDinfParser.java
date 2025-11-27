package com.ldinf.sim.model;

import java.util.*;
import java.util.stream.Collectors;

public class LDinfParser {
    
    public static Formula parse(String line) {
        line = line.trim();
        if (line.endsWith(".")) line = line.substring(0, line.length() - 1);

        if (line.startsWith("cost")) return parseCost(line);
        if (line.contains("->")) return parseImplication(line);
        return parseAtom(line);
    }

    private static Atom parseAtom(String text) {
        text = text.trim();
        if (!text.contains("(")) return new Atom(text, List.of());
        
        String predicate = text.substring(0, text.indexOf("(")).trim();
        String argsPart = text.substring(text.indexOf("(") + 1, text.lastIndexOf(")"));
        
        List<String> args = Arrays.stream(argsPart.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
        return new Atom(predicate, args);
    }

    private static Implication parseImplication(String text) {
        String[] parts = text.split("->");
        Atom conclusion = parseAtom(parts[1]);
        String separator = parts[0].contains("&") ? "&" : ",";
        List<Atom> premises = Arrays.stream(parts[0].split(separator))
                .map(LDinfParser::parseAtom)
                .collect(Collectors.toList());
        return new Implication(premises, conclusion);
    }

    // Syntax: cost(action, ENERGY:10, flour:5)
    private static ActionCost parseCost(String line) {
        String content = line.substring(line.indexOf("(") + 1, line.lastIndexOf(")"));
        String[] parts = content.split(",");
        
        String action = parts[0].trim();
        int energyCost = 0; // Costo Mentale (B1)
        Map<String, Integer> pRes = new HashMap<>(); // Risorse Fisiche (B2)
        
        for(int i=1; i<parts.length; i++) {
            String part = parts[i].trim();
            String[] kv = part.split(":");
            if (kv.length < 2) continue;
            
            String key = kv[0].trim();
            int val = Integer.parseInt(kv[1].trim());
            
            // PAPER COMPLIANCE: "ENERGY" is the mental resource
            if(key.equalsIgnoreCase("ENERGY") || key.equalsIgnoreCase("MENTAL")) energyCost = val;
            else pRes.put(key, val);
        }
        return new ActionCost(action, energyCost, pRes);
    }
}