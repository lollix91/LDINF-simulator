package com.ldinf.sim.model;

import java.util.List;

//Una regola: SE premises SONO VERE -> ALLORA conclusion È VERA
public record Implication(List<Atom> premises, Atom conclusion) implements Formula {
 @Override
 public String toString() {
     return "IMPLICA(" + premises + " -> " + conclusion + ")";
 }
}
