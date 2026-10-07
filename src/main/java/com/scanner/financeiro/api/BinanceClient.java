package com.scanner.financeiro.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.financeiro.api.dto.BinanceKlineDTO;
import com.scanner.financeiro.api.dto.BinanceTickerDTO;
import com.scanner.financeiro.modelo.AtivoFinanceiro;
import com.scanner.financeiro.motor.BackoffExponencial;
import com.scanner.financeiro.motor.MetodoHttp;
import com.scanner.financeiro.motor.MotorRequisicoes;
import com.scanner.financeiro.motor.ResultadoRequisicao;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Cliente para a API pública da Binance (Fase 2). Usa o MotorRequisicoes
 * (Fase 1) como transporte e o BackoffExponencial para absorver erros 429.
 *
 * Desenho pensado para extensão: para adicionar CoinGecko ou Yahoo Finance no
 * futuro, basta criar outra classe com a mesma forma (recebe um
 * MotorRequisicoes no construtor, expõe buscarPreco/buscarPrecos) — não é
 * necessário alterar esta classe nem quem já a utiliza (MonitorPrecos).
 */
public class BinanceClient {

    private static final String URL_BASE = "https://api.binance.com/api/v3/ticker/price";
    private static final String URL_KLINES = "https://api.binance.com/api/v3/klines";
    private static final int MAX_TENTATIVAS = 5;
    private static final Duration ATRASO_BASE = Duration.ofMillis(500);

    private final MotorRequisicoes motor;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public BinanceClient(MotorRequisicoes motor) {
        this.motor = motor;
    }

    public CompletableFuture<AtivoFinanceiro> buscarPreco(String simbolo) {
        String url = URL_BASE + "?symbol=" + simbolo.toUpperCase();
        return BackoffExponencial
                .executarComRetentativas(() -> motor.requisitarAsync(url, MetodoHttp.GET), MAX_TENTATIVAS, ATRASO_BASE)
                .thenApply(resultado -> converterParaAtivo(simbolo, resultado));
    }

    /** Busca vários símbolos em paralelo, respeitando a concorrência do motor — Fase 2.4. */
    public CompletableFuture<List<ResultadoAtivo>> buscarPrecos(List<String> simbolos) {
        List<CompletableFuture<ResultadoAtivo>> futuros = simbolos.stream()
                .map(simbolo -> buscarPreco(simbolo)
                        .thenApply(ResultadoAtivo::sucesso)
                        .exceptionally(erro -> ResultadoAtivo.falha(simbolo, erro)))
                .toList();

        return CompletableFuture.allOf(futuros.toArray(new CompletableFuture[0]))
                .thenApply(ignorado -> futuros.stream().map(CompletableFuture::join).toList());
    }

    public CompletableFuture<List<BinanceKlineDTO>> buscarCandlesFechados(String simbolo, String intervalo, int limite) {
        if (simbolo == null || !simbolo.matches("[A-Za-z0-9]{2,20}")) {
            throw new IllegalArgumentException("símbolo Binance inválido: " + simbolo);
        }
        if (intervalo == null || !intervalo.matches("\\d+[smhdwM]")) {
            throw new IllegalArgumentException("intervalo de candle Binance inválido: " + intervalo);
        }
        if (limite < 2 || limite > 1000) {
            throw new IllegalArgumentException("limite de candles deve estar entre 2 e 1000");
        }

        String url = URL_KLINES + "?symbol=" + simbolo.toUpperCase()
                + "&interval=" + intervalo + "&limit=" + limite;
        return BackoffExponencial
                .executarComRetentativas(() -> motor.requisitarAsync(url, MetodoHttp.GET), MAX_TENTATIVAS, ATRASO_BASE)
                .thenApply(resultado -> converterParaCandlesFechados(simbolo, resultado));
    }

    private AtivoFinanceiro converterParaAtivo(String simbolo, ResultadoRequisicao resultado) {
        if (!resultado.sucesso()) {
            throw new RequisicaoMercadoException(
                    "Falha ao buscar preço de " + simbolo + " (status " + resultado.codigoStatus() + ")", resultado.erro());
        }
        try {
            BinanceTickerDTO dto = jsonMapper.readValue(resultado.corpo(), BinanceTickerDTO.class);
            // Nunca passar pelo double: o preço nasce como texto na resposta JSON e vira
            // BigDecimal diretamente, preservando a precisão exata que a Binance enviou.
            return new AtivoFinanceiro(dto.symbol(), new BigDecimal(dto.price()), Instant.now());
        } catch (JsonProcessingException e) {
            throw new RequisicaoMercadoException("Resposta JSON inválida da Binance para " + simbolo, e);
        }
    }

    private List<BinanceKlineDTO> converterParaCandlesFechados(String simbolo, ResultadoRequisicao resultado) {
        if (!resultado.sucesso()) {
            throw new RequisicaoMercadoException(
                    "Falha ao buscar candles de " + simbolo + " (status " + resultado.codigoStatus() + ")", resultado.erro());
        }

        try {
            JsonNode candlesJson = jsonMapper.readTree(resultado.corpo());
            if (!candlesJson.isArray()) {
                throw new IllegalArgumentException("resposta de candles não é uma lista");
            }
            Instant agora = Instant.now();
            List<BinanceKlineDTO> candlesFechados = new ArrayList<>();
            for (JsonNode candleJson : candlesJson) {
                if (!candleJson.isArray() || candleJson.size() < 7
                        || !candleJson.get(4).isTextual() || !candleJson.get(6).canConvertToLong()) {
                    throw new IllegalArgumentException("formato de candle Binance inválido");
                }
                Instant encerramento = Instant.ofEpochMilli(candleJson.get(6).asLong());
                if (!encerramento.isAfter(agora)) {
                    candlesFechados.add(new BinanceKlineDTO(
                            encerramento, new BigDecimal(candleJson.get(4).asText())));
                }
            }
            return List.copyOf(candlesFechados);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new RequisicaoMercadoException("Resposta de candles inválida da Binance para " + simbolo, e);
        }
    }

    public static class RequisicaoMercadoException extends RuntimeException {
        public RequisicaoMercadoException(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }
}