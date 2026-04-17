/* Agentican Playground — vanilla JS, no frameworks */

const API = '/agentican';

let activeTaskId = null;
let activeEventSource = null;
let activeCheckpointId = null;
let metricsInterval = null;
let traceInterval = null;
const liveSpans = new Map();
const turnContext = new Map();  // turnId → { stepName, runIndex, turnIndex }
const stepContext = new Map();  // stepId → stepName
const runContext = new Map();   // runId → { stepId, runIndex }

function shortHexId() {
  return Math.floor(Math.random() * 0xffffffff).toString(16).padStart(8, '0');
}

// === Navigation (hash-based so refresh + back/forward preserve the panel) ===

const VALID_PANELS = new Set(['tasks','plans','agents','skills','tools','knowledge','metrics','config']);

function activatePanel(panel) {
  if (!VALID_PANELS.has(panel)) panel = 'tasks';
  document.querySelectorAll('.nav-item').forEach(n => {
    n.classList.toggle('active', n.dataset.panel === panel);
  });
  document.querySelectorAll('.panel').forEach(p => {
    p.classList.toggle('active', p.id === 'panel-' + panel);
  });
  switch (panel) {
    case 'tasks': loadTasks(); break;
    case 'plans': loadPlans(); break;
    case 'agents': loadAgents(); break;
    case 'skills': loadSkills(); break;
    case 'tools': loadTools(); break;
    case 'knowledge': loadKnowledge(); break;
    case 'metrics': loadMetrics(); break;
    case 'config': loadConfig(); break;
  }
}

document.querySelectorAll('.nav-item').forEach(item => {
  item.addEventListener('click', e => {
    e.preventDefault();
    const panel = item.dataset.panel;
    if ((location.hash.slice(1) || 'tasks') === panel) {
      activatePanel(panel);
    } else {
      location.hash = panel;
    }
  });
});

window.addEventListener('hashchange', () => {
  activatePanel(location.hash.slice(1) || 'tasks');
});

// === Theme ===

function toggleTheme() {
  const html = document.documentElement;
  const next = html.dataset.theme === 'dark' ? 'light' : 'dark';
  html.dataset.theme = next;
  localStorage.setItem('theme', next);
}

(function() {
  const saved = localStorage.getItem('theme');
  if (saved) document.documentElement.dataset.theme = saved;
})();

// === Toast ===

function toast(message, type = '') {
  const el = document.getElementById('toast');
  el.textContent = message;
  el.className = 'toast show ' + type;
  setTimeout(() => el.className = 'toast', 3000);
}

// === Diagnostics tabs ===

function switchDiagTab(btn) {
  document.querySelectorAll('.btn-group-item').forEach(t => t.classList.remove('active'));
  document.querySelectorAll('.tab-events, .tab-trace, .tab-metrics, .tab-result, .tab-plan').forEach(c => c.classList.remove('active'));
  btn.classList.add('active');
  document.getElementById(btn.dataset.tab).classList.add('active');
}

function resetDiagnostics() {
  document.getElementById('diag-plan-content').innerHTML =
    '<p class="tab-placeholder">Waiting for plan...</p>';
  document.getElementById('diag-steps-content').innerHTML =
    '<p class="tab-placeholder">Trace will appear here after task execution.</p>';
  document.getElementById('diag-metrics-content').innerHTML =
    '<p class="tab-placeholder">Waiting for metrics...</p>';
  document.getElementById('diag-result-content').innerHTML =
    '<p class="tab-placeholder">Task result will appear here after completion.</p>';
}

function clearEvents() {
  document.getElementById('event-table-body').innerHTML = '';
}

function totalTokens(t) {
  return (t.inputTokens || 0) + (t.outputTokens || 0)
       + (t.cacheReadTokens || 0) + (t.cacheWriteTokens || 0);
}

// === Tasks ===

async function loadTasks() {
  try {
    const res = await fetch(API + '/tasks?limit=20');
    const tasks = await res.json();
    renderTaskTable(tasks);
  } catch (e) {}
}

function renderTaskTable(tasks) {
  document.getElementById('tasks-body').innerHTML = tasks.map(t => `
    <div class="grid-row" onclick="loadTaskDetail('${t.taskId}')" style="cursor:pointer" id="task-row-${t.taskId}">
      <div><code>${t.taskId.substring(0, 8)}</code></div>
      <div>${escapeHtml(t.taskName || '—')}</div>
      <div><span data-status="${t.status}">${t.status}</span></div>
      <div>${totalTokens(t).toLocaleString()}</div>
      <div>${t.createdAt ? new Date(t.createdAt).toLocaleTimeString() : '—'}</div>
    </div>
  `).join('');
}

function updateTaskRow(taskId, status, tokens) {
  const row = document.getElementById('task-row-' + taskId);
  if (!row) return;
  const statusEl = row.querySelector('[data-status]');
  if (statusEl) { statusEl.textContent = status; statusEl.setAttribute('data-status', status); }
  if (tokens !== undefined) {
    const cells = row.querySelectorAll(':scope > div');
    if (cells[3]) cells[3].textContent = tokens.toLocaleString();
  }
}

async function submitTask() {
  const input = document.getElementById('task-input');
  const description = input.value.trim();
  if (!description) return toast('Enter a task description', 'error');

  try {
    const res = await fetch(API + '/tasks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ description })
    });

    if (!res.ok) {
      const err = await res.json();
      return toast(err.message || 'Failed to submit', 'error');
    }

    const data = await res.json();
    activeTaskId = data.taskId;

    resetDiagnostics();
    clearEvents();

    const eventsTab = document.querySelector('[data-tab="diag-events"]');
    if (eventsTab) switchDiagTab(eventsTab);

    // Add to recent tasks immediately
    addTaskToTable(activeTaskId, 'RUNNING');

    toast('Task submitted', 'success');
    subscribeToEvents(activeTaskId);
    startMetricsPolling(activeTaskId);

  } catch (e) {
    toast('Error: ' + e.message, 'error');
  }
}

function addTaskToTable(taskId, status) {
  const tbody = document.getElementById('tasks-body');
  const row = document.createElement('div');
  row.className = 'grid-row';
  row.id = 'task-row-' + taskId;
  row.style.cursor = 'pointer';
  row.onclick = () => loadTaskDetail(taskId);
  row.innerHTML = `
    <div><code>${taskId.substring(0, 8)}</code></div>
    <div>—</div>
    <div><span data-status="${status}">${status}</span></div>
    <div>0</div>
    <div>${new Date().toLocaleTimeString()}</div>
  `;
  tbody.insertBefore(row, tbody.firstChild);
}

// === SSE ===

