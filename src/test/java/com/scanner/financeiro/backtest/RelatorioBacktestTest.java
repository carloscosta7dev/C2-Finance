package com.scanner.financeiro.backtest;

import com.scanner.financeiro.modelo.Sinal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelatorioBacktestTest {

    @Test
    void calculaLucroEPercentualComCapitalInicialZeroProtegido() {
        RelatorioBacktest relatorio = new RelatorioBacktest(
                new BigDecimal("100"), new BigDecimal("125"), List.of());
        RelatorioBacktest semCapital = new RelatorioBacktest(BigDecimal.ZERO, BigDecimal.TEN, List.of());

        assertEquals(new BigDecimal("25"), relatorio.lucroPrejuizo());
        assertEquals(0, relatorio.lucroPrejuizoPercentual().compareTo(new BigDecimal("25.000000")));
        assertEquals(BigDecimal.ZERO, semCapital.lucroPrejuizoPercentual());
    }

    @Test
    void imprimeResumoEOperacoes() {
        RelatorioBacktest relatorio = new RelatorioBacktest(new BigDecimal("100"), new BigDecimal("120"), List.of(
                new RelatorioBacktest.Operacao(LocalDate.parse("2025-01-01"), Sinal.COMPRA,
                        new BigDecimal("10"), new BigDecimal("12.3456789"))));
        PrintStream saidaOriginal = System.out;
        ByteArrayOutputStream captura = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captura, true, StandardCharsets.UTF_8));
            relatorio.imprimirRelatorio();
        } finally {
            System.setOut(saidaOriginal);
        }

        String texto = captura.toString(StandardCharsets.UTF_8);
        assertTrue(texto.contains("Lucro"));
        assertTrue(texto.contains("Número de operações"));
        assertTrue(texto.contains("2025-01-01"));
        assertTrue(texto.contains("12.345678"));
    }
}