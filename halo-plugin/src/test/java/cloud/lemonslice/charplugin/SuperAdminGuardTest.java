package cloud.lemonslice.charplugin;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import run.halo.app.core.user.service.RoleService;

class SuperAdminGuardTest {
    @Test
    void onlyTheActualSuperRolePasses() {
        var roles = mock(RoleService.class);
        var guard = new SuperAdminGuard(roles);
        for (String role : List.of("guest", "author", "contributor", "admin")) {
            when(roles.getRolesByUsername("user")).thenReturn(Flux.just(role));
            StepVerifier.create(guard.requireSuperAdmin().contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new UsernamePasswordAuthenticationToken("user", "", List.of()))))
                .expectErrorSatisfies(error -> assertEquals(403, ((ResponseStatusException) error).getStatusCode().value())).verify();
        }
        when(roles.getRolesByUsername("user")).thenReturn(Flux.just("super-role"));
        StepVerifier.create(guard.requireSuperAdmin().contextWrite(ReactiveSecurityContextHolder.withAuthentication(
            new UsernamePasswordAuthenticationToken("user", "", List.of())))).expectNext("user").verifyComplete();
        StepVerifier.create(guard.requireSuperAdmin()).expectError(ResponseStatusException.class).verify();
    }
}