function subscribeToEvents(taskId) {
  if (activeEventSource) activeEventSource.close();

  const es = new EventSource(API + '/tasks/' + taskId + '/stream');
  activeEventSource = es;

  es.addEventListener('plan_started', e => {
    const data = JSON.parse(e.data);
    addEvent('plan_started', data.taskId, null, 'Planning task...');
  });

  es.addEventListener('plan_completed', e => {
    const data = JSON.parse(e.data);
    addEvent('plan_completed', data.taskId, null, `Plan: ${data.taskName}`);

    fetch(API + '/plans/' + data.planId)
      .then(r => r.json())
      .then(def => renderPlan(def.plan))
      .catch(err => console.error('Failed to fetch definition:', err));

    updateTaskRowName(taskId, data.taskName);
  });

  es.addEventListener('task_started', e => {
    const data = JSON.parse(e.data);
    addEvent('task_started', data.taskId, null, 'Task started');
  });

  es.addEventListener('step_started', e => {
    const data = JSON.parse(e.data);
    stepContext.set(data.stepId, data.stepName);
    addEvent('step_started', data.stepId, data.taskId, `Step started: ${data.stepName}`);
  });

  es.addEventListener('iteration_started', e => {
    const data = JSON.parse(e.data);
    addEvent('iteration_started', data.iterationId, data.parentStepId,
      `Iteration ${(data.index ?? 0) + 1} started: ${data.iterationName || ''}`);
  });

  es.addEventListener('iteration_completed', e => {
    const data = JSON.parse(e.data);
    addEvent('iteration_completed', data.iterationId, data.parentStepId,
      `Iteration completed (${data.status})`);
  });

  es.addEventListener('step_completed', e => {
    const data = JSON.parse(e.data);
    addEvent('step_completed', data.stepId, data.taskId, `Step completed: ${data.stepName} (${data.status})`);
    refreshTaskMetrics(taskId);
  });

  es.addEventListener('run_started', e => {
    const data = JSON.parse(e.data);
    runContext.set(data.runId, { stepId: data.stepId, runIndex: data.runIndex });
    addEvent('run_started', data.runId, data.stepId, `Run started: ${data.agentName}`);
  });

  es.addEventListener('run_completed', e => {
    const data = JSON.parse(e.data);
    addEvent('run_completed', data.runId, data.stepId, `Run completed: ${data.agentName}`);
  });

  es.addEventListener('turn_started', e => {
    const data = JSON.parse(e.data);
    const run = runContext.get(data.runId);
    const stepName = run ? stepContext.get(run.stepId) : null;
    turnContext.set(data.turnId, { stepName: stepName, runIndex: run ? run.runIndex : 0, turnIndex: data.turn });
    addEvent('turn_started', data.turnId, data.runId, `Turn started: ${data.agentName}/${data.turn}`);
  });

  es.addEventListener('turn_completed', e => {
    const data = JSON.parse(e.data);
    addEvent('turn_completed', data.turnId, data.runId, `Turn completed: ${data.agentName}/${data.turn}`);
  });

  es.addEventListener('message_sent', e => {
    const data = JSON.parse(e.data);
    addEvent('message_sent', data.messageId, data.turnId,
      `LLM request sent: ${data.agentName}/${data.turn}`, data.turnId);
  });

  es.addEventListener('response_received', e => {
    const data = JSON.parse(e.data);
    addEvent('response_received', data.responseId, data.turnId,
      `LLM response received: ${data.agentName}/${data.turn} (${data.stopReason})`, data.turnId);
  });

  es.addEventListener('tool_call_started', e => {
    const data = JSON.parse(e.data);
    addEvent('tool_call_started', data.toolCallId, data.turnId, `Tool call: ${data.toolName}`, data.turnId);
  });

  es.addEventListener('tool_call_completed', e => {
    const data = JSON.parse(e.data);
    const status = data.error ? 'FAILED' : 'OK';
    addEvent('tool_call_completed', data.toolCallId, data.turnId, `Tool result: ${data.toolName} ${status}`, data.turnId);
  });

  es.addEventListener('hitl_checkpoint', e => {
    const data = JSON.parse(e.data);
    activeCheckpointId = data.checkpoint ? data.checkpoint.id : null;
    var cpId = data.checkpoint ? data.checkpoint.id : null;
    var cpType = data.checkpoint ? data.checkpoint.type : null;
    var label = cpType === 'QUESTION' ? 'Question: ' : 'Approval needed: ';
    addEvent('hitl_checkpoint', cpId, data.stepId, label + (data.checkpoint ? data.checkpoint.description : ''));
    showHitlPrompt(data);
  });

  es.addEventListener('task_completed', e => {
    const data = JSON.parse(e.data);
    addEvent('task_completed', data.taskId, null, `Task completed (${data.status})`);
    updateTaskRow(taskId, data.status);
    es.close();
    activeEventSource = null;
    stopMetricsPolling();
    loadTaskDetail(taskId);

    // Start trace polling after task completes — BatchSpanProcessor needs time to flush.
    // Poll until we get spans, then stop.
    startTracePolling(taskId);
  });

  es.addEventListener('heartbeat', () => {});
  es.onerror = () => addEvent('connection', 'Stream disconnected');
}

function addEvent(type, id, parentId, message, clickTurnId, timestamp) {
  const container = document.getElementById('diag-events');
  const body = document.getElementById('event-table-body');
  const when = timestamp ? new Date(timestamp) : new Date();
  const time = when.toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const row = document.createElement('div');
  row.className = 'grid-row' + (clickTurnId ? ' clickable-event' : '');
  if (clickTurnId) {
    row.onclick = () => openTurnModal(clickTurnId, type);
  }
  row.innerHTML =
    `<div class="ev-time">${time}</div>` +
    `<div class="ev-type">${escapeHtml(type)}</div>` +
    `<div class="ev-id">${escapeHtml(id || 'N/A')}</div>` +
    `<div class="ev-parent">${escapeHtml(parentId || 'N/A')}</div>` +
    `<div class="ev-message">${escapeHtml(message)}</div>`;
  body.appendChild(row);
  container.scrollTop = container.scrollHeight;
}

// === Real-time step cards ===


// === Real-time metrics polling ===

function startMetricsPolling(taskId) {
  stopMetricsPolling();
  refreshTaskMetrics(taskId);
  metricsInterval = setInterval(() => refreshTaskMetrics(taskId), 3000);
}

function stopMetricsPolling() {
  if (metricsInterval) { clearInterval(metricsInterval); metricsInterval = null; }
}

// === Trace polling (post-completion) ===

var tracePollCount = 0;

function startTracePolling(taskId) {
  stopTracePolling();
  tracePollCount = 0;
  loadTraceWaterfall(taskId);
  traceInterval = setInterval(() => {
    loadTraceWaterfall(taskId);
    if (++tracePollCount >= 10) stopTracePolling();
  }, 1500);
}

function stopTracePolling() {
  if (traceInterval) { clearInterval(traceInterval); traceInterval = null; }
}

async function refreshTaskMetrics(taskId) {
  try {
    const res = await fetch(API + '/tasks/' + taskId + '/log');
    if (!res.ok) return;
    const log = await res.json();
    renderTaskMetrics(log);
    updateTaskRow(taskId, log.status || 'RUNNING', totalTokens(log));
    // Update task name in row
    const row = document.getElementById('task-row-' + taskId);
    if (row && log.taskName) { const cells = row.querySelectorAll(':scope > div'); if (cells[1]) cells[1].textContent = log.taskName; }
  } catch (e) {}
}

