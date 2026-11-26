package cloud.lemonslice.charplugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class DictionaryServiceTest {
    private DictionaryService service;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, ConfigMap> database = new HashMap<>();

    @BeforeEach
    void setup() throws Exception {
        ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
        when(client.fetch(eq(ConfigMap.class), anyString())).thenAnswer(call -> Mono.justOrEmpty(database.get(call.getArgument(1))));
        when(client.create(any(ConfigMap.class))).thenAnswer(call -> {
            ConfigMap config = call.getArgument(0);
            config.getMetadata().setVersion(0L);
            config.getMetadata().setCreationTimestamp(java.time.Instant.parse("2026-11-26T00:00:00Z"));
            database.put(config.getMetadata().getName(), mapper.convertValue(config, ConfigMap.class));
            return Mono.just(config);
        });
        when(client.update(any(ConfigMap.class))).thenAnswer(call -> {
            ConfigMap config = call.getArgument(0);
            ConfigMap current = database.get(config.getMetadata().getName());
            if (!current.getMetadata().getVersion().equals(config.getMetadata().getVersion())) return Mono.error(new OptimisticLockingFailureException("version"));
            config.getMetadata().setVersion(config.getMetadata().getVersion() + 1);
            database.put(config.getMetadata().getName(), mapper.convertValue(config, ConfigMap.class));
            return Mono.just(config);
        });
        service = new DictionaryService(client, mapper);
    }

    @Test
    void baselinePreservesForkDataAndHasNoStartupWrites() {
        var simplified = service.dictionary("v2s").block();
        var traditional = service.dictionary("v2t").block();
        assertEquals(6564, simplified.size());
        assertEquals(6766, traditional.size());
        assertEquals(List.of("团"), simplified.get("圑"));
        assertEquals(List.of("團"), traditional.get("圑"));
        for (String option : List.of("v2s", "v2t")) {
            var mapping = service.dictionary(option).block();
            for (String character : List.of("壹", "拾", "柒", "拿", "机", "砂")) assertFalse(mapping.containsKey(character));
            assertEquals(27, service.overrides(option).block().entries().size());
        }
        assertTrue(database.isEmpty());
    }

    @Test
    void modificationsPersistIndependentlyAndCanBeDisabledAndReset() throws Exception {
        var request = new DictionaryService.ChangeRequest("𠀀", "override", List.of("甲", "乙"), "修正", null);
        assertEquals(0L, service.change("v2s", request, "owner").block().version());
        assertEquals(List.of("甲", "乙"), service.dictionary("v2s").block().get("𠀀"));
        service.change("v2s", new DictionaryService.ChangeRequest("𠀀", "disable", null, "保留原字", 0L), "owner").block();
        assertFalse(service.dictionary("v2s").block().containsKey("𠀀"));
        assertEquals("owner", service.history("v2s").block().getFirst().updatedBy());
        assertEquals(2, service.history("v2s").block().size());
        var initial = service.overrides("v2s").block();
        assertTrue(initial.entries().get("壹").disabled());
        service.change("v2s", new DictionaryService.ChangeRequest("𠀀", "reset", null, "", 1L), "owner").block();
        assertFalse(service.overrides("v2s").block().entries().containsKey("𠀀"));
    }

    @Test
    void staleVersionCannotOverwriteNewCorrection() {
        service.change("v2s", new DictionaryService.ChangeRequest("圑", "override", List.of("甲"), "", null), "owner").block();
        var failure = assertThrows(ResponseStatusException.class, () -> service.change("v2s",
            new DictionaryService.ChangeRequest("圑", "override", List.of("乙"), "", null), "owner").block());
        assertEquals(409, failure.getStatusCode().value());
        assertEquals(List.of("甲"), service.dictionary("v2s").block().get("圑"));
    }

    @Test
    void concurrentFirstSaveAlsoReturnsAConflict() throws Exception {
        var client = mock(ReactiveExtensionClient.class);
        var existing = new ConfigMap();
        existing.setMetadata(new Metadata());
        existing.getMetadata().setVersion(0L);
        when(client.fetch(eq(ConfigMap.class), anyString())).thenReturn(Mono.empty(), Mono.just(existing));
        when(client.create(any(ConfigMap.class))).thenReturn(Mono.error(new RuntimeException("concurrent create")));
        var dictionary = new DictionaryService(client, mapper);
        var failure = assertThrows(ResponseStatusException.class, () -> dictionary.change("v2s",
            new DictionaryService.ChangeRequest("圑", "override", List.of("团"), "", null), "owner").block());
        assertEquals(409, failure.getStatusCode().value());
    }

    @Test
    void invalidInputIsRejectedAndSupplementaryPlaneCharactersAreAccepted() {
        for (var request : List.of(
            new DictionaryService.ChangeRequest("两个", "override", List.of("甲"), "", null),
            new DictionaryService.ChangeRequest("壹", "override", List.of(), "", null),
            new DictionaryService.ChangeRequest("壹", null, List.of("一"), "", null),
            new DictionaryService.ChangeRequest("神", "override", List.of("神"), "", null))) {
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> DictionaryService.validate(request)).getStatusCode().value());
        }
        assertDoesNotThrow(() -> DictionaryService.validate(new DictionaryService.ChangeRequest("𠀀", "override", List.of("一"), "", null)));
    }

    @Test
    void hexadecimalEditsAreSavedAsActualUnicodeCharacters() {
        service.change("v2s", new DictionaryService.ChangeRequest("0x20000", "override", List.of("U+4E00", "4e8c"), "Unicode 编辑", null), "owner").block();
        assertEquals(List.of("一", "二"), service.dictionary("v2s").block().get("𠀀"));
        assertFalse(service.dictionary("v2s").block().containsKey("0x20000"));
        assertEquals("一", DictionaryService.unicodeInput("4E00"));
        assertEquals("A", DictionaryService.unicodeInput("A"));
        for (String invalid : List.of("U+110000", "U+D800", "0xDFFF")) {
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> DictionaryService.unicodeInput(invalid)).getStatusCode().value());
        }
    }
}
