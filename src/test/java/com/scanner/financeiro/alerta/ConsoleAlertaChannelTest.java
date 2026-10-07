package com.scanner.financeiro.alerta;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleAlertaChannelTest {

    @Test
    void imprimeTituloMensagemELinhasComDestaqueAnsi() {
        PrintStream saidaOriginal = System.out;
        ByteArrayOutputStream captura = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captura, true, StandardCharsets.UTF_8));
            new ConsoleAlertaChannel().enviar("BTC caiu", "Preço atual: 90");
        } finally {
            System.setOut(saidaOriginal);
        }

        String texto = captura.toString(StandardCharsets.UTF_8);
        assertTrue(texto.contains("ALERTA: BTC caiu"));
        assertTrue(texto.contains("Preço atual: 90"));
        assertTrue(texto.contains("\u001B[31m"));
        assertTrue(texto.contains("\u001B[33m"));
    }
}