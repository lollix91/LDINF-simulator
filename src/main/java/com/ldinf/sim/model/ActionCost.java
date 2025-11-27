package com.ldinf.sim.model;

import java.util.Map;

//Definizione del costo di un'azione
public record ActionCost(String actionName, int mentalCost, Map<String, Integer> physicalResources) implements Formula {}
