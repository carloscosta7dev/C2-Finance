package com.scanner.financeiro.motor;

import java.net.http.HttpHeaders;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Política de retentativa com backoff exponencial + jitter — requisito de
 * RESILIÊNCIA do projeto, usada para absorver erros 429 (Rate Limit) e falhas
 * transitórias de rede.
 *
 * Fórmula do atraso: base * 2^tentativa + jitter aleatório (0..base). O
 * jitter evita que várias requisições que falharam juntas voltem a tentar
 * exatamente no mesmo instante ("thundering herd").
 *
 * Quando a resposta traz o cabeçalho "Retry-After" (a Binance costuma enviar
 * esse cabeçalho em respostas 429), ele tem prioridade sobre o cálculo
 * exponencial — é o próprio servidor dizendo explicitamente quanto esperar.
 */
public final class BackoffExponencial {

    private static final ScheduledExecutorService AGENDADOR = criarAgendador();

    private BackoffExponencial() {}

    public static CompletableFuture<ResultadoRequisicao> executarComRetentativas(
            Supplier<CompletableFuture<ResultadoRequisicao>> operacao,
            int maxTentativas,
            Duration atrasoBase) {
        return tentar(operacao, maxTentativas, atrasoBase, 0);
    }

    private static CompletableFuture<ResultadoRequisicao> tentar(
            Supplier<CompletableFuture<ResultadoRequisicao>> operacao,
            int maxTentativas,
            Duration atrasoBase,
            int tentativaAtual) {

        return operacao.get().thenCompose(resultado -> {
            boolean precisaRetentar = resultado.limiteTaxaExcedido() || resultado.falhaDeConexao();
            boolean esgotouTentativas = tentativaAtual >= maxTentativas - 1;

            if (!precisaRetentar || esgotouTentativas) {
                return CompletableFuture.completedFuture(resultado);
            }

            long atrasoMillis = calcularAtrasoMillis(atrasoBase, tentativaAtual, resultado.cabecalhos());
            CompletableFuture<ResultadoRequisicao> futuro = new CompletableFuture<>();

            AGENDADOR.schedule(() -> {
                tentar(operacao, maxTentativas, atrasoBase, tentativaAtual + 1)
                        .whenComplete((res, err) -> {
                            if (err != null) futuro.completeExceptionally(err);
                            else futuro.complete(res);
                        });
            }, atrasoMillis, TimeUnit.MILLISECONDS);

            return futuro;
        });
    }

    private static long calcularAtrasoMillis(Duration atrasoBase, int tentativa, HttpHeaders cabecalhos) {
        if (cabecalhos != null) {
            Optional<String> retryAfter = cabecalhos.firstValue("Retry-After");
            if (retryAfter.isPresent()) {
                try {
                    return Long.parseLong(retryAfter.get().trim()) * 1000L;
                } catch (NumberFormatException ignorado) {
                    // Cabeçalho em formato inesperado: usa o cálculo padrão abaixo.
                }
            }
        }
        long exponencial = atrasoBase.toMillis() * (1L << tentativa);
        long jitter = ThreadLocalRandom.current().nextLong(0, atrasoBase.toMillis() + 1);
        return exponencial + jitter;
    }

    private static ScheduledExecutorService criarAgendador() {
        AtomicInteger contador = new AtomicInteger(1);
        ThreadFactory fabrica = runnable -> {
            Thread t = new Thread(runnable, "backoff-agendador-" + contador.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        return Executors.newSingleThreadScheduledExecutor(fabrica);
    }
}