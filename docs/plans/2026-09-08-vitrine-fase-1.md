# Vitrine ByteGames — Fase 1 (Fundação) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Completar a capa de produto da loja — do upload no painel até a
renderização em WebP responsivo na vitrine — e remover a duplicação estrutural
dos templates, com o ganho de desempenho medido contra uma linha de base.

**Architecture:** A aplicação continua Spring Boot 3 + Thymeleaf renderizada no
servidor. Nasce a primeira camada de serviço do projeto
(`ArmazenamentoImagemService`), responsável por validar, nomear e derivar as
imagens; os controllers passam a delegar a ela em vez de tratar I/O. Um
`WebMvcConfigurer` novo expõe a pasta `uploads/` em `/imagens/**`. Nenhuma
animação entra nesta fase.

**Tech Stack:** Java 17 (Temurin 17.0.20 confirmado na máquina), Spring Boot
3.2.5, Thymeleaf, JPA/Hibernate, MySQL 8 (produção) e H2 (testes),
Maven Wrapper, Thumbnailator, encoder WebP a definir na Task 3.

**Spec:** `docs/specs/2026-09-08-vitrine-design.md`

---

## Global Constraints

Valores copiados literalmente da spec. Valem para todas as tasks.

- **Derivadas de imagem:** larguras de **400, 800 e 1200 px**, em WebP, com o
  original preservado como fallback.
- **Teto por arquivo enviado:** **5 MB** — deliberadamente abaixo do
  `spring.servlet.multipart.max-file-size=10MB` já configurado, para que a
  rejeição seja da aplicação e tenha mensagem legível em vez de um 413 cru.
- **Formatos aceitos no upload:** JPEG, PNG e WebP, validados por **assinatura
  do arquivo**, nunca pela extensão ou pelo `Content-Type` do cliente.
- **Nomes de arquivo:** baseados em `UUID`; o nome original enviado é descartado.
- **Cache dos arquivos servidos:** 1 ano, `immutable` (os nomes são UUID, o
  conteúdo em cada URL é imutável).
- **Reserva de espaço:** todo `<img>` sai com `width`/`height` ou
  `aspect-ratio`. Alvo de CLS: **< 0,05**.
- **Produto sem capa é estado válido** — cai no placeholder SVG, nunca em imagem
  quebrada.
- **Medição roda em perfil `prod`**, com `spring.thymeleaf.cache=true` e
  `spring.jpa.show-sql=false`.
- **Git:** trabalho em branch próprio, nunca na default. Mensagens de commit
  descritivas, sem rodapé e sem menção a ferramental de agente.
- **Comando único:** ao fim da fase, `./mvnw spring-boot:run` continua sendo o
  único comando necessário para subir o projeto.

---

## Estrutura de arquivos

**Criados**

| Arquivo | Responsabilidade |
|---|---|
| `src/main/java/loja/bytegames/service/ArmazenamentoImagemService.java` | Validar, nomear, derivar e remover imagens de produto |
| `src/main/java/loja/bytegames/service/CorCategoria.java` | Derivar uma cor estável a partir do nome da categoria (usada no placeholder) |
| `src/main/java/loja/bytegames/config/ConfiguracaoRecursosWeb.java` | Expor `uploads/` em `/imagens/**` com cache longo |
| `src/main/resources/templates/fragments/capa.html` | Fragment único que renderiza `<picture>` ou o placeholder SVG |
| `src/main/resources/application-prod.properties` | Perfil de medição — cache ligado, SQL calado |
| `src/test/resources/application-test.properties` | Perfil de teste em H2, para os testes novos rodarem sem MySQL |
| `src/test/java/loja/bytegames/service/ArmazenamentoImagemServiceTest.java` | Testes de unidade da validação e das derivadas |
| `src/test/java/loja/bytegames/VitrineRenderizacaoTest.java` | Testes MockMvc de renderização |
| `.gitattributes` | Fixar fim de linha e evitar ruído de diff |

**Modificados**

| Arquivo | Mudança |
|---|---|
| `pom.xml` | Dependências de imagem |
| `src/main/java/loja/bytegames/Produto.java` | Campo `imagem` |
| `src/main/java/loja/bytegames/repository/ProdutoRepository.java` | `inserirProduto` e `atualizarProduto` passam a carregar `imagem` |
| `src/main/java/loja/bytegames/ProdutoController.java` | Recebe `MultipartFile` e delega ao serviço |
| `src/main/resources/templates/produtos/form-criar.html` | `enctype` + campo de arquivo |
| `src/main/resources/templates/produtos/form-editar.html` | `enctype` + campo de arquivo + preview da capa atual |
| `src/main/resources/templates/loja/vitrine.html` | `<picture>` no cartão |
| `src/main/resources/templates/loja/detalhar.html` | `<picture>` no hero |
| `src/main/resources/static/css/estilo.css` | Estilos de capa; remoção do `@import` remoto |
| 11 templates com navbar inline | `th:replace` do fragment |

**Removidos**

- `src/main/resources/templates/produto/` (4 arquivos, código morto confirmado —
  `ProdutoController` só retorna `"produtos/..."`)

---

## Descobertas do levantamento que este plano incorpora

Três coisas encontradas na leitura do código que não estavam na spec e que o
plano precisa resolver:

1. **O formulário do admin não tem `enctype="multipart/form-data"` nem campo de
   arquivo.** A configuração de multipart em `application.properties` nunca foi
   ligada a nenhum formulário. Tasks 8 e 9 resolvem.
2. **`estilo.css` abre com `@import url('https://fonts.googleapis.com/...')`.**
   É requisição externa bloqueante de CSS — o pior caso para LCP — e contradiz o
   requisito de rodar offline da seção 5.1 da spec. Task 12 resolve.
3. **Os testes existentes exigem MySQL de pé.** `BytegamesDatabaseTests` é
   `@SpringBootTest` contra MySQL real. Os testes novos usam perfil H2 para não
   herdar essa dependência; o teste existente fica intocado. Task 2 resolve.

---

## Fase 0 — Preparação e linha de base

### Task 1: Branch de trabalho, perfil `prod` e medição da linha de base

**Files:**
- Create: `src/main/resources/application-prod.properties`
- Create: `docs/specs/medicoes-desempenho.md`

**Interfaces:**
- Consumes: nada — é a primeira task.
- Produces: o arquivo `docs/specs/medicoes-desempenho.md` com a seção
  "Linha de base", que as Tasks 13 e a Fase 2 vão acrescentar linhas.

- [x] **Step 1: Criar o branch de trabalho**

```bash
cd /c/dev/pessoal/byte-games
git checkout -b vitrine-capa-e-movimento
```

- [x] **Step 2: Criar o perfil de medição**

Criar `src/main/resources/application-prod.properties`:

```properties
# Perfil usado apenas para medir desempenho de forma honesta.
# O application.properties de desenvolvimento desliga o cache do Thymeleaf e
# imprime todo SQL, o que infla o tempo de servidor e invalidaria a comparacao.
spring.thymeleaf.cache=true
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false
```

- [x] **Step 3: Subir a aplicação no perfil de medição**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

Esperado: aplicação sobe em `http://localhost:8080` sem imprimir SQL no console.
Requer MySQL de pé com o `.env` preenchido. Se o MySQL não estiver disponível,
descomentar o bloco H2 em `application.properties` antes desta medição e usar o
mesmo banco nas duas medições (linha de base e pós-fase), senão a comparação não
vale.

- [x] **Step 4: Medir e registrar**

Com a aplicação de pé, rodar Lighthouse em `/` e em `/produto/1`, modo mobile,
e registrar em `docs/specs/medicoes-desempenho.md`:

```markdown
# Medições de desempenho — vitrine ByteGames

Todas as medições em perfil `prod`, Lighthouse mobile, mesma máquina e mesmo
banco. Sem isso os números não são comparáveis.

## Linha de base — 2026-09-08 (antes de qualquer alteração)

| Página | Performance | LCP | CLS | Peso total | JS transferido |
|---|---|---|---|---|---|
| `/` | | | | | |
| `/produto/1` | | | | | |

Observações:
- A vitrine é texto puro nesta medição; CLS deve estar próximo de zero. Esse é
  justamente o número que a introdução de imagens ameaça.
- `estilo.css` faz `@import` de fonte remota; isso aparece como requisição
  bloqueante em cadeia e é removido na Task 12.
```

Preencher a tabela com os valores medidos. **Não seguir para a Task 2 com a
tabela vazia** — sem esta linha de base, nenhuma afirmação de ganho no resto do
plano é verificável.

- [x] **Step 5: Commit**

```bash
git add src/main/resources/application-prod.properties docs/specs/medicoes-desempenho.md
git commit -m "Adiciona perfil de medicao e registra linha de base de desempenho"
```

---

### Task 2: Perfil de teste em H2

Destrava os testes novos para rodarem sem MySQL instalado. O
`BytegamesDatabaseTests` existente fica intocado e continua exigindo MySQL —
mudar isso não é objetivo desta fase.

