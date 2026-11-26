package cloud.lemonslice.charplugin;

import java.util.Set;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CharWebRouter {
    private final DictionaryService service;
    private final SuperAdminGuard guard;
    public CharWebRouter(DictionaryService service, SuperAdminGuard guard) { this.service = service; this.guard = guard; }
    private static final Set<String> FILES = Set.of("index.html", "styles.css", "app.js", "appearance.js", "charConverter.js", "img/lemon.svg", "LICENSE", "NOTICE.md");

    @Bean
    public RouterFunction<ServerResponse> charPublicRoutes() {
        return RouterFunctions.route()
            .GET("/tools/char/access", request -> guard.requireSuperAdmin().map(actor -> true)
                .onErrorResume(ResponseStatusException.class, error -> error.getStatusCode().value() == 403 ? Mono.just(false) : Mono.error(error))
                .flatMap(canManage -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore())
                    .bodyValue(Map.of("canManage", canManage))))
            .GET("/tools/char", request -> file("index.html"))
            .GET("/tools/char/char_converter/data/{option}.json", request -> service.dictionary(request.pathVariable("option"))
                .flatMap(value -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noCache()).bodyValue(value)))
            .GET("/tools/char/", request -> file("index.html"))
            .GET("/tools/char/{*path}", request -> file(request.pathVariable("path").replaceFirst("^/", "")))
            .build();
    }

    private Mono<ServerResponse> file(String name) {
        if (!FILES.contains(name)) return ServerResponse.notFound().build();
        MediaType type = name.endsWith(".html") ? MediaType.TEXT_HTML : name.endsWith(".css")
            ? MediaType.valueOf("text/css") : name.endsWith(".js") ? MediaType.valueOf("text/javascript")
            : name.endsWith(".svg") ? MediaType.valueOf("image/svg+xml") : MediaType.TEXT_PLAIN;
        return ServerResponse.ok().contentType(type).cacheControl(CacheControl.noCache())
            .header("X-Content-Type-Options", "nosniff")
            .body(BodyInserters.fromResource(new ClassPathResource("web/" + name, CharWebRouter.class.getClassLoader())));
    }
}
