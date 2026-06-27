package phucuong.ht.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class SwaggerUiTokenConfig {

    private static final String SWAGGER_TOKEN_SCRIPT_TAG =
            "<script src=\"/swagger-auth-token.js\"></script>";

    @Bean
    @Primary
    public SwaggerIndexTransformer swaggerIndexTransformer(
            SwaggerUiConfigProperties swaggerUiConfigProperties,
            SwaggerUiOAuthProperties swaggerUiOAuthProperties,
            SwaggerWelcomeCommon swaggerWelcomeCommon,
            ObjectMapperProvider objectMapperProvider
    ) {
        return new SwaggerIndexPageTransformer(
                swaggerUiConfigProperties,
                swaggerUiOAuthProperties,
                swaggerWelcomeCommon,
                objectMapperProvider
        ) {
            @Override
            public Resource transform(
                    HttpServletRequest request,
                    Resource resource,
                    ResourceTransformerChain transformerChain
            ) throws IOException {
                Resource transformed = super.transform(request, resource, transformerChain);
                String requestPath = request.getRequestURI();
                if (!requestPath.endsWith("/swagger-ui/index.html") && !requestPath.equals("/swagger-ui.html")) {
                    return transformed;
                }

                String html = new String(transformed.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                if (!html.contains(SWAGGER_TOKEN_SCRIPT_TAG)) {
                    html = html.replace("</body>", SWAGGER_TOKEN_SCRIPT_TAG + "</body>");
                }

                return new TransformedResource(transformed, html.getBytes(StandardCharsets.UTF_8));
            }
        };
    }
}
