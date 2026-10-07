package com.scanner.financeiro.modelo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrecoHistoricoTest {

    @Test
    void criaPontoHistoricoComPrecoZeroOuPositivo() {
        PrecoHistorico ponto = new PrecoHistorico(LocalDate.parse("2025-01-01"), BigDecimal.ZERO);

        assertEquals(LocalDate.parse("2025-01-01"), ponto.data());
        assertEquals(BigDecimal.ZERO, ponto.precoFechamento());
    }

    @Test
    void rejeitaCamposNulosEPrecoNegativo() {
        assertThrows(NullPointerException.class, () -> new PrecoHistorico(null, BigDecimal.ONE));
        assertThrows(NullPointerException.class, () -> new PrecoHistorico(LocalDate.now(), null));
        assertThrows(IllegalArgumentException.class,
                () -> new PrecoHistorico(LocalDate.now(), new BigDecimal("-1")));
    }
}