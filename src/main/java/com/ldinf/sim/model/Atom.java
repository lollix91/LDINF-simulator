package com.ldinf.sim.model;

import java.util.Arrays;
import java.util.List;

//Un atomo semplice, es: "ha_farina", "fatto(cucinare)"
public record Atom(String predicate, List<String> args) implements Formula {
 @Override
 public String toString() {
     return predicate + (args.isEmpty() ? "" : "(" + String.join(",", args) + ")");
 }
 
 // Helper per creare atomi velocemente
 public static Atom of(String pred, String... args) {
     return new Atom(pred, Arrays.asList(args));
 }
}