package cloud.lemonslice.charplugin;

import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class EndpointSecurityTest {
    @Test
    void everyAdministrativeEndpointRejectsBeforeReadingOrWritingData() {
        var service = mock(DictionaryService.class);
        var guard = mock(SuperAdminGuard.class);
        when(guard.requireSuperAdmin()).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)));
        var client = WebTestClient.bindToRouterFunction(new DictionaryEndpoint(service, guard).endpoint()).build();
        for (String path : List.of("/dictionaries/v2s", "/dictionaries/v2s/history", "/dictionaries/v2s/export")) {
            client.get().uri(path).exchange().expectStatus().isForbidden();
        }
        client.put().uri("/dictionaries/v2s/entries").contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isForbidden();
        verifyNoInteractions(service);
    }
}
