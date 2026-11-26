// SPDX-License-Identifier: MIT
package cloud.lemonslice.charplugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
public class DictionaryService {
    private final ReactiveExtensionClient client;
    private final ObjectMapper mapper;
    private final Map<String, Map<String, List<String>>> bases = new LinkedHashMap<>();
    private final Map<String, Map<String, List<String>>> seeds;

    @Autowired
    public DictionaryService(ReactiveExtensionClient client) throws IOException {
        this(client, new ObjectMapper());
    }

    DictionaryService(ReactiveExtensionClient client, ObjectMapper mapper) throws IOException {
        this.client = client;
        this.mapper = mapper;
        var loader = DictionaryService.class.getClassLoader();
        for (var option : List.of("v2s", "v2t")) {
            try (var stream = loader.getResourceAsStream("data/" + option + ".json")) {
                bases.put(option, mapper.readValue(stream, new TypeReference<>() {}));
            }
        }
        try (var stream = loader.getResourceAsStream("data/fork-overrides.json")) {
            seeds = mapper.readValue(stream, new TypeReference<>() {});
        }
    }

    public record Entry(List<String> candidates, boolean disabled, String note, String updatedBy, String updatedAt) {}
    public record Audit(String character, String action, List<String> before, List<String> after,
                        String note, String updatedBy, String updatedAt) {}
    public record State(Map<String, Entry> entries, List<Audit> history) {}
    public record ChangeRequest(String character, String action, List<String> candidates, String note, Long version) {}
    public record Saved(Long version) {}
    public record Row(String character, List<String> candidates, List<String> baseCandidates,
                      String source, String note, String updatedBy, String updatedAt) {}
    public record Page(List<Row> items, int total, int page, int size, Long version,
                       int effectiveCount, int changedCount) {}
    private record Snapshot(ConfigMap config, State state) {}

    private void checkOption(String option) {
        if (!bases.containsKey(option)) throw badRequest("请选择简体或繁体字库");
    }

