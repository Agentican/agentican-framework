package ai.agentican.quarkus.store.jpa;

import ai.agentican.framework.knowledge.KnowledgeEntry;
import ai.agentican.framework.knowledge.KnowledgeFact;
import ai.agentican.framework.knowledge.KnowledgeStatus;
import ai.agentican.framework.knowledge.KnowledgeStore;
import ai.agentican.framework.util.Json;
import ai.agentican.quarkus.store.jpa.entity.KnowledgeEntryEntity;
import ai.agentican.quarkus.store.jpa.entity.KnowledgeFactEntity;

import com.fasterxml.jackson.core.type.TypeReference;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
@IfBuildProperty(name = "agentican.store.backend", stringValue = "jpa", enableIfMissing = true)
public class JpaKnowledgeStore implements KnowledgeStore {

    private static final Logger LOG = LoggerFactory.getLogger(JpaKnowledgeStore.class);
    private static final TypeReference<List<String>> TAGS_TYPE = new TypeReference<>() {};

    @Override
    @Transactional
    public void save(KnowledgeEntry entry) {

        var existing = (KnowledgeEntryEntity) KnowledgeEntryEntity.findById(entry.id());

        var e = existing != null ? existing : new KnowledgeEntryEntity();

        if (existing == null) {
            e.id = entry.id();
            e.createdAt = entry.created() != null ? entry.created() : Instant.now();
        }

        e.name = entry.name();
        e.description = entry.description() != null ? entry.description() : "";
        e.status = entry.status() != null ? entry.status().name() : KnowledgeStatus.INDEXING.name();
        e.updatedAt = entry.updated() != null ? entry.updated() : Instant.now();

        e.persist();

        KnowledgeFactEntity.delete("entryId", entry.id());

        for (var fact : entry.facts()) {

            var f = new KnowledgeFactEntity();
            f.id = fact.id();
            f.entryId = entry.id();
            f.name = fact.name();
            f.content = fact.content() != null ? fact.content() : "";
            f.tagsJson = writeTagsJson(fact.tags());
            f.createdAt = fact.created() != null ? fact.created() : Instant.now();
            f.updatedAt = fact.updated() != null ? fact.updated() : f.createdAt;
            f.persist();
        }
    }

    @Override
    @Transactional
    public KnowledgeEntry get(String entryId) {

        var e = (KnowledgeEntryEntity) KnowledgeEntryEntity.findById(entryId);
        return e != null ? toDomain(e) : null;
    }

    @Override
    @Transactional
    public List<KnowledgeEntry> all() {

        List<KnowledgeEntryEntity> rows = KnowledgeEntryEntity.listAll();

        return rows.stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public List<KnowledgeEntry> indexed() {

        List<KnowledgeEntryEntity> rows = KnowledgeEntryEntity.list("status", KnowledgeStatus.INDEXED.name());

        return rows.stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void delete(String entryId) {

        KnowledgeFactEntity.delete("entryId", entryId);
        KnowledgeEntryEntity.deleteById(entryId);
    }

    private KnowledgeEntry toDomain(KnowledgeEntryEntity e) {

        var status = parseStatus(e.status);
        var entry = new KnowledgeEntry(e.id, e.name, e.description, status, e.createdAt, e.updatedAt);

        List<KnowledgeFactEntity> factRows = KnowledgeFactEntity.list("entryId", e.id);

        for (var f : factRows)
            entry.addFact(new KnowledgeFact(f.id, f.name, f.content, parseTags(f.tagsJson),
                    f.createdAt, f.updatedAt));

        return entry;
    }

    private static KnowledgeStatus parseStatus(String s) {

        if (s == null) return KnowledgeStatus.INDEXING;
        try { return KnowledgeStatus.valueOf(s); }
        catch (IllegalArgumentException ex) { return KnowledgeStatus.INDEXING; }
    }

    private static String writeTagsJson(List<String> tags) {

        if (tags == null || tags.isEmpty()) return "[]";
        try { return Json.writeValueAsString(tags); }
        catch (Exception ex) {
            LOG.warn("Failed to serialize tags: {}", ex.getMessage());
            return "[]";
        }
    }

    private static List<String> parseTags(String json) {

        if (json == null || json.isBlank()) return List.of();
        try { return Json.mapper().readValue(json, TAGS_TYPE); }
        catch (Exception ex) {
            LOG.warn("Failed to parse tags JSON: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }
}
