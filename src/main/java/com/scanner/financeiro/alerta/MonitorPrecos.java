package com.scanner.financeiro.alerta;

import com.scanner.financeiro.analise.AnalisadorTecnico;
import com.scanner.financeiro.api.BinanceClient;
import com.scanner.financeiro.api.dto.BinanceKlineDTO;
import com.scanner.financeiro.api.ResultadoAtivo;
import com.scanner.financeiro.modelo.AtivoFinanceiro;
import com.scanner.financeiro.modelo.Sinal;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Loop de monitoramento em background (Fase 4.1) que, a cada intervalo fixo,
 * busca os preços atuais via {@link BinanceClient} e dispara alertas (Fase
 * 4.2) quando um ativo cai abaixo do gatilho configurado.
 *
 * O alerta é "rearmado": uma vez disparado, só dispara de novo depois que o
 * preço volta a ficar acima do gatilho e cai abaixo outra vez. Sem isso, o
 * monitor imprimiria o mesmo alerta a cada tick enquanto o preço permanecesse
 * baixo — o que na prática vira ruído e as pessoas param de prestar atenção.
 */
public class MonitorPrecos {

    private final BinanceClient clienteBinance;
    private final CanalAlerta canalAlerta;
    private final List<String> simbolos;
    private final Map<String, BigDecimal> gatilhosQueda;
    private final String intervaloCandles;
    private final int periodoCurto;
    private final int periodoLongo;
    private final Map<String, Boolean> jaAlertado = new ConcurrentHashMap<>();
    private final Map<String, java.time.Instant> ultimoCandleProcessado = new ConcurrentHashMap<>();

    public MonitorPrecos(BinanceClient clienteBinance, CanalAlerta canalAlerta, Map<String, BigDecimal> gatilhosQueda) {
        this(clienteBinance, canalAlerta, new ArrayList<>(gatilhosQueda.keySet()), gatilhosQueda, null, 0, 0);
    }

    public MonitorPrecos(BinanceClient clienteBinance, CanalAlerta canalAlerta, List<String> simbolos,
                        Map<String, BigDecimal> gatilhosQueda, String intervaloCandles,
                        int periodoCurto, int periodoLongo) {
        if (intervaloCandles != null
                && (periodoCurto <= 0 || periodoLongo <= periodoCurto || periodoLongo > 998)) {
            throw new IllegalArgumentException("períodos inválidos: espera-se 0 < curto < longo <= 998");
        }
        this.clienteBinance = clienteBinance;
        this.canalAlerta = canalAlerta;
        this.simbolos = List.copyOf(simbolos);
        this.gatilhosQueda = gatilhosQueda;
        this.intervaloCandles = intervaloCandles;
        this.periodoCurto = periodoCurto;
        this.periodoLongo = periodoLongo;
    }

    /** Inicia o loop em uma thread daemon; não bloqueia quem chamou. */
    public ScheduledExecutorService iniciar(Duration intervalo) {
        if (intervalo.isNegative() || intervalo.isZero() || intervalo.toSeconds() == 0) {
            throw new IllegalArgumentException("intervalo de monitoramento deve ser de pelo menos 1 segundo");
        }
        ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "monitor-precos");
            t.setDaemon(true);
            return t;
        });
        agendador.scheduleAtFixedRate(this::verificarPrecos, 0, intervalo.toSeconds(), TimeUnit.SECONDS);
        return agendador;
    }

    private void verificarPrecos() {
        if (!gatilhosQueda.isEmpty()) {
            clienteBinance.buscarPrecos(new ArrayList<>(gatilhosQueda.keySet()))
                    .thenAccept(resultados -> resultados.forEach(this::avaliarResultado))
                    .exceptionally(erro -> {
                        System.err.println("Erro ao verificar preços: " + erro.getMessage());
                        return null;
                    });
        }

        if (intervaloCandles != null) {
            simbolos.forEach(this::verificarCruzamentoTecnico);
        }
    }

    private void verificarCruzamentoTecnico(String simbolo) {
        clienteBinance.buscarCandlesFechados(simbolo, intervaloCandles, periodoLongo + 2)
                .thenAccept(candles -> avaliarCruzamentoTecnico(simbolo, candles))
                .exceptionally(erro -> {
                    System.err.println("Falha na análise técnica de " + simbolo + ": " + erro.getMessage());
                    return null;
                });
    }

    private void avaliarCruzamentoTecnico(String simbolo, List<BinanceKlineDTO> candles) {
        if (candles.size() <= periodoLongo) {
            return;
        }

        BinanceKlineDTO ultimoCandle = candles.get(candles.size() - 1);
        AtomicBoolean candleNovo = new AtomicBoolean();
        ultimoCandleProcessado.compute(simbolo, (chave, anterior) -> {
            if (anterior == null || ultimoCandle.encerramento().isAfter(anterior)) {
                candleNovo.set(true);
                return ultimoCandle.encerramento();
            }
            return anterior;
        });
        if (!candleNovo.get()) {
            return;
        }

        List<BigDecimal> fechamentos = candles.stream().map(BinanceKlineDTO::precoFechamento).toList();
        List<BigDecimal> mediaCurta = AnalisadorTecnico.calcularSMA(fechamentos, periodoCurto);
        List<BigDecimal> mediaLonga = AnalisadorTecnico.calcularSMA(fechamentos, periodoLongo);
        int atual = fechamentos.size() - 1;
        Sinal sinal = AnalisadorTecnico.detectarCruzamento(
                mediaCurta.get(atual - 1), mediaLonga.get(atual - 1),
                mediaCurta.get(atual), mediaLonga.get(atual));

        if (sinal != Sinal.MANTER) {
            try {
                canalAlerta.enviar(
                        "Sinal técnico " + sinal + " para " + simbolo,
                        "Timeframe: " + intervaloCandles
                                + " | Fechamento: " + ultimoCandle.encerramento()
                                + " | Preço: " + ultimoCandle.precoFechamento()
                                + " | SMA" + periodoCurto + ": " + mediaCurta.get(atual)
                                + " | SMA" + periodoLongo + ": " + mediaLonga.get(atual));
            } catch (RuntimeException e) {
                ultimoCandleProcessado.remove(simbolo, ultimoCandle.encerramento());
                throw e;
            }
        }
    }

    private void avaliarResultado(ResultadoAtivo resultado) {
        if (!resultado.sucesso()) {
            System.err.println("Falha ao obter preço de " + resultado.simbolo() + ": " + resultado.erro().getMessage());
            return;
        }

        AtivoFinanceiro ativo = resultado.ativo();
        BigDecimal gatilho = gatilhosQueda.get(ativo.simbolo());
        if (gatilho == null) return;

        boolean abaixoDoGatilho = ativo.precoAtual().compareTo(gatilho) < 0;
        boolean jaDisparou = jaAlertado.getOrDefault(ativo.simbolo(), false);

        if (abaixoDoGatilho && !jaDisparou) {
            canalAlerta.enviar(
                    ativo.simbolo() + " caiu abaixo de " + gatilho,
                    "Preço atual: " + ativo.precoAtual() + " | Gatilho: " + gatilho + " | " + ativo.ultimaAtualizacao());
            jaAlertado.put(ativo.simbolo(), true);
        } else if (!abaixoDoGatilho && jaDisparou) {
            jaAlertado.put(ativo.simbolo(), false);
        }
    }
}