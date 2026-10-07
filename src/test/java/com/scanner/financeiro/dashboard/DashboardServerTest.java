package com.scanner.financeiro.dashboard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.financeiro.api.BinanceClient;
import com.scanner.financeiro.api.dto.BinanceKlineDTO;
import com.scanner.financeiro.motor.MotorRequisicoes;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardServerTest {

    @Test
    void servePaginaEserieDeMercadoNoLoopback() throws Exception {
        try (MotorRequisicoes motor = new MotorRequisicoes(1, Duration.ofSeconds(1))) {
            BinanceClient cliente = new BinanceClient(motor) {
                @Override
                public CompletableFuture<List<BinanceKlineDTO>> buscarCandlesFechados(
                        String simbolo, String timeframe, int limite) {
                    List<String> precos = List.of("10", "8", "11", "14", "12");
                    List<BinanceKlineDTO> candles = java.util.stream.IntStream.range(0, precos.size())
                        .mapToObj(indice -> new BinanceKlineDTO(
                            Instant.parse("2025-01-01T00:00:00Z").plusSeconds(indice * 60L),
                            new java.math.BigDecimal(precos.get(indice))))
                            .toList();
                    return CompletableFuture.completedFuture(candles);
                }
            };

            try (DashboardServer servidor = new DashboardServer(0, cliente)) {
                servidor.iniciar();
                HttpClient http = HttpClient.newHttpClient();
                String base = "http://127.0.0.1:" + servidor.porta();

                HttpResponse<String> pagina = http.send(
                        HttpRequest.newBuilder(URI.create(base + "/")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, pagina.statusCode());
                assertTrue(pagina.body().contains("C2 FINANCE"));
                assertTrue(pagina.body().contains("priceChart"));

                HttpResponse<String> mercado = http.send(
                        HttpRequest.newBuilder(URI.create(base + "/api/market?symbols=BTCUSDT&timeframe=1m&short=2&long=3")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, mercado.statusCode());
                JsonNode json = new ObjectMapper().readTree(mercado.body());
                assertEquals("1m", json.get("timeframe").asText());
                assertEquals(1, json.get("series").size());
                assertEquals("BTCUSDT", json.get("series").get(0).get("simbolo").asText());
                assertEquals("12", json.get("series").get(0).get("precoAtual").asText());
                assertEquals(5, json.get("series").get(0).get("pontos").size());
                assertEquals("COMPRA", json.get("series").get(0).get("ultimoCruzamento").asText());
            }
        }
    }

    @Test
    void rejeitaPeriodoMaiorQueLimiteDoGrafico() throws Exception {
        try (MotorRequisicoes motor = new MotorRequisicoes(1, Duration.ofSeconds(1))) {
            BinanceClient cliente = new BinanceClient(motor) {
                @Override
                public CompletableFuture<List<BinanceKlineDTO>> buscarCandlesFechados(
                        String simbolo, String timeframe, int limite) {
                    return CompletableFuture.failedFuture(new AssertionError("validação deveria ocorrer antes da requisição"));
                }
            };
            try (DashboardServer servidor = new DashboardServer(0, cliente)) {
                servidor.iniciar();
                HttpResponse<String> resposta = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + servidor.porta()
                                + "/api/market?symbols=BTCUSDT&short=9&long=200")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());

                assertEquals(400, resposta.statusCode());
                assertTrue(resposta.body().contains("períodos inválidos"));
            }
        }
    }
}