package ai.agentican.framework.vector.code;

import ai.agentican.framework.vector.VectorIndexRegistry;
import ai.agentican.framework.orchestration.code.CodeStep;
import ai.agentican.framework.orchestration.code.StepContext;

import java.util.stream.Collectors;

public final class RetrieveCodeStep implements CodeStep<RetrieveQuery, RetrieveOutput> {

    public static final String SLUG = "retrieve";

    public static final String DESCRIPTION =
            "Retrieve top-k hits from a registered vector index by semantic similarity.";

    public static final String HIT_SEPARATOR = "\n\n---\n\n";

    private final VectorIndexRegistry registry;

    public RetrieveCodeStep(VectorIndexRegistry registry) {

        if (registry == null)
            throw new IllegalArgumentException("VectorIndexRegistry is required");

        this.registry = registry;
    }

    @Override
    public RetrieveOutput execute(RetrieveQuery input, StepContext context) {

        var kb = registry.get(input.vectorIndex());

        if (kb == null)
            throw new IllegalStateException(
                    "Unknown vector index '" + input.vectorIndex()
                  + "'. Registered: " + registry.names());

        var hits = kb.retrieve(input.query(), input.k()).stream()
                .map(h -> new RetrieveHit(h.id(), h.score(), h.content(), h.metadata()))
                .toList();

        var formatted = hits.stream()
                .map(RetrieveHit::content)
                .collect(Collectors.joining(HIT_SEPARATOR));

        return new RetrieveOutput(hits, formatted);
    }
}
