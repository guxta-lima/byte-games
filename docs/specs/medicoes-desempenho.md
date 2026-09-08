# Medições de desempenho — vitrine ByteGames

Todas as medições em perfil `prod`, Lighthouse mobile, mesma máquina e mesmo
banco. Sem isso os números não são comparáveis.

## Como reproduzir

Este bloco existe para que a medição pós-fase seja feita exatamente igual. Se
qualquer item mudar, a comparação perde valor.

- **Perfil:** `./mvnw spring-boot:run -Dspring-boot.run.profiles=prod`
- **Banco:** MySQL 8.4.11 em container Docker no WSL2, publicado em
  `127.0.0.1:3306`, populado por `script_criacao_banco.sql` (4 categorias,
  5 produtos, todos com `imagem` nula).
- **Runtime:** Java 17 (Temurin 17.0.20).
- **Ferramenta:** Lighthouse CLI 13.4.1, Chrome headless, form factor mobile
  (padrão), `--only-categories=performance`.
- **Método:** 3 execuções por página; a tabela registra a **mediana** de cada
  métrica. Uma execução isolada de Lighthouse varia demais para sustentar
  afirmação de ganho.
- **Cuidado com o WSL2:** a VM do WSL desliga poucos segundos após o último
  comando terminar, derrubando o container do MySQL junto. É preciso manter um
  processo vivo dentro da distro durante toda a medição, senão a aplicação
  falha com `Connection refused` no meio do teste.
- **Cuidado com o devtools:** `spring-boot-devtools` está no classpath e injeta
  `spring.thymeleaf.cache=false` como padrão. Foi verificado empiricamente que o
  `application-prod.properties` vence esse padrão — alterar um template com a
  aplicação de pé não muda o HTML servido. Confirmar isso de novo antes da
  medição pós-fase.

## Linha de base — 2026-09-08 (antes de qualquer alteração)

| Página | Performance | LCP | CLS | Peso total | JS transferido |
|---|---|---|---|---|---|
| `/` | 92 | 2,7 s | 0,003 | 74,2 kB | 0,0 kB |
| `/produto/1` | 90 | 2,7 s | 0,000 | 80,0 kB | 0,0 kB |

Execuções individuais, para mostrar a dispersão:

| Página | Performance | LCP (ms) | Terceiros |
|---|---|---|---|
| `/` | 92, 92, 90 | 2704, 2696, 2697 | 58,4 kB |
| `/produto/1` | 92, 90, 90 | 2680, 2680, 2685 | 67,7 kB |

Observações:

- **A vitrine hoje não tem JavaScript nenhum** — `0,0 kB` transferidos em ambas
  as páginas. Todo orçamento de JS da Fase 2 será gasto a partir do zero, e é
  contra esta linha que ele tem de ser justificado.
- **CLS já está praticamente em zero** (0,003 e 0,000), porque as páginas são
  texto puro. Esse é justamente o número que a introdução de imagens ameaça; o
  alvo da spec é manter abaixo de 0,05.
- **As fontes remotas dominam o peso da página.** Do total, 58,4 kB em `/` e
  67,7 kB em `/produto/1` são de terceiros — o `@import` do Google Fonts na
  primeira linha de `estilo.css`. São 3 requisições em cadeia bloqueando o CSS,
  o pior caso para LCP, e o motivo do LCP estar em 2,7 s numa página sem imagem
  alguma. A Task 12 remove isso, e é onde deve aparecer o ganho mais limpo
  desta fase.
- O LCP de 2,7 s numa página de texto puro confirma que o gargalo atual é
  rede/bloqueio, não renderização nem banco.
