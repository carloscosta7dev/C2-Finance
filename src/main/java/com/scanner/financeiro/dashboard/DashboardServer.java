package com.scanner.financeiro.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scanner.financeiro.analise.AnalisadorTecnico;
import com.scanner.financeiro.api.BinanceClient;
import com.scanner.financeiro.api.dto.BinanceKlineDTO;
import com.scanner.financeiro.modelo.Sinal;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DashboardServer implements AutoCloseable {

    private static final int LIMITE_CANDLES = 200;
    private static final int LIMITE_SIMBOLOS = 8;

    private final BinanceClient clienteBinance;
    private final HttpServer servidor;
    private final ExecutorService executor;
    private final ObjectMapper jsonMapper = new ObjectMapper();

    public DashboardServer(int porta, BinanceClient clienteBinance) throws IOException {
        if (porta < 0 || porta > 65535) {
            throw new IllegalArgumentException("porta deve estar entre 0 e 65535");
        }
        this.clienteBinance = clienteBinance;
        this.servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", porta), 0);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        servidor.setExecutor(executor);
        servidor.createContext("/api/market", this::servirMercado);
        servidor.createContext("/", this::servirPagina);
    }

    public void iniciar() {
        servidor.start();
    }

    public int porta() {
        return servidor.getAddress().getPort();
    }

    private void servirPagina(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod())) {
                responderTexto(exchange, 405, "Método não permitido");
                return;
            }
            if (!"/".equals(exchange.getRequestURI().getPath())) {
                responderTexto(exchange, 404, "Não encontrado");
                return;
            }
            try (InputStream pagina = DashboardServer.class.getResourceAsStream("/dashboard.html")) {
                if (pagina == null) {
                    responderTexto(exchange, 404, "Página do dashboard não encontrada no classpath");
                    return;
                }
                byte[] conteudo = pagina.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, conteudo.length);
                try (OutputStream corpo = exchange.getResponseBody()) {
                    corpo.write(conteudo);
                }
            }
        } finally {
            exchange.close();
        }
    }

    private void servirMercado(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod())) {
                responderJson(exchange, 405, new ErroResposta("Método não permitido"));
                return;
            }

            Map<String, String> opcoes = lerQuery(exchange.getRequestURI().getRawQuery());
            List<String> simbolos = lerSimbolos(opcoes.getOrDefault("symbols", "BTCUSDT,ETHUSDT,SOLUSDT"));
            String timeframe = opcoes.getOrDefault("timeframe", "1m");
            int periodoCurto = lerInteiro(opcoes, "short", 9);
            int periodoLongo = lerInteiro(opcoes, "long", 21);
            if (periodoCurto <= 0 || periodoLongo <= periodoCurto || periodoLongo >= LIMITE_CANDLES) {
                throw new IllegalArgumentException("períodos inválidos; use 0 < SMA curta < SMA longa < 200");
            }

            int limite = Math.min(LIMITE_CANDLES, Math.max(80, periodoLongo + 2));
            List<CompletableFuture<SerieMercado>> futuros = simbolos.stream()
                    .map(simbolo -> clienteBinance.buscarCandlesFechados(simbolo, timeframe, limite)
                            .thenApply(candles -> criarSerie(simbolo, timeframe, periodoCurto, periodoLongo, candles)))
                    .toList();
            CompletableFuture.allOf(futuros.toArray(new CompletableFuture[0])).join();
            List<SerieMercado> series = futuros.stream().map(CompletableFuture::join).toList();

            responderJson(exchange, 200, new RespostaMercado(
                    timeframe, periodoCurto, periodoLongo, Instant.now().toString(), series));
        } catch (IllegalArgumentException e) {
            responderJson(exchange, 400, new ErroResposta(e.getMessage()));
        } catch (CompletionException e) {
            Throwable causa = e.getCause() == null ? e : e.getCause();
            responderJson(exchange, 502, new ErroResposta("Falha ao consultar a Binance: " + causa.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private static List<String> lerSimbolos(String texto) {
        List<String> simbolos = java.util.Arrays.stream(texto.split(","))
                .map(String::strip)
                .map(String::toUpperCase)
                .filter(simbolo -> !simbolo.isEmpty())
                .toList();
        if (simbolos.isEmpty() || simbolos.size() > LIMITE_SIMBOLOS
                || simbolos.stream().anyMatch(simbolo -> !simbolo.matches("[A-Z0-9]{2,20}"))) {
            throw new IllegalArgumentException("informe de 1 a 8 símbolos Binance válidos");
        }
        return simbolos;
    }

    private static int lerInteiro(Map<String, String> opcoes, String chave, int padrao) {
        try {
            return Integer.parseInt(opcoes.getOrDefault(chave, Integer.toString(padrao)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("parâmetro " + chave + " precisa ser um inteiro");
        }
    }

    private static Map<String, String> lerQuery(String query) {
        Map<String, String> opcoes = new HashMap<>();
        if (query == null || query.isBlank()) {
            return opcoes;
        }
        for (String par : query.split("&")) {
            int separador = par.indexOf('=');
            String chave = separador < 0 ? par : par.substring(0, separador);
            String valor = separador < 0 ? "" : par.substring(separador + 1);
            opcoes.put(
                    URLDecoder.decode(chave, StandardCharsets.UTF_8),
                    URLDecoder.decode(valor, StandardCharsets.UTF_8));
        }
        return opcoes;
    }

    private static SerieMercado criarSerie(String simbolo, String timeframe, int periodoCurto,
                                            int periodoLongo, List<BinanceKlineDTO> candles) {
        if (candles.size() <= periodoLongo) {
            throw new IllegalArgumentException("candles insuficientes para " + simbolo + " e SMA" + periodoLongo);
        }

        List<BigDecimal> fechamentos = candles.stream().map(BinanceKlineDTO::precoFechamento).toList();
        List<BigDecimal> smaCurta = AnalisadorTecnico.calcularSMA(fechamentos, periodoCurto);
        List<BigDecimal> smaLonga = AnalisadorTecnico.calcularSMA(fechamentos, periodoLongo);
        List<PontoMercado> pontos = new ArrayList<>(candles.size());
        Sinal ultimoCruzamento = Sinal.MANTER;
        String dataUltimoCruzamento = null;

        for (int i = 0; i < candles.size(); i++) {
            pontos.add(new PontoMercado(
                    candles.get(i).encerramento().toString(), fechamentos.get(i), smaCurta.get(i), smaLonga.get(i)));
            if (i > 0) {
                Sinal sinal = AnalisadorTecnico.detectarCruzamento(
                        smaCurta.get(i - 1), smaLonga.get(i - 1), smaCurta.get(i), smaLonga.get(i));
                if (sinal != Sinal.MANTER) {
                    ultimoCruzamento = sinal;
                    dataUltimoCruzamento = candles.get(i).encerramento().toString();
                }
            }
        }

        BigDecimal primeiro = fechamentos.getFirst();
        BigDecimal atual = fechamentos.getLast();
        BigDecimal variacao = primeiro.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : atual.subtract(primeiro)
                        .divide(primeiro, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
        String tendencia = smaCurta.getLast().compareTo(smaLonga.getLast()) > 0 ? "ALTA" : "BAIXA";

        return new SerieMercado(simbolo, timeframe, atual, variacao, tendencia,
                ultimoCruzamento == Sinal.MANTER ? null : ultimoCruzamento.name(), dataUltimoCruzamento, pontos);
    }

    private void responderJson(HttpExchange exchange, int status, Object conteudo) throws IOException {
        byte[] corpo = jsonMapper.writeValueAsBytes(conteudo);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, corpo.length);
        try (OutputStream saida = exchange.getResponseBody()) {
            saida.write(corpo);
        }
    }

    private static void responderTexto(HttpExchange exchange, int status, String texto) throws IOException {
        byte[] corpo = texto.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, corpo.length);
        try (OutputStream saida = exchange.getResponseBody()) {
            saida.write(corpo);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
        executor.shutdownNow();
    }

    public record RespostaMercado(String timeframe, int periodoCurto, int periodoLongo,
                                  String atualizadoEm, List<SerieMercado> series) {}

    public record SerieMercado(String simbolo, String timeframe, BigDecimal precoAtual,
                               BigDecimal variacaoPercentual, String tendencia,
                               String ultimoCruzamento, String dataUltimoCruzamento,
                               List<PontoMercado> pontos) {}

    public record PontoMercado(String horario, BigDecimal fechamento,
                               BigDecimal smaCurta, BigDecimal smaLonga) {}

    private record ErroResposta(String erro) {}
}