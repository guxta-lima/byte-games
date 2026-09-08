# Design — Vitrine com capa de jogo e camada de movimento

**Data:** 2026-09-08
**Escopo:** loja pública (`/`, `/produto/{id}`, `/carrinho`) e a estrutura de
templates que a sustenta.

---

## 1. Objetivo

A vitrine da ByteGames hoje é uma grade de cartões de texto. O objetivo deste
trabalho é entregar quatro coisas ao mesmo tempo, sem reescrever a aplicação:

- **Funcionalidade** — completar a capa de produto, que foi projetada no banco
  e nunca chegou ao código.
- **Beleza** — dar à loja uma identidade visual com movimento intencional.
- **Desempenho** — provar o ganho com número, não com impressão.
- **Qualidade** — remover duplicação estrutural e criar a primeira camada de
  serviço do projeto.

A aplicação continua Spring Boot 3 + Thymeleaf renderizada no servidor. Não há
reescrita para SPA, não entra build de front-end, e `./mvnw spring-boot:run`
continua sendo o único comando necessário para subir o projeto.

---

## 2. Estado atual

Levantado por leitura direta do código em 2026-09-08.

| Achado | Detalhe |
|---|---|
| Coluna `imagem` existe no banco | `script_criacao_banco.sql` já declara `imagem VARCHAR(255) DEFAULT NULL`; os 5 produtos de exemplo estão com `NULL` |
| Entidade não tem o campo | `Produto.java` não mapeia `imagem` — o banco está à frente do código |
| Nada serve `uploads/` | A pasta está fora do classpath e não há `WebMvcConfigurer` no projeto; os arquivos são inalcançáveis por HTTP |
| `uploads/` não é versionada | Está no `.gitignore`; os 3 arquivos presentes são resto local, um deles um PNG de 2,5 MB |
| `templates/produto/` é código morto | `ProdutoController` retorna somente `"produtos/..."` (plural). A pasta singular é cópia quase idêntica, divergindo por um link de FAQ e pelo typo `administrative` |
| Fragment de navbar subutilizado | Só os 3 templates de FAQ usam `th:replace`; os outros 15 têm a navbar copiada inline, e a cópia da vitrine já perdeu o link "Loja" |
| Sem camada de serviço | Os 4 controllers falam direto com os repositories; toda a regra vive no controller |
| Paleta já coerente | `estilo.css` define tokens de cor utilizáveis (`--bg-primary: #0d0d0f`, `--purple: #9871ff`, `--green: #04d361`) — falta o resto do sistema |

---

## 3. Escopo

### Entra

Camada de imagem completa; transição de página entre vitrine e detalhe; tilt 3D
nos cartões; adicionar ao carrinho sem recarregar; fundo com shader no hero;
sistema de tokens de design; limpeza dos templates duplicados.

### Fica de fora, com motivo

| Descartado | Motivo |
|---|---|
| **Three.js no hero** | ~150 KB gzipped para um efeito genérico, numa página cujo conteúdo visual deve ser as capas. Substituído por fragment shader próprio de ~2 KB |
| **Locomotive Scroll** | Sequestra o scroll nativo e prejudica acessibilidade; numa grade que se varre, o scroll nativo é superior |
| **Lottie** | Player acima de 250 KB para dois estados vazios; os placeholders SVG cobrem o caso |
| **ScrollTrigger** | Entrada em cascata sai com `IntersectionObserver` + CSS a custo zero de biblioteca |
| **React Three Fiber, Framer Motion, react-spring** | Exigem React; incompatíveis com a arquitetura Thymeleaf |
| **AVIF** | Encoder em Java exige dependência nativa adicional e o ganho sobre WebP não paga a complexidade |
| **Build de front-end (npm/Vite)** | Adiciona um segundo toolchain a um projeto Maven; bibliotecas vão vendoradas |

---

## 4. Fase 1 — Fundação

