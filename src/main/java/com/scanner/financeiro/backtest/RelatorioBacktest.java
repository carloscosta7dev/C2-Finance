package com.scanner.financeiro.backtest;

import com.scanner.financeiro.modelo.Sinal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/** Resultado de uma rodada de backtesting (Fase 4.4): capital inicial/final e o log de operações. */
public record RelatorioBacktest(BigDecimal capitalInicial, BigDecimal capitalFinal, List<Operacao> operacoes) {

    public record Operacao(LocalDate data, Sinal tipo, BigDecimal preco, BigDecimal quantidade) {}

    public BigDecimal lucroPrejuizo() {
        return capitalFinal.subtract(capitalInicial);
    }

    public BigDecimal lucroPrejuizoPercentual() {
        if (capitalInicial.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return lucroPrejuizo().divide(capitalInicial, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    public void imprimirRelatorio() {
        System.out.println("\n--- Relatório de Backtesting ---");
        System.out.printf("Capital inicial       : %s%n", capitalInicial.setScale(2, RoundingMode.HALF_UP));
        System.out.printf("Capital final          : %s%n", capitalFinal.setScale(2, RoundingMode.HALF_UP));
        String rotulo = lucroPrejuizo().compareTo(BigDecimal.ZERO) >= 0 ? "Lucro" : "Prejuízo";
        System.out.printf("%-23s: %s (%.2f%%)%n", rotulo, lucroPrejuizo().setScale(2, RoundingMode.HALF_UP), lucroPrejuizoPercentual());
        System.out.printf("Número de operações    : %d%n", operacoes.size());
        System.out.println("\nHistórico de operações:");
        for (Operacao op : operacoes) {
            System.out.printf(" -> [%s] %-6s %s unidades a %s%n",
                    op.data(), op.tipo(), op.quantidade().setScale(6, RoundingMode.DOWN), op.preco().setScale(2, RoundingMode.HALF_UP));
        }
    }
}