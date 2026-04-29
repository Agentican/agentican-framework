package ai.agentican.framework.vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class RecordingVectorStore implements VectorStore {

    public final List<VectorRecord> upserts  = new ArrayList<>();
    public final List<DeleteCall>   deletes  = new ArrayList<>();
    public final List<SearchCall>   searches = new ArrayList<>();

    private final int dimensions;

    private List<VectorHit> stubbedHits = List.of();

    public RecordingVectorStore(int dimensions) {

        this.dimensions = dimensions;
    }

    public void stubHits(List<VectorHit> hits) {

        this.stubbedHits = List.copyOf(hits);
    }

    @Override
    public void upsert(List<VectorRecord> records) {

        upserts.addAll(records);
    }

    @Override
    public List<VectorHit> search(float[] queryVector, int k) {

        searches.add(new SearchCall(queryVector, k));
        return stubbedHits.stream().limit(k).toList();
    }

    @Override
    public void delete(Collection<String> ids) {

        deletes.add(new DeleteCall(List.copyOf(ids)));
    }

    @Override
    public int dimensions() { return dimensions; }

    public record SearchCall(float[] vector, int k) { }

    public record DeleteCall(List<String> ids) { }
}