Nenhuma animação nesta fase. É o chão sobre o qual a Fase 2 pisa, e sozinha já
deve dominar o ganho de desempenho.

### 4.1 Modelo de dados

`Produto` ganha o campo `imagem` (`String`, nome do arquivo gravado, nullable).
A coluna já existe no SQL; `spring.jpa.hibernate.ddl-auto=update` cria em bases
que não a tenham. Não há migração a escrever.

O campo é opcional por decisão: produto sem capa é estado válido, tratado pelo
placeholder de 4.5.

### 4.2 Serviço de armazenamento

Nasce `src/main/java/loja/bytegames/service/ArmazenamentoImagemService.java` —
a primeira classe de serviço do projeto.

Responsabilidades:

- Receber o `MultipartFile` e validar **tipo real** (assinatura do arquivo, não
  apenas a extensão ou o `Content-Type` enviado pelo cliente) e tamanho.
- Gerar nome baseado em `UUID`, descartando o nome original enviado.
- Gravar o original e as derivadas em `uploads/`.
- Remover os arquivos de um produto quando o produto é excluído.

Formatos aceitos: JPEG, PNG e WebP. Teto por arquivo: 5 MB — abaixo do limite de
10 MB já configurado em `spring.servlet.multipart.max-file-size`, para que a
rejeição seja nossa e tenha mensagem de erro legível em vez de um 413 cru.

`ProdutoController` passa a delegar a esse serviço nos fluxos de criar, editar e
excluir. Nenhum `try/catch` de I/O fica no controller.

### 4.3 Servir os arquivos

Novo `src/main/java/loja/bytegames/config/ConfiguracaoRecursosWeb.java`,
implementando `WebMvcConfigurer`, mapeando `/imagens/**` para `file:uploads/`.

Como os nomes são UUID, o conteúdo em cada URL é imutável — o `Cache-Control`
sai longo (1 ano, `immutable`). O caminho da pasta vem de propriedade
configurável (`bytegames.uploads.diretorio`), com `uploads` como padrão.

### 4.4 Pipeline de imagem

No upload, o serviço gera derivadas em três larguras — **400, 800 e 1200 px** —
em WebP, preservando o original como fallback.

Os templates emitem `<picture>` com `<source type="image/webp" srcset="...">` e
um `<img>` JPEG/PNG de fallback. Atributo `loading="lazy"` em tudo que não é a
primeira dobra.

Bibliotecas: **Thumbnailator** para redimensionamento. Para a codificação WebP,
a escolha entre `scrimage-format-webp` e `webp-imageio` fica como primeira
tarefa da implementação — o critério é não exigir binário externo instalado no
Windows. Se nenhuma das duas satisfizer, o fallback aceito é entregar somente
JPEG redimensionado, que já resolve a maior parte do problema de peso.

### 4.5 Ausência elegante

Produto sem capa não renderiza imagem quebrada. Cai num placeholder SVG gerado
com a cor da categoria e as iniciais do título — vazio proposital, não defeito.
O SVG é inline no template, sem requisição de rede.

### 4.6 Limpeza estrutural

- Excluir `src/main/resources/templates/produto/` (4 arquivos, código morto
  confirmado).
- Converter os 11 templates restantes com navbar inline (15 hoje, menos os 4 da pasta morta) para `th:replace` do fragment
  `fragments/navbar.html`, que já existe.
- Corrigir o typo `administrative` → `administrativo`.
- Adicionar `.gitattributes` fixando `text=auto eol=lf` para os arquivos de
  código, evitando ruído de fim de linha nos diffs.

---

## 5. Fase 2 — Camada visual

### 5.1 Entrega do JavaScript

Bibliotecas vendoradas em `src/main/resources/static/js/vendor/`, servidas pelo
próprio Spring. Sem CDN — o projeto precisa rodar offline e o cache-control
precisa ser nosso. Sem `package.json`.

