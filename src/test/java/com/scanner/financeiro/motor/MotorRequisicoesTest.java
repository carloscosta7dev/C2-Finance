package com.scanner.financeiro.motor;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotorRequisicoesTest {

    private HttpServer servidor;
    private ExecutorService executorServidor;

    @BeforeEach
    void iniciarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executorServidor = Executors.newCachedThreadPool();
        servidor.setExecutor(executorServidor);
        servidor.createContext("/", exchange -> {
            byte[] corpo = exchange.getRequestMethod().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, corpo.length);
            try (OutputStream saida = exchange.getResponseBody()) {
                saida.write(corpo);
            }
        });
        servidor.start();
    }

    @AfterEach
    void pararServidor() {
        servidor.stop(0);
        executorServidor.shutdownNow();
    }

    @Test
    void enviaGetEPostEDevolveResposta() throws Exception {
        String url = url("/");
        try (MotorRequisicoes motor = new MotorRequisicoes(2, Duration.ofSeconds(2))) {
            ResultadoRequisicao get = motor.requisitarAsync(url, MetodoHttp.GET).get(3, TimeUnit.SECONDS);
            ResultadoRequisicao post = motor.requisitarAsync(url, MetodoHttp.POST).get(3, TimeUnit.SECONDS);

            assertTrue(get.sucesso());
            assertEquals("GET", get.corpo());
            assertTrue(post.sucesso());
            assertEquals("POST", post.corpo());
        }
    }

    @Test
    void respeitaLimiteDeConcorrenciaDoSemaforo() throws Exception {
        AtomicInteger ativas = new AtomicInteger();
        AtomicInteger pico = new AtomicInteger();
        servidor.createContext("/lento", exchange -> {
            int atual = ativas.incrementAndGet();
            pico.accumulateAndGet(atual, Math::max);
            try {
                Thread.sleep(100);
                byte[] corpo = "ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, corpo.length);
                try (OutputStream saida = exchange.getResponseBody()) {
                    saida.write(corpo);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                ativas.decrementAndGet();
            }
        });

        try (MotorRequisicoes motor = new MotorRequisicoes(2, Duration.ofSeconds(3))) {
            List<CompletableFuture<ResultadoRequisicao>> tarefas = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tarefas.add(motor.requisitarAsync(url("/lento"), MetodoHttp.GET));
            }
            CompletableFuture.allOf(tarefas.toArray(new CompletableFuture[0])).get(6, TimeUnit.SECONDS);

            assertTrue(pico.get() <= 2, "pico observado: " + pico.get());
            assertTrue(pico.get() >= 2, "o teste deve exercitar concorrência real");
            assertTrue(tarefas.stream().allMatch(tarefa -> tarefa.join().sucesso()));
        }
    }

    @Test
    void transformaUrlInvalidaEmFalhaDeRequisicao() throws Exception {
        try (MotorRequisicoes motor = new MotorRequisicoes(1, Duration.ofSeconds(1))) {
            ResultadoRequisicao resultado = motor.requisitarAsync("nao e uma url", MetodoHttp.GET)
                    .get(3, TimeUnit.SECONDS);

            assertFalse(resultado.sucesso());
            assertTrue(resultado.falhaDeConexao());
        }
    }

    @Test
    void rejeitaConcorrenciaNaoPositiva() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MotorRequisicoes(0, Duration.ofSeconds(1)));
    }

    private String url(String caminho) {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + caminho;
    }
}