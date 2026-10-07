package com.scanner.financeiro.modelo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Representa o preço de um ativo financeiro em um instante específico.
 *
 * Por que record? Um record é imutável por padrão (equals/hashCode/toString
 * gerados automaticamente), o que é exatamente o que queremos para um
 * "snapshot" de preço: depois de criado, nunca muda — para refletir um novo
 * preço, criamos uma nova instância em vez de alterar a existente. Isso evita
 * uma classe inteira de bugs de concorrência (nenhuma thread pode ver um
 * AtivoFinanceiro "pela metade").
 *
 * Por que BigDecimal e nunca double/float? double e float usam ponto
 * flutuante binário, que não consegue representar exatamente a maioria dos
 * valores decimais (0.1 em double, por exemplo, não é exatamente 0.1 — é uma
 * aproximação). Em um sistema financeiro, esses pequenos erros se acumulam a
 * cada soma/multiplicação e eventualmente geram diferenças de centavos (ou
 * mais) no saldo de alguém. BigDecimal representa o número exatamente como
 * uma sequência de dígitos decimais, sem essa aproximação.
 */
public record AtivoFinanceiro(String simbolo, BigDecimal precoAtual, Instant ultimaAtualizacao) {

    // Bloco compacto do record: roda antes dos campos serem atribuídos,
    // então é o lugar certo para validar invariantes.
    public AtivoFinanceiro {
        Objects.requireNonNull(simbolo, "simbolo não pode ser nulo");
        Objects.requireNonNull(precoAtual, "precoAtual não pode ser nulo");
        Objects.requireNonNull(ultimaAtualizacao, "ultimaAtualizacao não pode ser nula");
        if (precoAtual.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("precoAtual não pode ser negativo: " + precoAtual);
        }
    }
}