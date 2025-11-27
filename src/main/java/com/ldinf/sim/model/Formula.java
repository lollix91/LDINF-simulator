package com.ldinf.sim.model;

public sealed interface Formula permits Atom, Implication, ActionCost {}
