package com.scanner.financeiro.modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Um ponto de preço histórico (fechamento diário), usado pelo módulo de
 * backtesting (Fase 4). Mantido deliberadamente simples (data + fechamento) —
 * se no futuro você precisar de OHLC completo (abertura/máxima/mínima/volume),
 * é só adicionar os campos aqui; o resto do sistema só depende de data() e
 * precoFechamento().
 */
public record PrecoHistorico(LocalDate data, BigDecimal precoFechamento) {

    public PrecoHistorico {
        Objects.requireNonNull(data, "data não pode ser nula");
        Objects.requireNonNull(precoFechamento, "precoFechamento não pode ser nulo");
        if (precoFechamento.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("precoFechamento não pode ser negativo: " + precoFechamento);
        }
    }
}