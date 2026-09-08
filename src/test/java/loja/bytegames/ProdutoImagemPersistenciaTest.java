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