function renderTaskMetrics(log) {
  const el = document.getElementById('diag-metrics-content');
  // metrics content rendered
  const totalIn = log.inputTokens || 0;
  const totalOut = log.outputTokens || 0;
  const cacheRead = log.cacheReadTokens || 0;
  const cacheWrite = log.cacheWriteTokens || 0;
  const total = totalIn + totalOut + cacheRead + cacheWrite;
  const stepCount = (log.steps || []).length;
  const status = log.status || 'RUNNING';
  const statusClass = status === 'COMPLETED' ? 'success' : status === 'FAILED' ? 'danger' : '';

  const duration = log.durationMs
    ? formatDuration(log.durationMs)
    : (log.createdAt ? formatDuration(Date.now() - new Date(log.createdAt).getTime()) : 'N/A');

  const tiles = [
    { label: 'Cache Read Tokens',  value: cacheRead.toLocaleString() },
    { label: 'Cache Write Tokens', value: cacheWrite.toLocaleString() },
    { label: 'Duration',           value: duration },
    { label: 'Input Tokens',       value: totalIn.toLocaleString() },
    { label: 'Output Tokens',      value: totalOut.toLocaleString() },
    { label: 'Status',             value: status, valueClass: statusClass },
    { label: 'Steps',              value: stepCount },
    { label: 'Total Tokens',       value: total.toLocaleString() },
  ];

  tiles.sort((a, b) => a.label.localeCompare(b.label));

  el.innerHTML = '<div class="metrics-grid">' + tiles.map(t => `
      <div class="metric-tile">
        <div class="metric-label">${t.label}</div>
        <div class="metric-value ${t.valueClass || ''}"${t.style ? ` style="${t.style}"` : ''}>${t.value}</div>
      </div>`).join('') + '</div>';
}

// === Plan ===

// === Plan rendering ===
// Accepts the framework's Plan JSON: { id, name, description, params[], steps[] }.
// Each step has a discriminator `type` of 'agent' | 'loop' | 'branch' (from PlanStep).
// Scoped by a caller-provided id so multiple independent plan views (diagnostics tab,
// plans list) can each track their own expanded step.

const _planScopes = new Map(); // scopeId → { el, plan, expandedStep }

function renderPlan(plan) {
  renderPlanInto(document.getElementById('diag-plan-content'), plan, 'diag');
}

function renderPlanInto(el, plan, scopeId) {
  if (!el) return;
  if (!plan || !Array.isArray(plan.steps) || plan.steps.length === 0) {
    el.innerHTML = '<p class="tab-placeholder">No steps in plan.</p>';
    _planScopes.delete(scopeId);
    return;
  }
  const prev = _planScopes.get(scopeId);
  const expandedStep = prev ? prev.expandedStep : null;
  _planScopes.set(scopeId, { el, plan, expandedStep });

  const desc = plan.description
    ? `<div class="plan-summary-desc">${escapeHtml(plan.description)}</div>` : '';
  const params = plan.params && plan.params.length > 0 ? renderPlanParams(plan.params) : '';

  el.innerHTML =
    `<div class="plan-summary"><div class="plan-summary-name">${escapeHtml(plan.name || 'Plan')}</div></div>` +
    `<div class="plan-body">${desc}${params}${renderPlanFlow(plan.steps, scopeId, expandedStep)}</div>`;
}

function renderPlanParams(params) {
  const rows = params.map(p => `
    <div class="plan-param-row">
      <span class="plan-param-name">${escapeHtml(p.name)}</span>
      ${p.description ? `<span class="plan-param-desc">${escapeHtml(p.description)}</span>` : ''}
      ${p.defaultValue != null && p.defaultValue !== ''
        ? `<span class="plan-param-default">default: <code>${escapeHtml(String(p.defaultValue))}</code></span>`
        : ''}
    </div>`).join('');
  return `<div class="plan-params-block">
    <div class="plan-section-label">Parameters</div>
    ${rows}
  </div>`;
}

// Lay steps out in topological layers; steps at the same depth render side-by-side (parallel).
function renderPlanFlow(steps, scopeId, expandedStep) {
  const layers = computePlanLayers(steps);
  return `<div class="plan-flow">${layers.map((layer, i) => {
    const connector = i > 0 ? '<div class="plan-flow-connector"></div>' : '';
    const cards = layer.map(s => renderPlanStep(s, scopeId, expandedStep)).join('');
    if (layer.length > 1) {
      return `${connector}<div class="plan-flow-parallel">
        <span class="plan-flow-parallel-label">&#8741; Parallel &middot; ${layer.length} steps</span>
        ${cards}
      </div>`;
    }
    return connector + cards;
  }).join('')}</div>`;
}

function computePlanLayers(steps) {
  const nameSet = new Set(steps.map(s => s.name));
  const stepsByName = new Map(steps.map(st => [st.name, st]));
  // `over` (loop) and `from` (branch) are implicit dependencies in the framework —
  // the planner is told not to repeat them in `dependencies`. Fold them in here so
  // depth layering matches the actual DAG.
  const effectiveDeps = (s) => {
    const deps = new Set((s.dependencies || []).filter(d => nameSet.has(d)));
    if (s.type === 'loop' && s.over && nameSet.has(s.over)) deps.add(s.over);
    if (s.type === 'branch' && s.from && nameSet.has(s.from)) deps.add(s.from);
    return deps;
  };
  const depths = new Map();
  const depthOf = (s) => {
    if (depths.has(s.name)) return depths.get(s.name);
    depths.set(s.name, 0); // cycle guard
    const deps = [...effectiveDeps(s)];
    const d = deps.length === 0 ? 0 : Math.max(...deps.map(dn => depthOf(stepsByName.get(dn)))) + 1;
    depths.set(s.name, d);
    return d;
  };
  steps.forEach(depthOf);
  const maxDepth = Math.max(0, ...depths.values());
  const layers = [];
  for (let d = 0; d <= maxDepth; d++) {
    const ls = steps.filter(s => depths.get(s.name) === d);
    if (ls.length) layers.push(ls);
  }
  return layers;
}

function renderPlanStep(step, scopeId, expandedStep) {
  switch (step.type) {
    case 'loop':   return renderPlanStepLoop(step, scopeId, expandedStep);
    case 'branch': return renderPlanStepBranch(step, scopeId, expandedStep);
    default:       return renderPlanStepAgent(step, scopeId, expandedStep);
  }
}

