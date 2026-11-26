package cloud.lemonslice.charplugin;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public class DictionaryEndpoint implements CustomEndpoint {
    private final DictionaryService service;
    private final SuperAdminGuard guard;
    public DictionaryEndpoint(DictionaryService service, SuperAdminGuard guard) { this.service = service; this.guard = guard; }

    @Override
    public GroupVersion groupVersion() { return GroupVersion.parseAPIVersion("console.api.char.lemonslice.cloud/v1alpha1"); }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("/dictionaries/{option}", request -> guard.requireSuperAdmin().flatMap(actor ->
                service.list(request.pathVariable("option"), request.queryParam("query").orElse("").trim(),
                    request.queryParam("source").orElse("all"), number(request, "page", 1), number(request, "size", 50)))
                .flatMap(value -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(value)))
            .PUT("/dictionaries/{option}/entries", request -> guard.requireSuperAdmin().flatMap(actor ->
                request.bodyToMono(DictionaryService.ChangeRequest.class).flatMap(body -> service.change(request.pathVariable("option"), body, actor)))
                .flatMap(value -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(value)))
            .GET("/dictionaries/{option}/history", request -> guard.requireSuperAdmin().flatMap(actor -> service.history(request.pathVariable("option")))
                .flatMap(value -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(value)))
            .GET("/dictionaries/{option}/export", request -> guard.requireSuperAdmin().flatMap(actor ->
                "overrides".equals(request.queryParam("kind").orElse("merged"))
                    ? service.overrides(request.pathVariable("option")).cast(Object.class)
                    : service.dictionary(request.pathVariable("option")).cast(Object.class))
                .flatMap(value -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(value)))
            .build();
    }

    private int number(ServerRequest request, String name, int fallback) {
        try { return Integer.parseInt(request.queryParam(name).orElse(Integer.toString(fallback))); }
        catch (NumberFormatException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效"); }
    }
}