**Files:**
- Create: `src/test/resources/application-test.properties`
- Test: `src/test/java/loja/bytegames/PerfilDeTesteTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: o perfil Spring `test`, usado por `@ActiveProfiles("test")` em todos
  os testes criados nas Tasks 5, 6, 8 e 10.

- [x] **Step 1: Escrever o teste que falha**

Criar `src/test/java/loja/bytegames/PerfilDeTesteTest.java`:

```java
package loja.bytegames;

import loja.bytegames.repository.CategoriaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class PerfilDeTesteTest {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Test
    void contextoSobeSemMySql() {
        // Se o perfil de teste estiver correto, o contexto sobe contra H2 e o
        // repositorio responde sem nenhum servico externo ligado.
        assertNotNull(categoriaRepository.buscarTodasCategorias());
    }
}
```

- [x] **Step 2: Rodar para confirmar que falha**

```bash
./mvnw test -Dtest=PerfilDeTesteTest
```

Esperado: FALHA. Sem o arquivo de perfil, o Spring tenta o MySQL do
`application.properties` e quebra na conexão (ou nas variáveis `DB_USER` /
`DB_PASSWORD` ausentes).

- [x] **Step 3: Criar o perfil de teste**

Criar `src/test/resources/application-test.properties`:

```properties
# Banco em memoria para os testes automatizados. Modo MySQL para que o dialeto e
# o comportamento de constraints fiquem proximos do banco real.
spring.datasource.url=jdbc:h2:mem:bytegames_test;DB_CLOSE_DELAY=-1;MODE=MySQL
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=false
spring.thymeleaf.cache=false

# Diretorio de upload isolado, para que teste nunca escreva na pasta real.
bytegames.uploads.diretorio=target/uploads-teste
```

- [x] **Step 4: Rodar para confirmar que passa**

```bash
./mvnw test -Dtest=PerfilDeTesteTest
```

Esperado: PASSA.

- [x] **Step 5: Commit**

```bash
git add src/test/resources/application-test.properties src/test/java/loja/bytegames/PerfilDeTesteTest.java
git commit -m "Adiciona perfil de teste em H2 para testes sem banco externo"
```

---

## Fase 1 — Fundação

### Task 3: Resolver o encoder WebP

A spec deixou esta escolha explicitamente para a implementação, com critério
definido: **não pode exigir binário externo instalado no Windows**. Esta task é
a que decide, e o teste é o árbitro.

**Files:**
- Modify: `pom.xml`
- Test: `src/test/java/loja/bytegames/service/CodificacaoWebpTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: a garantia de que `ImageIO.write(imagem, "webp", arquivo)` funciona
  nesta máquina. A Task 6 depende disso.

- [ ] **Step 1: Escrever o teste que falha**

Criar `src/test/java/loja/bytegames/service/CodificacaoWebpTest.java`:

```java
package loja.bytegames.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodificacaoWebpTest {

    @Test
    void imageIoConsegueEscreverWebpNestaMaquina() throws Exception {
        BufferedImage imagem = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();

        boolean escreveu = ImageIO.write(imagem, "webp", saida);

        assertTrue(escreveu, "Nenhum writer de WebP registrado no ImageIO");
        // Assinatura de arquivo WebP: bytes 0-3 sao "RIFF", bytes 8-11 sao "WEBP".
        byte[] bytes = saida.toByteArray();
        assertTrue(bytes.length > 12, "Saida WebP vazia ou truncada");
        assertTrue(bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F',
                "Saida nao comeca com RIFF");
        assertTrue(bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P',
                "Saida nao identifica WEBP");
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

```bash
./mvnw test -Dtest=CodificacaoWebpTest
```

Esperado: FALHA com "Nenhum writer de WebP registrado no ImageIO" — o JDK 17 não
traz encoder WebP.

- [ ] **Step 3: Adicionar as dependências**

Em `pom.xml`, dentro de `<dependencies>`, acrescentar:

```xml
        <dependency>
            <groupId>net.coobird</groupId>
            <artifactId>thumbnailator</artifactId>
            <version>0.4.20</version>
        </dependency>
        <dependency>
            <groupId>org.sejda.imageio</groupId>
            <artifactId>webp-imageio</artifactId>
            <version>0.1.6</version>
        </dependency>
```

`webp-imageio` da Sejda registra um writer no ImageIO e embarca a biblioteca
nativa para Windows, Linux e macOS — nada a instalar na máquina. Thumbnailator
faz o redimensionamento e escreve através do ImageIO, então as duas se
encaixam sem código de cola.

- [ ] **Step 4: Rodar para confirmar que passa**

```bash
./mvnw test -Dtest=CodificacaoWebpTest
```

Esperado: PASSA.

**Se falhar** — por exemplo com `UnsatisfiedLinkError`, que indica ausência de
binário nativo para esta plataforma — trocar por
`com.sksamuel.scrimage:scrimage-webp` e rodar de novo. Se as duas falharem, a
spec já autorizou o fallback: seguir sem WebP, entregando apenas JPEG
redimensionado nas três larguras, e registrar a decisão no topo de
`ArmazenamentoImagemService`. As Tasks 6 e 10 têm o caminho alternativo marcado.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/test/java/loja/bytegames/service/CodificacaoWebpTest.java
git commit -m "Adiciona Thumbnailator e encoder WebP com teste de plataforma"
```

---

### Task 4: Campo `imagem` na entidade e no repositório

**Files:**
- Modify: `src/main/java/loja/bytegames/Produto.java`
- Modify: `src/main/java/loja/bytegames/repository/ProdutoRepository.java`
- Test: `src/test/java/loja/bytegames/ProdutoImagemPersistenciaTest.java`

**Interfaces:**
- Consumes: perfil `test` da Task 2.
- Produces:
  - `Produto.getImagem()` / `Produto.setImagem(String)` — o valor é o nome-base
    do arquivo, sem extensão e sem caminho. Exemplo:
    `"a3f1c2d4-...-9b7e"`.
  - `ProdutoRepository.inserirProduto(String nome, String descricao,
    BigDecimal preco, Integer estoque, Long categoriaId, String imagem)`
  - `ProdutoRepository.atualizarProduto(Long id, String nome, String descricao,
    BigDecimal preco, Integer estoque, Long categoriaId, String imagem)` — com
    a regra de que **`imagem == null` preserva a imagem existente**.

- [ ] **Step 1: Escrever o teste que falha**

Criar `src/test/java/loja/bytegames/ProdutoImagemPersistenciaTest.java`:

```java
package loja.bytegames;

import loja.bytegames.repository.CategoriaRepository;
import loja.bytegames.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
class ProdutoImagemPersistenciaTest {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private Long criarCategoria(String nome) {
        categoriaRepository.inserirCategoria(nome, "Categoria de teste");
        return categoriaRepository.buscarCategoriaPorNome(nome)
                .orElseThrow(() -> new AssertionError("Categoria nao inserida"))
                .getId();
    }

    private Produto buscarPorNome(String nome) {
        return produtoRepository.findByNomeContainingIgnoreCase(nome)
                .stream().findFirst()
                .orElseThrow(() -> new AssertionError("Produto nao encontrado: " + nome));
    }

    @Test
    void gravaEleImagemDoProduto() {
        Long categoriaId = criarCategoria("Cat Imagem Gravar");

        produtoRepository.inserirProduto("Jogo Com Capa", "Descricao",
                new BigDecimal("10.00"), 1, categoriaId, "capa-abc-123");

        assertEquals("capa-abc-123", buscarPorNome("Jogo Com Capa").getImagem());
    }

    @Test
    void produtoSemCapaGravaImagemNula() {
        Long categoriaId = criarCategoria("Cat Imagem Nula");

        produtoRepository.inserirProduto("Jogo Sem Capa", "Descricao",
                new BigDecimal("10.00"), 1, categoriaId, null);

        assertNull(buscarPorNome("Jogo Sem Capa").getImagem());
    }

    @Test
    void atualizarComImagemNulaPreservaCapaExistente() {
        Long categoriaId = criarCategoria("Cat Imagem Preservar");

        produtoRepository.inserirProduto("Jogo Preserva", "Descricao",
                new BigDecimal("10.00"), 1, categoriaId, "capa-original");
        Long produtoId = buscarPorNome("Jogo Preserva").getId();

        // Editar o produto sem enviar arquivo novo nao pode apagar a capa.
        produtoRepository.atualizarProduto(produtoId, "Jogo Preserva", "Nova descricao",
                new BigDecimal("20.00"), 2, categoriaId, null);

        Produto atualizado = produtoRepository.buscarProdutoPorId(produtoId)
                .orElseThrow(() -> new AssertionError("Produto sumiu"));
        assertEquals("capa-original", atualizado.getImagem());
        assertEquals("Nova descricao", atualizado.getDescricao());
    }

    @Test
    void atualizarComImagemNovaSubstituiCapa() {
        Long categoriaId = criarCategoria("Cat Imagem Substituir");

        produtoRepository.inserirProduto("Jogo Substitui", "Descricao",
                new BigDecimal("10.00"), 1, categoriaId, "capa-velha");
        Long produtoId = buscarPorNome("Jogo Substitui").getId();

        produtoRepository.atualizarProduto(produtoId, "Jogo Substitui", "Descricao",
                new BigDecimal("10.00"), 1, categoriaId, "capa-nova");

        assertEquals("capa-nova", produtoRepository.buscarProdutoPorId(produtoId)
                .orElseThrow(() -> new AssertionError("Produto sumiu")).getImagem());
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

```bash
./mvnw test -Dtest=ProdutoImagemPersistenciaTest
```

Esperado: FALHA de compilação — `inserirProduto` não aceita 6 argumentos e
`getImagem()` não existe.

- [ ] **Step 3: Adicionar o campo na entidade**

Em `Produto.java`, depois do campo `estoque` e antes do bloco `@ManyToOne`:

```java
    // Nome-base do arquivo de capa, sem extensao e sem caminho. Nulo e estado
    // valido: produto sem capa cai no placeholder da categoria.
    @Column(length = 255)
    private String imagem;
