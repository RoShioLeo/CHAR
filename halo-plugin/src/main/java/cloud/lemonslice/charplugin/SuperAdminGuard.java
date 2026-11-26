package cloud.lemonslice.charplugin;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.user.service.RoleService;

@Component
public class SuperAdminGuard {
    private final RoleService roles;
    public SuperAdminGuard(RoleService roles) { this.roles = roles; }

    public Mono<String> requireSuperAdmin() {
        return ReactiveSecurityContextHolder.getContext()
            .map(context -> context.getAuthentication())
            .filter(auth -> auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
            .flatMap(auth -> roles.getRolesByUsername(auth.getName()).hasElement("super-role")
                .filter(Boolean::booleanValue).map(allowed -> auth.getName()))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超级管理员可以管理字库")));
    }
}
