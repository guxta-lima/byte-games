package loja.bytegames;

// Importa os recursos de banco de dados (JPA) e validação
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

// @Entity diz que essa classe representa uma tabela no banco de dados
@Entity
@Table(name = "produto")
public class Produto {

    // @Id define a chave primária (código único de identificação)
    // @GeneratedValue diz que o ID será gerado automaticamente pelo banco (1, 2, 3...)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @NotBlank garante que o nome não seja nulo nem vazio
    @NotBlank(message = "O nome é obrigatório")
    private String nome;

    // @NotBlank garante que a descrição não seja nula nem vazia
    @NotBlank(message = "A descrição é obrigatória")
    private String descricao;

    // @NotNull garante que o preço não seja nulo (vazio)
    @NotNull(message = "O preço é obrigatório")
    private BigDecimal preco;

    // @NotNull garante que o estoque não seja nulo (vazio)
    @NotNull(message = "O estoque é obrigatório")
    private Integer estoque;

    // Nome-base do arquivo de capa, sem extensao e sem caminho. Nulo e estado
    // valido: produto sem capa cai no placeholder da categoria.
    @Column(length = 255)
    private String imagem;

    // @ManyToOne indica que vários produtos pertencem a uma única Categoria
    // @JoinColumn define a coluna de chave estrangeira que une as duas tabelas
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    // Construtor vazio padrão, exigido pelo banco de dados
    public Produto() {
    }

    // Métodos Getters e Setters (leitura e gravação das variáveis)
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public void setPreco(BigDecimal preco) {
        this.preco = preco;
    }

    public Integer getEstoque() {
        return estoque;
    }

    public void setEstoque(Integer estoque) {
        this.estoque = estoque;
    }

    public String getImagem() {
        return imagem;
    }

    public void setImagem(String imagem) {
        this.imagem = imagem;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
    }
}