```

E, junto dos demais getters e setters:

```java
    public String getImagem() {
        return imagem;
    }

    public void setImagem(String imagem) {
        this.imagem = imagem;
    }
```

- [ ] **Step 4: Atualizar os métodos do repositório**

Em `ProdutoRepository.java`, substituir `inserirProduto` e `atualizarProduto`
pelas versões abaixo. Note a diferença de regra entre as duas: na inserção,
`imagem` nula significa "sem capa"; na atualização, significa "não mexer na capa
que já está lá".

```java
    // Metodo personalizado para criar um novo produto e associar sua categoria
    default void inserirProduto(String nome, String descricao, BigDecimal preco,
                                Integer estoque, Long categoriaId, String imagem) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setDescricao(descricao);
        produto.setPreco(preco);
        produto.setEstoque(estoque);
        produto.setImagem(imagem);

        // Cria a categoria apenas com o ID informado
        Categoria categoria = new Categoria();
        categoria.setId(categoriaId);
        produto.setCategoria(categoria);

        // Salva o produto no banco
        save(produto);
    }

    // Metodo personalizado para atualizar um produto existente.
    // imagem nula significa "manter a capa atual" -- editar um produto sem
    // enviar arquivo novo nao pode apagar a capa que ja existe.
    default void atualizarProduto(Long id, String nome, String descricao, BigDecimal preco,
                                  Integer estoque, Long categoriaId, String imagem) {
        findById(id).ifPresent(produto -> {
            produto.setNome(nome);
            produto.setDescricao(descricao);
            produto.setPreco(preco);
            produto.setEstoque(estoque);
            if (imagem != null) {
                produto.setImagem(imagem);
            }

            Categoria categoria = new Categoria();
            categoria.setId(categoriaId);
            produto.setCategoria(categoria);

            // Grava as atualizacoes de volta no banco
            save(produto);
        });
    }
```

- [ ] **Step 5: Ajustar as chamadas existentes no controller**

`ProdutoController` ainda chama as assinaturas antigas e não compila. Ajuste
mínimo por enquanto — a Task 8 é que traz o upload de verdade. Em
`salvarNovo`, acrescentar `null` como último argumento de `inserirProduto`; em
`atualizar`, acrescentar `null` como último argumento de `atualizarProduto`.

- [ ] **Step 6: Rodar para confirmar que passa**

```bash
./mvnw test -Dtest=ProdutoImagemPersistenciaTest
```

Esperado: PASSA, 4 testes.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/loja/bytegames/Produto.java src/main/java/loja/bytegames/repository/ProdutoRepository.java src/main/java/loja/bytegames/ProdutoController.java src/test/java/loja/bytegames/ProdutoImagemPersistenciaTest.java
git commit -m "Mapeia campo imagem do produto e preserva capa na edicao sem upload"
```

---

### Task 5: Validação de upload no serviço de armazenamento

**Files:**
- Create: `src/main/java/loja/bytegames/service/ArmazenamentoImagemService.java`
- Test: `src/test/java/loja/bytegames/service/ArmazenamentoImagemServiceTest.java`

**Interfaces:**
- Consumes: perfil `test` (Task 2), propriedade `bytegames.uploads.diretorio`.
- Produces:
  - `ArmazenamentoImagemService.guardar(MultipartFile arquivo)` → `String`
    (nome-base UUID) ou `null` quando o arquivo é vazio/ausente.
  - `ArmazenamentoImagemService.remover(String nomeBase)` → `void`.
  - `ImagemInvalidaException extends RuntimeException` — classe aninhada
    estática, com `getMessage()` legível para exibir ao usuário.
  - Constante pública `LARGURAS = {400, 800, 1200}`.

- [ ] **Step 1: Escrever o teste que falha**

Criar `src/test/java/loja/bytegames/service/ArmazenamentoImagemServiceTest.java`:

```java
package loja.bytegames.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmazenamentoImagemServiceTest {

    @TempDir
    Path pastaTemporaria;

    private ArmazenamentoImagemService servico() {
        return new ArmazenamentoImagemService(pastaTemporaria.toString());
    }

    private byte[] jpegValido(int largura, int altura) throws Exception {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, "jpeg", saida);
        return saida.toByteArray();
    }

    @Test
    void arquivoAusenteDevolveNuloSemErro() {
        // Editar um produto sem escolher arquivo e o caso normal, nao um erro.
        MockMultipartFile vazio = new MockMultipartFile("imagem", "", "image/jpeg", new byte[0]);
        assertNull(servico().guardar(vazio));
    }

    @Test
    void rejeitaArquivoQueMenteOTipo() {
        // Content-Type diz JPEG, conteudo e texto. A validacao le a assinatura,
        // entao a mentira do cliente nao passa.
        MockMultipartFile mentiroso = new MockMultipartFile(
                "imagem", "virus.jpg", "image/jpeg", "isto nao e uma imagem".getBytes());

        ArmazenamentoImagemService.ImagemInvalidaException erro = assertThrows(
                ArmazenamentoImagemService.ImagemInvalidaException.class,
                () -> servico().guardar(mentiroso));

        assertTrue(erro.getMessage().toLowerCase().contains("formato"));
    }

    @Test
    void rejeitaArquivoAcimaDoTeto() throws Exception {
        // 5 MB e o teto definido na spec.
        byte[] gigante = new byte[6 * 1024 * 1024];
        // Assinatura JPEG valida no inicio, para provar que a rejeicao e por
        // tamanho e nao por formato.
        byte[] cabecalho = jpegValido(8, 8);
        System.arraycopy(cabecalho, 0, gigante, 0, cabecalho.length);

        MockMultipartFile pesado = new MockMultipartFile(
                "imagem", "grande.jpg", "image/jpeg", gigante);

        ArmazenamentoImagemService.ImagemInvalidaException erro = assertThrows(
                ArmazenamentoImagemService.ImagemInvalidaException.class,
                () -> servico().guardar(pesado));

        assertTrue(erro.getMessage().contains("5"));
    }

    @Test
    void aceitaJpegValidoEDevolveNomeBase() throws Exception {
        MockMultipartFile bom = new MockMultipartFile(
                "imagem", "capa.jpg", "image/jpeg", jpegValido(1600, 900));

        String nomeBase = servico().guardar(bom);

        assertNotNull(nomeBase);
        // Nome original descartado: o nome-base nao carrega nada do que o
        // cliente enviou.
        assertTrue(!nomeBase.contains("capa"));
        assertTrue(pastaTemporaria.resolve(nomeBase + ".jpg").toFile().exists(),
                "Original deveria ter sido gravado");
    }

    @Test
    void removerApagaTodosOsArquivosDoProduto() throws Exception {
        ArmazenamentoImagemService servico = servico();
        MockMultipartFile bom = new MockMultipartFile(
                "imagem", "capa.jpg", "image/jpeg", jpegValido(1600, 900));
        String nomeBase = servico.guardar(bom);

        servico.remover(nomeBase);

        assertTrue(!pastaTemporaria.resolve(nomeBase + ".jpg").toFile().exists(),
                "Original deveria ter sido apagado");
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

```bash
./mvnw test -Dtest=ArmazenamentoImagemServiceTest
```

Esperado: FALHA de compilação — a classe não existe.

- [ ] **Step 3: Escrever o serviço**

Criar `src/main/java/loja/bytegames/service/ArmazenamentoImagemService.java`:

```java
package loja.bytegames.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Cuida do ciclo de vida das imagens de capa: valida, nomeia, grava, deriva e
 * remove. Toda regra de arquivo vive aqui -- os controllers nao tocam em I/O.
 */
@Service
public class ArmazenamentoImagemService {

    /** Larguras das derivadas responsivas, em pixels. */
    public static final int[] LARGURAS = {400, 800, 1200};

    /** Teto por arquivo enviado, abaixo do limite de multipart do Spring. */
    private static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;

    private final Path diretorio;

    public ArmazenamentoImagemService(
            @Value("${bytegames.uploads.diretorio:uploads}") String diretorio) {
        this.diretorio = Paths.get(diretorio).toAbsolutePath().normalize();
    }