    private State seed(String option) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        seeds.get(option).forEach((character, candidates) -> entries.put(character,
            new Entry(candidates, candidates == null, "已保留的字形修正", "RoShioLeo", null)));
        return new State(entries, new ArrayList<>());
    }

    private Mono<Snapshot> snapshot(String option) {
        checkOption(option);
        var empty = new ConfigMap();
        empty.setMetadata(new Metadata());
        empty.getMetadata().setName("lemonslice-char-" + option);
        empty.setData(new LinkedHashMap<>());
        return client.fetch(ConfigMap.class, empty.getMetadata().getName()).defaultIfEmpty(empty)
            .map(config -> {
                try {
                    String json = config.getData() == null ? null : config.getData().get("dictionary");
                    State state = json == null ? seed(option) : mapper.readValue(json, State.class);
                    return new Snapshot(config, state);
                } catch (IOException exception) { throw new IllegalStateException("字库修正记录无法读取", exception); }
            });
    }

    private Map<String, List<String>> effective(String option, State state) {
        var result = new TreeMap<>(bases.get(option));
        state.entries().forEach((character, entry) -> {
            if (entry.disabled()) result.remove(character);
            else result.put(character, entry.candidates());
        });
        return result;
    }

    public Mono<Map<String, List<String>>> dictionary(String option) {
        return snapshot(option).map(snapshot -> effective(option, snapshot.state()));
    }

    public Mono<Page> list(String option, String query, String source, int page, int size) {
        if (page < 1 || size < 1 || size > 100 || query.length() > 100) throw badRequest("查询参数无效");
        return snapshot(option).map(snapshot -> {
            var state = snapshot.state();
            var mapping = effective(option, state);
            var characters = new TreeSet<>(bases.get(option).keySet());
            characters.addAll(state.entries().keySet());
            var rows = new ArrayList<Row>();
            for (String character : characters) {
                Entry entry = state.entries().get(character);
                if ("changed".equals(source) && entry == null) continue;
                var candidates = mapping.get(character);
                String code = "U+" + Integer.toHexString(character.codePointAt(0)).toUpperCase();
                if (!query.isEmpty() && !character.contains(query) && !code.contains(query.toUpperCase())
                    && (candidates == null || candidates.stream().noneMatch(value -> value.contains(query)))) continue;
                rows.add(new Row(character, candidates, bases.get(option).get(character),
                    entry == null ? "base" : entry.disabled() ? "disabled" : "changed",
                    entry == null ? "" : entry.note(), entry == null ? null : entry.updatedBy(),
                    entry == null ? null : entry.updatedAt()));
            }
            int start = (int) Math.min((long) (page - 1) * size, rows.size());
            return new Page(rows.subList(start, Math.min(start + size, rows.size())), rows.size(), page, size,
                snapshot.config().getMetadata().getVersion(), mapping.size(), state.entries().size());
        });
    }

    public Mono<List<Audit>> history(String option) {
        return snapshot(option).map(snapshot -> {
            var items = new ArrayList<>(snapshot.state().history());
            Collections.reverse(items);
            return items;
        });
    }

    public Mono<State> overrides(String option) { return snapshot(option).map(Snapshot::state); }

    public Mono<Saved> change(String option, ChangeRequest request, String actor) {
        return changeNormalized(option, new ChangeRequest(unicodeInput(request.character()), request.action(),
            request.candidates() == null ? null : request.candidates().stream().map(DictionaryService::unicodeInput).toList(),
            request.note(), request.version()), actor);
    }

    private Mono<Saved> changeNormalized(String option, ChangeRequest request, String actor) {
        validate(request);
        return snapshot(option).flatMap(snapshot -> {
            if (!Objects.equals(request.version(), snapshot.config().getMetadata().getVersion())) {
                return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "字库已被修改，请刷新后重新确认"));
            }
            var entries = new LinkedHashMap<>(snapshot.state().entries());
            var history = new ArrayList<>(snapshot.state().history());
            String character = request.character();
            var before = effective(option, snapshot.state()).get(character);
            String now = Instant.now().toString();
            String note = request.note() == null ? "" : request.note().trim();
            if ("reset".equals(request.action())) entries.remove(character);
            else entries.put(character, new Entry("disable".equals(request.action()) ? null : request.candidates().stream().map(String::trim).distinct().toList(),
                "disable".equals(request.action()), note, actor, now));
            var next = new State(entries, history);
            history.add(new Audit(character, request.action(), before, effective(option, next).get(character), note, actor, now));
            var config = new ConfigMap();
            var metadata = new Metadata();
            BeanUtils.copyProperties(snapshot.config().getMetadata(), metadata);
            config.setMetadata(metadata);
            try { config.setData(new LinkedHashMap<>(Map.of("dictionary", mapper.writeValueAsString(next)))); }
            catch (IOException exception) { return Mono.error(exception); }
            var saved = config.getMetadata().getVersion() == null ? client.create(config) : client.update(config);
            return saved.map(value -> new Saved(value.getMetadata().getVersion()))
                .onErrorResume(error -> client.fetch(ConfigMap.class, config.getMetadata().getName())
                    .flatMap(current -> !Objects.equals(current.getMetadata().getVersion(), request.version())
                        ? Mono.<Saved>error(new ResponseStatusException(HttpStatus.CONFLICT, "字库已被修改，请刷新后重新确认", error))
                        : Mono.<Saved>error(error))
                    .switchIfEmpty(Mono.error(error)));
        });
    }

    static void validate(ChangeRequest request) {
        if (request.character() == null || request.character().codePointCount(0, request.character().length()) != 1
            || request.character().isBlank() || Character.isISOControl(request.character().codePointAt(0))) {
            throw badRequest("原字必须是一个字符");
        }
        if (!Normalizer.isNormalized(request.character(), Normalizer.Form.NFC)) {
            throw badRequest("请编辑对应的规范字：" + Normalizer.normalize(request.character(), Normalizer.Form.NFC));
        }
        if (request.action() == null || !List.of("override", "disable", "reset").contains(request.action())) throw badRequest("操作无效");
        if (request.note() != null && request.note().length() > 300) throw badRequest("备注最多 300 字");
        if ("override".equals(request.action()) && (request.candidates() == null || request.candidates().isEmpty()
            || request.candidates().size() > 50 || request.candidates().stream().anyMatch(value -> value == null || value.isBlank() || value.codePointCount(0, value.length()) > 16))) {
            throw badRequest("请输入候选字，每行一个，最多 50 个候选");
        }
    }

    static String unicodeInput(String input) {
        if (input == null) return null;
        String value = input.trim();
        String hex = null;
        if (value.matches("(?i)^(U\\+|0x)[0-9a-f]{1,6}$")) hex = value.substring(2);
        else if (value.matches("(?i)^[0-9a-f]{4,6}$")) hex = value;
        if (hex == null) return value;
        int code = Integer.parseInt(hex, 16);
        if (code > Character.MAX_CODE_POINT || (code >= 0xD800 && code <= 0xDFFF)) throw badRequest("请输入有效的 Unicode 码值");
        return new String(Character.toChars(code));
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
