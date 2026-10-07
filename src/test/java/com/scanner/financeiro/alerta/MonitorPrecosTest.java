package com.scanner.financeiro.alerta;

import com.scanner.financeiro.api.BinanceClient;
import com.scanner.financeiro.api.ResultadoAtivo;
import com.scanner.financeiro.api.dto.BinanceKlineDTO;
import com.scanner.financeiro.modelo.AtivoFinanceiro;
import com.scanner.financeiro.motor.MotorRequisicoes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorPrecosTest {

    @Test
    void alertaUmaVezAbaixoDoGatilhoERearmaAposRecuperacao() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();
        CountDownLatch quatroTicks = new CountDownLatch(4);
        LinkedBlockingQueue<String> alertas = new LinkedBlockingQueue<>();

        try (MotorRequisicoes motor = new MotorRequisicoes(1, Duration.ofSeconds(1))) {
            BinanceClient cliente = new BinanceClient(motor) {
                @Override
                public java.util.concurrent.CompletableFuture<List<ResultadoAtivo>> buscarPrecos(List<String> simbolos) {
                    int tick = chamadas.getAndIncrement();
                    quatroTicks.countDown();
                    String preco = switch (tick) {
                        case 0, 1 -> "90";
                        case 2 -> "110";
                        default -> "90";
                    };
                    AtivoFinanceiro ativo = new AtivoFinanceiro("BTCUSDT", new BigDecimal(preco), Instant.now());
                    return java.util.concurrent.CompletableFuture.completedFuture(
                            List.of(ResultadoAtivo.sucesso(ativo)));
                }
            };
            CanalAlerta canal = (titulo, mensagem) -> alertas.add(titulo);
            MonitorPrecos monitor = new MonitorPrecos(cliente, canal, Map.of("BTCUSDT", new BigDecimal("100")));
            ScheduledExecutorService agendador = monitor.iniciar(Duration.ofSeconds(1));

            try {
                assertTrue(quatroTicks.await(6, TimeUnit.SECONDS), "o monitor nao completou quatro verificacoes");
            } finally {
                agendador.shutdownNow();
                assertTrue(agendador.awaitTermination(2, TimeUnit.SECONDS));
            }
        }

        List<String> titulos = new ArrayList<>(alertas);
        assertEquals(2, titulos.size());
        assertTrue(titulos.stream().allMatch(titulo -> titulo.contains("caiu abaixo de 100")));
    }

    @Test
    void monitoraCruzamentosEmCandlesFechadosSemDuplicarSinalNaMesmaVela() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();
        CountDownLatch doisSinais = new CountDownLatch(2);
        LinkedBlockingQueue<String> alertas = new LinkedBlockingQueue<>();

        try (MotorRequisicoes motor = new MotorRequisicoes(1, Duration.ofSeconds(1))) {
            BinanceClient cliente = new BinanceClient(motor) {
                @Override
                public java.util.concurrent.CompletableFuture<List<BinanceKlineDTO>> buscarCandlesFechados(
                        String simbolo, String intervalo, int limite) {
                    int tick = chamadas.getAndIncrement();
                    List<String> precos = tick < 2
                            ? List.of("10", "8", "11")
                            : List.of("10", "8", "11", "9");
                    List<BinanceKlineDTO> candles = java.util.stream.IntStream.range(0, precos.size())
                            .mapToObj(indice -> new BinanceKlineDTO(
                                    Instant.ofEpochSecond(indice + 1L), new BigDecimal(precos.get(indice))))
                            .toList();
                    return java.util.concurrent.CompletableFuture.completedFuture(candles);
                }
            };
            CanalAlerta canal = (titulo, mensagem) -> {
                alertas.add(titulo);
                doisSinais.countDown();
            };
            MonitorPrecos monitor = new MonitorPrecos(
                    cliente, canal, List.of("BTCUSDT"), Map.of(), "1m", 1, 2);
            ScheduledExecutorService agendador = monitor.iniciar(Duration.ofSeconds(1));

            try {
                assertTrue(doisSinais.await(6, TimeUnit.SECONDS), "o monitor nao detectou os dois cruzamentos");
            } finally {
                agendador.shutdownNow();
                assertTrue(agendador.awaitTermination(2, TimeUnit.SECONDS));
            }
        }

        List<String> titulos = new ArrayList<>(alertas);
        assertEquals(2, titulos.size());
        assertTrue(titulos.get(0).contains("COMPRA"));
        assertTrue(titulos.get(1).contains("VENDA"));
        assertTrue(chamadas.get() >= 3);
    }

    @Test
    void tentaEntregarNovamenteQuandoCanalFalha() throws Exception {
        AtomicInteger tentativasEntrega = new AtomicInteger();
        CountDownLatch entregaBemSucedida = new CountDownLatch(1);
        List<BinanceKlineDTO> candles = List.of(
                new BinanceKlineDTO(Instant.ofEpochSecond(1), new BigDecimal("10")),
                new BinanceKlineDTO(Instant.ofEpochSecond(2), new BigDecimal("8")),
                new BinanceKlineDTO(Instant.ofEpochSecond(3), new BigDecimal("11")));

        try (MotorRequisicoes motor = new MotorRequisicoes(1, Duration.ofSeconds(1))) {
            BinanceClient cliente = new BinanceClient(motor) {
                @Override
                public java.util.concurrent.CompletableFuture<List<BinanceKlineDTO>> buscarCandlesFechados(
                        String simbolo, String intervalo, int limite) {
                    return java.util.concurrent.CompletableFuture.completedFuture(candles);
                }
            };
            CanalAlerta canal = (titulo, mensagem) -> {
                if (tentativasEntrega.getAndIncrement() == 0) {
                    throw new IllegalStateException("SMTP indisponível");
                }
                entregaBemSucedida.countDown();
            };
            MonitorPrecos monitor = new MonitorPrecos(
                    cliente, canal, List.of("BTCUSDT"), Map.of(), "1m", 1, 2);
            ScheduledExecutorService agendador = monitor.iniciar(Duration.ofSeconds(1));

            try {
                assertTrue(entregaBemSucedida.await(4, TimeUnit.SECONDS), "o canal nao foi tentado novamente");
            } finally {
                agendador.shutdownNow();
                assertTrue(agendador.awaitTermination(2, TimeUnit.SECONDS));
            }
        }

        assertEquals(2, tentativasEntrega.get());
    }
}