function renderPlanStepAgent(step, scopeId, expandedStep) {
  const isExpanded = expandedStep === step.name;
  const agentLabel = step.agentId ? (_agentNameById.get(step.agentId) || step.agentId) : null;
  const agent = agentLabel
    ? `<span class="plan-step-agent" title="${escapeAttr(step.agentId)}">${escapeHtml(agentLabel)}</span>` : '';
  const hitl = step.hitl ? '<span class="plan-step-flag" title="Requires approval">&#9873;</span>' : '';

  const deps = step.dependencies && step.dependencies.length > 0
    ? `<div class="plan-step-deps">Depends on: ${step.dependencies.map(escapeHtml).join(', ')}</div>`
    : '';

  const tools = (step.tools || []).map(t =>
    `<span class="plan-tool-badge" title="${escapeAttr(t)}">${escapeHtml(t)}</span>`).join('');
  const skills = (step.skills || []).map(s => {
    const label = _skillNameById.get(s) || s;
    return `<span class="plan-tool-badge skill" title="${escapeAttr(s)}">${escapeHtml(label)}</span>`;
  }).join('');
  const badges = (tools || skills) ? `<div class="plan-step-badges">${tools}${skills}</div>` : '';

  const meta = [];
  if (step.maxRetries) meta.push(`retries: ${step.maxRetries}`);
  if (step.timeout) meta.push(`timeout: ${step.timeout}`);
  if (step.conditions && step.conditions.length > 0) meta.push(`conditions: ${step.conditions.length}`);
  const metaHtml = meta.length > 0 ? `<div class="plan-step-meta">${meta.map(escapeHtml).join(' · ')}</div>` : '';

  const instructions = isExpanded && step.instructions
    ? `<div class="plan-step-instructions">${escapeHtml(step.instructions)}</div>`
    : '';

  return `
    <div class="plan-step-card${isExpanded ? ' expanded' : ''}" onclick="togglePlanStep('${escapeAttr(scopeId)}','${escapeAttr(step.name)}')">
      <div class="plan-step-header">
        <span class="plan-step-name">${escapeHtml(step.name)}</span>
        ${agent}${hitl}
      </div>
      ${instructions}
      ${badges}
      ${deps}
      ${metaHtml}
    </div>`;
}

function renderPlanStepLoop(step, scopeId, expandedStep) {
  const deps = step.dependencies && step.dependencies.length > 0
    ? `<div class="plan-step-deps">Depends on: ${step.dependencies.map(escapeHtml).join(', ')}</div>`
    : '';
  const hitl = step.hitl ? '<span class="plan-step-flag" title="Requires approval">&#9873;</span>' : '';
  return `
    <div class="plan-step-card plan-step-control">
      <div class="plan-step-header">
        <span class="plan-step-type-badge">&#10227; Loop</span>
        <span class="plan-step-name">${escapeHtml(step.name)}</span>
        ${step.over ? `<span class="plan-step-over">over <code>${escapeHtml(step.over)}</code></span>` : ''}
        ${hitl}
      </div>
      ${deps}
      <div class="plan-step-body">
        ${renderPlanFlow(step.body || [], scopeId, expandedStep)}
      </div>
    </div>`;
}

function renderPlanStepBranch(step, scopeId, expandedStep) {
  const deps = step.dependencies && step.dependencies.length > 0
    ? `<div class="plan-step-deps">Depends on: ${step.dependencies.map(escapeHtml).join(', ')}</div>`
    : '';
  const hitl = step.hitl ? '<span class="plan-step-flag" title="Requires approval">&#9873;</span>' : '';
  const paths = (step.paths || []).map(p => {
    const isDefault = step.defaultPath && p.pathName === step.defaultPath;
    return `
      <div class="plan-branch-path">
        <div class="plan-branch-path-label">Path: ${escapeHtml(p.pathName)}${isDefault ? ' <em>(default)</em>' : ''}</div>
        ${renderPlanFlow(p.body || [], scopeId, expandedStep)}
      </div>`;
  }).join('');
  return `
    <div class="plan-step-card plan-step-control">
      <div class="plan-step-header">
        <span class="plan-step-type-badge">&#8690; Branch</span>
        <span class="plan-step-name">${escapeHtml(step.name)}</span>
        ${step.from ? `<span class="plan-step-over">from <code>${escapeHtml(step.from)}</code></span>` : ''}
        ${hitl}
      </div>
      ${deps}
      <div class="plan-step-body">${paths}</div>
    </div>`;
}

function togglePlanStep(scopeId, name) {
  const scope = _planScopes.get(scopeId);
  if (!scope) return;
  scope.expandedStep = scope.expandedStep === name ? null : name;
  renderPlanInto(scope.el, scope.plan, scopeId);
}

function collapseAllPlanSteps(scopeId) {
  const scope = _planScopes.get(scopeId);
  if (!scope || scope.expandedStep == null) return;
  scope.expandedStep = null;
  renderPlanInto(scope.el, scope.plan, scopeId);
}

