package com.scanner.financeiro;

import com.scanner.financeiro.alerta.CanalAlerta;
import com.scanner.financeiro.alerta.ConsoleAlertaChannel;
import com.scanner.financeiro.alerta.EmailAlertaChannel;
import com.scanner.financeiro.alerta.MonitorPrecos;
import com.scanner.financeiro.api.BinanceClient;
import com.scanner.financeiro.backtest.BacktestEngine;
import com.scanner.financeiro.backtest.LeitorHistorico;
import com.scanner.financeiro.backtest.RelatorioBacktest;
import com.scanner.financeiro.dashboard.DashboardServer;
import com.scanner.financeiro.modelo.PrecoHistorico;
import com.scanner.financeiro.motor.MetodoHttp;
import com.scanner.financeiro.motor.MotorRequisicoes;
import com.scanner.financeiro.motor.ResultadoRequisicao;

import java.awt.Desktop;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.BindException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Ponto de entrada único, com três subcomandos — um por frente do roadmap:
 *
 *   loadtest  -> Fase 1: porta direta do script Python (motor assíncrono).
 *   monitor   -> Fases 2 e 4.1/4.2: monitoramento em tempo real + alertas.
 *   backtest  -> Fase 4.3/4.4: backtesting contra um CSV histórico.
 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        String comando = args.length == 0 ? "dashboard" : args[0];
        String[] resto = args.length == 0 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);

        try {
            switch (comando) {
                case "help", "--help" -> imprimirUso();
                case "loadtest" -> executarLoadTest(resto);
                case "monitor" -> executarMonitor(resto);
                case "backtest" -> executarBacktest(resto);
                case "dashboard" -> executarDashboard(resto);
                default -> {
                    System.err.println("Comando desconhecido: " + comando);
                    imprimirUso();
                }
            }
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void imprimirUso() {
        System.out.println("""
                Uso: java -jar scanner-financeiro.jar <comando> [opções]

                Comandos disponíveis:
                  loadtest  -u <url> [-r requisicoes=1000] [-c concorrencia=50] [-m GET|POST]
                  monitor   --simbolos BTCUSDT,ETHUSDT [--gatilhos 60000,3000] [--timeframe 1m] [--curto 9] [--longo 21] [--intervalo 30] [--canal console|email]
                  backtest  --arquivo caminho.csv [--curto 9] [--longo 21] [--capital 10000]
                                    dashboard [--porta 8765]
                """);
    }

    // ---------- loadtest (Fase 1: porta direta do script Python) ----------

    private static void executarLoadTest(String[] args) {
        Map<String, String> opcoes = parsearOpcoes(args);
        String url = exigir(opcoes, "u", "url");
        int totalRequisicoes = Integer.parseInt(opcoes.getOrDefault("r", opcoes.getOrDefault("requests", "1000")));
        int concorrencia = Integer.parseInt(opcoes.getOrDefault("c", opcoes.getOrDefault("concurrency", "50")));
        MetodoHttp metodo = MetodoHttp.valueOf(opcoes.getOrDefault("m", opcoes.getOrDefault("method", "GET")).toUpperCase());

        System.out.println("Iniciando teste de carga...");
        System.out.printf("Alvo: %s | Método: %s | Total: %d | Concorrência: %d%n", url, metodo, totalRequisicoes, concorrencia);
        System.out.println("-".repeat(50));

        Map<String, LongAdder> estatisticas = new ConcurrentHashMap<>();

        try (MotorRequisicoes motor = new MotorRequisicoes(concorrencia, Duration.ofSeconds(10))) {
            long inicioNanos = System.nanoTime();

            List<CompletableFuture<Void>> tarefas = new ArrayList<>(totalRequisicoes);
            for (int i = 0; i < totalRequisicoes; i++) {
                tarefas.add(motor.requisitarAsync(url, metodo)
                        .thenAccept(resultado -> registrarEstatistica(estatisticas, resultado)));
            }
            CompletableFuture.allOf(tarefas.toArray(new CompletableFuture[0])).join();

            double tempoTotalSegundos = (System.nanoTime() - inicioNanos) / 1_000_000_000.0;

            System.out.println("\n--- Relatório de Performance ---");
            System.out.printf("Tempo total executado  : %.3f segundos%n", tempoTotalSegundos);
            if (tempoTotalSegundos > 0) {
                System.out.printf("Requisições por segundo: %.2f req/s%n", totalRequisicoes / tempoTotalSegundos);
            }
            System.out.println("\nResumo das Respostas HTTP:");
            estatisticas.forEach((chave, contador) ->
                    System.out.printf(" -> [%s]: %d requisições%n", chave, contador.sum()));
        }
    }

    private static void registrarEstatistica(Map<String, LongAdder> estatisticas, ResultadoRequisicao resultado) {
        String chave = resultado.falhaDeConexao() ? "Falhas de Conexão" : String.valueOf(resultado.codigoStatus());
        estatisticas.computeIfAbsent(chave, k -> new LongAdder()).increment();
    }

    // ---------- monitor (Fases 2 + 4.1/4.2) ----------

    private static void executarMonitor(String[] args) {
        Map<String, String> opcoes = parsearOpcoes(args);
        List<String> simbolos = Arrays.stream(exigir(opcoes, "simbolos").split(","))
                .map(String::strip)
                .filter(simbolo -> !simbolo.isEmpty())
                .map(simbolo -> simbolo.toUpperCase(Locale.ROOT))
                .toList();
        if (simbolos.isEmpty()) {
            throw new IllegalArgumentException("informe ao menos um símbolo em --simbolos");
        }

        String gatilhosOpcao = opcoes.getOrDefault("gatilhos", "").strip();
        List<String> gatilhosTexto = gatilhosOpcao.isEmpty()
                ? List.of()
                : Arrays.stream(gatilhosOpcao.split(",")).map(String::strip).toList();
        if (!gatilhosTexto.isEmpty() && gatilhosTexto.size() != simbolos.size()) {
            throw new IllegalArgumentException("informe um gatilho por símbolo ou omita --gatilhos");
        }
        int intervaloSegundos = Integer.parseInt(opcoes.getOrDefault("intervalo", "30"));
        String intervaloCandles = opcoes.getOrDefault("timeframe", "1m").strip();
        int periodoCurto = Integer.parseInt(opcoes.getOrDefault("curto", "9"));
        int periodoLongo = Integer.parseInt(opcoes.getOrDefault("longo", "21"));

        Map<String, BigDecimal> gatilhos = new HashMap<>();
        for (int i = 0; i < gatilhosTexto.size(); i++) {
            gatilhos.put(simbolos.get(i), new BigDecimal(gatilhosTexto.get(i)));
        }

        String tipoCanal = opcoes.getOrDefault("canal", "console").toLowerCase(Locale.ROOT);
        CanalAlerta canal = switch (tipoCanal) {
            case "console" -> new ConsoleAlertaChannel();
            case "email" -> EmailAlertaChannel.apartirDeVariaveisAmbiente();
            default -> throw new IllegalArgumentException("canal inválido: " + tipoCanal + " (use console ou email)");
        };

        System.out.printf("Monitorando %s com SMA%d×SMA%d em candles %s, verificando a cada %ds pelo canal %s.%n",
                simbolos, periodoCurto, periodoLongo, intervaloCandles, intervaloSegundos, tipoCanal);
        if (!gatilhos.isEmpty()) {
            System.out.println("Gatilhos de preço adicionais: " + gatilhos);
        }
        System.out.println("Pressione Ctrl+C para encerrar.");

        try (MotorRequisicoes motor = new MotorRequisicoes(10, Duration.ofSeconds(10))) {
            BinanceClient cliente = new BinanceClient(motor);
            MonitorPrecos monitor = new MonitorPrecos(
                    cliente, canal, simbolos, gatilhos, intervaloCandles, periodoCurto, periodoLongo);
            var agendador = monitor.iniciar(Duration.ofSeconds(intervaloSegundos));

            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                agendador.shutdownNow();
            }
        }
    }

    private static void executarDashboard(String[] args) throws IOException {
        Map<String, String> opcoes = parsearOpcoes(args);
        int porta = Integer.parseInt(opcoes.getOrDefault("porta", "8765"));

        try (MotorRequisicoes motor = new MotorRequisicoes(10, Duration.ofSeconds(10));
               DashboardServer dashboard = criarDashboard(porta, new BinanceClient(motor))) {
            dashboard.iniciar();
            String endereco = "http://127.0.0.1:" + dashboard.porta();
            System.out.println("Dashboard disponível em " + endereco);
            System.out.println("Pressione Ctrl+C para encerrar.");
            abrirNoNavegador(endereco);
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static DashboardServer criarDashboard(int porta, BinanceClient cliente) throws IOException {
        try {
            return new DashboardServer(porta, cliente);
        } catch (BindException e) {
            if (porta == 0) {
                throw e;
            }
            System.err.println("Porta " + porta + " ocupada; selecionando uma porta livre.");
            return new DashboardServer(0, cliente);
        }
    }

    private static void abrirNoNavegador(String endereco) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        try {
            if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(endereco));
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            System.err.println("Não foi possível abrir o navegador automaticamente: " + e.getMessage());
        }
    }

    // ---------- backtest (Fases 4.3/4.4) ----------

    private static void executarBacktest(String[] args) throws IOException {
        Map<String, String> opcoes = parsearOpcoes(args);
        Path arquivo = Path.of(exigir(opcoes, "arquivo"));
        int periodoCurto = Integer.parseInt(opcoes.getOrDefault("curto", "9"));
        int periodoLongo = Integer.parseInt(opcoes.getOrDefault("longo", "21"));
        BigDecimal capitalInicial = new BigDecimal(opcoes.getOrDefault("capital", "10000"));

        List<PrecoHistorico> historico = LeitorHistorico.lerCsv(arquivo);
        System.out.printf("Carregados %d pontos de dados históricos de %s%n", historico.size(), arquivo);

        RelatorioBacktest relatorio = BacktestEngine.executar(historico, periodoCurto, periodoLongo, capitalInicial);
        relatorio.imprimirRelatorio();
    }

    // ---------- parsing de argumentos de linha de comando ----------

    private static Map<String, String> parsearOpcoes(String[] args) {
        Map<String, String> opcoes = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String chave = args[i];
            if (chave.startsWith("--")) {
                chave = chave.substring(2);
            } else if (chave.startsWith("-")) {
                chave = chave.substring(1);
            } else {
                continue;
            }
            String valor = (i + 1 < args.length) ? args[++i] : "";
            opcoes.put(chave, valor);
        }
        return opcoes;
    }

    private static String exigir(Map<String, String> opcoes, String... chaves) {
        for (String chave : chaves) {
            if (opcoes.containsKey(chave)) return opcoes.get(chave);
        }
        throw new IllegalArgumentException("Parâmetro obrigatório ausente: --" + chaves[0]);
    }
}