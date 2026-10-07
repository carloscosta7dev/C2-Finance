# C2 Finance — Scanner e Analisador Financeiro (Java)

Aplicação Java para teste de carga HTTP, consulta de preços públicos da Binance, análise técnica e backtesting. Inclui motor HTTP assíncrono com limite de concorrência, monitor de preços com alertas e um dashboard local com gráficos de mercado.

## Pilha técnica

- **Java 21**: records, switch expressions e threads virtuais.
- **Maven**: compilação e empacotamento do JAR executável.
- **Jackson** (`jackson-databind`): leitura da resposta JSON da Binance.
- **Jakarta Mail** (`jakarta.mail-api` e `angus-mail`): canal de alerta SMTP opcional.

## Estrutura

```text
src/main/java/com/scanner/financeiro/
├── Main.java
├── modelo/             # AtivoFinanceiro, PrecoHistorico, Sinal
├── motor/              # HTTP assíncrono, limite de concorrência e backoff
├── api/                # Cliente Binance e resultados por símbolo
│   └── dto/            # DTO da resposta de ticker
├── dashboard/          # Servidor HTTP local do painel
├── analise/            # SMA, EMA e cruzamentos
├── alerta/             # Canais de alerta e monitor de preços
└── backtest/           # Leitura CSV, engine e relatório

scripts/
└── verificar_sintaxe.py # Verificador estrutural dos fontes Java

src/main/resources/
└── dashboard.html       # Interface responsiva com gráficos

src/test/java/com/scanner/financeiro/
├── modelo/             # Invariantes dos modelos
├── motor/              # Retry, resultado HTTP e concorrência local
├── api/                # Conversão e busca em lote
├── analise/            # SMA, EMA e cruzamentos
├── alerta/             # Saída, configuração e ciclo do monitor
├── dashboard/          # Servidor local do painel
└── backtest/           # CSV, estratégia e relatório
```

## Build

Requer JDK 21 e Maven instalados.

```bash
mvn clean package
```

O JAR executável é gerado em `target/scanner-financeiro.jar`, com as dependências incluídas.

## Uso

### Teste de carga HTTP

```bash
java -jar target/scanner-financeiro.jar loadtest -u http://127.0.0.1:8080/api -r 1000 -c 50 -m GET
```

Opções: `-u` (URL, obrigatório), `-r` (total de requisições, padrão `1000`), `-c` (concorrência máxima, padrão `50`) e `-m` (`GET` ou `POST`, padrão `GET`). O resumo agrupa resultados por código HTTP e contabiliza falhas de conexão.

### Monitoramento de preços

```bash
java -jar target/scanner-financeiro.jar monitor --simbolos BTCUSDT,ETHUSDT,SOLUSDT --timeframe 1m --curto 9 --longo 21 --intervalo 30 --canal console
```

O monitor consulta a API REST pública da Binance a cada intervalo (em segundos, padrão `30`) e analisa candles fechados do timeframe escolhido (padrão `1m`). Por padrão, detecta cruzamentos SMA 9×21. Os parâmetros `--curto` e `--longo` alteram esses períodos. Uma vela ainda aberta não gera sinal; cada vela fechada é processada uma única vez. O monitor precisa de acesso à internet.

Os gatilhos de preço continuam disponíveis como opção adicional: passe `--gatilhos 55000,2500,120` para associar um valor a cada símbolo, na mesma ordem. Esses alertas são disparados quando o preço spot cai abaixo do valor e são rearmados após recuperação.

O canal padrão é `console`. Para enviar os sinais técnicos e alertas de preço por e-mail, configure as variáveis SMTP abaixo e passe `--canal email`.

### Dashboard no navegador

```powershell
java -jar target/scanner-financeiro.jar dashboard --porta 8765
```

Abra `http://127.0.0.1:8765`. O painel é servido apenas no loopback local e consulta a Binance pelo processo Java. A navegação inclui:

- **Visão de mercado**: lista de preços, variação no período e gráficos de candles fechados, SMA curta/longa e distância entre as médias.
- **Sinais técnicos**: tabela de tendência, último cruzamento, horário, preço e valores das SMAs; permite filtrar compra, venda ou ausência de cruzamento.
- **Ativos monitorados**: adicionar/remover até oito símbolos, configurar gatilhos de preço e ver os alertas. A lista e os gatilhos ficam salvos no `localStorage` deste navegador; os avisos de gatilho aparecem no painel enquanto ele estiver aberto. Para alertas por e-mail, use o comando `monitor --canal email`.

