# Parada — 08/09/2026, 12h

Trabalho interrompido por escolha de prioridade (mudança para outro projeto).
Nada quebrado, nada pendente de decisão. Este arquivo não está versionado.

## Onde paramos, em uma linha

Fase 1 do plano `docs/plans/2026-09-08-vitrine-fase-1.md`: **7 das 14 tasks
concluídas, 38 dos 81 steps**, no meio da Task 8.

## Estado do git

Branch: **`vitrine-capa-e-movimento`** (a `main` está intocada, em `0c6bf0b`).

Oito commits, todos com teste verde antes do commit:

```
64a0798 Serve pasta de uploads em /imagens com cache imutavel
3c2ea2f Gera derivadas responsivas em WebP sem ampliar imagem pequena
d8922ec Adiciona servico de armazenamento de imagem com validacao por assinatura
514205a Mapeia campo imagem do produto e preserva capa na edicao sem upload
261f43a Adiciona Thumbnailator e encoder WebP com teste de plataforma
11567c4 Adiciona perfil de teste em H2 para testes sem banco externo
bd93c90 Adiciona perfil de medicao e registra linha de base de desempenho
42fece9 Adiciona especificacao e plano da vitrine com capa de produto
```

**Não commitado, e isto importa:** `src/test/java/loja/bytegames/UploadCapaTest.java`
existe e **está vermelho de propósito**. É o teste da Task 8, escrito antes da
implementação. O `ProdutoController` ainda **não** foi ligado ao serviço de
imagem, então esse teste falha. Não é regressão — é o passo vermelho do ciclo,
parado no meio.

## O que já está pronto e provado por teste

| Task | Entrega | Testes |
|---|---|---|
| 1 | Perfil `prod` de medição e linha de base registrada | — |
| 2 | Perfil de teste em H2 (`application-test.properties`) | 1 |
| 3 | Encoder WebP resolvido (`webp-imageio` da Sejda + Thumbnailator) | 1 |
| 4 | Campo `imagem` na entidade e no repositório | 4 |
| 5 | `ArmazenamentoImagemService` com validação por assinatura | 5 |
| 6 | Derivadas 400/800/1200 em WebP, sem ampliar imagem pequena | 7 |
| 7 | Rota `/imagens/**` com cache de 1 ano `immutable` | 3 |

A decisão que a especificação havia deixado em aberto está **resolvida**: o
encoder WebP funciona nesta máquina sem binário externo. O caminho alternativo
previsto no plano (só JPEG, ou `scrimage`) fica descartado — as Tasks 9 e 10
podem contar com `.webp` no `srcset` e com `<source type="image/webp">`.

## Linha de base de desempenho (registrada em `docs/specs/medicoes-desempenho.md`)

Lighthouse mobile, perfil `prod`, mediana de 3 execuções por página:

| Página | Performance | LCP | CLS | Peso total | JS |
|---|---|---|---|---|---|
| `/` | 92 | 2,7 s | 0,003 | 74,2 kB | **0 kB** |
| `/produto/1` | 90 | 2,7 s | 0,000 | 80,0 kB | **0 kB** |

Dois números que devem guiar o resto da fase:

- **Zero JavaScript hoje.** Todo orçamento de JS da Fase 2 sai do zero.
- **58–68 kB de terceiros**, ou seja quase todo o peso da página, vêm do
  `@import` de fonte remota na **primeira linha** do `estilo.css`. É o que
  mantém o LCP em 2,7 s numa página sem imagem nenhuma. A **Task 12** remove
  isso, e é onde o ganho mais limpo da fase deve aparecer.

## Próxima sessão: retomar exatamente aqui

1. **Task 8, Step 3** — ligar o serviço ao `ProdutoController`. O código está
   escrito no plano, a partir da linha 1312: injetar
   `ArmazenamentoImagemService` no construtor e substituir `salvarNovo`,
   `atualizar` e `excluir`. O teste já está no disco esperando ficar verde.
2. `./mvnw test -Dtest=UploadCapaTest` → esperado 3 testes passando (Step 4).
3. Commit (Step 5), e seguir para as Tasks 9 a 14.

## Antes de rodar qualquer coisa: três armadilhas desta máquina