function escapeAttr(v) {
  return String(v == null ? '' : v).replace(/'/g, '&#39;').replace(/"/g, '&quot;');
}

function updateTaskRowName(taskId, name) {
  const row = document.getElementById('task-row-' + taskId);
  if (row) {
    const cells = row.querySelectorAll(':scope > div');
    if (cells[1]) cells[1].textContent = name;
  }
}

// === Reconstruct events from a completed task log ===

function renderEventsFromLog(log) {
  if (!log) return;

  if (log.createdAt)
    addEvent('task_started', log.taskId, null, 'Task started', null, log.createdAt);

  (log.steps || []).forEach(step => {
    const runs = step.runs || [];
    const stepStart = runs[0] && runs[0].startedAt ? runs[0].startedAt : log.createdAt;
    const stepEnd = runs.length > 0 && runs[runs.length - 1].completedAt
      ? runs[runs.length - 1].completedAt : log.completedAt;

    addEvent('step_started', step.id, log.taskId,
      `Step started: ${step.stepName}`, null, stepStart);

    runs.forEach(run => {
      const agent = run.agentName || '';
      if (run.startedAt)
        addEvent('run_started', run.id, step.id, `Run started: ${agent}`, null, run.startedAt);

      (run.turns || []).forEach(turn => {
        if (turn.startedAt) {
          addEvent('turn_started', turn.id, run.id,
            `Turn started: ${agent}/${turn.index}`, null, turn.startedAt);
          addEvent('message_sent', turn.messageId, turn.id,
            `LLM request sent: ${agent}/${turn.index}`, turn.id, turn.startedAt);
        }
        if (turn.completedAt) {
          const sr = turn.stopReason ? ` (${turn.stopReason})` : '';
          addEvent('response_received', turn.responseId, turn.id,
            `LLM response received: ${agent}/${turn.index}${sr}`, turn.id, turn.completedAt);
          addEvent('turn_completed', turn.id, run.id,
            `Turn completed: ${agent}/${turn.index}`, null, turn.completedAt);
        }
      });

      if (run.completedAt)
        addEvent('run_completed', run.id, step.id, `Run completed: ${agent}`, null, run.completedAt);
    });

    addEvent('step_completed', step.id, log.taskId,
      `Step completed: ${step.stepName} (${step.status || ''})`, null, stepEnd);
  });

  if (log.completedAt)
    addEvent('task_completed', log.taskId, null,
      `Task completed (${log.status || ''})`, null, log.completedAt);
}

// === Task detail (on completion or click) ===

async function loadTaskDetail(taskId) {
  try {
    const res = await fetch(API + '/tasks/' + taskId + '/log');
    if (!res.ok) return;
    const log = await res.json();

    const isSwitchingTasks = activeTaskId !== taskId;
    const isTerminal = log.status && log.status !== 'RUNNING';

    activeTaskId = taskId;

    resetDiagnostics();
    if (isSwitchingTasks || isTerminal) {
      clearEvents();
      renderEventsFromLog(log);
    }

    if (!isTerminal) {
      if (isSwitchingTasks || !activeEventSource) subscribeToEvents(taskId);
    } else if (activeEventSource) {
      activeEventSource.close();
      activeEventSource = null;
    }

    // Result
    var lastStep = log.steps && log.steps.length > 0 ? log.steps[log.steps.length - 1] : null;
    var resultText = lastStep && lastStep.output ? lastStep.output : '(no output)';
    document.getElementById('diag-result-content').innerHTML =
      '<div style="font-family:var(--mono);font-size:13px;white-space:pre-wrap;line-height:1.6">' + escapeHtml(resultText) + '</div>';

    // Plan tab is strictly the plan DEFINITION — static structure from the plan registry.
    // Progress / status / execution order lives in the Events and Trace tabs, not here.
    const planEl = document.getElementById('diag-plan-content');
    if (log.planId) {
      fetch(API + '/plans/' + log.planId)
        .then(r => r.ok ? r.json() : null)
        .then(def => {
          if (def && def.plan) renderPlan(def.plan);
          else planEl.innerHTML = '<p class="tab-placeholder">Plan definition not available.</p>';
        })
        .catch(() => {
          planEl.innerHTML = '<p class="tab-placeholder">Failed to load plan definition.</p>';
        });
    } else {
      planEl.innerHTML = '<p class="tab-placeholder">This task has no registered plan.</p>';
    }

    // Metrics
    renderTaskMetrics(log);

    // Trace waterfall
    loadTraceWaterfall(taskId);

    // Update task row
    updateTaskRow(taskId, log.status || 'RUNNING', totalTokens(log));

  } catch (e) {
    console.error('Failed to load task detail', e);
  }
}

// === Trace Waterfall ===

const SPAN_COLORS = {
  'agentican.step':      '#6366f1',
  'agentican.run':       '#a855f7',
  'agentican.turn':      '#64748b',
  'agentican.llm.call':  '#22c55e',
  'agentican.tool.call': '#f59e0b',
  'agentican.hitl.wait': '#ec4899',
};

function spanColor(name) {
  for (const [prefix, color] of Object.entries(SPAN_COLORS)) {
    if (name.startsWith(prefix)) return color;
  }
  return '#94a3b8';
}

async function loadTraceWaterfall(taskId) {
  const container = document.getElementById('diag-steps-content');
  try {
    const res = await fetch(API + '/traces/' + taskId);
    if (!res.ok) return;
    const spans = await res.json();
    if (!spans.length) return;
    renderWaterfall(container, spans);

    // Stop polling once we have the task span (trace is complete)
    if (spans.some(s => s.name === 'agentican.task')) stopTracePolling();
  } catch (e) {
    // silently ignore — polling will retry
  }
}

function renderWaterfall(container, spans) {
  // Build tree
  const byId = {};
  spans.forEach(s => byId[s.spanId] = { ...s, children: [] });
  const roots = [];
  spans.forEach(s => {
    if (s.parentSpanId && byId[s.parentSpanId]) {
      byId[s.parentSpanId].children.push(byId[s.spanId]);
    } else {
      roots.push(byId[s.spanId]);
    }
  });

  // Sort children by start time
  const sortChildren = node => {
    node.children.sort((a, b) => a.startTimeUnixNano - b.startTimeUnixNano);
    node.children.forEach(sortChildren);
  };
  roots.sort((a, b) => a.startTimeUnixNano - b.startTimeUnixNano);
  roots.forEach(sortChildren);

  // Global time range
  const minTime = Math.min(...spans.map(s => s.startTimeUnixNano));
  const maxTime = Math.max(...spans.map(s => s.endTimeUnixNano));
  const totalDuration = maxTime - minTime;

  // Flatten tree with depth
  const rows = [];
  const flatten = (node, depth) => {
    rows.push({ span: node, depth });
    node.children.forEach(child => flatten(child, depth + 1));
  };
  roots.forEach(root => flatten(root, 0));

  // Render
  let html = '<div class="waterfall">';
  html += '<div class="waterfall-header"><span class="wf-name-col">Span</span><span class="wf-dur-col">Duration</span><span class="wf-bar-col">Timeline</span></div>';

  for (const row of rows) {
    const s = row.span;
    const indent = row.depth * 16;
    const color = spanColor(s.name);
    const barLeft = totalDuration > 0 ? ((s.startTimeUnixNano - minTime) / totalDuration * 100) : 0;
    const barWidth = totalDuration > 0 ? Math.max(((s.endTimeUnixNano - s.startTimeUnixNano) / totalDuration * 100), 0.5) : 100;
    const durLabel = formatDuration(s.durationMs);

    // Build attribute summary
    const attrParts = [];
    if (s.attributes['gen_ai.request.model']) attrParts.push(s.attributes['gen_ai.request.model']);
    if (s.attributes['gen_ai.usage.input_tokens']) attrParts.push(s.attributes['gen_ai.usage.input_tokens'] + 'in');
    if (s.attributes['gen_ai.usage.output_tokens']) attrParts.push(s.attributes['gen_ai.usage.output_tokens'] + 'out');
    if (s.attributes['agentican.tool.name']) attrParts.push(s.attributes['agentican.tool.name']);
    if (s.attributes['agentican.turn.stop_reason']) attrParts.push(s.attributes['agentican.turn.stop_reason']);
    const attrText = attrParts.length > 0 ? ' — ' + attrParts.join(', ') : '';

    const isClickable = s.name === 'agentican.llm.call' || s.name === 'agentican.tool.call';
    const clickClass = isClickable ? ' clickable-span' : '';
    const clickAttr = isClickable ? ` onclick="openSpanModal('${s.spanId}', spanData)"` : '';

    html += `<div class="waterfall-row${clickClass}" data-span-id="${s.spanId}" title="${escapeHtml(JSON.stringify(s.attributes))}">`;
    html += `<span class="wf-name-col" style="padding-left:${indent}px"><span class="wf-dot" style="background:${color}"></span>${escapeHtml(s.name)}<span class="wf-attr">${escapeHtml(attrText)}</span></span>`;
    html += `<span class="wf-dur-col">${durLabel}</span>`;
    html += `<span class="wf-bar-col"><span class="wf-bar" style="left:${barLeft}%;width:${barWidth}%;background:${color}"></span></span>`;
    html += '</div>';
  }

  html += '</div>';
  container.innerHTML = html;

  // Attach click handlers for clickable spans
  container.querySelectorAll('.clickable-span').forEach(el => {
    el.addEventListener('click', () => {
      const spanId = el.dataset.spanId;
      const span = byId[spanId];
      if (!span) return;

      // Walk parent chain to find step name and turn index
      var stepName = null, turnIndex = 0, runIndex = 0;
      var current = span;
      while (current) {
        if (current.name && current.name.startsWith('agentican.turn')) {
          turnIndex = parseInt(current.attributes?.['agentican.turn.index'] || '0');
        }
        if (current.name && current.name.startsWith('agentican.step')) {
          stepName = current.attributes?.['agentican.step.name'];
        }
        current = current.parentSpanId ? byId[current.parentSpanId] : null;
      }

      if (!stepName) return;

      // Build a temporary turn context and open modal
      var tempTurnId = '__span_' + spanId;
      turnContext.set(tempTurnId, { stepName, runIndex, turnIndex });
      var focusTab = span.name === 'agentican.tool.call' ? 'tool_call_completed' : 'response_received';
      openTurnModal(tempTurnId, focusTab);
    });
  });
}

function formatDuration(ms) {
  if (ms < 1) return '<1ms';
  if (ms < 1000) return ms + 'ms';
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's';
  return (ms / 60000).toFixed(1) + 'm';
}

// === HITL ===

function showHitlPrompt(data) {
  const panel = document.getElementById('hitl-panel');
  const title = document.getElementById('hitl-title');
  const prompt = document.getElementById('hitl-prompt');
  const approvalActions = document.getElementById('hitl-approval-actions');
  const questionActions = document.getElementById('hitl-question-actions');
  const answerWrap = document.getElementById('hitl-answer-wrap');
  const answerEl = document.getElementById('hitl-answer');

  const cp = data.checkpoint;
  const isQuestion = cp && cp.type === 'QUESTION';

  title.textContent = isQuestion ? 'Question From Agent' : 'Approval Required';

  if (isQuestion) {
    prompt.innerHTML = `
      <strong>${escapeHtml(cp.description || 'Agent has a question')}</strong>
      ${cp.content ? '<br><br><em>Context:</em> ' + escapeHtml(cp.content) : ''}
    `;
    answerWrap.style.display = 'block';
    answerEl.value = '';
    approvalActions.style.display = 'none';
    questionActions.style.display = '';
    setTimeout(() => answerEl.focus(), 50);
  } else {
    prompt.innerHTML = `
      <strong>Human approval required</strong><br>
      ${cp ? escapeHtml(cp.description) : 'Checkpoint pending'}
      ${cp && cp.content ? '<br><br><code>' + escapeHtml(cp.content.substring(0, 500)) + '</code>' : ''}
    `;
    answerWrap.style.display = 'none';
    approvalActions.style.display = '';
    questionActions.style.display = 'none';
  }

  panel.style.display = 'block';
}

async function respondHitl(approved) {
  if (!activeCheckpointId) return;
  try {
    const feedback = approved ? '' : prompt('Rejection feedback:') || 'Rejected';
    await fetch(API + '/checkpoints/' + activeCheckpointId + '/respond', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ approved, feedback: approved ? null : feedback })
    });
    document.getElementById('hitl-panel').style.display = 'none';
    addEvent('hitl_response', shortHexId(), activeCheckpointId, approved ? 'Approved' : 'Rejected: ' + feedback);
    activeCheckpointId = null;
  } catch (e) { toast('Failed to respond: ' + e.message, 'error'); }
}

