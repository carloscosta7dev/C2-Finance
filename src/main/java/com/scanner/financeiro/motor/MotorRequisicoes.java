package com.scanner.financeiro.motor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Motor de requisições HTTP assíncronas com controle de concorrência — o
 * equivalente Java do núcleo do script Python:
 *
 *   semaphore = asyncio.Semaphore(args.concurrency)
 *   async with httpx.AsyncClient(timeout=10.0) as client:
 *       tarefas = [fazer_requisicao(client, url, semaphore, ...) for _ in range(n)]
 *       await asyncio.gather(*tarefas)
 *
 * A ideia do "funil" é a mesma: no máximo N requisições em voo ao mesmo
 * tempo. A diferença é que Java não tem um event loop cooperativo como o
 * asyncio — em vez disso, cada chamada a requisitarAsync() é despachada numa
 * THREAD VIRTUAL (Project Loom, padrão desde o Java 21, que é a versão
 * instalada neste ambiente). Threads virtuais são baratíssimas de criar e
 * bloquear — dá para lançar milhares delas esperando no semáforo sem gastar
 * as threads de sistema operacional que um ExecutorService tradicional
 * consumiria. É o que mais se aproxima, em Java, do modelo "uma corrotina por
 * requisição" do asyncio.
 *
 * O semáforo bloqueia apenas a thread virtual que está esperando a vez. Assim
 * que ela consegue a permissão, delega o envio para HttpClient.sendAsync(),
 * que é 100% não bloqueante (usa o próprio mecanismo assíncrono de I/O do
 * HttpClient — nenhuma thread fica "presa" esperando a rede). A permissão só
 * é liberada quando a resposta (ou erro) chega, no whenComplete().
 */
public class MotorRequisicoes implements AutoCloseable {

    private final HttpClient httpClient;
    private final Semaphore semaforo;
    private final ExecutorService executorControle;
    private final Duration timeoutRequisicao;

    public MotorRequisicoes(int concorrenciaMaxima, Duration timeoutRequisicao) {
        if (concorrenciaMaxima <= 0) {
            throw new IllegalArgumentException("concorrenciaMaxima deve ser positivo, recebido: " + concorrenciaMaxima);
        }
        this.timeoutRequisicao = timeoutRequisicao;
        this.semaforo = new Semaphore(concorrenciaMaxima);
        this.executorControle = Executors.newVirtualThreadPerTaskExecutor();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeoutRequisicao)
                .build();
    }

    /**
     * Dispara uma requisição respeitando o limite de concorrência configurado.
     * Nunca lança exceção diretamente: falhas de rede viram um
     * ResultadoRequisicao com erro() preenchido, assim como o script Python
     * captura httpx.RequestError e soma em stats em vez de propagar.
     */
    public CompletableFuture<ResultadoRequisicao> requisitarAsync(String url, MetodoHttp metodo) {
        return CompletableFuture
                .runAsync(this::aguardarPermissao, executorControle)
                .thenCompose(ignorado -> enviarComTratamentoDeErro(url, metodo)
                        .whenComplete((resultado, erro) -> semaforo.release()));
    }

    private void aguardarPermissao() {
        try {
            semaforo.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException("Interrompido ao aguardar permissão do semáforo", e);
        }
    }

    private CompletableFuture<ResultadoRequisicao> enviarComTratamentoDeErro(String url, MetodoHttp metodo) {
        try {
            HttpRequest requisicao = construirRequisicao(url, metodo);
            return httpClient.sendAsync(requisicao, HttpResponse.BodyHandlers.ofString())
                    .thenApply(resp -> new ResultadoRequisicao(resp.statusCode(), resp.body(), resp.headers(), null))
                    .exceptionally(erro -> new ResultadoRequisicao(-1, null, null, causaRaiz(erro)));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(new ResultadoRequisicao(-1, null, null, e));
        }
    }

    private HttpRequest construirRequisicao(String url, MetodoHttp metodo) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeoutRequisicao);
        return switch (metodo) {
            case GET -> builder.GET().build();
            case POST -> builder.POST(HttpRequest.BodyPublishers.noBody()).build();
        };
    }

    private static Throwable causaRaiz(Throwable t) {
        Throwable atual = t;
        while (atual instanceof CompletionException && atual.getCause() != null) {
            atual = atual.getCause();
        }
        return atual;
    }

    @Override
    public void close() {
        executorControle.shutdown();
    }
}