Símbolos, timeframe, períodos SMA e frequência de atualização são configuráveis. O gráfico usa Chart.js e fontes carregadas por CDN; o navegador precisa de internet para esses recursos e o processo Java precisa de acesso à Binance.

No VS Code, executar `Main` sem argumentos inicia o dashboard e tenta abrir o navegador automaticamente. Se a porta `8765` estiver ocupada, o sistema seleciona outra porta livre e imprime o endereço correspondente. Use `--help` para exibir a lista de comandos.

### Backtest

```bash
java -jar target/scanner-financeiro.jar backtest --arquivo caminho/para/historico.csv --curto 9 --longo 21 --capital 10000
```

Opções: `--arquivo` (CSV, obrigatório), `--curto` (período SMA curto, padrão `9`), `--longo` (período SMA longo, padrão `21`) e `--capital` (capital inicial, padrão `10000`). O CSV deve conter uma data ISO (`yyyy-MM-dd`) e o preço de fechamento separados por vírgula. O cabeçalho é opcional. Exemplo:

```csv
data,preco_fechamento
2025-01-01,95000.25
2025-01-02,96200.10
```

O backtest investe todo o caixa em sinais de compra e liquida a posição em sinais de venda. Não modela taxas nem slippage. Uma posição ainda aberta ao final é avaliada pelo último preço do histórico.

## Alertas por e-mail (opcional)

`EmailAlertaChannel` lê credenciais do ambiente. No PowerShell:

```powershell
$env:SMTP_HOST = "smtp.seuservidor.com"
$env:SMTP_PORT = "587"
$env:SMTP_USER = "seu-usuario@dominio.com"
$env:SMTP_PASS = "sua-senha-de-app"
$env:ALERTA_DESTINATARIO = "voce@dominio.com"
```

O comando `monitor --canal email` instancia `EmailAlertaChannel.apartirDeVariaveisAmbiente()` automaticamente. Nunca grave credenciais no código ou no controle de versão. O envio depende de credenciais SMTP válidas e conectividade com o servidor. Se e-mail não for necessário, as dependências Jakarta Mail e a implementação `EmailAlertaChannel` podem ser removidas; o restante do sistema depende da interface `CanalAlerta`.

## Verificação

O verificador estrutural pode ser executado na raiz do repositório:

```bash
python scripts/verificar_sintaxe.py .
```

Ele verifica delimitadores, literais, nomes de tipos públicos e correspondência entre `package` e caminho do arquivo. Não substitui o compilador Java. A suíte JUnit 5 é executada com:

```bash
mvn test
```

Os testes cobrem os cálculos técnicos, o retry, respostas HTTP locais e limite de concorrência, conversão e falha isolada por símbolo, alertas, leitura CSV, backtesting e as rotas locais do dashboard. A última execução passou com 42 testes, sem falhas ou erros. `mvn clean package` também concluiu sem erros; o verificador estrutural encontrou 0 problemas nos 21 arquivos Java.

## Status de prontidão

O projeto está pronto para uso e avaliação local como MVP: o JAR compila, os 41 testes passam, o dashboard foi exercitado no navegador em desktop e celular e uma consulta real de candles fechados à Binance foi confirmada. O dashboard e o monitor dependem de acesso à internet para consultar a API pública.

O envio de e-mail ainda não foi validado contra um servidor SMTP real. Para confirmar essa parte, configure as variáveis SMTP no seu ambiente e execute o monitor com `--canal email`; não compartilhe a senha no repositório nem no chat. Por isso, o projeto ainda precisa dessa verificação antes de ser considerado pronto para operação com e-mail em produção.

## Transparência sobre IA

Ferramentas de inteligência artificial foram utilizadas como apoio na implementação e atualização de código, testes e documentação. As alterações foram compiladas e verificadas com a suíte automatizada; integrações que dependem de credenciais próprias, como SMTP, ainda precisam ser validadas no ambiente do responsável pelo projeto.

## Observações

- O backtesting é uma ferramenta de estudo e não garante desempenho futuro nem constitui recomendação de investimento.
- Proteja credenciais SMTP e quaisquer outras informações sensíveis; use variáveis de ambiente ou um gerenciador de segredos.