    /**
     * Valida e grava o arquivo enviado.
     *
     * @return o nome-base gerado (UUID, sem extensao), ou null quando nenhum
     *         arquivo foi enviado -- o que e caso normal, nao erro.
     */
    public String guardar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            return null;
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new ImagemInvalidaException(
                    "A imagem passa de 5 MB. Envie um arquivo menor.");
        }

        Formato formato = detectarFormato(arquivo);
        if (formato == null) {
            throw new ImagemInvalidaException(
                    "Formato nao reconhecido. Aceitamos JPEG, PNG ou WebP.");
        }

        String nomeBase = UUID.randomUUID().toString();
        try {
            Files.createDirectories(diretorio);
            try (InputStream entrada = arquivo.getInputStream()) {
                Files.copy(entrada, diretorio.resolve(nomeBase + formato.extensao),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ImagemInvalidaException("Nao foi possivel gravar a imagem.");
        }
        return nomeBase;
    }

    /** Apaga o original e todas as derivadas de um nome-base. */
    public void remover(String nomeBase) {
        if (nomeBase == null || nomeBase.isBlank()) {
            return;
        }
        try (Stream<Path> arquivos = Files.list(diretorio)) {
            arquivos.filter(p -> p.getFileName().toString().startsWith(nomeBase))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignorado) {
                            // Arquivo orfao na pasta nao justifica derrubar a
                            // exclusao do produto no banco.
                        }
                    });
        } catch (IOException ignorado) {
            // Pasta inexistente: nada a remover.
        }
    }

    /**
     * Le a assinatura do arquivo. Nao confiamos na extensao nem no Content-Type,
     * porque os dois vem do cliente e podem mentir.
     */
    private Formato detectarFormato(MultipartFile arquivo) {
        byte[] cabecalho = new byte[12];
        try (InputStream entrada = arquivo.getInputStream()) {
            if (entrada.read(cabecalho) < 12) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }

        if ((cabecalho[0] & 0xFF) == 0xFF && (cabecalho[1] & 0xFF) == 0xD8
                && (cabecalho[2] & 0xFF) == 0xFF) {
            return Formato.JPEG;
        }
        if ((cabecalho[0] & 0xFF) == 0x89 && cabecalho[1] == 'P'
                && cabecalho[2] == 'N' && cabecalho[3] == 'G') {
            return Formato.PNG;
        }
        if (cabecalho[0] == 'R' && cabecalho[1] == 'I' && cabecalho[2] == 'F'
                && cabecalho[3] == 'F' && cabecalho[8] == 'W' && cabecalho[9] == 'E'
                && cabecalho[10] == 'B' && cabecalho[11] == 'P') {
            return Formato.WEBP;
        }
        return null;
    }

    private enum Formato {
        JPEG(".jpg"), PNG(".png"), WEBP(".webp");

        final String extensao;

        Formato(String extensao) {
            this.extensao = extensao;
        }
    }

    /** Erro de upload com mensagem pronta para exibir no formulario. */
    public static class ImagemInvalidaException extends RuntimeException {
        public ImagemInvalidaException(String mensagem) {
            super(mensagem);
        }
    }
}
```

- [ ] **Step 4: Rodar para confirmar que passa**

```bash
./mvnw test -Dtest=ArmazenamentoImagemServiceTest
```

Esperado: PASSA, 5 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/loja/bytegames/service/ArmazenamentoImagemService.java src/test/java/loja/bytegames/service/ArmazenamentoImagemServiceTest.java
git commit -m "Adiciona servico de armazenamento de imagem com validacao por assinatura"
```

---

### Task 6: Derivadas responsivas em WebP

**Files:**
- Modify: `src/main/java/loja/bytegames/service/ArmazenamentoImagemService.java`
- Modify: `src/test/java/loja/bytegames/service/ArmazenamentoImagemServiceTest.java`

**Interfaces:**
- Consumes: `guardar` da Task 5, encoder WebP da Task 3.
- Produces: para um nome-base `X`, os arquivos `X.jpg` (original),
  `X-400.webp`, `X-800.webp` e `X-1200.webp`. A Task 9 monta o `srcset` a partir
  exatamente desses nomes.

- [ ] **Step 1: Escrever o teste que falha**

Acrescentar a `ArmazenamentoImagemServiceTest`:

```java
    @Test
    void geraAsTresDerivadasEmWebp() throws Exception {
        MockMultipartFile bom = new MockMultipartFile(
                "imagem", "capa.jpg", "image/jpeg", jpegValido(1600, 900));

        String nomeBase = servico().guardar(bom);

        for (int largura : ArmazenamentoImagemService.LARGURAS) {
            assertTrue(pastaTemporaria.resolve(nomeBase + "-" + largura + ".webp").toFile().exists(),
                    "Derivada de " + largura + "px deveria existir");
        }
    }

    @Test
    void naoAmpliaImagemMenorQueALarguraAlvo() throws Exception {
        // Uma capa de 500px nao deve virar um arquivo de 1200px borrado.
        MockMultipartFile pequena = new MockMultipartFile(
                "imagem", "pequena.jpg", "image/jpeg", jpegValido(500, 300));

        String nomeBase = servico().guardar(pequena);

        assertTrue(pastaTemporaria.resolve(nomeBase + "-400.webp").toFile().exists(),
                "Derivada de 400px cabe e deveria existir");
        assertTrue(!pastaTemporaria.resolve(nomeBase + "-1200.webp").toFile().exists(),
                "Derivada de 1200px ampliaria a imagem e nao deveria existir");
    }
```

- [ ] **Step 2: Rodar para confirmar que falha**

```bash
./mvnw test -Dtest=ArmazenamentoImagemServiceTest
```

Esperado: FALHA nos dois testes novos — nenhuma derivada é gerada ainda.

- [ ] **Step 3: Gerar as derivadas**

Em `ArmazenamentoImagemService`, acrescentar os imports:

```java
import net.coobird.thumbnailator.Thumbnails;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
```

Dentro de `guardar`, logo antes de `return nomeBase;`, chamar a geração:

```java
        gerarDerivadas(nomeBase, formato);
        return nomeBase;
```

E acrescentar o método:

```java
    /**
     * Gera as versoes responsivas em WebP. Larguras maiores que a imagem
     * original sao puladas -- ampliar so produz arquivo maior e mais borrado.
     */
    private void gerarDerivadas(String nomeBase, Formato formato) {
        Path original = diretorio.resolve(nomeBase + formato.extensao);
        try {
            BufferedImage imagem = ImageIO.read(original.toFile());
            if (imagem == null) {
                throw new ImagemInvalidaException("Nao foi possivel ler a imagem enviada.");
            }
            for (int largura : LARGURAS) {
                if (imagem.getWidth() < largura) {
                    continue;
                }
                Path destino = diretorio.resolve(nomeBase + "-" + largura + ".webp");
                Thumbnails.of(imagem)
                        .width(largura)
                        .outputFormat("webp")
                        .toFile(destino.toFile());
            }
        } catch (IOException e) {
            throw new ImagemInvalidaException("Nao foi possivel processar a imagem.");
        }
    }
```

**Caminho alternativo** — se a Task 3 concluiu que nenhum encoder WebP funciona
nesta plataforma: trocar `.outputFormat("webp")` por `.outputFormat("jpg")` e
`nomeBase + "-" + largura + ".webp"` por `".jpg"`, e ajustar os dois testes
acima para esperar `.jpg`. O resto do plano segue igual; muda só a extensão que
a Task 9 monta no `srcset`, e o `<source type="image/webp">` sai do fragment.

- [ ] **Step 4: Rodar para confirmar que passa**

```bash
./mvnw test -Dtest=ArmazenamentoImagemServiceTest
```