1. **A VM do WSL2 derruba o MySQL.** O banco roda em container Docker dentro do
   WSL, e a VM desliga cerca de **5 segundos** depois do último comando
   terminar — levando o container com ela. O sintoma é
   `Connection refused: getsockopt` no `127.0.0.1:3306`, e ele engana: um teste
   de porta feito logo após um comando conecta normalmente, porque a VM ainda
   está viva naquele instante. Numa ocasião isso fez o HikariCP ficar **54
   minutos** em retry. Antes de subir a aplicação, deixar um processo de longa
   duração rodando dentro da distro (`wsl -d Ubuntu -- sleep infinity`) durante
   todo o trabalho.

   Subir o banco do zero, se o container tiver sido removido:

   ```bash
   wsl -d Ubuntu -- docker run -d --name bytegames-mysql --restart unless-stopped \
     -e MYSQL_ROOT_PASSWORD=bytegames_dev -e MYSQL_DATABASE=Byteloja \
     -p 3306:3306 mysql:8
   wsl -d Ubuntu -- sh -c 'docker exec -i bytegames-mysql mysql -uroot -pbytegames_dev \
     < /mnt/c/dev/pessoal/byte-games/script_criacao_banco.sql'
   ```

   O `.env` já está preenchido e é lido pelo `spring-dotenv`. Ele está no
   `.gitignore` — conferir que continua assim antes de qualquer push.

   **Nada disso é necessário para os testes das Tasks 2 a 14**, que rodam em H2.
   Só a aplicação de verdade e o `BytegamesDatabaseTests` precisam do MySQL.

2. **O `spring-boot-devtools` tenta desligar o cache do Thymeleaf.** Ele injeta
   `spring.thymeleaf.cache=false` como padrão. O `application-prod.properties`
   vence esse padrão — isso foi verificado empiricamente, alterando um template
   com a aplicação de pé e confirmando que o HTML servido não mudou. **Repetir
   essa verificação antes da medição pós-fase da Task 14**, senão os dois
   números não são comparáveis.

3. **`C:\Users\Gusta\AppData\Local\Temp\package.json` está corrompido** — é um
   texto de licença MIT salvo com nome de `package.json`. Ele quebra a resolução
   de módulos do Node em **qualquer** script executado sob a pasta `Temp`, com
   `ERR_INVALID_PACKAGE_CONFIG`. Nada a ver com este projeto, mas atrapalha
   ferramental. Não foi removido por não ter sido pedido.

O Lighthouse foi instalado global via npm (`lighthouse@13.4.1`) para a medição.
Se quiser desfazer: `npm rm -g lighthouse`.

## Três desvios do plano, já aplicados e commitados

1. **Task 2 — assertiva do teste reforçada.** O plano mandava só verificar que o
   contexto sobe, e esperava que isso falhasse sem o perfil H2. Com o MySQL de
   desenvolvimento ligado, o teste **passava contra o MySQL** e não provava nada
   sobre o H2 — pior, se o perfil quebrasse depois, a suíte passaria a escrever
   no banco de desenvolvimento sem sinal nenhum. O teste agora afirma
   `databaseProductName == "H2"`. Decisão confirmada com o usuário.
2. **Task 4 — `BytegamesDatabaseTests` teve de ser tocado.** O plano dizia que
   ele ficaria intocado, mas mudar a assinatura do repositório quebra a
   compilação dos testes. Os dois call sites passam `null`, preservando
   exatamente o que aquele teste já verificava.
3. **Task 5 — `readNBytes` em vez de `read`.** `read(byte[])` pode devolver
   menos bytes que o buffer mesmo havendo mais para ler. Com `MockMultipartFile`
   isso nunca acontece, então o teste não pegaria — mas num upload real, servido
   por stream de arquivo, uma leitura curta rejeitaria imagem válida de forma
   intermitente.

## Duas coisas que ficaram sem verificação

1. **A suíte inteira nunca rodou junta.** Só testes individuais, por
   `-Dtest=...`. Vale rodar `./mvnw test` (com o MySQL de pé, por causa do
   `BytegamesDatabaseTests`) antes de fechar a fase.
2. **O upload nunca foi exercitado pelo navegador.** Está provado por teste, não
   por uso. Faz sentido na Task 9, quando o formulário ganhar o campo de
   arquivo.

## Uma pendência descoberta de passagem

A pasta `uploads/` **já tinha 3 arquivos** de 22/08, de uma tentativa anterior,
com padrão de nome diferente do que o serviço novo gera
(`UUID_nomeoriginal.ext`, contra `UUID.ext` + `UUID-400.webp` etc.):

```
2535f7a0-...-91e1e3f97b2b_download.jpg
60aa91a6-...-52ac0b3855da_ChatGPT_Image_9_de_jun._de_2026,_14_44_12.png
8e044ebd-...-03c4152a9b97_ChatGPT_Image_9_de_jun._de_2026,_16_48_02.png
```

São órfãos: a coluna `imagem` de todos os 5 produtos do
`script_criacao_banco.sql` é `NULL`, então nenhum registro aponta para eles, e o
`remover(nomeBase)` do serviço novo nunca vai casar com esses nomes. Não
atrapalham nada — mas não vão aparecer na vitrine e podem ser apagados quando
você quiser. Não apaguei por não ter sido pedido.
