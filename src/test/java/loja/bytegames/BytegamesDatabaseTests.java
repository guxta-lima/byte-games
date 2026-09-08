package loja.bytegames;

import loja.bytegames.Categoria;
import loja.bytegames.Produto;
import loja.bytegames.repository.CategoriaRepository;
import loja.bytegames.repository.ProdutoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BytegamesDatabaseTests {

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Test
    void testCrudFlowOnMySql() {
        // 0. Preparação
        categoriaRepository.inserirCategoria("Categoria Temp Teste", "Categoria temporária para testes");
        
        Categoria categoria = categoriaRepository.buscarCategoriaPorNome("Categoria Temp Teste")
                .orElseThrow(() -> new AssertionError("Categoria de teste deveria ter sido inserida."));
        assertNotNull(categoria.getId());

        // 1. CREATE
        produtoRepository.inserirProduto(
                "Jogo Teste Automatizado",
                "Um produto temporário para testar banco",
                new BigDecimal("99.99"),
                5,
                categoria.getId(),
                null // sem capa neste teste
        );

        Produto novoProduto = produtoRepository.findByNomeContainingIgnoreCase("Jogo Teste Automatizado")
                .stream().findFirst()
                .orElseThrow(() -> new AssertionError("O produto deveria ter sido inserido."));
        Long produtoId = novoProduto.getId();
        assertNotNull(produtoId);

        // 2. READ
        Optional<Produto> produtoBuscadoOpt = produtoRepository.buscarProdutoPorId(produtoId);
        assertTrue(produtoBuscadoOpt.isPresent());
        Produto produtoBuscado = produtoBuscadoOpt.get();

        // 3. UPDATE
        BigDecimal novoPreco = new BigDecimal("129.90");
        produtoRepository.atualizarProduto(
                produtoId,
                produtoBuscado.getNome(),
                produtoBuscado.getDescricao(),
                novoPreco,
                produtoBuscado.getEstoque(),
                categoria.getId(),
                null // sem capa neste teste
                
        );
        
        Produto produtoAtualizado = produtoRepository.buscarProdutoPorId(produtoId)
                .orElseThrow(() -> new AssertionError("O produto deveria existir após atualização."));
        assertEquals(novoPreco, produtoAtualizado.getPreco());

        // 4. DELETE
        produtoRepository.deletarProdutoPorId(produtoId);
        Optional<Produto> produtoDeletadoOpt = produtoRepository.buscarProdutoPorId(produtoId);
        assertFalse(produtoDeletadoOpt.isPresent());

        // Limpeza
        categoriaRepository.deletarCategoriaPorId(categoria.getId());
        Optional<Categoria> categoriaDeletadaOpt = categoriaRepository.buscarCategoriaPorId(categoria.getId());
        assertFalse(categoriaDeletadaOpt.isPresent());
    }
}
