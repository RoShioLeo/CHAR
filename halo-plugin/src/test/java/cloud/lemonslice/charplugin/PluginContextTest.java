package cloud.lemonslice.charplugin;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.ReactiveExtensionClient;

class PluginContextTest {
    @Test
    void startsWithPluginSharedServicesWithoutAnObjectMapperBean() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ReactiveExtensionClient.class, () -> mock(ReactiveExtensionClient.class));
            context.registerBean(RoleService.class, () -> mock(RoleService.class));
            context.register(DictionaryService.class, SuperAdminGuard.class, DictionaryEndpoint.class, CharWebRouter.class);
            context.refresh();
            assertNotNull(context.getBean(DictionaryService.class));
            assertNotNull(context.getBean(DictionaryEndpoint.class));
        }
    }
}