async function submitHitlAnswer() {
  if (!activeCheckpointId) return;
  const answerEl = document.getElementById('hitl-answer');
  const answer = (answerEl.value || '').trim();
  if (!answer) { toast('Please enter an answer', 'error'); answerEl.focus(); return; }
  try {
    await fetch(API + '/checkpoints/' + activeCheckpointId + '/respond', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ approved: true, feedback: answer })
    });
    document.getElementById('hitl-panel').style.display = 'none';
    addEvent('hitl_response', shortHexId(), activeCheckpointId, 'Answered: ' + (answer.length > 80 ? answer.substring(0, 77) + '...' : answer));
    activeCheckpointId = null;
  } catch (e) { toast('Failed to respond: ' + e.message, 'error'); }
}

document.getElementById('task-input').addEventListener('keydown', e => {
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) submitTask();
});

// === Agents ===

async function loadConfig() {
  try {
    const res = await fetch(API + '/config');
    const props = await res.json();
    props.sort((a, b) => a.name.localeCompare(b.name));
    document.getElementById('config-content').innerHTML = props.length === 0
      ? '<p style="color:var(--text-secondary)">No configuration properties found.</p>'
      : `<div class="grid-table config-grid">
          <div class="grid-header"><div>Property</div><div>Value</div></div>
          ${props.map(p => `
            <div class="grid-row">
              <div><code>${escapeHtml(p.name)}</code></div>
              <div style="font-family:var(--mono)">${escapeHtml(p.value)}</div>
            </div>`).join('')}
        </div>`;
  } catch (e) {
    document.getElementById('config-content').innerHTML =
      '<p style="color:var(--danger)">Failed to load configuration.</p>';
  }
}

async function loadPlans() {
  try {
    const res = await fetch(API + '/plans');
    const plans = await res.json();
    plans.sort((a, b) => (a.plan?.name || a.planId || '').localeCompare(b.plan?.name || b.planId || ''));
    const list = document.getElementById('plans-list');

    if (plans.length === 0) {
      list.innerHTML = '<div class="card"><p style="color:var(--text-secondary)">No plans registered yet. Submit a task to create one.</p></div>';
      return;
    }

    list.innerHTML = plans.map(p =>
      `<div class="card plan-card-collapsible" id="plan-card-${escapeAttr(p.planId)}"></div>`
    ).join('');

    plans.forEach(p => {
      const el = document.getElementById('plan-card-' + p.planId);
      if (!el || !p.plan) return;
      const scopeId = 'plans-' + p.planId;
      renderPlanInto(el, p.plan, scopeId);
      // Delegated listener: survives the innerHTML rewrites that happen when a
      // nested step card is expanded/collapsed via togglePlanStep.
      el.addEventListener('click', (e) => {
        if (!e.target.closest('.plan-summary')) return;
        const nowExpanded = el.classList.toggle('expanded');
        if (!nowExpanded) collapseAllPlanSteps(scopeId);
      });
    });
  } catch (e) {}
}

