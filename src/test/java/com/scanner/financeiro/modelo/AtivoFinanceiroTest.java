package com.scanner.financeiro.modelo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtivoFinanceiroTest {

    @Test
    void criaAtivoComPrecoExato() {
        Instant instante = Instant.parse("2025-01-01T00:00:00Z");
        AtivoFinanceiro ativo = new AtivoFinanceiro("BTCUSDT", new BigDecimal("63521.44000000"), instante);

        assertEquals("BTCUSDT", ativo.simbolo());
        assertEquals(new BigDecimal("63521.44000000"), ativo.precoAtual());
        assertEquals(instante, ativo.ultimaAtualizacao());
    }

    @Test
    void rejeitaCamposObrigatoriosNulos() {
        assertThrows(NullPointerException.class,
                () -> new AtivoFinanceiro(null, BigDecimal.ONE, Instant.EPOCH));
        assertThrows(NullPointerException.class,
                () -> new AtivoFinanceiro("BTCUSDT", null, Instant.EPOCH));
        assertThrows(NullPointerException.class,
                () -> new AtivoFinanceiro("BTCUSDT", BigDecimal.ONE, null));
    }

    @Test
    void rejeitaPrecoNegativo() {
        assertThrows(IllegalArgumentException.class,
                () -> new AtivoFinanceiro("BTCUSDT", new BigDecimal("-0.01"), Instant.EPOCH));
    }
}