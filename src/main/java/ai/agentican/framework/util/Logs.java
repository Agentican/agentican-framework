package ai.agentican.framework.util;

public class Logs {

    // Agentican
    public static final String AGENTICAN_INIT = "Initialized: {} llms, {} toolkits, {} agents, {} plans";
    public static final String AGENTICAN_DEL_TASK = "Delegating task";
    public static final String AGENTICAN_DEL_TASK_FULL = "Task: {}";
    public static final String AGENTICAN_BUILT_AGENT = "Built agent: {}";

    // PlannerAgent
    public static final String PLANNER_CREATING = "Creating task plan";
    public static final String PLANNER_SEND_LLM = "Sending LLM request";
    public static final String PLANNER_RECD_LLM = "Received LLM response";
    public static final String PLANNER_PLAN_CREATED = "Created task plan: {} stepConfigs, {} new agent(s)";
    public static final String PLANNER_REFINE_STEP = "Refining task step: type={}, name={}, toolkits={}, tools={}";
    public static final String PLANNER_PLAN = "Task task: {}";

    // TaskRunner
    public static final String RUNNER_TASK_RUNNING = "Running task: {}";
    public static final String RUNNER_TASK_CANCELLED = "Task cancelled";
    public static final String RUNNER_DISPATCH_NODE = "Dispatching task step: name={}";
    public static final String RUNNER_RUN_AGENT_STEP = "Running task step: name={}, type=agent";
    public static final String RUNNER_RUN_LOOP_STEP = "Running task step: name={}, type=loop, items={}";
    public static final String RUNNER_RUN_LOOP_STEP_ITEM = "Running task step loop: name={}, item={}";
    public static final String RUNNER_STEP_FINISHED = "Finished task step: name={}, status={}";
    public static final String RUNNER_STEP_FAILED = "Task step failed, halting task";
    public static final String RUNNER_TASK_FAILED = "Task failed, deadlocked stepConfigs={}";
    public static final String RUNNER_TASK_COMPLETED = "Finished task";

    // SmackAgentRunner
    public static final String AGENT_RUNNING_STEP = "Running task step: agent={}, skills={}, tools={}";
    public static final String AGENT_RUNNING_STEP_FULL = "Task step: {}";
    public static final String AGENT_RUNNING_LOOP = "[Turn {}] Running agent loop";
    public static final String AGENT_SEND_LLM = "[Turn {}] Sending LLM request";
    public static final String AGENT_RECD_LLM = "[Turn {}] Received LLM response: {}";
    public static final String AGENT_TOOL_USE = "[Turn {}] Using tool: {}";
}
