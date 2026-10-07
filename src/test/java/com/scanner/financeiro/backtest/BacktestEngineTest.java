package com.scanner.financeiro.backtest;

import com.scanner.financeiro.modelo.PrecoHistorico;
import com.scanner.financeiro.modelo.Sinal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BacktestEngineTest {

    @Test
    void compraNoCruzamentoDeAltaEVendeNoCruzamentoDeBaixa() {
        List<PrecoHistorico> historico = historico("10", "8", "11", "14", "12");

        RelatorioBacktest relatorio = BacktestEngine.executar(historico, 1, 2, new BigDecimal("100"));

        assertEquals(2, relatorio.operacoes().size());
        assertEquals(Sinal.COMPRA, relatorio.operacoes().get(0).tipo());
        assertEquals(Sinal.VENDA, relatorio.operacoes().get(1).tipo());
        assertEquals(new BigDecimal("11"), relatorio.operacoes().get(0).preco());
        assertEquals(new BigDecimal("12"), relatorio.operacoes().get(1).preco());
        assertEquals(new BigDecimal("109.09090908"), relatorio.capitalFinal());
    }

    @Test
    void rejeitaPeriodosInconsistentesEHistoricoInsuficiente() {
        List<PrecoHistorico> historico = historico("10", "8", "11");

        assertThrows(IllegalArgumentException.class,
                () -> BacktestEngine.executar(historico, 2, 2, BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,
                () -> BacktestEngine.executar(historico.subList(0, 2), 1, 2, BigDecimal.TEN));
    }

    private static List<PrecoHistorico> historico(String... valores) {
        return java.util.stream.IntStream.range(0, valores.length)
                .mapToObj(indice -> new PrecoHistorico(
                        LocalDate.of(2025, 1, 1).plusDays(indice), new BigDecimal(valores[indice])))
                .toList();
    }
}