Esperado: PASSA, 7 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/loja/bytegames/service/ArmazenamentoImagemService.java src/test/java/loja/bytegames/service/ArmazenamentoImagemServiceTest.java
git commit -m "Gera derivadas responsivas em WebP sem ampliar imagem pequena"
```

---

### Task 7: Servir a pasta de uploads

**Files:**
- Create: `src/main/java/loja/bytegames/config/ConfiguracaoRecursosWeb.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/loja/bytegames/config/ServirImagensTest.java`

**Interfaces:**
- Consumes: propriedade `bytegames.uploads.diretorio`.
- Produces: a rota `/imagens/{arquivo}`, que a Task 9 usa para montar `src` e
  `srcset`.

- [ ] **Step 1: Escrever o teste que falha**

Criar `src/test/java/loja/bytegames/config/ServirImagensTest.java`:

```java
package loja.bytegames.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServirImagensTest {

    @Autowired
    private MockMvc mockMvc;

    // Mesmo valor de bytegames.uploads.diretorio em application-test.properties
    private final Path pasta = Paths.get("target/uploads-teste");

    @BeforeEach
    void prepararArquivo() throws Exception {
        Files.createDirectories(pasta);
        Files.write(pasta.resolve("exemplo-400.webp"), new byte[]{'R', 'I', 'F', 'F'});
    }

    @Test
    void serveArquivoDaPastaDeUploads() throws Exception {
        mockMvc.perform(get("/imagens/exemplo-400.webp"))
                .andExpect(status().isOk());
    }

    @Test
    void marcaCacheLongoEImutavel() throws Exception {
        // Os nomes sao UUID, entao o conteudo em cada URL nunca muda.
        mockMvc.perform(get("/imagens/exemplo-400.webp"))
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("immutable")));
    }

    @Test
    void arquivoInexistenteDevolve404() throws Exception {
        mockMvc.perform(get("/imagens/nao-existe.webp"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

```bash
./mvnw test -Dtest=ServirImagensTest
```

Esperado: FALHA — `/imagens/**` devolve 404 porque nada serve essa rota hoje.

- [ ] **Step 3: Escrever a configuração**

Criar `src/main/java/loja/bytegames/config/ConfiguracaoRecursosWeb.java`:

```java
package loja.bytegames.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.time.Duration;

/**
 * Expoe a pasta de uploads em /imagens/**. A pasta fica fora do classpath, entao
 * sem este mapeamento os arquivos enviados sao inalcancaveis por HTTP.
 */
@Configuration
public class ConfiguracaoRecursosWeb implements WebMvcConfigurer {

    private final String diretorioUploads;

    public ConfiguracaoRecursosWeb(
            @Value("${bytegames.uploads.diretorio:uploads}") String diretorioUploads) {
        this.diretorioUploads = diretorioUploads;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        String caminho = Paths.get(diretorioUploads).toAbsolutePath().normalize().toUri().toString();
        registro.addResourceHandler("/imagens/**")
                .addResourceLocations(caminho)
                // Os nomes sao UUID: o conteudo de cada URL nunca muda, entao
                // cachear por um ano e seguro.
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).immutable());
    }
}
```

- [ ] **Step 4: Declarar a propriedade no perfil padrão**

Ao fim de `src/main/resources/application.properties`:

```properties
# Pasta onde as capas de produto sao gravadas (fora do classpath, ignorada pelo git)
bytegames.uploads.diretorio=uploads
```

- [ ] **Step 5: Rodar para confirmar que passa**

```bash
./mvnw test -Dtest=ServirImagensTest
```

Esperado: PASSA, 3 testes.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/loja/bytegames/config/ConfiguracaoRecursosWeb.java src/main/resources/application.properties src/test/java/loja/bytegames/config/ServirImagensTest.java
git commit -m "Serve pasta de uploads em /imagens com cache imutavel"
```

---

### Task 8: Upload de capa no painel administrativo

**Files:**
- Modify: `src/main/java/loja/bytegames/ProdutoController.java`
- Test: `src/test/java/loja/bytegames/UploadCapaTest.java`

**Interfaces:**
- Consumes: `ArmazenamentoImagemService.guardar` / `.remover` (Tasks 5 e 6),
  assinaturas de repositório da Task 4.
- Produces: os endpoints existentes passam a aceitar a parte `imagem` do
  multipart. A Task 9 depende de `produto.imagem` estar populado.

- [ ] **Step 1: Escrever o teste que falha**

Criar `src/test/java/loja/bytegames/UploadCapaTest.java`:

```java
package loja.bytegames;

import loja.bytegames.repository.CategoriaRepository;
import loja.bytegames.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadCapaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private byte[] jpeg() throws Exception {
        BufferedImage imagem = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(imagem, "jpeg", saida);
        return saida.toByteArray();
    }

    @Test
    void criarProdutoComCapaGravaONomeDaImagem() throws Exception {
        categoriaRepository.inserirCategoria("Cat Upload", "Teste de upload");
        Long categoriaId = categoriaRepository.buscarCategoriaPorNome("Cat Upload")
                .orElseThrow(() -> new AssertionError("Categoria nao inserida")).getId();

        MockMultipartFile capa = new MockMultipartFile(
                "imagem", "capa.jpg", "image/jpeg", jpeg());

        mockMvc.perform(multipart("/produtos")
                        .file(capa)
                        .param("nome", "Jogo Com Upload")
                        .param("descricao", "Descricao do jogo")
                        .param("preco", "49.90")
                        .param("estoque", "3")
                        .param("categoria.id", String.valueOf(categoriaId)))
                .andExpect(status().is3xxRedirection());

        Produto salvo = produtoRepository.findByNomeContainingIgnoreCase("Jogo Com Upload")
                .stream().findFirst()
                .orElseThrow(() -> new AssertionError("Produto nao foi salvo"));
        assertNotNull(salvo.getImagem(), "A capa deveria ter sido gravada");
    }

    @Test
    void criarProdutoSemCapaContinuaFuncionando() throws Exception {
        categoriaRepository.inserirCategoria("Cat Sem Upload", "Teste sem upload");
        Long categoriaId = categoriaRepository.buscarCategoriaPorNome("Cat Sem Upload")
                .orElseThrow(() -> new AssertionError("Categoria nao inserida")).getId();

        MockMultipartFile vazio = new MockMultipartFile(
                "imagem", "", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/produtos")
                        .file(vazio)
                        .param("nome", "Jogo Sem Upload")
                        .param("descricao", "Descricao do jogo")
                        .param("preco", "19.90")
                        .param("estoque", "1")
                        .param("categoria.id", String.valueOf(categoriaId)))
                .andExpect(status().is3xxRedirection());

        produtoRepository.findByNomeContainingIgnoreCase("Jogo Sem Upload")
                .stream().findFirst()
                .orElseThrow(() -> new AssertionError("Produto sem capa deveria ter sido salvo"));
    }

    @Test
    void arquivoInvalidoVoltaAoFormularioComMensagem() throws Exception {
        categoriaRepository.inserirCategoria("Cat Invalida", "Teste invalido");
        Long categoriaId = categoriaRepository.buscarCategoriaPorNome("Cat Invalida")
                .orElseThrow(() -> new AssertionError("Categoria nao inserida")).getId();

        MockMultipartFile mentiroso = new MockMultipartFile(
                "imagem", "virus.jpg", "image/jpeg", "nao e imagem".getBytes());

        mockMvc.perform(multipart("/produtos")
                        .file(mentiroso)
                        .param("nome", "Jogo Invalido")
                        .param("descricao", "Descricao do jogo")
                        .param("preco", "19.90")
                        .param("estoque", "1")
                        .param("categoria.id", String.valueOf(categoriaId)))
                // Nao redireciona: volta ao formulario para o usuario corrigir.
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

```bash
./mvnw test -Dtest=UploadCapaTest
```

Esperado: FALHA — o controller ignora a parte `imagem` e grava `null`.

- [ ] **Step 3: Ligar o serviço ao controller**

Em `ProdutoController.java`, acrescentar os imports:

```java
import loja.bytegames.service.ArmazenamentoImagemService;
import org.springframework.web.multipart.MultipartFile;
```

Injetar o serviço — acrescentar o campo e o parâmetro do construtor:

```java
    private final ArmazenamentoImagemService armazenamentoImagem;

    public ProdutoController(ProdutoRepository produtoRepository,
                             CategoriaRepository categoriaRepository,
                             ArmazenamentoImagemService armazenamentoImagem) {
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
        this.armazenamentoImagem = armazenamentoImagem;
    }
```

Substituir `salvarNovo` por:

```java
    // @PostMapping salva os dados do formulario de criacao de produtos
    @PostMapping
    public String salvarNovo(@Valid @ModelAttribute("produto") Produto produto,
                             BindingResult result,
                             @RequestParam(value = "imagem", required = false) MultipartFile imagem,
                             Model model) {
        // Se houver algum erro de validacao (ex: preco vazio ou nome curto)
        if (result.hasErrors()) {
            model.addAttribute("categorias", categoriaRepository.buscarTodasCategorias());
            return "produtos/form-criar";
        }

        String nomeImagem;
        try {
            nomeImagem = armazenamentoImagem.guardar(imagem);
        } catch (ArmazenamentoImagemService.ImagemInvalidaException e) {
            // Volta ao formulario com a mensagem legivel, em vez de estourar 500.
            model.addAttribute("categorias", categoriaRepository.buscarTodasCategorias());
            model.addAttribute("erroImagem", e.getMessage());
            return "produtos/form-criar";
        }

        produtoRepository.inserirProduto(
            produto.getNome(),
            produto.getDescricao(),
            produto.getPreco(),
            produto.getEstoque(),
            produto.getCategoria().getId(),
            nomeImagem
        );

        return "redirect:/produtos";
    }
```

Substituir `atualizar` por:

```java
    // @PostMapping("/{id}") salva as alteracoes enviadas pelo formulario de edicao
    @PostMapping("/{id}")
    public String atualizar(@PathVariable("id") Long id,
                            @Valid @ModelAttribute("produto") Produto produto,
                            BindingResult result,
                            @RequestParam(value = "imagem", required = false) MultipartFile imagem,
                            Model model) {
        if (result.hasErrors()) {
            model.addAttribute("categorias", categoriaRepository.buscarTodasCategorias());
            return "produtos/form-editar";
        }

        String nomeImagem;
        try {
            nomeImagem = armazenamentoImagem.guardar(imagem);
        } catch (ArmazenamentoImagemService.ImagemInvalidaException e) {
            model.addAttribute("categorias", categoriaRepository.buscarTodasCategorias());
            model.addAttribute("erroImagem", e.getMessage());
            return "produtos/form-editar";
        }

        // Se veio capa nova, a antiga vira lixo em disco -- apaga antes de trocar.
        if (nomeImagem != null) {
            produtoRepository.buscarProdutoPorId(id)
                    .map(Produto::getImagem)
                    .ifPresent(armazenamentoImagem::remover);
        }

        // nomeImagem nulo significa "manter a capa atual" (ver ProdutoRepository)
        produtoRepository.atualizarProduto(
            id,
            produto.getNome(),
            produto.getDescricao(),
            produto.getPreco(),
            produto.getEstoque(),
            produto.getCategoria().getId(),
            nomeImagem
        );

        return "redirect:/produtos";
    }
```

Substituir `excluir` por:

```java
    // @PostMapping("/{id}/excluir") remove o produto pelo ID
    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable("id") Long id) {
        // Apaga os arquivos antes do registro, senao perdemos a referencia.
        produtoRepository.buscarProdutoPorId(id)
                .map(Produto::getImagem)
                .ifPresent(armazenamentoImagem::remover);
        produtoRepository.deletarProdutoPorId(id);
        return "redirect:/produtos";
    }
```

- [ ] **Step 4: Rodar para confirmar que passa**

```bash
./mvnw test -Dtest=UploadCapaTest
```

Esperado: PASSA, 3 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/loja/bytegames/ProdutoController.java src/test/java/loja/bytegames/UploadCapaTest.java
git commit -m "Liga upload de capa ao CRUD de produto com erro tratado no formulario"
```

---

### Task 9: Campo de arquivo nos formulários do painel

**Files:**
- Modify: `src/main/resources/templates/produtos/form-criar.html`
- Modify: `src/main/resources/templates/produtos/form-editar.html`
- Modify: `src/main/resources/static/css/estilo.css`

**Interfaces:**
- Consumes: `erroImagem` no model (Task 8), campo `produto.imagem` (Task 4).
- Produces: a parte multipart chamada `imagem`, que a Task 8 lê.

Sem esta task o upload da Task 8 é inalcançável pela interface — o formulário
atual não tem `enctype` nem campo de arquivo.

- [ ] **Step 1: Adicionar `enctype` e campo em `form-criar.html`**

Trocar a abertura do formulário (linha 20) por:

```html
            <form th:action="@{/produtos}" th:object="${produto}" method="post"
                  enctype="multipart/form-data">
```

Antes do bloco de botões que fecha o formulário, acrescentar:

```html
                <div class="form-group">
                    <label for="imagem">Capa do jogo</label>
                    <input type="file" id="imagem" name="imagem"
                           accept="image/jpeg,image/png,image/webp">
                    <small class="ajuda-campo">JPEG, PNG ou WebP, ate 5 MB. Opcional.</small>
                    <span class="erro-campo" th:if="${erroImagem}" th:text="${erroImagem}"></span>
                </div>
```

- [ ] **Step 2: Mesma mudança em `form-editar.html`, com preview**

Trocar a abertura do formulário por (mantendo a action existente do arquivo):

```html
            <form th:action="@{/produtos/{id}(id=${produto.id})}" th:object="${produto}"
                  method="post" enctype="multipart/form-data">
```

E acrescentar o mesmo grupo, com a capa atual visível para que o usuário entenda
que deixar em branco preserva o que já existe:

```html
                <div class="form-group">
                    <label for="imagem">Capa do jogo</label>
                    <img th:if="${produto.imagem}" class="preview-capa"
                         th:src="@{'/imagens/' + ${produto.imagem} + '-400.webp'}"
                         width="200" height="113" alt="Capa atual do jogo">
                    <input type="file" id="imagem" name="imagem"
                           accept="image/jpeg,image/png,image/webp">
                    <small class="ajuda-campo">Deixe em branco para manter a capa atual.</small>
                    <span class="erro-campo" th:if="${erroImagem}" th:text="${erroImagem}"></span>
                </div>
```

- [ ] **Step 3: Estilizar os elementos novos**

Ao fim de `estilo.css`:

```css
/* ===== CAMPO DE CAPA ===== */
.ajuda-campo {
    display: block;
    margin-top: 6px;
    color: var(--text-muted);
    font-size: 12px;
}

.erro-campo {
    display: block;
    margin-top: 6px;
    color: var(--red);
    font-size: 13px;
}

.preview-capa {
    display: block;
    margin-bottom: 10px;
    border-radius: 6px;
    border: 1px solid var(--border);
    object-fit: cover;
}
```

- [ ] **Step 4: Verificar na aplicação**

```bash
./mvnw spring-boot:run
```

Abrir `http://localhost:8080/produtos/novo`, cadastrar um jogo com uma capa
qualquer, e conferir na pasta `uploads/` que apareceram o original e as
derivadas `-400`, `-800` e `-1200`. Depois abrir a edição desse produto e
salvar **sem** escolher arquivo — a capa tem que continuar lá.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/produtos/form-criar.html src/main/resources/templates/produtos/form-editar.html src/main/resources/static/css/estilo.css
git commit -m "Adiciona campo de capa aos formularios de produto com preview na edicao"
```

---

### Task 10: Fragment de capa com placeholder de categoria

**Files:**
- Create: `src/main/java/loja/bytegames/service/CorCategoria.java`
- Create: `src/main/resources/templates/fragments/capa.html`
- Test: `src/test/java/loja/bytegames/service/CorCategoriaTest.java`

**Interfaces:**
- Consumes: `produto.imagem` (Task 4), rota `/imagens/**` (Task 7), nomes de
  derivada da Task 6.
- Produces:
  - Bean Spring de nome `corCategoria`, com `hex(String nomeCategoria)` →
    `String` no formato `#rrggbb`. Chamado em Thymeleaf por
    `${@corCategoria.hex(...)}`.
  - Fragment `capa(produto, larguraCss, prioritaria)` em
    `fragments/capa.html`, usado pelas Tasks 11 e 12.

`Categoria` não tem campo de cor e não vamos adicionar um — a cor é derivada do
nome, de forma estável, a partir da paleta que já existe no CSS.

- [ ] **Step 1: Escrever o teste que falha**

Criar `src/test/java/loja/bytegames/service/CorCategoriaTest.java`:

```java
package loja.bytegames.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorCategoriaTest {

    private final CorCategoria cor = new CorCategoria();

    @Test
    void devolveSempreAMesmaCorParaOMesmoNome() {
        // Estabilidade importa: a capa de um jogo nao pode mudar de cor a cada
        // recarregamento da pagina.
        assertEquals(cor.hex("RPG"), cor.hex("RPG"));
    }

    @Test
    void devolveHexadecimalValido() {
        assertTrue(cor.hex("Estrategia").matches("#[0-9a-f]{6}"));
    }

    @Test
    void toleraNomeNulo() {
        // Produto orfao de categoria nao pode derrubar a renderizacao da vitrine.
        assertTrue(cor.hex(null).matches("#[0-9a-f]{6}"));
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

```bash
./mvnw test -Dtest=CorCategoriaTest
```

Esperado: FALHA de compilação — a classe não existe.

- [ ] **Step 3: Escrever o bean**

Criar `src/main/java/loja/bytegames/service/CorCategoria.java`:

```java
package loja.bytegames.service;

import org.springframework.stereotype.Component;

/**
 * Deriva uma cor estavel a partir do nome da categoria, para o placeholder de
 * produto sem capa. A entidade Categoria nao tem campo de cor e nao precisa ter:
 * a cor e funcao do nome, entao nunca fica dessincronizada do banco.
 */
@Component("corCategoria")
public class CorCategoria {

    // Puxadas da paleta que ja existe em estilo.css, para o placeholder nao
    // parecer corpo estranho na pagina.
    private static final String[] PALETA = {
            "#9871ff", // --purple
            "#04d361", // --green
            "#4da6ff", // --blue
            "#e05252", // --red
            "#7c5ce0"  // --purple-dark
    };

    public String hex(String nomeCategoria) {
        if (nomeCategoria == null || nomeCategoria.isBlank()) {
            return PALETA[0];
        }
        // Math.abs(Integer.MIN_VALUE) e negativo -- mascarar o sinal evita o
        // indice negativo que isso causaria.
        int indice = (nomeCategoria.hashCode() & 0x7fffffff) % PALETA.length;
        return PALETA[indice];
    }
}
```

- [ ] **Step 4: Rodar para confirmar que passa**

```bash
./mvnw test -Dtest=CorCategoriaTest
```

Esperado: PASSA, 3 testes.

- [ ] **Step 5: Escrever o fragment**

Criar `src/main/resources/templates/fragments/capa.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>

<!--
  Fragment unico de capa. Recebe:
    produto      - a entidade
    larguraCss   - valor de sizes, ex: '300px' ou '100vw'
    prioritaria  - true no hero da pagina de detalhe (carrega adiantado),
                   false na grade da vitrine (carrega preguicoso)

  A reserva de espaco (width/height) e obrigatoria: sem ela, cada capa que chega
  empurra o layout e o CLS estoura o alvo de 0,05 da spec.
-->
<th:block th:fragment="capa(produto, larguraCss, prioritaria)">

    <picture th:if="${produto.imagem}" class="capa-moldura">
        <source type="image/webp"
                th:srcset="|/imagens/${produto.imagem}-400.webp 400w, /imagens/${produto.imagem}-800.webp 800w, /imagens/${produto.imagem}-1200.webp 1200w|"
                th:sizes="${larguraCss}">
        <img th:src="@{'/imagens/' + ${produto.imagem} + '-800.webp'}"
             th:alt="|Capa do jogo ${produto.nome}|"
             class="capa-imagem"
             width="800" height="450"
             th:attr="loading=${prioritaria} ? 'eager' : 'lazy', fetchpriority=${prioritaria} ? 'high' : 'auto'">
    </picture>

    <!-- Ausencia elegante: produto sem capa recebe um bloco com a cor da
         categoria e a inicial do titulo, nunca uma imagem quebrada. -->
    <div th:unless="${produto.imagem}" class="capa-moldura capa-vazia"
         th:style="|background: linear-gradient(140deg, ${@corCategoria.hex(produto.categoria.nome)} 0%, var(--bg-card) 100%)|"
         th:attr="aria-label=|Jogo ${produto.nome} sem capa cadastrada|">
        <span class="capa-vazia-inicial" th:text="${#strings.substring(produto.nome, 0, 1)}"></span>
    </div>

</th:block>

</body>
</html>
```

- [ ] **Step 6: Estilizar a capa**

Ao fim de `estilo.css`:

```css
/* ===== CAPA DE JOGO ===== */
.capa-moldura {
    display: block;
    width: 100%;
    /* Reserva o espaco antes do primeiro byte chegar -- e isto que segura o CLS. */
    aspect-ratio: 16 / 9;
    border-radius: 8px;
    overflow: hidden;
    background-color: var(--bg-secondary);
}

.capa-imagem {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
}

.capa-vazia {
    display: flex;
    align-items: center;
    justify-content: center;
}

.capa-vazia-inicial {
    font-family: 'Rajdhani', sans-serif;
    font-size: 48px;
    font-weight: 700;
    color: var(--text-primary);
    opacity: 0.55;
}
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/loja/bytegames/service/CorCategoria.java src/main/resources/templates/fragments/capa.html src/main/resources/static/css/estilo.css src/test/java/loja/bytegames/service/CorCategoriaTest.java
git commit -m "Adiciona fragment de capa com placeholder derivado da categoria"
```

---

### Task 11: Capa na vitrine e na página de detalhe

**Files:**
- Modify: `src/main/resources/templates/loja/vitrine.html`
- Modify: `src/main/resources/templates/loja/detalhar.html`
- Test: `src/test/java/loja/bytegames/VitrineRenderizacaoTest.java`

**Interfaces:**
- Consumes: fragment `capa` (Task 10).
- Produces: nada consumido por tasks posteriores desta fase.

- [ ] **Step 1: Escrever o teste que falha**

Criar `src/test/java/loja/bytegames/VitrineRenderizacaoTest.java`:

```java
package loja.bytegames;

import loja.bytegames.repository.CategoriaRepository;
import loja.bytegames.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VitrineRenderizacaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private Long prepararCategoria(String nome) {
        categoriaRepository.inserirCategoria(nome, "Categoria de teste");
        return categoriaRepository.buscarCategoriaPorNome(nome)
                .orElseThrow(() -> new AssertionError("Categoria nao inserida")).getId();
    }

    @Test
    void produtoComCapaRenderizaSrcsetResponsivo() throws Exception {
        Long categoriaId = prepararCategoria("Cat Render Capa");
        produtoRepository.inserirProduto("Jogo Renderiza Capa", "Descricao",
                new BigDecimal("10.00"), 1, categoriaId, "capa-render");

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("capa-render-400.webp 400w")))
                .andExpect(content().string(containsString("capa-render-1200.webp 1200w")));
    }

    @Test
    void produtoSemCapaRenderizaPlaceholderEnaoImagemQuebrada() throws Exception {
        Long categoriaId = prepararCategoria("Cat Render Vazia");
        produtoRepository.inserirProduto("Zelda Sem Capa", "Descricao",
                new BigDecimal("10.00"), 1, categoriaId, null);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("capa-vazia")))
                // Nenhum src apontando para /imagens/null
                .andExpect(content().string(not(containsString("/imagens/null"))));
    }

    @Test
    void capaReservaEspacoParaNaoDeslocarOLayout() throws Exception {
        Long categoriaId = prepararCategoria("Cat Render Reserva");
        produtoRepository.inserirProduto("Jogo Reserva", "Descricao",
                new BigDecimal("10.00"), 1, categoriaId, "capa-reserva");

        mockMvc.perform(get("/"))
                .andExpect(content().string(containsString("width=\"800\"")))
                .andExpect(content().string(containsString("height=\"450\"")));
    }

    @Test
    void paginaDeDetalheCarregaACapaComPrioridade() throws Exception {
        Long categoriaId = prepararCategoria("Cat Render Detalhe");
        produtoRepository.inserirProduto("Jogo Detalhe", "Descricao",
                new BigDecimal("10.00"), 1, categoriaId, "capa-detalhe");
        Long id = produtoRepository.findByNomeContainingIgnoreCase("Jogo Detalhe")
                .stream().findFirst()
                .orElseThrow(() -> new AssertionError("Produto nao encontrado")).getId();

        mockMvc.perform(get("/produto/" + id))
                .andExpect(status().isOk())
                // A capa do hero e o LCP da pagina: nao pode ser lazy.
                .andExpect(content().string(containsString("loading=\"eager\"")));
    }
}
```

- [ ] **Step 2: Rodar para confirmar que falha**

```bash
./mvnw test -Dtest=VitrineRenderizacaoTest
```

Esperado: FALHA nos 4 — os templates ainda não renderizam capa nenhuma.

- [ ] **Step 3: Inserir a capa na vitrine**

Em `loja/vitrine.html`, dentro de `.card-produto`, o link do produto passa a
envolver a capa. Substituir o `<a>` existente por:

```html
                    <a th:href="@{/produto/{id}(id=${prod.id})}" style="text-decoration: none; color: inherit; display: block; cursor: pointer;">
                        <th:block th:replace="~{fragments/capa :: capa(${prod}, '(max-width: 600px) 100vw, 300px', false)}"></th:block>
                        <span class="badge-categoria" th:text="${prod.categoria.nome}" style="margin-top: 12px; display: inline-block;"></span>
                        <h4 th:text="${prod.nome}" style="margin-top: 10px;"></h4>
                    </a>
