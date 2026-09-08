package loja.bytegames.repository;

import loja.bytegames.Produto;
import loja.bytegames.Categoria;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    // Método que busca produtos aproximando o nome informado (ex: pesquisar na vitrine)
    List<Produto> findByNomeContainingIgnoreCase(String nome);

    // Método padrão para buscar todos os produtos cadastrados
    default List<Produto> buscarTodosProdutos() {
        return findAll();
    }

    // Método padrão para buscar um produto pelo ID
    default Optional<Produto> buscarProdutoPorId(Long id) {
        return findById(id);
    }

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

    // Método padrão para deletar um produto pelo ID
    default void deletarProdutoPorId(Long id) {
        deleteById(id);
    }
}
