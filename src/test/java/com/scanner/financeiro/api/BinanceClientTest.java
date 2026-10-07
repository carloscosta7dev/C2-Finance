package com.scanner.financeiro.api;

import com.scanner.financeiro.api.dto.BinanceKlineDTO;
import com.scanner.financeiro.modelo.AtivoFinanceiro;
import com.scanner.financeiro.motor.MetodoHttp;
import com.scanner.financeiro.motor.MotorRequisicoes;
import com.scanner.financeiro.motor.ResultadoRequisicao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.http.HttpHeaders;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BinanceClientTest {

    @Test
    void normalizaSimboloEConvertePrecoPreservandoPrecisao() throws Exception {
        AtomicReference<String> urlChamada = new AtomicReference<>();
        try (StubMotor motor = new StubMotor(url -> {
            urlChamada.set(url);
            return resposta(200, "{\"symbol\":\"BTCUSDT\",\"price\":\"63521.44000000\"}");
        })) {
            AtivoFinanceiro ativo = new BinanceClient(motor).buscarPreco("btcusdt").get();

            assertTrue(urlChamada.get().endsWith("?symbol=BTCUSDT"));
            assertEquals("BTCUSDT", ativo.simbolo());
            assertEquals("63521.44000000", ativo.precoAtual().toPlainString());
        }
    }

    @Test
    void buscaEmLoteIsolaFalhaDeUmSimbolo() throws Exception {
        try (StubMotor motor = new StubMotor(url -> {
            if (url.endsWith("=BAD")) {
                return resposta(503, "indisponivel");
            }
            String simbolo = url.substring(url.indexOf("?symbol=") + "?symbol=".length());
            return resposta(200, "{\"symbol\":\"" + simbolo + "\",\"price\":\"1.25\"}");
        })) {
            List<ResultadoAtivo> resultados = new BinanceClient(motor)
                    .buscarPrecos(List.of("BTCUSDT", "BAD", "ETHUSDT")).get();

            assertEquals(3, resultados.size());
            assertTrue(resultados.get(0).sucesso());
            assertFalse(resultados.get(1).sucesso());
            assertEquals("BAD", resultados.get(1).simbolo());
            assertNotNull(resultados.get(1).erro());
            assertTrue(resultados.get(2).sucesso());
        }
    }

    @Test
    void transformaJsonInvalidoEmExcecaoDeMercado() throws Exception {
        try (StubMotor motor = new StubMotor(url -> resposta(200, "nao-json"))) {
            ExecutionException erro = assertThrows(ExecutionException.class,
                    () -> new BinanceClient(motor).buscarPreco("BTCUSDT").get());

            assertTrue(erro.getCause() instanceof BinanceClient.RequisicaoMercadoException);
        }
    }

    @Test
    void buscaSomenteCandlesFechadosEPreservaPrecoDecimal() throws Exception {
        AtomicReference<String> urlChamada = new AtomicReference<>();
        long agora = Instant.now().toEpochMilli();
        String corpo = "[[0,\"0\",\"0\",\"0\",\"10.12500000\",\"0\","
                + (agora - 10_000) + "],[1,\"0\",\"0\",\"0\",\"11.5\",\"0\","
                + (agora + 60_000) + "]]";

        try (StubMotor motor = new StubMotor(url -> {
            urlChamada.set(url);
            return resposta(200, corpo);
        })) {
            List<BinanceKlineDTO> candles = new BinanceClient(motor)
                    .buscarCandlesFechados("btcusdt", "1m", 3).get();

            assertEquals(1, candles.size());
            assertEquals(new BigDecimal("10.12500000"), candles.get(0).precoFechamento());
            assertEquals(Instant.ofEpochMilli(agora - 10_000), candles.get(0).encerramento());
            assertTrue(urlChamada.get().contains("symbol=BTCUSDT&interval=1m&limit=3"));
        }
    }

    @Test
    void rejeitaIntervaloInvalidoAntesDeFazerRequisicao() {
        try (StubMotor motor = new StubMotor(url -> {
            throw new AssertionError("não deveria requisitar uma URL com intervalo inválido");
        })) {
            assertThrows(IllegalArgumentException.class,
                    () -> new BinanceClient(motor).buscarCandlesFechados("BTCUSDT", "1m&limit=0", 3));
        }
    }

    private static ResultadoRequisicao resposta(int status, String corpo) {
        return new ResultadoRequisicao(status, corpo,
                HttpHeaders.of(Map.of(), (nome, valor) -> true), null);
    }

    private static final class StubMotor extends MotorRequisicoes {
        private final Function<String, ResultadoRequisicao> responder;

        private StubMotor(Function<String, ResultadoRequisicao> responder) {
            super(1, Duration.ofSeconds(1));
            this.responder = responder;
        }

        @Override
        public CompletableFuture<ResultadoRequisicao> requisitarAsync(String url, MetodoHttp metodo) {
            return CompletableFuture.completedFuture(responder.apply(url));
        }
    }
}