```

- [ ] **Step 4: Inserir a capa no detalhe**

Em `loja/detalhar.html`, imediatamente antes do `<span class="badge-categoria">`
(dentro do `<div>` que abre o bloco de conteúdo), acrescentar:

```html
                <th:block th:replace="~{fragments/capa :: capa(${produto}, '(max-width: 700px) 100vw, 600px', true)}"></th:block>
```

E, logo abaixo, dar respiro ao badge:

```html
                <span class="badge-categoria" th:text="${produto.categoria.nome}" style="margin-top: 16px; display: inline-block;"></span>
```

- [ ] **Step 5: Rodar para confirmar que passa**

```bash
./mvnw test -Dtest=VitrineRenderizacaoTest
```

Esperado: PASSA, 4 testes.

- [ ] **Step 6: Conferir na aplicação**

```bash
./mvnw spring-boot:run
```

Abrir `http://localhost:8080`. Os 5 jogos de exemplo têm `imagem = NULL`, então
devem aparecer com o placeholder colorido da categoria — não com imagem
quebrada. Subir uma capa em um deles pelo painel e recarregar a vitrine.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/templates/loja/vitrine.html src/main/resources/templates/loja/detalhar.html src/test/java/loja/bytegames/VitrineRenderizacaoTest.java
git commit -m "Renderiza capa responsiva na vitrine e na pagina de detalhe"
```

---

### Task 12: Limpeza estrutural

**Files:**
- Delete: `src/main/resources/templates/produto/` (4 arquivos)
- Modify: 11 templates com navbar inline
- Create: `.gitattributes`

**Interfaces:**
- Consumes: nada.
- Produces: nada. É limpeza pura — o comportamento observável não muda.

- [ ] **Step 1: Confirmar que a pasta é mesmo código morto**

```bash
cd /c/dev/pessoal/byte-games
grep -rn '"produto/' src/main/java/
```

Esperado: **nenhuma linha**. Todo retorno de view usa `"produtos/..."` no plural.
Se aparecer alguma ocorrência, parar e reavaliar antes de apagar.

- [ ] **Step 2: Apagar a pasta morta**

```bash
git rm -r src/main/resources/templates/produto/
```

- [ ] **Step 3: Unificar a navbar**

Em cada um dos 11 templates abaixo, substituir o bloco `<nav class="navbar">
... </nav>` inteiro por uma linha:

```html
    <nav class="navbar" th:replace="~{fragments/navbar :: navbar}"></nav>