Código próprio dividido por responsabilidade em `static/js/`:
`transicoes.js`, `tilt.js`, `carrinho.js`, `fundo-shader.js`, `revelar.js`.

### 5.2 Sistema de tokens

`estilo.css` ganha, ao lado das cores que já tem:

- escala de espaçamento e escala tipográfica;
- raios de borda;
- **tokens de movimento** — `--dur-rapida`, `--dur-media`, `--ease-saida`,
  `--ease-entrada`.

Nenhuma duração ou curva fica escrita solta no JavaScript; tudo lê do CSS. Assim
movimento vira vocabulário compartilhado em vez de número mágico.

### 5.3 Transição vitrine → produto

**Barba.js + GSAP Flip.** A capa do jogo sai do cartão da grade e cresce até
virar o hero da página de detalhe — elemento compartilhado, sem corte de página.

Restrições de implementação, obrigatórias:

- Barba intercepta links. As rotas administrativas (`/produtos`, `/categorias`,
  `/faq`) e **todos os formulários** saem via `data-barba-prevent`.
- O carrinho vive em `HttpSession` e é indiferente à transição — a sessão
  sobrevive à navegação Barba normalmente.
- Sem JavaScript, a navegação continua sendo link comum. Nada depende de Barba
  para funcionar.

### 5.4 Tilt nos cartões

`rotateX`/`rotateY` seguindo o ponteiro. Somente `transform`, somente GPU,
escrito à mão em ~1 KB, sem biblioteca.

Ativo apenas sob `@media (pointer: fine)` — nada em toque — e desligado sob
`prefers-reduced-motion: reduce`.

### 5.5 Carrinho sem perder o lugar

Hoje o botão faz `POST /carrinho/adicionar` e redireciona para `/carrinho`,
arrancando o usuário da grade que estava lendo.

Passa a existir `POST /api/carrinho/itens`, com `@ResponseBody`, devolvendo
`{ totalItens, nomeProduto }`. O endpoint atual de formulário permanece
intacto como fallback sem JavaScript; ambos delegam ao mesmo método privado de
`LojaController`, para que exista uma só fonte de verdade da regra.

Com JavaScript: a capa voa até o ícone do carrinho e o contador incrementa com
um *pop* elástico. Sem JavaScript: o formulário posta e redireciona como sempre.

### 5.6 Fundo com shader

Um `<canvas>` atrás do hero da home. WebGL1, fragment shader único — gradiente
animado com ruído, puxando de `--bg-primary` para `--purple`.

Três travas de desempenho, todas obrigatórias:

- DPR limitado a 2, para não renderizar 4x pixels em tela hi-dpi;
- `IntersectionObserver` pausando o loop de animação assim que o hero sai da
  viewport;
- nenhuma inicialização se faltar contexto WebGL ou se `prefers-reduced-motion`
  estiver ativo — nesses casos, gradiente CSS estático visualmente próximo.

### 5.7 Acessibilidade

`prefers-reduced-motion: reduce` desliga tilt, voo do carrinho, transição de
página e shader. A navegação permanece inteira — apenas instantânea. Nenhuma
funcionalidade depende de animação para ser alcançável.

---

## 6. Desempenho

### 6.1 Linha de base

Antes de qualquer alteração, medir `/` e `/produto/{id}` com Lighthouse e
registrar o resultado neste diretório.

**A medição roda em perfil `prod`**, com `spring.thymeleaf.cache=true` e
`spring.jpa.show-sql=false`. Os valores atuais de desenvolvimento inflam o tempo
de servidor e tornariam a comparação inválida.

### 6.2 Orçamento de JavaScript

| Item | Custo estimado (gzip) |
|---|---|
| GSAP core + Flip | ~30 KB |
| Barba core | ~7 KB |
| Código próprio (tilt, carrinho, shader, reveal, transições) | ~5 KB |
| **Teto assumido** | **42 KB** |

