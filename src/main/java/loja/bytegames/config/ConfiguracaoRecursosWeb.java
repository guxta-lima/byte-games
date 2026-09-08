package loja.bytegames.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.time.Duration;

/**
 * Expoe a pasta de uploads em /imagens/**. A pasta fica fora do classpath, entao
 * sem este mapeamento os arquivos enviados sao inalcancaveis por HTTP.
 */
@Configuration
public class ConfiguracaoRecursosWeb implements WebMvcConfigurer {

    private final String diretorioUploads;

    public ConfiguracaoRecursosWeb(
            @Value("${bytegames.uploads.diretorio:uploads}") String diretorioUploads) {
        this.diretorioUploads = diretorioUploads;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registro) {
        String caminho = Paths.get(diretorioUploads).toAbsolutePath().normalize().toUri().toString();
        registro.addResourceHandler("/imagens/**")
                .addResourceLocations(caminho)
                // Os nomes sao UUID: o conteudo de cada URL nunca muda, entao
                // cachear por um ano e seguro.
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).immutable());
    }
}
