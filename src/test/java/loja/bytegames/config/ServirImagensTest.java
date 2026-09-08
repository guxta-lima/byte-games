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
