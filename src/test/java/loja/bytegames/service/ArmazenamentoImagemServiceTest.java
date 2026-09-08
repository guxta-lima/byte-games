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
}
