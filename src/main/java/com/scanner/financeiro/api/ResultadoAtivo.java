package com.scanner.financeiro.api;

import com.scanner.financeiro.modelo.AtivoFinanceiro;

/**
 * Resultado de uma busca de preço que pode ter falhado. Usado nas buscas em
 * lote (ex.: 50 símbolos de uma vez, Fase 2.4) para que a falha de UM símbolo
 * não derrube o lote inteiro — resiliência também se aplica aqui.
 */
public record ResultadoAtivo(String simbolo, AtivoFinanceiro ativo, Throwable erro) {

    public static ResultadoAtivo sucesso(AtivoFinanceiro ativo) {
        return new ResultadoAtivo(ativo.simbolo(), ativo, null);
    }

    public static ResultadoAtivo falha(String simbolo, Throwable erro) {
        return new ResultadoAtivo(simbolo, null, erro);
    }

    public boolean sucesso() {
        return erro == null;
    }
}