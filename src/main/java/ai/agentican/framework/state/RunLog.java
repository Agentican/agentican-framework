package ai.agentican.framework.state;

import ai.agentican.framework.llm.TokenUsage;
import ai.agentican.framework.util.Ids;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RunLog {

    private final String id;
    private final int index;
    private final String agentName;
    private final List<TurnLog> turns;

    public RunLog(String id, int index, String agentName) {

        this.id = id;
        this.index = index;
        this.agentName = agentName;
        this.turns = new CopyOnWriteArrayList<>();
    }

    public RunLog(int index, List<TurnLog> turns) {

        this.id = Ids.generate();
        this.index = index;
        this.agentName = null;
        this.turns = new CopyOnWriteArrayList<>(turns != null ? turns : List.of());
    }

    public void addTurn(TurnLog turn) { turns.add(turn); }

    public String id() { return id; }
    public int index() { return index; }
    public String agentName() { return agentName; }
    public List<TurnLog> turns() { return List.copyOf(turns); }

    public TurnLog lastTurn() { return turns.isEmpty() ? null : turns.getLast(); }
    public Instant startedAt() {
        return turns.isEmpty() ? null : turns.getFirst().startedAt();
    }
    public Instant completedAt() {
        return turns.isEmpty() ? null : turns.getLast().completedAt();
    }

    public TokenUsage tokenUsage() {

        return TokenUsage.sum(turns.stream()
                .filter(t -> t.response() != null)
                .map(t -> t.response().tokenUsage()));
    }

    public long inputTokens() { return tokenUsage().input(); }
    public long outputTokens() { return tokenUsage().output(); }
    public long cacheReadTokens() { return tokenUsage().cacheRead(); }
    public long cacheWriteTokens() { return tokenUsage().cacheWrite(); }
    public long webSearchRequests() { return tokenUsage().webSearches(); }
}
