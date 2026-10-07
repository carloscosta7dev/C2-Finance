# Roadmap do Projeto: Scanner e Analisador Financeiro em Java

Status: `[ ]` Pendente | `[x]` Concluído | `[~]` Em andamento

As quatro fases abaixo estão implementadas. Cada item referencia os arquivos que fornecem a funcionalidade.

## Fase 1: Fundações e motor assíncrono

- [x] Passo 1.1: Modelo de dados com `BigDecimal` (`src/main/java/com/scanner/financeiro/modelo/AtivoFinanceiro.java`).
- [x] Passo 1.2: Cliente HTTP moderno (`src/main/java/com/scanner/financeiro/motor/MotorRequisicoes.java`).
- [x] Passo 1.3: Controle de concorrência com `Semaphore`, `CompletableFuture` e threads virtuais do Java 21 (`MotorRequisicoes.java`).
- [x] Passo 1.4: Subcomando `loadtest` com opções de URL, quantidade de requisições e concorrência (`src/main/java/com/scanner/financeiro/Main.java`).

## Fase 2: Conexão com o mercado

- [x] Passo 2.1: Cliente da API pública de ticker da Binance (`src/main/java/com/scanner/financeiro/api/BinanceClient.java`).
- [x] Passo 2.2: Desserialização JSON com Jackson (`src/main/java/com/scanner/financeiro/api/dto/BinanceTickerDTO.java`).
- [x] Passo 2.3: Tratamento de falhas de rede e respostas HTTP 429 (`src/main/java/com/scanner/financeiro/motor/BackoffExponencial.java`).
- [x] Passo 2.4: Busca paralela de múltiplos símbolos e resultado independente por ativo (`BinanceClient.buscarPrecos`, `src/main/java/com/scanner/financeiro/api/ResultadoAtivo.java`).

## Fase 3: Análise técnica

- [x] Passo 3.1: Analisador técnico (`src/main/java/com/scanner/financeiro/analise/AnalisadorTecnico.java`).
- [x] Passo 3.2: Média móvel simples (SMA) com janela deslizante O(n) (`AnalisadorTecnico.calcularSMA`).
- [x] Passo 3.3: Média móvel exponencial (EMA) (`AnalisadorTecnico.calcularEMA`).
- [x] Passo 3.4: Detecção de cruzamento das médias (`AnalisadorTecnico.detectarCruzamento`).

## Fase 4: Alertas, automação e backtesting

- [x] Passo 4.1: Monitor em background com polling de candles fechados da Binance e detecção SMA ao vivo (`src/main/java/com/scanner/financeiro/alerta/MonitorPrecos.java`, `src/main/java/com/scanner/financeiro/api/BinanceClient.java`).
- [x] Passo 4.2: Alertas de cruzamento/gatilho por console ou e-mail SMTP selecionável no CLI (`src/main/java/com/scanner/financeiro/alerta/`, `src/main/java/com/scanner/financeiro/Main.java`).
- [x] Passo 4.3: Leitura de CSV com dados históricos (`src/main/java/com/scanner/financeiro/backtest/LeitorHistorico.java`).
- [x] Passo 4.4: Backtest de cruzamento de médias e relatório de lucro/prejuízo (`BacktestEngine.java`, `RelatorioBacktest.java` no pacote `backtest`).
- [x] Painel local no navegador com gráficos de candles fechados, SMAs, distância entre médias e dados Binance (`src/main/java/com/scanner/financeiro/dashboard/DashboardServer.java`, `src/main/resources/dashboard.html`).

## Verificação

- `mvn clean package` concluiu sem erros e gerou o JAR executável.
- `mvn test` executou 41 testes JUnit 5, incluindo testes locais do endpoint e página do dashboard, sem depender de SMTP externo.
- `python scripts/verificar_sintaxe.py .` verificou 21 arquivos Java e encontrou 0 problemas. Esse verificador é estrutural e não substitui a compilação Java.

## Próxima etapa

As fases acima estão concluídas. Evoluções novas devem ser tratadas como uma Fase 5, a definir com o autor do projeto.