```

Arquivos: `categorias/detalhar.html`, `categorias/form-criar.html`,
`categorias/form-editar.html`, `categorias/listar.html`, `loja/carrinho.html`,
`loja/detalhar.html`, `loja/vitrine.html`, `produtos/detalhar.html`,
`produtos/form-criar.html`, `produtos/form-editar.html`, `produtos/listar.html`.

Os 3 templates de FAQ já usam o fragment — não mexer neles.

Isso corrige de graça uma divergência existente: a cópia da vitrine tinha
perdido o link "Loja" que o fragment tem.

- [ ] **Step 4: Corrigir o typo**

```bash
grep -rn 'administrative' src/main/resources/templates/
```

Trocar `administrative` por `administrativo` onde aparecer.

- [ ] **Step 5: Fixar fim de linha**

Criar `.gitattributes`:

```gitattributes
* text=auto eol=lf
*.bat text eol=crlf
*.cmd text eol=crlf
mvnw text eol=lf
*.jar binary
*.png binary
*.jpg binary
*.webp binary
```

- [ ] **Step 6: Rodar a suíte inteira**

```bash
./mvnw test -Dtest='!BytegamesDatabaseTests'
```

Esperado: todos os testes das Tasks 2 a 11 passam. `BytegamesDatabaseTests` fica
de fora porque exige MySQL de pé; rodar separado se o MySQL estiver disponível.

- [ ] **Step 7: Conferir a navegação no navegador**

```bash
./mvnw spring-boot:run
```

Percorrer as 5 áreas — vitrine, detalhe, carrinho, produtos, categorias, FAQ — e
confirmar que a navbar aparece igual em todas e que o link "Loja" agora existe
em todo lugar.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "Remove templates duplicados, unifica navbar em fragment e fixa fim de linha"
```

