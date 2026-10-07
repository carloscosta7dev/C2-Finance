package com.scanner.financeiro;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void mostraAjudaQuandoNaoHaComando() {
        PrintStream saidaOriginal = System.out;
        ByteArrayOutputStream captura = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captura, true, StandardCharsets.UTF_8));
            Main.main(new String[0]);
        } finally {
            System.setOut(saidaOriginal);
        }

        String texto = captura.toString(StandardCharsets.UTF_8);
        assertTrue(texto.contains("Comandos disponíveis"));
        assertTrue(texto.contains("loadtest"));
        assertTrue(texto.contains("monitor"));
        assertTrue(texto.contains("backtest"));
        assertTrue(texto.contains("dashboard"));
    }
}