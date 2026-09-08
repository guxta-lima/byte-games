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