async function loadTools() {
  try {
    const res = await fetch(API + '/tools');
    const toolkits = await res.json();
    toolkits.sort((a, b) => (a.displayName || a.slug || '').localeCompare(b.displayName || b.slug || ''));
    toolkits.forEach(tk => {
      if (tk.tools) tk.tools.sort((a, b) => (a.displayName || a.name || '').localeCompare(b.displayName || b.name || ''));
    });
    document.getElementById('tools-list').innerHTML = toolkits.length === 0
      ? '<p style="color:var(--text-secondary)">No toolkits registered.</p>'
      : toolkits.map(tk => `
        <div class="plan-card" onclick="this.classList.toggle('expanded')">
          <div class="plan-card-header">
            <span class="plan-card-name">${escapeHtml(tk.displayName || tk.slug)}</span>
            <span style="color:var(--text-secondary);font-size:13px">${tk.tools.length} tool${tk.tools.length !== 1 ? 's' : ''}</span>
          </div>
          ${tk.tools.length > 0 ? `<div class="plan-card-details"><div class="tools-scroll"><div class="steps-list">${tk.tools.map(t => `
            <div class="agent-card" onclick="event.stopPropagation(); this.classList.toggle('expanded')">
              <div class="agent-card-header">
                <span class="agent-card-name" style="font-size:14px">${escapeHtml(t.displayName || t.name)}</span>
              </div>
              <p class="agent-desc">${escapeHtml(t.description)}</p>
            </div>`).join('')}</div></div></div>` : ''}
        </div>
      `).join('');
  } catch (e) {}
}

let _agentNameById = new Map();
let _skillNameById = new Map();

async function loadAgents() {
  try {
    const res = await fetch(API + '/agents');
    const agents = await res.json();
    _agentNameById = new Map(agents.map(a => [a.id, a.name]));
    agents.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    document.getElementById('agents-list').innerHTML = agents.map(a => `
      <div class="agent-card" onclick="this.classList.toggle('expanded')">
        <div class="agent-card-header">
          <span class="agent-card-name">${escapeHtml(a.name)}</span>
        </div>
        <div class="agent-desc">
          ${a.id ? `<div class="agent-field"><span class="agent-field-label">ID</span><div class="agent-field-value" style="font-family:var(--mono);font-size:12px">${escapeHtml(a.id)}</div></div>` : ''}
          ${a.role ? `<div class="agent-field"><span class="agent-field-label">Role</span><div class="agent-field-value">${escapeHtml(a.role)}</div></div>` : ''}
        </div>
      </div>`).join('');
  } catch (e) {}
}

async function loadSkills() {
  try {
    const res = await fetch(API + '/skills');
    const skills = await res.json();
    _skillNameById = new Map(skills.map(s => [s.id, s.name]));
    skills.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    const list = document.getElementById('skills-list');

    if (skills.length === 0) {
      list.innerHTML = '<div class="card"><p style="color:var(--text-secondary)">No skills registered yet. Skills are defined in config or minted by the planner when needed.</p></div>';
      return;
    }

    list.innerHTML = skills.map(s => `
      <div class="agent-card" onclick="this.classList.toggle('expanded')">
        <div class="agent-card-header">
          <span class="agent-card-name">${escapeHtml(s.name)}</span>
        </div>
        <div class="agent-desc">
          ${s.id ? `<div class="agent-field"><span class="agent-field-label">ID</span><div class="agent-field-value" style="font-family:var(--mono);font-size:12px">${escapeHtml(s.id)}</div></div>` : ''}
          <div class="agent-field">
            <span class="agent-field-label">Instructions</span>
            <div class="agent-field-value" style="white-space:pre-wrap">${escapeHtml(s.instructions)}</div>
          </div>
        </div>
      </div>`).join('');
  } catch (e) {}
}

// === Knowledge ===

async function loadKnowledge() {
  try {
    const res = await fetch(API + '/knowledge');
    const entries = await res.json();
    entries.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    const list = document.getElementById('knowledge-list');

    if (entries.length === 0) {
      list.innerHTML = '<div class="card"><p style="color:var(--text-secondary)">No knowledge entries yet.</p></div>';
      return;
    }

    list.innerHTML = entries.map(e => `
      <div class="agent-card" onclick="viewKnowledge('${e.id}')">
        <div class="agent-card-header">
          <span class="agent-card-name">${escapeHtml(e.name)}</span>
          <span class="fact-count-pill">${e.factCount} fact${e.factCount !== 1 ? 's' : ''}</span>
        </div>
      </div>
    `).join('');
  } catch (e) {}
}

async function viewKnowledge(id) {
  try {
    const res = await fetch(API + '/knowledge/' + id);
    const entry = await res.json();
    const facts = entry.facts || [];

    document.getElementById('knowledge-modal-title').textContent = entry.name;
    document.getElementById('knowledge-modal-desc').innerHTML = entry.description
      ? `<p class="knowledge-modal-desc-text">${escapeHtml(entry.description)}</p>`
      : '';

    const body = document.getElementById('knowledge-modal-facts');
    body.innerHTML = facts.length === 0
      ? '<p style="color:var(--text-secondary)">No facts yet.</p>'
      : facts.map(f => `
        <div class="fact-row">
          <div class="fact-name">${escapeHtml(f.name || '—')}</div>
          <div class="fact-content">${escapeHtml(f.content || '')}</div>
          ${f.tags && f.tags.length > 0
            ? `<div class="fact-tags">${f.tags.map(t => `<span class="fact-tag">${escapeHtml(t)}</span>`).join('')}</div>`
            : ''}
        </div>
      `).join('');

    document.getElementById('knowledge-modal').classList.add('visible');
  } catch (e) { toast('Failed to load', 'error'); }
}

function closeKnowledgeModal() {
  document.getElementById('knowledge-modal').classList.remove('visible');
}

// === Metrics (global page) ===

async function loadMetrics() {
  try {
    const res = await fetch('/q/metrics');
    const text = await res.text();
    const metrics = [];
    for (const line of text.split('\n')) {
      if (line.startsWith('#') || !line.startsWith('agentican_')) continue;
      const match = line.match(/^([a-z_]+)(\{[^}]*\})?\s+(.+)$/);
      if (!match) continue;
      const value = parseFloat(match[3]);
      if (isNaN(value)) continue;
      metrics.push({ name: match[1], tags: match[2] || '', value });
    }
    const grid = document.getElementById('metrics-grid');
    metrics.sort((a, b) => a.name.localeCompare(b.name));
    if (metrics.length === 0) {
      grid.innerHTML = '<p style="color:var(--text-secondary)">No Agentican metrics yet. Run a task first.</p>';
    } else {
      grid.innerHTML = `
        <div class="grid-table metrics-page-grid">
          <div class="grid-header"><div>Metric</div><div>Value</div><div>Tags</div></div>
          ${metrics.map(m => `
            <div class="grid-row">
              <div><code>${escapeHtml(m.name)}</code></div>
              <div style="font-family:var(--mono);font-weight:600">${formatMetricValue(m.name, m.value)}</div>
              <div style="color:var(--text-secondary);font-size:12px">${m.tags ? escapeHtml(m.tags) : ''}</div>
            </div>`).join('')}
        </div>`;
    }
  } catch (e) {
    document.getElementById('metrics-grid').innerHTML =
      '<p style="color:var(--danger);grid-column:1/-1">Failed to load metrics.</p>';
  }
}

