package com.ldinf.sim.model;

import java.util.*;
import java.util.stream.Collectors;

public class LDinfParser {
    
    public static Formula parse(String line) {
        line = line.trim();
        if (line.endsWith(".")) line = line.substring(0, line.length() - 1);
        if (line.isEmpty() || line.startsWith("#")) return null;

        if (line.startsWith("cost")) return parseCost(line);
        if (line.startsWith("pref")) return parsePreference(line);
        if (line.startsWith("equiv")) return parseEquivalence(line);
        if (line.startsWith("inference_cost")) return parseInferenceCost(line);
        if (line.contains("->")) return parseImplication(line);
        return parseAtom(line);
    }

    // pref(cook_pasta, 10)
    private static Preference parsePreference(String line) {
        String content = extractContent(line);
        String[] parts = content.split(",");
        return new Preference(parts[0].trim(), Integer.parseInt(parts[1].trim()));
    }

    // equiv(cook_pasta, cook_rice)
    private static Equivalence parseEquivalence(String line) {
        String content = extractContent(line);
        String[] parts = content.split(",");
        return new Equivalence(parts[0].trim(), parts[1].trim());
    }

    // inference_cost(2) -> Costo di base per ogni step logico (vdash)
    private static InferenceCost parseInferenceCost(String line) {
        String content = extractContent(line);
        return new InferenceCost(Integer.parseInt(content.trim()));
    }

    // --- Metodi precedenti (parseCost, parseImplication, parseAtom) restano quasi uguali ---
    
    private static String extractContent(String line) {
        return line.substring(line.indexOf("(") + 1, line.lastIndexOf(")"));
    }

    private static Atom parseAtom(String text) {
        text = text.trim();
        if (!text.contains("(")) return new Atom(text, List.of());
        String predicate = text.substring(0, text.indexOf("(")).trim();
        String argsPart = extractContent(text);
        List<String> args = Arrays.stream(argsPart.split(","))
                .map(String::trim).collect(Collectors.toList());
        return new Atom(predicate, args);
    }

    private static Implication parseImplication(String text) {
        String[] parts = text.split("->");
        Atom conclusion = parseAtom(parts[1]);
        String separator = parts[0].contains("&") ? "&" : ",";
        List<Atom> premises = Arrays.stream(parts[0].split(separator))
                .map(LDinfParser::parseAtom).collect(Collectors.toList());
        return new Implication(premises, conclusion);
    }

    private static ActionCost parseCost(String line) {
        String content = extractContent(line);
        String[] parts = content.split(",");
        String action = parts[0].trim();
        int energyCost = 0;
        Map<String, Integer> pRes = new HashMap<>();
        for(int i=1; i<parts.length; i++) {
            String part = parts[i].trim();
            String[] kv = part.split(":");
            if (kv.length < 2) continue;
            String key = kv[0].trim();
            int val = Integer.parseInt(kv[1].trim());
            if(key.equalsIgnoreCase("ENERGY") || key.equalsIgnoreCase("MENTAL")) energyCost = val;
            else pRes.put(key, val);
        }
        return new ActionCost(action, energyCost, pRes);
    }
}