---

### Task 13: Fontes self-hosted

O `@import` remoto no topo de `estilo.css` é requisição bloqueante em cadeia —
o navegador precisa baixar o CSS, achar o `@import`, buscar o CSS do Google e só
então buscar os arquivos de fonte. Também contradiz o requisito de rodar offline
da seção 5.1 da spec.

**Files:**
- Create: `src/main/resources/static/fontes/` (arquivos woff2)
- Modify: `src/main/resources/static/css/estilo.css`

**Interfaces:**
- Consumes: nada.
- Produces: nada. Muda apenas a origem das fontes.

- [ ] **Step 1: Baixar os arquivos woff2**

Baixar as fontes usadas hoje — **Rajdhani** (pesos 400, 600, 700) e **Inter**
(pesos 300, 400, 500, 600), subconjunto latin — e gravar em
`src/main/resources/static/fontes/`. Ambas são licenciadas sob SIL Open Font
License, que permite redistribuição junto com o projeto.

- [ ] **Step 2: Substituir o `@import` por `@font-face`**

Em `estilo.css`, apagar a linha 1 inteira (o `@import url('https://fonts...')`)
e colocar no lugar, repetindo o bloco para cada peso baixado:

```css
/* Fontes servidas pelo proprio projeto: sem requisicao externa bloqueante e o
   projeto roda offline. font-display: swap evita texto invisivel enquanto a
   fonte carrega. */
@font-face {
    font-family: 'Rajdhani';
    src: url('/fontes/rajdhani-700.woff2') format('woff2');
    font-weight: 700;
    font-style: normal;
    font-display: swap;
}

@font-face {
    font-family: 'Inter';
    src: url('/fontes/inter-400.woff2') format('woff2');
    font-weight: 400;
    font-style: normal;
    font-display: swap;
}
```

- [ ] **Step 3: Garantir pilha de fallback**

Onde o CSS declara `font-family: 'Inter', sans-serif;` e
`font-family: 'Rajdhani', sans-serif;`, trocar por pilhas reais, para que a
página não fique feia se uma fonte falhar:

```css
    font-family: 'Inter', system-ui, -apple-system, 'Segoe UI', sans-serif;
```

```css
    font-family: 'Rajdhani', 'Segoe UI', system-ui, sans-serif;
```

- [ ] **Step 4: Verificar sem rede**

```bash
./mvnw spring-boot:run
```

Abrir o DevTools, aba Network, e confirmar que **nenhuma requisição sai para
`fonts.googleapis.com` ou `fonts.gstatic.com`**. Depois marcar "Offline" no
DevTools e recarregar: a tipografia deve continuar correta.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/fontes/ src/main/resources/static/css/estilo.css
git commit -m "Serve fontes pelo projeto e remove import remoto bloqueante"
```

---

### Task 14: Medição pós-Fase 1 e fechamento

**Files:**
- Modify: `docs/specs/medicoes-desempenho.md`

**Interfaces:**
- Consumes: linha de base da Task 1.
- Produces: os números que a Fase 2 vai usar para calibrar o orçamento de
  JavaScript.

- [ ] **Step 1: Preparar um cenário comparável**

Subir capas reais para os 5 jogos de exemplo pelo painel. A comparação com a
linha de base só é honesta se a vitrine tiver conteúdo de imagem de verdade —
medir a página com placeholders mediria a ausência da feature, não a feature.

Deliberadamente usar pelo menos uma imagem grande e não otimizada (o PNG de
2,5 MB que já está em `uploads/` serve) para provar que o pipeline dá conta.

- [ ] **Step 2: Medir no mesmo perfil da linha de base**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

Rodar Lighthouse em `/` e `/produto/1`, mobile, mesma máquina.

- [ ] **Step 3: Registrar**

Acrescentar a `docs/specs/medicoes-desempenho.md`:

```markdown
## Pós-Fase 1 — fundação de imagem

| Página | Performance | LCP | CLS | Peso total | JS transferido |
|---|---|---|---|---|---|
| `/` | | | | | |
| `/produto/1` | | | | | |

Mudanças desta fase que afetam os números:
- Capas responsivas em WebP (400/800/1200) substituindo ausência de imagem.
- Reserva de espaço com `aspect-ratio` em toda capa.
- Fontes servidas pelo projeto; `@import` remoto removido.

Verificação de CLS: alvo da spec é < 0,05. Valor medido: ___
```

- [ ] **Step 4: Conferir os critérios de aceite alcançáveis nesta fase**

Da seção 10 da spec, esta fase fecha os itens 1, 2, 3, 6 (parcial — a vitrine
ganha nova medição na Fase 2), 8 e a primeira metade do 9. Os itens 4, 5 e 7
pertencem à Fase 2. Marcar no documento de medições o que ficou provado.

- [ ] **Step 5: Rodar a suíte completa**

```bash
./mvnw test -Dtest='!BytegamesDatabaseTests'
```

Esperado: verde.

- [ ] **Step 6: Commit**

```bash
git add docs/specs/medicoes-desempenho.md
git commit -m "Registra medicao de desempenho apos a fase de fundacao"
```

---

## Auto-revisão deste plano

**Cobertura da spec.** Seção 4.1 → Task 4. Seção 4.2 → Tasks 5 e 8. Seção 4.3 →
Task 7. Seção 4.4 → Tasks 3 e 6. Seção 4.5 → Task 10. Seção 4.6 → Task 12.
Seção 6.1 → Task 1. Seção 6.3 (CLS) → Tasks 10 e 11. Seção 6.4 → Task 14.
Seção 7 (unidade e MockMvc) → Tasks 5, 6, 8, 10 e 11.

Duas coisas do levantamento não estavam na spec e ganharam task própria:
formulário sem `enctype` (Task 9) e `@import` remoto de fonte (Task 13). Uma
terceira, o perfil de teste H2 (Task 2), é infraestrutura sem a qual nenhum
teste deste plano roda numa máquina sem MySQL.

**Seções da spec que pertencem à Fase 2 e não têm task aqui:** 5.1 a 5.7 inteira
(entrega de JS, tokens de movimento, Barba, tilt, carrinho assíncrono, shader,
acessibilidade de movimento), 6.2 (orçamento de JS) e a parte Playwright da
seção 7. Ficam para o plano da Fase 2.

**Consistência de tipos.** `guardar(MultipartFile)` → `String` é usado com essa
assinatura nas Tasks 5, 6 e 8. `remover(String)` idem nas Tasks 5 e 8. O
nome-base gravado no banco na Task 4 é o mesmo que a Task 6 usa como prefixo de
arquivo e que a Task 10 monta em `/imagens/{base}-{largura}.webp`. `LARGURAS` é
declarada na Task 5 e consumida nas Tasks 6 e 11. O bean `corCategoria` da Task
10 é chamado com esse nome no fragment da mesma task.

**Sem placeholders.** Todo passo de código traz o código. Os dois pontos que
dependem da máquina — encoder WebP (Task 3) e arquivos de fonte (Task 13) —
trazem critério de decisão, comando de verificação e caminho alternativo
explícito.
