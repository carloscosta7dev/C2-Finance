package com.scanner.financeiro.api;

import com.scanner.financeiro.modelo.AtivoFinanceiro;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultadoAtivoTest {

    @Test
    void fabricaResultadoDeSucessoComSimboloDoAtivo() {
        AtivoFinanceiro ativo = new AtivoFinanceiro("BTCUSDT", BigDecimal.TEN, Instant.EPOCH);
        ResultadoAtivo resultado = ResultadoAtivo.sucesso(ativo);

        assertTrue(resultado.sucesso());
        assertEquals("BTCUSDT", resultado.simbolo());
        assertEquals(ativo, resultado.ativo());
        assertNull(resultado.erro());
    }

    @Test
    void fabricaResultadoDeFalhaSemAtivo() {
        RuntimeException erro = new RuntimeException("falha");
        ResultadoAtivo resultado = ResultadoAtivo.falha("ETHUSDT", erro);

        assertFalse(resultado.sucesso());
        assertEquals("ETHUSDT", resultado.simbolo());
        assertNull(resultado.ativo());
        assertEquals(erro, resultado.erro());
    }
}