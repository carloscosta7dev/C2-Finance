package com.scanner.financeiro.backtest;

import com.scanner.financeiro.analise.AnalisadorTecnico;
import com.scanner.financeiro.modelo.PrecoHistorico;
import com.scanner.financeiro.modelo.Sinal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Roda a estratégia de cruzamento de médias (SMA curta x SMA longa) contra
 * uma série histórica (Fase 4.4). Estratégia "tudo ou nada": ao sinal de
 * COMPRA investe 100% do caixa disponível; ao sinal de VENDA, liquida 100% da
 * posição. Não modela taxas de corretagem nem slippage — é um ponto natural
 * de evolução, deixado de fora aqui para manter a lógica central legível.
 */
public final class BacktestEngine {

    private static final int ESCALA = 8;

    private BacktestEngine() {}

    public static RelatorioBacktest executar(List<PrecoHistorico> historico, int periodoCurto, int periodoLongo, BigDecimal capitalInicial) {
        if (periodoCurto >= periodoLongo) {
            throw new IllegalArgumentException("periodoCurto deve ser menor que periodoLongo");
        }
        if (historico.size() <= periodoLongo) {
            throw new IllegalArgumentException(
                    "histórico insuficiente: são necessários mais de " + periodoLongo + " pontos de dados, recebidos " + historico.size());
        }

        List<BigDecimal> precos = historico.stream().map(PrecoHistorico::precoFechamento).toList();
        List<BigDecimal> mediaCurta = AnalisadorTecnico.calcularSMA(precos, periodoCurto);
        List<BigDecimal> mediaLonga = AnalisadorTecnico.calcularSMA(precos, periodoLongo);

        BigDecimal caixa = capitalInicial;
        BigDecimal quantidadeAtivo = BigDecimal.ZERO;
        List<RelatorioBacktest.Operacao> operacoes = new ArrayList<>();

        for (int i = 1; i < precos.size(); i++) {
            Sinal sinal = AnalisadorTecnico.detectarCruzamento(
                    mediaCurta.get(i - 1), mediaLonga.get(i - 1),
                    mediaCurta.get(i), mediaLonga.get(i));

            BigDecimal precoAtual = precos.get(i);
            LocalDate dataAtual = historico.get(i).data();

            if (sinal == Sinal.COMPRA && quantidadeAtivo.compareTo(BigDecimal.ZERO) == 0 && caixa.compareTo(BigDecimal.ZERO) > 0) {
                quantidadeAtivo = caixa.divide(precoAtual, ESCALA, RoundingMode.DOWN);
                operacoes.add(new RelatorioBacktest.Operacao(dataAtual, Sinal.COMPRA, precoAtual, quantidadeAtivo));
                caixa = BigDecimal.ZERO;
            } else if (sinal == Sinal.VENDA && quantidadeAtivo.compareTo(BigDecimal.ZERO) > 0) {
                caixa = quantidadeAtivo.multiply(precoAtual).setScale(ESCALA, RoundingMode.DOWN);
                operacoes.add(new RelatorioBacktest.Operacao(dataAtual, Sinal.VENDA, precoAtual, quantidadeAtivo));
                quantidadeAtivo = BigDecimal.ZERO;
            }
        }

        BigDecimal capitalFinal = caixa;
        if (quantidadeAtivo.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ultimoPreco = precos.get(precos.size() - 1);
            capitalFinal = caixa.add(quantidadeAtivo.multiply(ultimoPreco));
        }

        return new RelatorioBacktest(capitalInicial, capitalFinal, operacoes);
    }
}