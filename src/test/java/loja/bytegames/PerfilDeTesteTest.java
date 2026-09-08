package loja.bytegames;

import loja.bytegames.repository.CategoriaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class PerfilDeTesteTest {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextoSobeContraH2ENaoContraMySql() throws Exception {
        // O perfil de teste tem de TROCAR o banco, nao apenas deixar o contexto
        // subir. Com o MySQL de desenvolvimento ligado, um teste que so verifica
        // "o contexto subiu" passa contra o MySQL e nao prova nada sobre o H2 --
        // e se o perfil quebrar no futuro, a suite passa a escrever no banco de
        // desenvolvimento sem ninguem notar.
        try (Connection conexao = dataSource.getConnection()) {
            assertEquals("H2", conexao.getMetaData().getDatabaseProductName(),
                    "O teste precisa rodar contra H2; o perfil de teste nao foi aplicado");
        }
        assertNotNull(categoriaRepository.buscarTodasCategorias());
    }
}