function formatMetricValue(name, value) {
  if (name.includes('_seconds')) return value.toFixed(3) + 's';
  if (name.includes('_tokens')) return value.toLocaleString();
  if (value === Math.floor(value)) return value.toLocaleString();
  return value.toFixed(2);
}

function escapeHtml(text) {
  if (!text) return '';
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

// === Turn Detail Modal ===

function switchModalTab(btn) {
  document.querySelectorAll('.modal-tab').forEach(t => t.classList.remove('active'));
  document.querySelectorAll('.modal-tab-content').forEach(c => c.classList.remove('active'));
  btn.classList.add('active');
  document.getElementById('mtab-' + btn.dataset.mtab).classList.add('active');
}

function closeTurnModal() {
  document.getElementById('turn-modal').classList.remove('visible');
}

async function openTurnModal(turnId, focusTab) {
  const ctx = turnContext.get(turnId);
  if (!ctx || !ctx.stepName) return;

  const url = API + '/tasks/' + activeTaskId + '/steps/' + encodeURIComponent(ctx.stepName)
             + '/runs/' + ctx.runIndex + '/turns/' + ctx.turnIndex;

  try {
    const res = await fetch(url);
    if (!res.ok) return;
    const turn = await res.json();
    renderTurnModal(turn, focusTab, ctx);
  } catch (e) {
    console.error('Failed to load turn detail', e);
  }
}

function switchModalSub(btn) {
  var parent = btn.closest('.modal-tab-content');
  parent.querySelectorAll('.btn-group-item').forEach(b => b.classList.remove('active'));
  parent.querySelectorAll('.modal-sub-content').forEach(c => c.style.display = 'none');
  btn.classList.add('active');
  parent.querySelector('#' + btn.dataset.sub).style.display = 'block';
}

function renderTurnModal(turn, focusTab, ctx) {
  var title = 'Step "' + (ctx.stepName || '?') + '", Run ' + ctx.runIndex + ', Turn ' + ctx.turnIndex;
  document.getElementById('turn-modal-title').textContent = title;

  // Request tab — button group: System Prompt | User Message
  var reqSubDefault = focusTab === 'message_sent' ? 'user-message' : 'system-prompt';
  document.getElementById('mtab-request').innerHTML = `
    <div class="modal-meta">
      ${turn.model ? `<span>Model: ${escapeHtml(turn.model)}</span>` : ''}
      ${turn.provider ? `<span>Provider: ${escapeHtml(turn.provider)}</span>` : ''}
    </div>
    <div class="btn-group" style="margin-bottom:12px">
      <button class="btn-group-item${reqSubDefault === 'system-prompt' ? ' active' : ''}" data-sub="sub-system-prompt" onclick="switchModalSub(this)">System Prompt</button>
      <button class="btn-group-item${reqSubDefault === 'user-message' ? ' active' : ''}" data-sub="sub-user-message" onclick="switchModalSub(this)">User Message</button>
    </div>
    <div id="sub-system-prompt" class="modal-sub-content" style="display:${reqSubDefault === 'system-prompt' ? 'block' : 'none'}">
      <div class="modal-code">${escapeHtml(turn.systemPrompt || '')}</div>
    </div>
    <div id="sub-user-message" class="modal-sub-content" style="display:${reqSubDefault === 'user-message' ? 'block' : 'none'}">
      <div class="modal-code">${escapeHtml(turn.userMessage || '')}</div>
    </div>
  `;

  // Response tab — button group: Output | Tool Calls
  var hasToolCalls = (turn.toolCalls || []).length > 0;
  var respSubDefault = (focusTab === 'tool_call_started' || focusTab === 'tool_call_completed') && hasToolCalls ? 'resp-tools' : 'resp-output';
  var toolsContent = !hasToolCalls
    ? '<p style="color:var(--text-secondary)">No tool calls in this turn.</p>'
    : turn.toolCalls.map(tc => `
      <div class="tool-call-card">
        <div class="tool-call-name">${escapeHtml(tc.toolName)} ${tc.error ? '<span style="color:var(--danger)">FAILED</span>' : ''}</div>
        <div class="modal-section">
          <div class="modal-section-title">Input</div>
          <div class="modal-code">${escapeHtml(JSON.stringify(tc.args, null, 2))}</div>
        </div>
        ${tc.result ? `<div class="modal-section">
          <div class="modal-section-title">Output</div>
          <div class="modal-code">${escapeHtml(tc.result)}</div>
        </div>` : ''}
      </div>
    `).join('');

  document.getElementById('mtab-response').innerHTML = `
    <div class="modal-meta">
      <span>Stop: ${escapeHtml(turn.stopReason || '')}</span>
      <span>Input: ${(turn.inputTokens || 0).toLocaleString()}</span>
      <span>Output: ${(turn.outputTokens || 0).toLocaleString()}</span>
      <span>Cache Read: ${(turn.cacheReadTokens || 0).toLocaleString()}</span>
      <span>Cache Write: ${(turn.cacheWriteTokens || 0).toLocaleString()}</span>
    </div>
    <div class="btn-group" style="margin-bottom:12px">
      <button class="btn-group-item${respSubDefault === 'resp-output' ? ' active' : ''}" data-sub="resp-output" onclick="switchModalSub(this)">Output</button>
      <button class="btn-group-item${respSubDefault === 'resp-tools' ? ' active' : ''}" data-sub="resp-tools" onclick="switchModalSub(this)">Tool Calls${hasToolCalls ? ' (' + turn.toolCalls.length + ')' : ''}</button>
    </div>
    <div id="resp-output" class="modal-sub-content" style="display:${respSubDefault === 'resp-output' ? 'block' : 'none'}">
      <div class="modal-code">${escapeHtml(turn.responseText || '(no text)')}</div>
    </div>
    <div id="resp-tools" class="modal-sub-content" style="display:${respSubDefault === 'resp-tools' ? 'block' : 'none'}">
      ${toolsContent}
    </div>
  `;

  // Focus the right tab
  var tabName = 'request';
  if (focusTab === 'response_received' || focusTab === 'tool_call_started' || focusTab === 'tool_call_completed') tabName = 'response';

  document.querySelectorAll('.modal-tab').forEach(t => {
    t.classList.toggle('active', t.dataset.mtab === tabName);
  });
  document.querySelectorAll('.modal-tab-content').forEach(c => {
    c.classList.toggle('active', c.id === 'mtab-' + tabName);
  });

  document.getElementById('turn-modal').classList.add('visible');
}

// === Init ===
activatePanel(location.hash.slice(1) || 'tasks');
// Prefetch so plan step badges can show display names (not ids) regardless of nav order.
fetch(API + '/agents').then(r => r.ok ? r.json() : []).then(agents => {
  _agentNameById = new Map(agents.map(a => [a.id, a.name]));
}).catch(() => {});
fetch(API + '/skills').then(r => r.ok ? r.json() : []).then(skills => {
  _skillNameById = new Map(skills.map(s => [s.id, s.name]));
}).catch(() => {});
