package loja.bytegames;

// Importa os repositórios, a classe Produto e recursos do Spring
import loja.bytegames.repository.ProdutoRepository;
import loja.bytegames.repository.CategoriaRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

// @Controller diz ao Spring que esta classe controla páginas HTML (Web)
// @RequestMapping("/produtos") define que todas as rotas começam com "/produtos"
@Controller
@RequestMapping("/produtos")
public class ProdutoController {

    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;

    // Construtor: o Spring injeta os repositórios necessários automaticamente
    public ProdutoController(ProdutoRepository produtoRepository, CategoriaRepository categoriaRepository) {
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
    }

    // @GetMapping lista todos os produtos na página /produtos
    @GetMapping
    public String listarTodos(Model model) {
        // Envia a lista de produtos para exibir no HTML
        model.addAttribute("produtos", produtoRepository.buscarTodosProdutos());
        return "produtos/listar"; // Abre a página templates/produtos/listar.html
    }

    // @GetMapping("/novo") abre o formulário para cadastrar um novo produto
    @GetMapping("/novo")
    public String exibirFormCriar(Model model) {
        // Cria um produto em branco e envia para a tela mapear os campos
        model.addAttribute("produto", new Produto());
        // Envia as categorias para a caixa de seleção (dropdown)
        model.addAttribute("categorias", categoriaRepository.buscarTodasCategorias());
        return "produtos/form-criar"; // Abre templates/produtos/form-criar.html
    }

    // @PostMapping salva os dados do formulário de criação de produtos
    @PostMapping
    public String salvarNovo(@Valid @ModelAttribute("produto") Produto produto, BindingResult result, Model model) {
        // Se houver algum erro de validação (ex: preço vazio ou nome curto)
        if (result.hasErrors()) {
            // Recarrega as categorias e mantém o usuário no formulário exibindo os erros
            model.addAttribute("categorias", categoriaRepository.buscarTodasCategorias());
            return "produtos/form-criar";
        }

        // Insere o produto através do método do repositório
        produtoRepository.inserirProduto(
            produto.getNome(),
            produto.getDescricao(),
            produto.getPreco(),
            produto.getEstoque(),
            produto.getCategoria().getId(),
            null // capa ainda nao enviada pelo formulario
        );

        // Redireciona o navegador de volta para a lista geral de produtos
        return "redirect:/produtos";
    }

    // @GetMapping("/{id}") exibe a página de detalhes de um produto específico
    @GetMapping("/{id}")
    public String detalhar(@PathVariable("id") Long id, Model model) {
        Optional<Produto> produtoOptional = produtoRepository.buscarProdutoPorId(id);
        // Se o produto existir no banco, exibe a ficha dele
        if (produtoOptional.isPresent()) {
            model.addAttribute("produto", produtoOptional.get());
            return "produtos/detalhar"; // Abre templates/produtos/detalhar.html
        }
        return "redirect:/produtos";
    }

    // @GetMapping("/{id}/editar") abre a tela de edição do produto
    @GetMapping("/{id}/editar")
    public String exibirFormEditar(@PathVariable("id") Long id, Model model) {
        Optional<Produto> produtoOptional = produtoRepository.buscarProdutoPorId(id);
        // Se o produto existir, carrega os dados dele no formulário
        if (produtoOptional.isPresent()) {
            model.addAttribute("produto", produtoOptional.get());
            model.addAttribute("categorias", categoriaRepository.buscarTodasCategorias());
            return "produtos/form-editar"; // Abre templates/produtos/form-editar.html
        }
        return "redirect:/produtos";
    }

    // @PostMapping("/{id}") salva as alterações enviadas pelo formulário de edição
    @PostMapping("/{id}")
    public String atualizar(@PathVariable("id") Long id, @Valid @ModelAttribute("produto") Produto produto, BindingResult result, Model model) {
        // Se houver erros nos dados informados na edição
        if (result.hasErrors()) {
            model.addAttribute("categorias", categoriaRepository.buscarTodasCategorias());
            return "produtos/form-editar";
        }

        // Atualiza os dados no banco utilizando o repositório
        produtoRepository.atualizarProduto(
            id,
            produto.getNome(),
            produto.getDescricao(),
            produto.getPreco(),
            produto.getEstoque(),
            produto.getCategoria().getId(),
            null // capa ainda nao enviada pelo formulario
        );

        return "redirect:/produtos";
    }

    // @PostMapping("/{id}/excluir") remove o produto pelo ID
    @PostMapping("/{id}/excluir")
    public String excluir(@PathVariable("id") Long id) {
        produtoRepository.deletarProdutoPorId(id);
        return "redirect:/produtos";
    }
}
