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
