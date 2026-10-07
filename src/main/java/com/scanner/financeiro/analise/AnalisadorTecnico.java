package com.scanner.financeiro.analise;

import com.scanner.financeiro.modelo.Sinal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Indicadores de análise técnica: Média Móvel Simples (SMA), Média Móvel
 * Exponencial (EMA) e detecção de cruzamento entre duas médias.
 *
 * Todas as divisões usam escala fixa (ESCALA casas decimais) e
 * RoundingMode.HALF_UP — isso é obrigatório em Java: BigDecimal.divide()
 * lança ArithmeticException quando o resultado de uma divisão não é
 * representável exatamente em decimal (ex.: 10 / 3), então uma escala e um
 * modo de arredondamento explícitos são necessários sempre que dividimos.
 */
public final class AnalisadorTecnico {

    private static final int ESCALA = 8;

    private AnalisadorTecnico() {}

    /**
     * Calcula a SMA usando uma janela deslizante (soma incremental): O(n) no
     * total, em vez de O(n * periodo) que uma implementação ingênua (somar a
     * janela inteira a cada ponto) teria.
     *
     * @return lista do mesmo tamanho de {@code precos}; as primeiras
     *         (periodo - 1) posições ficam {@code null} (dados insuficientes).
     */
    public static List<BigDecimal> calcularSMA(List<BigDecimal> precos, int periodo) {
        validarEntrada(precos, periodo);
        List<BigDecimal> resultado = new ArrayList<>(Collections.nCopies(precos.size(), null));
        BigDecimal somaJanela = BigDecimal.ZERO;

        for (int i = 0; i < precos.size(); i++) {
            somaJanela = somaJanela.add(precos.get(i));
            if (i >= periodo) {
                somaJanela = somaJanela.subtract(precos.get(i - periodo));
            }
            if (i >= periodo - 1) {
                resultado.set(i, somaJanela.divide(BigDecimal.valueOf(periodo), ESCALA, RoundingMode.HALF_UP));
            }
        }
        return resultado;
    }

    /**
     * Calcula a EMA. A primeira EMA válida (índice periodo - 1) é semeada com
     * a SMA do mesmo período; a partir daí aplica a fórmula clássica:
     * EMA(hoje) = (preço(hoje) - EMA(ontem)) * k + EMA(ontem), k = 2 / (periodo + 1).
     */
    public static List<BigDecimal> calcularEMA(List<BigDecimal> precos, int periodo) {
        validarEntrada(precos, periodo);
        List<BigDecimal> resultado = new ArrayList<>(Collections.nCopies(precos.size(), null));
        if (precos.size() < periodo) {
            return resultado;
        }

        BigDecimal multiplicador = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(periodo + 1), ESCALA, RoundingMode.HALF_UP);

        BigDecimal somaInicial = BigDecimal.ZERO;
        for (int i = 0; i < periodo; i++) {
            somaInicial = somaInicial.add(precos.get(i));
        }
        BigDecimal emaAnterior = somaInicial.divide(BigDecimal.valueOf(periodo), ESCALA, RoundingMode.HALF_UP);
        resultado.set(periodo - 1, emaAnterior);

        for (int i = periodo; i < precos.size(); i++) {
            BigDecimal emaAtual = precos.get(i).subtract(emaAnterior)
                    .multiply(multiplicador)
                    .add(emaAnterior)
                    .setScale(ESCALA, RoundingMode.HALF_UP);
            resultado.set(i, emaAtual);
            emaAnterior = emaAtual;
        }
        return resultado;
    }

    /**
     * Compara o par (média curta, média longa) entre dois instantes
     * consecutivos e identifica um cruzamento:
     *  - COMPRA (Golden Cross): a média curta estava abaixo/igual e passou a ficar acima.
     *  - VENDA (Death Cross): a média curta estava acima/igual e passou a ficar abaixo.
     *  - MANTER: sem cruzamento, ou dados insuficientes (algum valor null).
     */
    public static Sinal detectarCruzamento(BigDecimal curtaAnterior, BigDecimal longaAnterior,
                                            BigDecimal curtaAtual, BigDecimal longaAtual) {
        if (curtaAnterior == null || longaAnterior == null || curtaAtual == null || longaAtual == null) {
            return Sinal.MANTER;
        }

        boolean estavaAbaixoOuIgual = curtaAnterior.compareTo(longaAnterior) <= 0;
        boolean agoraEstaAcima = curtaAtual.compareTo(longaAtual) > 0;
        if (estavaAbaixoOuIgual && agoraEstaAcima) {
            return Sinal.COMPRA;
        }

        boolean estavaAcimaOuIgual = curtaAnterior.compareTo(longaAnterior) >= 0;
        boolean agoraEstaAbaixo = curtaAtual.compareTo(longaAtual) < 0;
        if (estavaAcimaOuIgual && agoraEstaAbaixo) {
            return Sinal.VENDA;
        }

        return Sinal.MANTER;
    }

    private static void validarEntrada(List<BigDecimal> precos, int periodo) {
        Objects.requireNonNull(precos, "a lista de preços não pode ser nula");
        if (periodo <= 0) {
            throw new IllegalArgumentException("período deve ser positivo, recebido: " + periodo);
        }
    }
}