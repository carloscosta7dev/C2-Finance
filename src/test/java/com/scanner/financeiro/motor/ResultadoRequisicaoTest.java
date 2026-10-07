package com.scanner.financeiro.motor;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultadoRequisicaoTest {

    @Test
    void classificaRespostasHttpDeSucessoELimiteDeTaxa() {
        assertTrue(resultado(200, null).sucesso());
        assertTrue(resultado(429, null).limiteTaxaExcedido());
        assertFalse(resultado(503, null).sucesso());
        assertFalse(resultado(200, null).limiteTaxaExcedido());
    }

    @Test
    void classificaFalhaDeConexao() {
        assertTrue(resultado(-1, new IllegalStateException("offline")).falhaDeConexao());
        assertFalse(resultado(200, null).falhaDeConexao());
    }

    private static ResultadoRequisicao resultado(int status, Throwable erro) {
        return new ResultadoRequisicao(status, "", HttpHeaders.of(Map.of(), (nome, valor) -> true), erro);
    }
}