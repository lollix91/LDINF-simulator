package com.ldinf.sim.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FileLoader {
    
    // Legge tutte le righe di un file reale, ignorando commenti (#) e righe vuote
    public static List<String> readRealFile(String filePath) {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            System.err.println("ERRORE: File non trovato -> " + path.toAbsolutePath());
            return Collections.emptyList();
        }

        try {
            return Files.readAllLines(path).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#")) // Ignora commenti
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}