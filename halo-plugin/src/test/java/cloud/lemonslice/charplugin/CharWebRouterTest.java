package cloud.lemonslice.charplugin;

import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

class CharWebRouterTest {
    @Test
    void publicPageServesOnlyAllowlistedAssetsAndEffectiveMappings() {
        var service = mock(DictionaryService.class);
        when(service.dictionary("v2s")).thenReturn(Mono.just(Map.of("圑", List.of("团"))));
        var guard = mock(SuperAdminGuard.class);
        when(guard.requireSuperAdmin()).thenReturn(Mono.error(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)));
        var client = WebTestClient.bindToRouterFunction(new CharWebRouter(service, guard).charPublicRoutes()).build();
        client.get().uri("/tools/char/access").exchange().expectStatus().isOk().expectHeader().valueEquals("Cache-Control", "no-store").expectBody().jsonPath("$.canManage").isEqualTo(false);
        when(guard.requireSuperAdmin()).thenReturn(Mono.just("owner"));
        client.get().uri("/tools/char/access").exchange().expectStatus().isOk().expectBody().jsonPath("$.canManage").isEqualTo(true);
        client.get().uri("/tools/char").exchange().expectStatus().isOk().expectBody(String.class).value(body -> org.junit.jupiter.api.Assertions.assertTrue(body.contains("<base href=\"/tools/char/\">")));
        client.get().uri("/tools/char/").exchange().expectStatus().isOk().expectBody(String.class).value(body -> org.junit.jupiter.api.Assertions.assertTrue(body.contains("默认转换")));
        client.get().uri("/tools/char/styles.css").exchange().expectStatus().isOk().expectHeader().contentType("text/css");
        client.get().uri("/tools/char/char_converter/data/v2s.json").exchange().expectStatus().isOk().expectBody().jsonPath("$.圑[0]").isEqualTo("团");
        client.get().uri("/tools/char/NOTICE.md").exchange().expectStatus().isOk();
        client.get().uri("/tools/char/LICENSES.md").exchange().expectStatus().isOk()
            .expectHeader().contentType("text/plain;charset=UTF-8").expectBody(String.class)
            .value(body -> org.junit.jupiter.api.Assertions.assertTrue(body.contains("许可范围")));
        client.get().uri("/tools/char/data/fork-overrides.json").exchange().expectStatus().isNotFound();
        client.get().uri("/tools/char/.git/config").exchange().expectStatus().isNotFound();
    }
}
