package com.scanner.financeiro;

import com.scanner.financeiro.api.BinanceClient;
import com.scanner.financeiro.dashboard.DashboardServer;
import com.scanner.financeiro.motor.MotorRequisicoes;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void mostraAjudaQuandoPassaComandoHelp() {
        PrintStream saidaOriginal = System.out;
        ByteArrayOutputStream captura = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captura, true, StandardCharsets.UTF_8));
            Main.main(new String[]{"--help"});
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

    @Test
    void selecionaPortaLivreQuandoAPortaPreferidaEstaOcupada() throws Exception {
        try (MotorRequisicoes motor = new MotorRequisicoes(1, Duration.ofSeconds(1));
             DashboardServer ocupado = new DashboardServer(0, new BinanceClient(motor))) {
            ocupado.iniciar();
            try (DashboardServer alternativo = Main.criarDashboard(ocupado.porta(), new BinanceClient(motor))) {
                assertNotEquals(ocupado.porta(), alternativo.porta());
            }
        }
    }
}