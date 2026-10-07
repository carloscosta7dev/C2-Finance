package com.scanner.financeiro.motor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackoffExponencialTest {

    @Test
    void repete429EFalhaDeRedeAteUmaRespostaBemSucedida() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();

        ResultadoRequisicao resultado = BackoffExponencial.executarComRetentativas(() -> {
            int tentativa = chamadas.incrementAndGet();
            if (tentativa == 1) {
                return CompletableFuture.completedFuture(new ResultadoRequisicao(
                        429, "", cabecalhos("0"), null));
            }
            if (tentativa == 2) {
                return CompletableFuture.completedFuture(new ResultadoRequisicao(
                        -1, null, null, new IOException("conexao perdida")));
            }
            return CompletableFuture.completedFuture(new ResultadoRequisicao(200, "ok", cabecalhos(null), null));
        }, 4, Duration.ZERO).get(3, TimeUnit.SECONDS);

        assertEquals(3, chamadas.get());
        assertEquals(200, resultado.codigoStatus());
        org.junit.jupiter.api.Assertions.assertTrue(resultado.sucesso());
    }

    @Test
    void respeitaNumeroMaximoDeTentativas() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();

        ResultadoRequisicao resultado = BackoffExponencial.executarComRetentativas(() -> {
            chamadas.incrementAndGet();
            return CompletableFuture.completedFuture(new ResultadoRequisicao(
                    429, "", cabecalhos("0"), null));
        }, 2, Duration.ZERO).get(3, TimeUnit.SECONDS);

        assertEquals(2, chamadas.get());
        assertEquals(429, resultado.codigoStatus());
    }

    @Test
    void naoRepeteStatusQueNaoSeja429NemFalhaDeRede() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();

        ResultadoRequisicao resultado = BackoffExponencial.executarComRetentativas(() -> {
            chamadas.incrementAndGet();
            return CompletableFuture.completedFuture(new ResultadoRequisicao(503, "indisponivel", null, null));
        }, 5, Duration.ZERO).get(3, TimeUnit.SECONDS);

        assertEquals(1, chamadas.get());
        assertEquals(503, resultado.codigoStatus());
    }

    private static HttpHeaders cabecalhos(String retryAfter) {
        Map<String, java.util.List<String>> valores = retryAfter == null
                ? Map.of()
                : Map.of("Retry-After", java.util.List.of(retryAfter));
        return HttpHeaders.of(valores, (nome, valor) -> true);
    }
}