Se o orçamento estourar, o plano B é escrever a transição de elemento
compartilhado à mão com Web Animations API, removendo GSAP inteiro por 0 KB ao
custo de aproximadamente 40 linhas a mais.

### 6.3 Métricas

- **LCP** — passa a ser a primeira capa visível. Alvo: melhorar ou manter em
  relação à linha de base.
- **CLS** — é a métrica que esta mudança ameaça. Hoje a vitrine é texto puro e
  não desloca nada; ao ganhar imagem, todo cartão vira candidato a pulo de
  layout. **Regra dura: todo `<img>` reserva espaço com `width`/`height` ou
  `aspect-ratio` antes do primeiro byte chegar.** Alvo: CLS < 0,05.
- **INP** — o tilt não pode encostar na thread principal; sendo só `transform`,
  não deve.

### 6.4 Quando medir

Duas medições contra a linha de base: uma ao fim da Fase 1, outra ao fim da
Fase 2. Cada fase prova o próprio ganho. Espera-se que a Fase 1 domine o
resultado — trocar um PNG de 2,5 MB por WebP responsivo é a maior alavanca do
projeto — e é desejável que isso apareça separado do brilho da Fase 2.

---

## 7. Testes

`BytegamesDatabaseTests` permanece como está. Somam-se:

**Unidade — `ArmazenamentoImagemService`**

- rejeita tipo inválido (arquivo que se declara JPEG mas não é);
- rejeita arquivo acima de 5 MB;
- grava as três derivadas com nomes previsíveis;
- remove os arquivos ao excluir o produto.

**MockMvc**

- a vitrine renderiza `<picture>` com `srcset` para produto com capa;
- produto sem capa cai no placeholder SVG;
- `POST /api/carrinho/itens` devolve JSON com a contagem correta;
- **o `POST` de formulário original continua funcionando sem JavaScript** — o
  caminho de fallback é testado, não presumido.

**Playwright** (já instalado no ambiente)

- carrega a vitrine e soma o JavaScript transferido contra o teto de 42 KB;
- mede CLS via `PerformanceObserver`;
- captura screenshot para comparação visual entre as fases.

---

## 8. Riscos

| Risco | Mitigação |
|---|---|
| Barba interceptando POST do admin | `data-barba-prevent` em rotas administrativas e em todos os formulários; teste de navegação cobrindo o CRUD |
| Encoder WebP exigindo binário nativo no Windows | Verificação é a primeira tarefa da Fase 1; fallback aceito é JPEG redimensionado |
| CLS piorando ao introduzir imagens | Reserva de espaço obrigatória; CLS medido nas duas fases |
| Shader custando bateria em notebook | DPR limitado, pausa fora da viewport, desligado sob reduced-motion |
| Medição de desempenho enganosa | Perfil `prod` obrigatório nas medições |

---

## 9. Fora de escopo

Autenticação e autorização do painel administrativo; checkout e pagamento;
persistência do carrinho além da sessão; internacionalização; reescrita do
histórico de commits existente.

---

## 10. Critérios de aceite

1. Produto pode receber capa pelo painel; a capa aparece na vitrine e no
   detalhe, servida em WebP responsivo.
2. Produto sem capa exibe o placeholder da categoria, nunca imagem quebrada.
3. `templates/produto/` não existe mais e os templates restantes usam o fragment
   de navbar único.
4. Adicionar ao carrinho não recarrega a página, e continua funcionando com
   JavaScript desativado.
5. JavaScript total transferido na vitrine fica em 42 KB gzipped ou menos.
6. CLS abaixo de 0,05 na vitrine e no detalhe.
7. `prefers-reduced-motion: reduce` desliga toda animação sem quebrar navegação.
8. `./mvnw test` passa; `./mvnw spring-boot:run` continua sendo o único comando
   para subir o projeto.
9. Relatório de desempenho com linha de base, pós-Fase 1 e pós-Fase 2
   registrado em `docs/specs/`.
