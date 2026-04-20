package ai.agentican.quarkus.rest.resource;

import ai.agentican.framework.state.TaskLog;
import ai.agentican.framework.store.TaskStateStore;
import ai.agentican.framework.orchestration.execution.TaskStatus;
import ai.agentican.quarkus.rest.TaskEventBus;
import ai.agentican.quarkus.rest.TaskService;
import ai.agentican.quarkus.rest.dto.SubmitTaskRequest;
import ai.agentican.quarkus.rest.dto.SubmitTaskResponse;
import ai.agentican.quarkus.rest.dto.TaskLogView;
import ai.agentican.quarkus.rest.dto.TaskSummary;
import ai.agentican.quarkus.rest.dto.TurnDetailView;
import ai.agentican.quarkus.rest.sse.SequencedEvent;
import ai.agentican.quarkus.rest.sse.SseEventTypes;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Path("/agentican/tasks")
@Produces(MediaType.APPLICATION_JSON)
public class TasksResource {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    @Inject
    TaskService taskService;

    @Inject
    TaskStateStore taskStateStore;

    @Inject
    TaskEventBus eventBus;

    @Context
    Sse sse;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response submit(SubmitTaskRequest request, @Context jakarta.ws.rs.core.UriInfo uriInfo) {

        if (request == null)
            throw new jakarta.ws.rs.BadRequestException("Request body is required");

        var modeCount = (request.isPlannerMode() ? 1 : 0)
                + (request.isPreBuiltMode() ? 1 : 0)
                + (request.isPlanMode() ? 1 : 0);

        if (modeCount != 1)
            throw new jakarta.ws.rs.BadRequestException(
                    "Exactly one of 'description', 'task', or 'planId' must be provided");

        var inputs = request.inputs() != null ? request.inputs() : java.util.Map.<String, String>of();

        var handle = request.isPlannerMode()
                ? taskService.submit(request.description())
                : request.isPlanMode()
                        ? taskService.submitByPlan(request.planId(), inputs)
                        : taskService.submit(request.task(), inputs);

        var location = uriInfo.getAbsolutePathBuilder()
                .path(handle.taskId())
                .build();

        return Response.created(location)
                .entity(new SubmitTaskResponse(handle.taskId()))
                .build();
    }

    @GET
    public List<TaskSummary> list(@QueryParam("limit") Integer limit,
                                  @QueryParam("status") String status,
                                  @QueryParam("since") String since) {

        var effectiveLimit = limit == null
                ? DEFAULT_LIMIT
                : Math.min(Math.max(limit, 1), MAX_LIMIT);

        var stream = taskStateStore.list().stream()
                .sorted(Comparator.comparing(log -> log.createdAt(), Comparator.reverseOrder()));

        if (status != null && !status.isBlank()) {
            var match = status.equalsIgnoreCase("RUNNING") ? null
                    : TaskStatus.valueOf(status.toUpperCase());
            stream = stream.filter(log -> log.status() == match);
        }

        if (since != null && !since.isBlank()) {
            var sinceInstant = Instant.parse(since);
            stream = stream.filter(log -> log.createdAt().isAfter(sinceInstant));
        }

        return stream
                .limit(effectiveLimit)
                .map(TaskSummary::of)
                .toList();
    }

    @GET
    @Path("/{taskId}")
    public TaskSummary get(@PathParam("taskId") String taskId) {

        return TaskSummary.of(loadOrThrow(taskId));
    }

    @GET
    @Path("/{taskId}/log")
    public TaskLogView log(@PathParam("taskId") String taskId) {

        return TaskLogView.of(loadOrThrow(taskId));
    }

    @GET
    @Path("/{taskId}/steps/{stepName}/runs/{runIndex}/turns/{turnIndex}")
    public TurnDetailView turnDetail(@PathParam("taskId") String taskId,
                                     @PathParam("stepName") String stepName,
                                     @PathParam("runIndex") int runIndex,
                                     @PathParam("turnIndex") int turnIndex) {

        var taskLog = loadOrThrow(taskId);
        var stepLog = taskLog.steps().get(stepName);

        if (stepLog == null)
            throw new NotFoundException("No step '" + stepName + "' in task " + taskId);

        var runs = stepLog.runs();

        if (runIndex < 0 || runIndex >= runs.size())
            throw new NotFoundException("No run " + runIndex + " in step '" + stepName + "'");

        var turns = runs.get(runIndex).turns();

        if (turnIndex < 0 || turnIndex >= turns.size())
            throw new NotFoundException("No turn " + turnIndex + " in run " + runIndex);

        return TurnDetailView.of(turns.get(turnIndex));
    }

    @DELETE
    @Path("/{taskId}")
    public Response cancel(@PathParam("taskId") String taskId) {

        var handle = taskService.handleFor(taskId);

        if (handle == null)
            throw new NotFoundException("No active task with id: " + taskId);

        handle.cancel();

        return Response.noContent().build();
    }

    @GET
    @Path("/{taskId}/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<OutboundSseEvent> stream(@PathParam("taskId") String taskId,
                                          @HeaderParam("Last-Event-ID") String lastEventIdHeader,
                                          @QueryParam("lastEventId") Long lastEventIdParam) {

        var lastEventId = parseLastEventId(lastEventIdHeader, lastEventIdParam);

        var events = eventBus.stream(taskId, lastEventId).map(this::toSseEvent);

        var heartbeats = Multi.createFrom().ticks().every(Duration.ofSeconds(30))
                .onItem().transform(t -> heartbeat());

        return Multi.createBy().merging().streams(events, heartbeats);
    }

    private OutboundSseEvent toSseEvent(SequencedEvent event) {

        return sse.newEventBuilder()
                .id(String.valueOf(event.id()))
                .name(SseEventTypes.nameFor(event.payload()))
                .data(event.payload())
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }

    private OutboundSseEvent heartbeat() {

        return sse.newEventBuilder()
                .name(SseEventTypes.HEARTBEAT)
                .comment("keep-alive")
                .build();
    }

    private static long parseLastEventId(String header, Long param) {

        if (header != null && !header.isBlank()) {
            try { return Long.parseLong(header); } catch (NumberFormatException ignored) {}
        }

        return param != null ? param : -1;
    }

    private TaskLog loadOrThrow(String taskId) {

        var log = taskStateStore.load(taskId);

        if (log == null)
            throw new NotFoundException("No task with id: " + taskId);

        return log;
    }
}
