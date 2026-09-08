package loja.bytegames.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Cuida do ciclo de vida das imagens de capa: valida, nomeia, grava, deriva e
 * remove. Toda regra de arquivo vive aqui -- os controllers nao tocam em I/O.
 */
@Service
public class ArmazenamentoImagemService {

    /** Larguras das derivadas responsivas, em pixels. */
    public static final int[] LARGURAS = {400, 800, 1200};

    /** Teto por arquivo enviado, abaixo do limite de multipart do Spring. */
    private static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;

    /** Bytes de cabecalho suficientes para identificar JPEG, PNG e WebP. */
    private static final int BYTES_DE_ASSINATURA = 12;

    private final Path diretorio;

    public ArmazenamentoImagemService(
            @Value("${bytegames.uploads.diretorio:uploads}") String diretorio) {
        this.diretorio = Paths.get(diretorio).toAbsolutePath().normalize();
    }

    /**
     * Valida e grava o arquivo enviado.
     *
     * @return o nome-base gerado (UUID, sem extensao), ou null quando nenhum
     *         arquivo foi enviado -- o que e caso normal, nao erro.
     */
    public String guardar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            return null;
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new ImagemInvalidaException(
                    "A imagem passa de 5 MB. Envie um arquivo menor.");
        }

        Formato formato = detectarFormato(arquivo);
        if (formato == null) {
            throw new ImagemInvalidaException(
                    "Formato nao reconhecido. Aceitamos JPEG, PNG ou WebP.");
        }

        String nomeBase = UUID.randomUUID().toString();
        try {
            Files.createDirectories(diretorio);
            try (InputStream entrada = arquivo.getInputStream()) {
                Files.copy(entrada, diretorio.resolve(nomeBase + formato.extensao),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new ImagemInvalidaException("Nao foi possivel gravar a imagem.");
        }
        return nomeBase;
    }

    /** Apaga o original e todas as derivadas de um nome-base. */
    public void remover(String nomeBase) {
        if (nomeBase == null || nomeBase.isBlank()) {
            return;
        }
        try (Stream<Path> arquivos = Files.list(diretorio)) {
            arquivos.filter(p -> p.getFileName().toString().startsWith(nomeBase))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignorado) {
                            // Arquivo orfao na pasta nao justifica derrubar a
                            // exclusao do produto no banco.
                        }
                    });
        } catch (IOException ignorado) {
            // Pasta inexistente: nada a remover.
        }
    }

    /**
     * Le a assinatura do arquivo. Nao confiamos na extensao nem no Content-Type,
     * porque os dois vem do cliente e podem mentir.
     */
    private Formato detectarFormato(MultipartFile arquivo) {
        byte[] cabecalho;
        try (InputStream entrada = arquivo.getInputStream()) {
            // readNBytes e nao read: read(byte[]) pode devolver menos bytes que o
            // buffer mesmo havendo mais para ler, e uma leitura curta rejeitaria
            // imagem valida. readNBytes so para no fim do arquivo.
            cabecalho = entrada.readNBytes(BYTES_DE_ASSINATURA);
        } catch (IOException e) {
            return null;
        }
        if (cabecalho.length < BYTES_DE_ASSINATURA) {
            return null;
        }

        if ((cabecalho[0] & 0xFF) == 0xFF && (cabecalho[1] & 0xFF) == 0xD8
                && (cabecalho[2] & 0xFF) == 0xFF) {
            return Formato.JPEG;
        }
        if ((cabecalho[0] & 0xFF) == 0x89 && cabecalho[1] == 'P'
                && cabecalho[2] == 'N' && cabecalho[3] == 'G') {
            return Formato.PNG;
        }
        if (cabecalho[0] == 'R' && cabecalho[1] == 'I' && cabecalho[2] == 'F'
                && cabecalho[3] == 'F' && cabecalho[8] == 'W' && cabecalho[9] == 'E'
                && cabecalho[10] == 'B' && cabecalho[11] == 'P') {
            return Formato.WEBP;
        }
        return null;
    }

    private enum Formato {
        JPEG(".jpg"), PNG(".png"), WEBP(".webp");

        final String extensao;

        Formato(String extensao) {
            this.extensao = extensao;
        }
    }

    /** Erro de upload com mensagem pronta para exibir no formulario. */
    public static class ImagemInvalidaException extends RuntimeException {
        public ImagemInvalidaException(String mensagem) {
            super(mensagem);
        }
    }
}
