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

const VALID_PANELS = new Set(['tasks','plans','plan-editor','agents','skills','tools','knowledge','metrics','config','audit']);

function parseHash() {
  const raw = location.hash.slice(1);
  const slash = raw.indexOf('/');
  const panel = (slash < 0 ? raw : raw.slice(0, slash)) || 'tasks';
  const ref   = slash < 0 ? null : decodeURIComponent(raw.slice(slash + 1));
  return { panel, ref };
}

function activatePanel(panel, ref) {
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
    case 'plan-editor': _loadPlanEditor(ref); break;
    case 'agents': loadAgents(); break;
    case 'skills': loadSkills(); break;
    case 'tools': loadTools(); break;
    case 'knowledge': loadKnowledge(); break;
    case 'metrics': loadMetrics(); break;
    case 'config': loadConfig(); break;
    case 'audit': loadAudit(); break;
  }
}

document.querySelectorAll('.nav-item').forEach(item => {
  item.addEventListener('click', e => {
    e.preventDefault();
    const panel = item.dataset.panel;
    if (parseHash().panel === panel) {
      activatePanel(panel);
    } else {
      location.hash = panel;
    }
  });
});

window.addEventListener('hashchange', () => {
  const { panel, ref } = parseHash();
  activatePanel(panel, ref);
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

// === Catalog export / import ===

async function exportCatalogYaml() {
  try {
    const res = await fetch(API + '/config/export.yaml');
    if (!res.ok) {
      toast('Export failed: ' + res.status, 'error');
      return;
    }
    const body = await res.text();
    const blob = new Blob([body], { type: 'application/yaml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'catalog.yaml';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast('Exported', 'success');
  } catch (e) {
    toast('Network error: ' + e.message, 'error');
  }
}

function openImportModal() {
  document.getElementById('import-body').value = '';
  document.getElementById('import-error').hidden = true;
  document.getElementById('import-success').hidden = true;
  document.getElementById('import-dry-run').checked = true;
  document.getElementById('import-submit').textContent = 'Preview (dry run)';
  document.getElementById('import-modal').hidden = false;
}

function closeImportModal() {
  document.getElementById('import-modal').hidden = true;
}

document.addEventListener('change', (e) => {
  if (e.target && e.target.id === 'import-dry-run') {
    document.getElementById('import-submit').textContent =
      e.target.checked ? 'Preview (dry run)' : 'Apply';
  }
});

async function submitImport(event) {
  event.preventDefault();

  const errEl = document.getElementById('import-error');
  const okEl  = document.getElementById('import-success');
  errEl.hidden = true;
  okEl.hidden = true;

  const body = document.getElementById('import-body').value;
  const dryRun = document.getElementById('import-dry-run').checked;

  // Detect YAML vs JSON by first non-whitespace char.
  const trimmed = body.trim();
  const isJson = trimmed.startsWith('{') || trimmed.startsWith('[');
  const contentType = isJson ? 'application/json' : 'application/yaml';

  try {
    const res = await fetch(API + '/config/import?dryRun=' + dryRun, {
      method: 'POST',
      headers: { 'Content-Type': contentType },
      body
    });

    const result = await res.json().catch(() => ({ message: 'Error ' + res.status }));

    if (!res.ok) {
      errEl.textContent = result.message || 'Import failed';
      errEl.hidden = false;
      return;
    }

    okEl.innerHTML = renderImportSummary(result);
    okEl.hidden = false;

    if (!dryRun) {
      loadAgents();
      loadSkills();
      loadPlans();
    }
  } catch (e) {
    errEl.textContent = 'Network error: ' + e.message;
    errEl.hidden = false;
  }
}

function renderImportSummary(r) {
  const heading = r.dryRun ? 'Dry run — nothing applied' : 'Applied';

  const row = (label, counts) =>
    `<li><b>${escapeHtml(label)}:</b> ${counts.created} created, ${counts.updated} updated, ${counts.skipped} skipped</li>`;

  const errors = r.errors && r.errors.length
    ? `<div style="margin-top:8px"><b>Issues:</b><ul>${r.errors.map(e => '<li>' + escapeHtml(e) + '</li>').join('')}</ul></div>`
    : '';

  return `<b>${heading}</b><ul>${row('Agents', r.agents)}${row('Skills', r.skills)}${row('Plans', r.plans)}</ul>${errors}`;
}

async function loadPlans() {
  try {
    const res = await fetch(API + '/plans');
    const plans = await res.json();
    plans.sort((a, b) => (a.plan?.name || a.planId || '').localeCompare(b.plan?.name || b.planId || ''));
    const list = document.getElementById('plans-list');

    if (plans.length === 0) {
      list.innerHTML = '<div class="card"><p style="color:var(--text-secondary)">No plans registered yet. Click “+ New plan” to add one.</p></div>';
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

      // Inject action buttons into the plan summary.
      const summary = el.querySelector('.plan-summary');
      const refKey = p.plan.id || p.planId;
      if (summary && p.plan.id) {
        const actions = document.createElement('span');
        actions.className = 'card-actions';
        actions.innerHTML = `
          <button class="card-btn" onclick="event.stopPropagation(); openPlanEditor('${escapeAttr(refKey)}')">Edit</button>
          <button class="card-btn card-btn-danger" onclick="event.stopPropagation(); deletePlan('${escapeAttr(refKey)}', '${escapeAttr(p.plan.name || refKey)}')">Delete</button>`;
        summary.appendChild(actions);
      }

      // Delegated listener: survives the innerHTML rewrites that happen when a
      // nested step card is expanded/collapsed via togglePlanStep.
      el.addEventListener('click', (e) => {
        if (!e.target.closest('.plan-summary')) return;
        if (e.target.closest('.card-actions')) return;
        const nowExpanded = el.classList.toggle('expanded');
        if (!nowExpanded) collapseAllPlanSteps(scopeId);
      });
    });
  } catch (e) {}
}

// === Plan CRUD modal ===

let _planEditRef = null;
let _planMode = 'canvas';             // 'form' | 'canvas' | 'json'
let _planAgentChoices = [];           // populated from /agents on editor open
let _planSkillChoices = [];           // populated from /skills on editor open
let _planToolChoices  = [];           // [{ name, label, toolkit }] from /tools on editor open

const BLANK_PLAN = () => ({
  name: '',
  description: '',
  id: '',
  outputStep: '',
  params: [],
  steps: [{
    type: 'agent',
    name: 'step-1',
    agentId: '',
    instructions: '',
    dependencies: [],
    hitl: false,
    skills: [],
    tools: []
  }]
});

function openPlanEditor(ref) {
  location.hash = ref ? 'plan-editor/' + encodeURIComponent(ref) : 'plan-editor';
}

function _loadPlanEditor(ref) {
  _planEditRef = ref || null;

  document.getElementById('plan-editor-title').textContent = ref ? 'Edit plan' : 'New plan';
  document.getElementById('plan-error').hidden = true;
  document.getElementById('plan-error').textContent = '';

  // Refresh agent/skill/tool options for the form view & canvas inspector.
  Promise.all([
    fetch(API + '/agents').then(r => r.ok ? r.json() : []).catch(() => []),
    fetch(API + '/skills').then(r => r.ok ? r.json() : []).catch(() => []),
    fetch(API + '/tools' ).then(r => r.ok ? r.json() : []).catch(() => [])
  ]).then(([agents, skills, toolkits]) => {
    _planAgentChoices = agents.map(a => a.id || a.name).filter(Boolean);
    _planSkillChoices = skills.map(s => s.id || s.name).filter(Boolean);
    _planToolChoices  = (toolkits || []).flatMap(tk => (tk.tools || []).map(t => ({
      name:    t.name,
      label:   t.displayName || t.name,
      toolkit: tk.displayName || tk.slug || 'Tools'
    })));

    if (ref) {
      fetch(API + '/plans/' + encodeURIComponent(ref))
        .then(r => r.json())
        .then(view => populatePlanEditor(view.plan))
        .catch(() => toast('Failed to load plan', 'error'));
    } else {
      populatePlanEditor(BLANK_PLAN());
    }
  });
}

function populatePlanEditor(plan) {

  const editable = {
    name:        plan.name        || '',
    description: plan.description || '',
    id:          plan.id          || '',
    outputStep:  plan.outputStep  || '',
    params:      plan.params      || [],
    steps:       plan.steps       || []
  };

  document.getElementById('plan-json').value = JSON.stringify(editable, null, 2);

  renderPlanForm(editable);

  const hasCode = planHasCodeStep(editable.steps);
  const warnEl = document.getElementById('plan-form-warning');
  if (hasCode) {
    warnEl.textContent = 'This plan contains code steps which the form editor doesn\'t handle yet. Switch to JSON mode to edit them.';
    warnEl.hidden = false;
  } else {
    warnEl.hidden = true;
  }

  // Default to canvas — it handles code/loop/branch as read-only blocks.
  canvasLoadPlan(editable);
  setPlanMode('canvas');
}

function planHasCodeStep(steps) {
  for (const s of (steps || [])) {
    if (s.type === 'code') return true;
    if (s.type === 'loop' && planHasCodeStep(s.body)) return true;
    if (s.type === 'branch' && (s.paths || []).some(p => planHasCodeStep(p.body))) return true;
  }
  return false;
}

function renderPlanForm(plan) {

  document.getElementById('plan-form-name').value        = plan.name || '';
  document.getElementById('plan-form-id').value          = plan.id || '';
  document.getElementById('plan-form-description').value = plan.description || '';
  document.getElementById('plan-form-outputStep').value  = plan.outputStep || '';

  const paramsEl = document.getElementById('plan-form-params');
  paramsEl.innerHTML = '';
  (plan.params || []).forEach(p => paramsEl.appendChild(paramRow(p)));

  const stepsEl = document.getElementById('plan-form-steps');
  stepsEl.innerHTML = '';
  (plan.steps || []).forEach(s => stepsEl.appendChild(stepRow(s)));
}

function paramRow(param) {

  const p = param || { name: '', description: '', required: false };
  const row = document.createElement('div');
  row.className = 'editor-row';
  row.innerHTML = `
    <div class="form-row-inline">
      <div class="form-row" style="flex:1"><input data-field="name" placeholder="Name" value="${escapeAttr(p.name || '')}"></div>
      <div class="form-row" style="flex:2"><input data-field="description" placeholder="Description" value="${escapeAttr(p.description || '')}"></div>
      <label class="toggle-inline"><input type="checkbox" data-field="required" ${p.required ? 'checked' : ''}> Required</label>
      <button type="button" class="card-btn card-btn-danger editor-row-remove" onclick="this.closest('.editor-row').remove()">×</button>
    </div>`;
  return row;
}

function addParamRow() {
  document.getElementById('plan-form-params').appendChild(paramRow(null));
}

function stepRow(step) {

  const s = step || { type: 'agent', name: '' };
  const type = s.type || 'agent';

  const row = document.createElement('div');
  row.className = 'editor-row step-row';
  row.dataset.stepType = type;
  row.innerHTML = stepRowHeaderHtml(s, type) + stepRowBodyHtml(s, type);

  // Populate nested bodies (loop.body, branch.paths[].body) recursively.
  if (type === 'loop') {
    const rows = row.querySelector(':scope > .step-loop-body > .step-body-rows');
    (s.body || []).forEach(child => rows.appendChild(stepRow(child)));
  }
  else if (type === 'branch') {
    const paths = row.querySelector(':scope > .step-branch-paths > .step-paths-rows');
    (s.paths || []).forEach(p => paths.appendChild(branchPathRow(p)));
  }

  return row;
}

function stepRowHeaderHtml(s, type) {
  const typeOpts = ['agent', 'loop', 'branch']
    .map(t => `<option value="${t}" ${t === type ? 'selected' : ''}>${t}</option>`)
    .join('');

  return `
    <div class="step-row-header">
      <input class="step-name" data-field="name" placeholder="Step name" value="${escapeAttr(s.name || '')}">
      <select class="step-type-select" onchange="changeStepType(this)">${typeOpts}</select>
      <span class="step-row-actions">
        <button type="button" class="card-btn" onclick="moveStep(this,-1)">↑</button>
        <button type="button" class="card-btn" onclick="moveStep(this,1)">↓</button>
        <button type="button" class="card-btn card-btn-danger" onclick="this.closest('.editor-row').remove()">×</button>
      </span>
    </div>`;
}

function stepRowBodyHtml(s, type) {
  if (type === 'agent') return agentStepBody(s);
  if (type === 'loop')  return loopStepBody(s);
  if (type === 'branch') return branchStepBody(s);
  return `<div class="form-row"><p class="form-help">Step type "${escapeHtml(type)}" is not editable in form mode — switch to JSON.</p></div>`;
}

function agentStepBody(s) {
  const agentOpts = ['<option value=""></option>']
    .concat(_planAgentChoices.map(a => `<option value="${escapeAttr(a)}" ${a === s.agentId ? 'selected' : ''}>${escapeHtml(a)}</option>`))
    .join('');

  return `
    <div class="form-row-inline">
      <div class="form-row" style="flex:1">
        <select data-field="agentId">${agentOpts}</select>
      </div>
      <div class="form-row" style="flex:1">
        <input data-field="skills" placeholder="Skills (comma-separated)" value="${escapeAttr((s.skills || []).join(', '))}">
      </div>
    </div>
    <div class="form-row">
      <textarea data-field="instructions" rows="3" placeholder="Instructions">${escapeHtml(s.instructions || '')}</textarea>
    </div>
    <div class="form-row-inline">
      <div class="form-row" style="flex:2">
        <input data-field="dependencies" placeholder="Dependencies (comma-separated step names)" value="${escapeAttr((s.dependencies || []).join(', '))}">
      </div>
      <div class="form-row" style="flex:1">
        <input data-field="tools" placeholder="Tools (comma-separated)" value="${escapeAttr((s.tools || []).join(', '))}">
      </div>
      <label class="toggle-inline"><input type="checkbox" data-field="hitl" ${s.hitl ? 'checked' : ''}> HITL</label>
    </div>`;
}

function loopStepBody(s) {
  return `
    <div class="form-row-inline">
      <div class="form-row" style="flex:1">
        <input data-field="over" placeholder="Over (step name or param name)" value="${escapeAttr(s.over || '')}">
      </div>
      <div class="form-row" style="flex:1">
        <input data-field="dependencies" placeholder="Dependencies (comma-separated)" value="${escapeAttr((s.dependencies || []).join(', '))}">
      </div>
      <label class="toggle-inline"><input type="checkbox" data-field="hitl" ${s.hitl ? 'checked' : ''}> HITL</label>
    </div>
    <div class="step-loop-body nested-body">
      <div class="nested-body-label">Body (run per item)</div>
      <div class="step-body-rows editor-rows"></div>
      <button type="button" class="card-btn" onclick="addNestedStep(this)">+ Add step</button>
    </div>`;
}

function branchStepBody(s) {
  return `
    <div class="form-row-inline">
      <div class="form-row" style="flex:1">
        <input data-field="from" placeholder="From (step name or param name)" value="${escapeAttr(s.from || '')}">
      </div>
      <div class="form-row" style="flex:1">
        <input data-field="defaultPath" placeholder="Default path" value="${escapeAttr(s.defaultPath || '')}">
      </div>
      <div class="form-row" style="flex:1">
        <input data-field="dependencies" placeholder="Dependencies (comma-separated)" value="${escapeAttr((s.dependencies || []).join(', '))}">
      </div>
      <label class="toggle-inline"><input type="checkbox" data-field="hitl" ${s.hitl ? 'checked' : ''}> HITL</label>
    </div>
    <div class="step-branch-paths nested-body">
      <div class="nested-body-label">Paths</div>
      <div class="step-paths-rows editor-rows"></div>
      <button type="button" class="card-btn" onclick="addBranchPath(this)">+ Add path</button>
    </div>`;
}

function branchPathRow(path) {
  const p = path || { pathName: '', body: [] };
  const el = document.createElement('div');
  el.className = 'editor-row step-branch-path';
  el.innerHTML = `
    <div class="step-row-header">
      <input class="step-name" data-field="pathName" placeholder="Path name (e.g. low / high)" value="${escapeAttr(p.pathName || '')}">
      <span class="step-row-actions">
        <button type="button" class="card-btn card-btn-danger" onclick="this.closest('.step-branch-path').remove()">×</button>
      </span>
    </div>
    <div class="nested-body">
      <div class="nested-body-label">Path body</div>
      <div class="step-body-rows editor-rows"></div>
      <button type="button" class="card-btn" onclick="addNestedStep(this)">+ Add step</button>
    </div>`;
  const body = el.querySelector(':scope > .nested-body > .step-body-rows');
  (p.body || []).forEach(child => body.appendChild(stepRow(child)));
  return el;
}

function addStepRow() {
  const count = document.querySelectorAll('#plan-form-steps > .editor-row').length + 1;
  document.getElementById('plan-form-steps').appendChild(stepRow({ type: 'agent', name: 'step-' + count }));
}

function addNestedStep(btn) {
  // Button's previous sibling is the .step-body-rows container.
  const container = btn.previousElementSibling;
  const count = container.querySelectorAll(':scope > .editor-row').length + 1;
  container.appendChild(stepRow({ type: 'agent', name: 'step-' + count }));
}

function addBranchPath(btn) {
  const container = btn.previousElementSibling;
  container.appendChild(branchPathRow(null));
}

function moveStep(btn, direction) {
  const row = btn.closest('.editor-row');
  if (direction < 0 && row.previousElementSibling) row.parentNode.insertBefore(row, row.previousElementSibling);
  if (direction > 0 && row.nextElementSibling)     row.parentNode.insertBefore(row.nextElementSibling, row);
}

function changeStepType(select) {
  const row = select.closest('.step-row');
  const current = gatherStep(row) || {};
  const newType = select.value;

  // Preserve common fields across the switch; type-specific fields reset.
  const stub = {
    type: newType,
    name: current.name || '',
    dependencies: current.dependencies || [],
    hitl: !!current.hitl
  };

  if (newType === 'agent') {
    stub.agentId = current.agentId || '';
    stub.instructions = current.instructions || '';
    stub.skills = current.skills || [];
    stub.tools  = current.tools  || [];
  }
  else if (newType === 'loop') {
    stub.over = current.over || '';
    stub.body = current.body || [];
  }
  else if (newType === 'branch') {
    stub.from = current.from || '';
    stub.defaultPath = current.defaultPath || '';
    stub.paths = current.paths || [];
  }

  row.replaceWith(stepRow(stub));
}

// --- Gather form → JSON ---

const _csvToList = v => v ? v.split(',').map(x => x.trim()).filter(Boolean) : [];

function gatherStep(row) {
  if (!row) return null;
  const type = row.dataset.stepType;

  const header = row.querySelector(':scope > .step-row-header');
  const name   = header.querySelector('[data-field="name"]').value.trim();

  // Shared fields live in the row's immediate children (not in nested bodies).
  const field = (sel) => row.querySelector(':scope > * ' + sel);

  const hitlEl = row.querySelector(':scope > .form-row-inline [data-field="hitl"]');
  const depsEl = row.querySelector(':scope > .form-row-inline [data-field="dependencies"]');
  const hitl = hitlEl ? hitlEl.checked : false;
  const deps = _csvToList(depsEl ? depsEl.value : '');

  if (type === 'agent') {
    const agentSel = row.querySelector(':scope > .form-row-inline [data-field="agentId"]');
    return {
      type: 'agent',
      name,
      agentId:      agentSel ? agentSel.value.trim() : '',
      instructions: row.querySelector(':scope > .form-row [data-field="instructions"]')?.value || '',
      dependencies: deps,
      skills:       _csvToList(row.querySelector(':scope > .form-row-inline [data-field="skills"]')?.value || ''),
      tools:        _csvToList(row.querySelector(':scope > .form-row-inline [data-field="tools"]')?.value || ''),
      hitl
    };
  }

  if (type === 'loop') {
    const bodyRows = row.querySelectorAll(':scope > .step-loop-body > .step-body-rows > .editor-row');
    return {
      type: 'loop',
      name,
      over:         row.querySelector(':scope > .form-row-inline [data-field="over"]')?.value.trim() || '',
      body:         Array.from(bodyRows).map(gatherStep).filter(Boolean),
      dependencies: deps,
      hitl
    };
  }

  if (type === 'branch') {
    const paths = Array.from(row.querySelectorAll(':scope > .step-branch-paths > .step-paths-rows > .step-branch-path')).map(p => {
      const nameInput = p.querySelector(':scope > .step-row-header [data-field="pathName"]');
      const bodyRows  = p.querySelectorAll(':scope > .nested-body > .step-body-rows > .editor-row');
      return {
        pathName: nameInput ? nameInput.value.trim() : '',
        body:     Array.from(bodyRows).map(gatherStep).filter(Boolean)
      };
    }).filter(p => p.pathName);

    return {
      type: 'branch',
      name,
      from:         row.querySelector(':scope > .form-row-inline [data-field="from"]')?.value.trim() || '',
      defaultPath:  row.querySelector(':scope > .form-row-inline [data-field="defaultPath"]')?.value.trim() || null,
      paths,
      dependencies: deps,
      hitl
    };
  }

  return null;
}

function gatherFormToPlan() {

  const params = Array.from(document.querySelectorAll('#plan-form-params > .editor-row')).map(r => ({
    name:        r.querySelector('[data-field="name"]').value.trim(),
    description: r.querySelector('[data-field="description"]').value.trim() || null,
    required:    r.querySelector('[data-field="required"]').checked
  })).filter(p => p.name);

  const steps = Array.from(document.querySelectorAll('#plan-form-steps > .editor-row'))
    .map(gatherStep)
    .filter(Boolean);

  return {
    name:        document.getElementById('plan-form-name').value.trim(),
    description: document.getElementById('plan-form-description').value.trim(),
    id:          document.getElementById('plan-form-id').value.trim(),
    outputStep:  document.getElementById('plan-form-outputStep').value.trim() || null,
    params,
    steps
  };
}

function setPlanMode(mode) {
  _planMode = mode;
  document.querySelectorAll('.mode-btn').forEach(b => b.classList.toggle('active', b.dataset.mode === mode));
  document.getElementById('plan-form-view').hidden   = mode !== 'form';
  document.getElementById('plan-canvas-view').hidden = mode !== 'canvas';
  document.getElementById('plan-json-view').hidden   = mode !== 'json';
  if (mode === 'canvas') canvasRender();
}

function switchPlanMode(mode) {
  if (mode === _planMode) return;

  // Gather current plan from the source view we're leaving.
  let plan = null;
  if (_planMode === 'form')        plan = gatherFormToPlan();
  else if (_planMode === 'canvas') plan = gatherCanvasToPlan();
  else if (_planMode === 'json') {
    try { plan = JSON.parse(document.getElementById('plan-json').value); }
    catch (e) { toast('Invalid JSON — fix it first to switch modes', 'error'); return; }
  }

  // Populate the target view.
  if (mode === 'json') {
    document.getElementById('plan-json').value = JSON.stringify(plan, null, 2);
  } else if (mode === 'form') {
    renderPlanForm(plan);
    const hasComplex = (plan.steps || []).some(s => s.type && s.type !== 'agent');
    const warnEl = document.getElementById('plan-form-warning');
    if (hasComplex) {
      warnEl.textContent = 'This plan contains loop/branch/code steps. The form editor handles only agent steps; the others will be dropped if you save from form mode.';
      warnEl.hidden = false;
    } else {
      warnEl.hidden = true;
    }
  } else if (mode === 'canvas') {
    canvasLoadPlan(plan);
  }
  setPlanMode(mode);
}

function closePlanEditor() {
  _planEditRef = null;
  _planMode = 'canvas';
  location.hash = 'plans';
}

async function submitPlanForm(event) {
  event.preventDefault();

  const errEl = document.getElementById('plan-error');
  errEl.hidden = true;

  let plan;
  if (_planMode === 'form') {
    plan = gatherFormToPlan();
  } else if (_planMode === 'canvas') {
    plan = gatherCanvasToPlan();
  } else {
    try {
      plan = JSON.parse(document.getElementById('plan-json').value);
    } catch (e) {
      errEl.textContent = 'Invalid JSON: ' + e.message;
      errEl.hidden = false;
      return;
    }
  }

  const isEdit = !!_planEditRef;
  const url = API + '/plans' + (isEdit ? '/' + encodeURIComponent(_planEditRef) : '');
  const method = isEdit ? 'PUT' : 'POST';

  try {
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ plan })
    });

    if (res.ok) {
      closePlanEditor();
      toast(isEdit ? 'Updated' : 'Created', 'success');
      return;
    }

    const err = await res.json().catch(() => ({ message: 'Error ' + res.status }));
    if (err.code === 'invalid_plan' && err.referring && err.referring.length) {
      errEl.innerHTML = 'Validation failed:<ul>' + err.referring.map(r => '<li>' + escapeHtml(r) + '</li>').join('') + '</ul>';
    } else {
      errEl.textContent = err.message || 'Save failed';
    }
    errEl.hidden = false;
  } catch (e) {
    errEl.textContent = 'Network error: ' + e.message;
    errEl.hidden = false;
  }
}

// === Canvas (drag-and-drop) plan editor ===
// v1 scope: agent steps only; loop/branch nodes pass through read-only.
// Positions are local-only (localStorage by plan id); not part of the plan schema.

const CANVAS_NODE_W = 200;
const CANVAS_NODE_H = 88;
const CANVAS_LAYER_GAP_X = 40;
const CANVAS_LAYER_GAP_Y = 20;
const CANVAS_PAD = 16;
const CANVAS_HANDLE_INSET = 7; // distance from node edge to outer edge of handle circle

let _canvasState = {
  plan: null,
  positions: new Map(),
  selected: null,
  connecting: null,   // { fromStep, x, y }
  dragging: null      // { stepName, offsetX, offsetY }
};

function canvasStorageKey() {
  const id = (_canvasState.plan && _canvasState.plan.id) || '_new';
  return 'agentican.canvas.' + id;
}

function canvasSavePositions() {
  try {
    const obj = {};
    _canvasState.positions.forEach((v, k) => { obj[k] = v; });
    localStorage.setItem(canvasStorageKey(), JSON.stringify(obj));
  } catch (e) { /* localStorage unavailable; ignore */ }
}

function canvasLoadPositions() {
  try {
    const raw = localStorage.getItem(canvasStorageKey());
    if (!raw) return new Map();
    const obj = JSON.parse(raw);
    return new Map(Object.entries(obj));
  } catch (e) { return new Map(); }
}

function canvasLoadPlan(plan) {
  _canvasState.plan = JSON.parse(JSON.stringify(plan || BLANK_PLAN()));
  _canvasState.positions = canvasLoadPositions();
  _canvasState.selected = null;
  _canvasState.connecting = null;
  _canvasState.dragging = null;
  canvasAutoLayoutMissing();

  // Sync the canvas-view header inputs from the plan.
  const n  = document.getElementById('plan-canvas-name');
  const e  = document.getElementById('plan-canvas-id');
  const o  = document.getElementById('plan-canvas-outputStep');
  if (n) n.value = _canvasState.plan.name       || '';
  if (e) e.value = _canvasState.plan.id         || '';
  if (o) o.value = _canvasState.plan.outputStep || '';

  const hasNonAgent = (_canvasState.plan.steps || []).some(s => s.type && s.type !== 'agent');
  const warnEl = document.getElementById('plan-canvas-warning');
  if (hasNonAgent) {
    warnEl.textContent = 'Canvas mode edits only agent steps. Loop / branch / code steps are shown as read-only blocks and preserved on save.';
    warnEl.hidden = false;
  } else {
    warnEl.hidden = true;
  }
}

function canvasUpdatePlanMeta() {
  if (!_canvasState.plan) return;
  const n = document.getElementById('plan-canvas-name');
  const e = document.getElementById('plan-canvas-id');
  const o = document.getElementById('plan-canvas-outputStep');
  if (n) _canvasState.plan.name       = n.value.trim();
  if (e) _canvasState.plan.id         = e.value.trim();
  if (o) _canvasState.plan.outputStep = o.value.trim();
}

function gatherCanvasToPlan() {
  canvasUpdatePlanMeta();
  return _canvasState.plan ? JSON.parse(JSON.stringify(_canvasState.plan)) : BLANK_PLAN();
}

function canvasAutoLayoutMissing() {
  // Run topological layering on the top-level steps; assign positions only to steps
  // that don't already have one (preserves user-dragged positions across reloads).
  const steps = (_canvasState.plan.steps || []);
  if (steps.length === 0) return;
  const layers = computePlanLayers(steps);
  layers.forEach((layer, li) => {
    layer.forEach((step, ri) => {
      if (_canvasState.positions.has(step.name)) return;
      _canvasState.positions.set(step.name, {
        x: CANVAS_PAD + li * (CANVAS_NODE_W + CANVAS_LAYER_GAP_X),
        y: CANVAS_PAD + ri * (CANVAS_NODE_H + CANVAS_LAYER_GAP_Y)
      });
    });
  });
}

function canvasAutoLayout() {
  // Force a fresh layout for every step.
  _canvasState.positions.clear();
  canvasAutoLayoutMissing();
  canvasSavePositions();
  canvasRender();
}

function canvasAddAgentStep() {
  const steps = _canvasState.plan.steps = _canvasState.plan.steps || [];
  let n = steps.length + 1;
  let name = 'step-' + n;
  while (steps.some(s => s.name === name)) { n++; name = 'step-' + n; }
  steps.push({
    type: 'agent', name,
    agentId: '', instructions: '',
    skills: [], tools: [], dependencies: [], hitl: false
  });
  // Drop new step centered in the visible workspace.
  const ws = document.getElementById('plan-canvas-workspace');
  const wsRect = ws.getBoundingClientRect();
  _canvasState.positions.set(name, {
    x: Math.max(CANVAS_PAD, (ws.scrollLeft || 0) + wsRect.width / 2 - CANVAS_NODE_W / 2),
    y: Math.max(CANVAS_PAD, (ws.scrollTop  || 0) + wsRect.height / 2 - CANVAS_NODE_H / 2)
  });
  canvasSavePositions();
  _canvasState.selected = name;
  canvasRender();
}

function canvasDeleteStep(name) {
  const steps = _canvasState.plan.steps || [];
  const idx = steps.findIndex(s => s.name === name);
  if (idx < 0) return;
  steps.splice(idx, 1);
  // Strip references to the deleted step from other steps' dependencies / over / from.
  steps.forEach(s => {
    if (Array.isArray(s.dependencies)) s.dependencies = s.dependencies.filter(d => d !== name);
    if (s.over === name) s.over = '';
    if (s.from === name) s.from = '';
  });
  if (_canvasState.plan.outputStep === name) _canvasState.plan.outputStep = '';
  _canvasState.positions.delete(name);
  if (_canvasState.selected === name) _canvasState.selected = null;
  canvasSavePositions();
  canvasRender();
}

function canvasRenameStep(oldName, newName) {
  newName = (newName || '').trim();
  if (!newName || newName === oldName) return false;
  const steps = _canvasState.plan.steps || [];
  if (steps.some(s => s.name === newName)) { toast('A step named "' + newName + '" already exists', 'error'); return false; }
  const step = steps.find(s => s.name === oldName);
  if (!step) return false;
  step.name = newName;
  steps.forEach(s => {
    if (Array.isArray(s.dependencies))
      s.dependencies = s.dependencies.map(d => d === oldName ? newName : d);
    if (s.over === oldName) s.over = newName;
    if (s.from === oldName) s.from = newName;
  });
  if (_canvasState.plan.outputStep === oldName) _canvasState.plan.outputStep = newName;
  const pos = _canvasState.positions.get(oldName);
  if (pos) { _canvasState.positions.delete(oldName); _canvasState.positions.set(newName, pos); }
  if (_canvasState.selected === oldName) _canvasState.selected = newName;
  canvasSavePositions();
  return true;
}

function canvasAddDependency(fromName, toName) {
  if (!fromName || !toName || fromName === toName) return;
  const target = (_canvasState.plan.steps || []).find(s => s.name === toName);
  if (!target) return;
  // Reject if it'd create a cycle.
  if (canvasCreatesCycle(fromName, toName)) {
    toast('That edge would create a cycle', 'error');
    return;
  }
  target.dependencies = target.dependencies || [];
  if (!target.dependencies.includes(fromName)) target.dependencies.push(fromName);
}

function canvasCreatesCycle(fromName, toName) {
  // If fromName is reachable from toName via existing edges, adding fromName→toName closes a cycle.
  const steps = _canvasState.plan.steps || [];
  const byName = new Map(steps.map(s => [s.name, s]));
  const visited = new Set();
  const stack = [toName];
  while (stack.length) {
    const cur = stack.pop();
    if (cur === fromName) return true;
    if (visited.has(cur)) continue;
    visited.add(cur);
    if (!byName.has(cur)) continue;
    steps.forEach(other => {
      const deps = other.dependencies || [];
      if (deps.includes(cur)) stack.push(other.name);
      if (other.type === 'loop' && other.over === cur) stack.push(other.name);
      if (other.type === 'branch' && other.from === cur) stack.push(other.name);
    });
  }
  return false;
}

function canvasEdges() {
  // Yield { from, to, kind } for every edge implied by the plan.
  const edges = [];
  (_canvasState.plan.steps || []).forEach(s => {
    (s.dependencies || []).forEach(d => edges.push({ from: d, to: s.name, kind: 'dep' }));
    if (s.type === 'loop' && s.over)   edges.push({ from: s.over, to: s.name, kind: 'over' });
    if (s.type === 'branch' && s.from) edges.push({ from: s.from, to: s.name, kind: 'from' });
  });
  return edges;
}

function canvasRender() {
  const nodesEl  = document.getElementById('plan-canvas-nodes');
  const edgesEl  = document.getElementById('plan-canvas-edges');
  if (!nodesEl || !edgesEl) return;

  const steps = _canvasState.plan && _canvasState.plan.steps ? _canvasState.plan.steps : [];

  // Pre-compute which handles are connected so we can highlight them.
  const edges = canvasEdges();
  const sourceNodes = new Set(edges.map(e => e.from));
  const targetNodes = new Set(edges.map(e => e.to));

  // Nodes
  nodesEl.innerHTML = steps.map(s => {
    const pos = _canvasState.positions.get(s.name) || { x: CANVAS_PAD, y: CANVAS_PAD };
    const isAgent = !s.type || s.type === 'agent';
    const isSelected = _canvasState.selected === s.name;
    const agentLabel = isAgent && s.agentId
      ? (_agentNameById.get(s.agentId) || s.agentId)
      : '';
    const typeBadge = !isAgent
      ? `<span class="canvas-node-type-badge">${escapeHtml(s.type)}</span>` : '';
    const hitl = s.hitl ? '<span class="canvas-node-flag" title="Requires approval">&#9873;</span>' : '';
    const subtitle = isAgent
      ? (agentLabel ? `<span class="canvas-node-agent">${escapeHtml(agentLabel)}</span>` : '<span class="canvas-node-agent muted">(no agent)</span>')
      : `<span class="canvas-node-agent muted">read-only</span>`;
    const inCls  = targetNodes.has(s.name) ? ' canvas-handle-connected' : '';
    const outCls = sourceNodes.has(s.name) ? ' canvas-handle-connected' : '';
    return `
      <div class="canvas-node${isAgent ? '' : ' canvas-node-readonly'}${isSelected ? ' selected' : ''}"
           data-step="${escapeAttr(s.name)}"
           style="left:${pos.x}px; top:${pos.y}px; width:${CANVAS_NODE_W}px; height:${CANVAS_NODE_H}px">
        <div class="canvas-node-handle canvas-handle-in${inCls}"   data-step="${escapeAttr(s.name)}" data-handle="in"></div>
        <div class="canvas-node-handle canvas-handle-out${outCls}" data-step="${escapeAttr(s.name)}" data-handle="out"></div>
        <div class="canvas-node-header">
          <span class="canvas-node-name">${escapeHtml(s.name)}</span>
          ${typeBadge}${hitl}
        </div>
        <div class="canvas-node-body">${subtitle}</div>
      </div>`;
  }).join('');

  // SVG size + viewport
  const bounds = canvasBounds();
  edgesEl.setAttribute('width',  bounds.w);
  edgesEl.setAttribute('height', bounds.h);
  edgesEl.style.width  = bounds.w + 'px';
  edgesEl.style.height = bounds.h + 'px';

  // Edges (preserve defs/marker by clearing only paths)
  edgesEl.querySelectorAll('path.canvas-edge, path.canvas-edge-temp').forEach(p => p.remove());
  edges.forEach(e => {
    const a = _canvasState.positions.get(e.from);
    const b = _canvasState.positions.get(e.to);
    if (!a || !b) return;
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', canvasEdgePath(
      a.x + CANVAS_NODE_W + CANVAS_HANDLE_INSET, a.y + CANVAS_NODE_H / 2,
      b.x - CANVAS_HANDLE_INSET,                 b.y + CANVAS_NODE_H / 2
    ));
    path.setAttribute('class', 'canvas-edge canvas-edge-' + e.kind);
    path.setAttribute('marker-end', 'url(#canvas-arrow)');
    path.dataset.from = e.from;
    path.dataset.to   = e.to;
    path.dataset.kind = e.kind;
    edgesEl.appendChild(path);
  });

  // Temp connect path
  if (_canvasState.connecting) {
    const a = _canvasState.positions.get(_canvasState.connecting.fromStep);
    if (a) {
      const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      path.setAttribute('d', canvasEdgePath(
        a.x + CANVAS_NODE_W + CANVAS_HANDLE_INSET, a.y + CANVAS_NODE_H / 2,
        _canvasState.connecting.x, _canvasState.connecting.y
      ));
      path.setAttribute('class', 'canvas-edge-temp');
      edgesEl.appendChild(path);
    }
  }

  canvasRenderInspector();
}

function canvasBounds() {
  // Size the SVG to actual node extent, not to the workspace. Defaulting to
  // workspace clientWidth × clientHeight made the SVG match the visible area
  // exactly and any sub-pixel/scrollbar-gutter wobble tipped it into "overflowing"
  // — both scrollbars would appear with just a single step on screen.
  let maxX = 0, maxY = 0;
  _canvasState.positions.forEach(p => {
    if (p.x + CANVAS_NODE_W > maxX) maxX = p.x + CANVAS_NODE_W;
    if (p.y + CANVAS_NODE_H > maxY) maxY = p.y + CANVAS_NODE_H;
  });
  return { w: Math.max(maxX, 1), h: Math.max(maxY, 1) };
}

function canvasEdgePath(x1, y1, x2, y2) {
  const dx = Math.max(40, Math.abs(x2 - x1) / 2);
  return `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`;
}

function canvasRenderInspector() {
  const el = document.getElementById('plan-canvas-inspector');
  const name = _canvasState.selected;
  const step = name ? (_canvasState.plan.steps || []).find(s => s.name === name) : null;
  if (!step) { el.hidden = true; el.innerHTML = ''; return; }
  el.hidden = false;

  if (step.type && step.type !== 'agent') {
    el.innerHTML = `
      <div class="canvas-inspector-header">
        <h3>${escapeHtml(step.name)}</h3>
        <button class="modal-close" onclick="canvasSelectStep(null)">×</button>
      </div>
      <p class="form-help">${escapeHtml(step.type)} steps aren't editable in canvas mode yet. Switch to the JSON editor.</p>
    `;
    return;
  }

  el.innerHTML = `
    <div class="canvas-inspector-header">
      <h3>Step</h3>
      <button class="modal-close" onclick="canvasSelectStep(null)">×</button>
    </div>
    <div class="form-row">
      <input id="canvas-insp-name" type="text" placeholder="Step name" value="${escapeAttr(step.name)}" onchange="canvasInspectorRename(this.value)">
    </div>
    <div class="form-row">
      <div class="canvas-picker-wrapper">
        <input type="text" class="canvas-picker-input" id="canvas-insp-agent-input"
               placeholder="Agent" autocomplete="off"
               value="${escapeAttr(step.agentId || '')}"
               onfocus="canvasPickerOpen('agent', true)"
               oninput="canvasPickerOpen('agent', false)"
               onblur="canvasPickerClose('agent')">
        <div class="canvas-picker-list" id="canvas-insp-agent-list"></div>
      </div>
    </div>
    <div class="form-row">
      <textarea id="canvas-insp-instructions" rows="5" placeholder="Instructions" oninput="canvasInspectorField('instructions', this.value)">${escapeHtml(step.instructions || '')}</textarea>
    </div>
    <div class="form-row">
      <div class="canvas-chips" id="canvas-insp-skills-chips">${canvasInspectorSkillChips(step)}</div>
      <div class="canvas-picker-wrapper">
        <input type="text" class="canvas-picker-input" id="canvas-insp-skills-input"
               placeholder="Add skill" autocomplete="off"
               onfocus="canvasPickerOpen('skills', true)"
               oninput="canvasPickerOpen('skills', false)"
               onblur="canvasPickerClose('skills')">
        <div class="canvas-picker-list" id="canvas-insp-skills-list"></div>
      </div>
    </div>
    <div class="form-row">
      <div class="canvas-chips" id="canvas-insp-tools-chips">${canvasInspectorToolChips(step)}</div>
      <div class="canvas-picker-wrapper">
        <input type="text" class="canvas-picker-input" id="canvas-insp-tools-input"
               placeholder="Add tool" autocomplete="off"
               onfocus="canvasPickerOpen('tools', true)"
               oninput="canvasPickerOpen('tools', false)"
               onblur="canvasPickerClose('tools')">
        <div class="canvas-picker-list" id="canvas-insp-tools-list"></div>
      </div>
    </div>
    <div class="form-row">
      <label class="toggle-inline"><input type="checkbox" ${step.hitl ? 'checked' : ''} onchange="canvasInspectorField('hitl', this.checked)"> HITL approval</label>
    </div>
    <div class="form-row canvas-inspector-actions">
      <button type="button" class="card-btn card-btn-danger" onclick="canvasDeleteSelected()">Delete step</button>
    </div>
  `;
}

function canvasSelectStep(name) {
  _canvasState.selected = name || null;
  canvasRender();
}

function canvasInspectorField(field, value, transform) {
  const step = (_canvasState.plan.steps || []).find(s => s.name === _canvasState.selected);
  if (!step) return;
  if (transform === 'csv') step[field] = _csvToList(value);
  else                     step[field] = value;
  // No full re-render for simple text edits — but nodes show agent/hitl/name → re-render lightly.
  if (field === 'agentId' || field === 'hitl') canvasRender();
}

function canvasInspectorRename(newName) {
  const old = _canvasState.selected;
  if (canvasRenameStep(old, newName)) canvasRender();
  else if (old) {
    // Revert input on collision.
    const input = document.getElementById('canvas-insp-name');
    if (input) input.value = old;
  }
}

function canvasDeleteSelected() {
  const name = _canvasState.selected;
  if (!name) return;
  if (!confirm('Delete step “' + name + '”?')) return;
  canvasDeleteStep(name);
}

// --- Inspector pickers: skills + tools ---

function canvasInspectorSkillChips(step) {
  return (step.skills || []).map(s =>
    `<span class="canvas-chip">${escapeHtml(s)}<button type="button" onclick="canvasInspectorRemoveSkill('${escapeAttr(s)}')">×</button></span>`
  ).join('');
}

function canvasInspectorToolChips(step) {
  return (step.tools || []).map(t => {
    const choice = _planToolChoices.find(c => c.name === t);
    const display = choice ? choice.label : t;
    return `<span class="canvas-chip">${escapeHtml(display)}<button type="button" onclick="canvasInspectorRemoveTool('${escapeAttr(t)}')">×</button></span>`;
  }).join('');
}

// Open / re-filter the picker dropdown. `which` is 'agent', 'skills', or 'tools'.
function canvasPickerOpen(which, showAll) {
  const input = document.getElementById('canvas-insp-' + which + '-input');
  const list  = document.getElementById('canvas-insp-' + which + '-list');
  if (!input || !list) return;
  const step = (_canvasState.plan.steps || []).find(s => s.name === _canvasState.selected);
  if (!step) return;

  const q = input.value.trim().toLowerCase();
  const match = (text) => showAll || !q || (text || '').toLowerCase().includes(q);

  let html;
  if (which === 'agent') {
    const items = _planAgentChoices.filter(a => match(a));
    html = items.length === 0
      ? '<div class="canvas-picker-option canvas-picker-empty">No agents</div>'
      : '<div class="canvas-picker-option" data-value="">(no agent)</div>'
        + items.map(a => `<div class="canvas-picker-option" data-value="${escapeAttr(a)}">${escapeHtml(a)}</div>`).join('');
  } else if (which === 'skills') {
    const picked = new Set(step.skills || []);
    const items = _planSkillChoices.filter(s => !picked.has(s) && match(s));
    html = items.length === 0
      ? '<div class="canvas-picker-option canvas-picker-empty">No skills</div>'
      : items.map(s => `<div class="canvas-picker-option" data-value="${escapeAttr(s)}">${escapeHtml(s)}</div>`).join('');
  } else {
    const picked = new Set(step.tools || []);
    const items = _planToolChoices.filter(c => !picked.has(c.name) && (match(c.label) || match(c.toolkit)));
    if (items.length === 0) {
      html = '<div class="canvas-picker-option canvas-picker-empty">No tools</div>';
    } else {
      const byKit = new Map();
      items.forEach(c => {
        const g = c.toolkit || 'Tools';
        if (!byKit.has(g)) byKit.set(g, []);
        byKit.get(g).push(c);
      });
      html = [...byKit.entries()].map(([g, tools]) =>
        `<div class="canvas-picker-group">${escapeHtml(g)}</div>` +
        tools.map(c => `<div class="canvas-picker-option" data-value="${escapeAttr(c.name)}">${escapeHtml(c.label)}</div>`).join('')
      ).join('');
    }
  }
  list.innerHTML = html;
  list.classList.add('canvas-picker-open');

  // Use mousedown (not click) and preventDefault so the input keeps focus —
  // otherwise blur fires before click and onblur closes the list first.
  list.querySelectorAll('.canvas-picker-option[data-value]').forEach(el => {
    el.addEventListener('mousedown', e => {
      e.preventDefault();
      canvasPickerSelect(which, el.dataset.value);
    });
  });
}

function canvasPickerClose(which) {
  // Defer so click→select on an option still fires before close.
  setTimeout(() => {
    const list = document.getElementById('canvas-insp-' + which + '-list');
    if (list) list.classList.remove('canvas-picker-open');
    // Single-select agent picker: restore the input text to the stored value so
    // a stray typed query doesn't linger after the user clicks away.
    if (which === 'agent') {
      const step = (_canvasState.plan.steps || []).find(s => s.name === _canvasState.selected);
      const input = document.getElementById('canvas-insp-agent-input');
      if (step && input) input.value = step.agentId || '';
    }
  }, 150);
}

function canvasPickerSelect(which, value) {
  const step = (_canvasState.plan.steps || []).find(s => s.name === _canvasState.selected);
  if (!step) return;

  if (which === 'agent') {
    step.agentId = value || '';
    const input = document.getElementById('canvas-insp-agent-input');
    const list  = document.getElementById('canvas-insp-agent-list');
    if (input) { input.value = step.agentId; input.blur(); }
    if (list) list.classList.remove('canvas-picker-open');
    canvasRender(); // node card shows the agent label
    return;
  }

  if (!value) return; // chip-additive pickers ignore the empty/clear sentinel
  const field = which === 'skills' ? 'skills' : 'tools';
  step[field] = step[field] || [];
  if (!step[field].includes(value)) step[field].push(value);

  // Refresh chips + re-filter dropdown without losing input focus.
  const chipsEl = document.getElementById('canvas-insp-' + which + '-chips');
  const input   = document.getElementById('canvas-insp-' + which + '-input');
  if (chipsEl) chipsEl.innerHTML = which === 'skills'
    ? canvasInspectorSkillChips(step) : canvasInspectorToolChips(step);
  if (input) { input.value = ''; input.focus(); }
  canvasPickerOpen(which, true);
}

function canvasInspectorRemoveSkill(value) {
  const step = (_canvasState.plan.steps || []).find(s => s.name === _canvasState.selected);
  if (!step || !Array.isArray(step.skills)) return;
  step.skills = step.skills.filter(s => s !== value);
  const chipsEl = document.getElementById('canvas-insp-skills-chips');
  if (chipsEl) chipsEl.innerHTML = canvasInspectorSkillChips(step);
  const list = document.getElementById('canvas-insp-skills-list');
  if (list && list.classList.contains('canvas-picker-open')) canvasPickerOpen('skills', false);
}

function canvasInspectorRemoveTool(value) {
  const step = (_canvasState.plan.steps || []).find(s => s.name === _canvasState.selected);
  if (!step || !Array.isArray(step.tools)) return;
  step.tools = step.tools.filter(t => t !== value);
  const chipsEl = document.getElementById('canvas-insp-tools-chips');
  if (chipsEl) chipsEl.innerHTML = canvasInspectorToolChips(step);
  const list = document.getElementById('canvas-insp-tools-list');
  if (list && list.classList.contains('canvas-picker-open')) canvasPickerOpen('tools', false);
}

// --- Mouse handling: node drag + handle-to-handle connect ---

(function initCanvasMouse() {
  const wsId = 'plan-canvas-workspace';
  document.addEventListener('mousedown', e => {
    const ws = document.getElementById(wsId);
    if (!ws || !ws.contains(e.target)) return;

    // Inspector lives inside the workspace; clicks in it are form interactions, not canvas events.
    if (e.target.closest('.canvas-inspector')) return;

    const handle = e.target.closest('.canvas-node-handle');
    if (handle && handle.dataset.handle === 'out') {
      // Begin connect.
      e.preventDefault();
      const pt = canvasMouseInWorkspace(e);
      _canvasState.connecting = { fromStep: handle.dataset.step, x: pt.x, y: pt.y };
      canvasRender();
      return;
    }
    if (handle) return; // mousedown on input handle: ignore

    const node = e.target.closest('.canvas-node');
    if (node) {
      e.preventDefault();
      const stepName = node.dataset.step;
      // Read-only node: select only.
      const step = (_canvasState.plan.steps || []).find(s => s.name === stepName);
      _canvasState.selected = stepName;
      if (step && step.type && step.type !== 'agent') { canvasRender(); return; }

      const pos = _canvasState.positions.get(stepName) || { x: 0, y: 0 };
      const pt = canvasMouseInWorkspace(e);
      _canvasState.dragging = { stepName, offsetX: pt.x - pos.x, offsetY: pt.y - pos.y };
      canvasRender();
      return;
    }

    // Click empty space → deselect.
    if (_canvasState.selected) { _canvasState.selected = null; canvasRender(); }
  });

  document.addEventListener('mousemove', e => {
    if (_canvasState.dragging) {
      const pt = canvasMouseInWorkspace(e);
      _canvasState.positions.set(_canvasState.dragging.stepName, {
        x: Math.max(0, pt.x - _canvasState.dragging.offsetX),
        y: Math.max(0, pt.y - _canvasState.dragging.offsetY)
      });
      canvasRender();
      return;
    }
    if (_canvasState.connecting) {
      const pt = canvasMouseInWorkspace(e);
      _canvasState.connecting.x = pt.x;
      _canvasState.connecting.y = pt.y;
      canvasRender();
    }
  });

  document.addEventListener('mouseup', e => {
    if (_canvasState.dragging) {
      _canvasState.dragging = null;
      canvasSavePositions();
    }
    if (_canvasState.connecting) {
      const target = e.target.closest('.canvas-node-handle');
      if (target && target.dataset.handle === 'in') {
        canvasAddDependency(_canvasState.connecting.fromStep, target.dataset.step);
      }
      _canvasState.connecting = null;
      canvasRender();
    }
  });
})();

function canvasMouseInWorkspace(e) {
  const ws = document.getElementById('plan-canvas-workspace');
  const r = ws.getBoundingClientRect();
  return { x: e.clientX - r.left + ws.scrollLeft, y: e.clientY - r.top + ws.scrollTop };
}

async function deletePlan(ref, name) {
  if (!confirm('Delete plan “' + name + '”?')) return;

  try {
    const res = await fetch(API + '/plans/' + encodeURIComponent(ref), { method: 'DELETE' });

    if (res.status === 204) {
      loadPlans();
      toast('Deleted', 'success');
      return;
    }

    const err = await res.json().catch(() => ({}));
    toast(err.message || 'Delete failed', 'error');
  } catch (e) {
    toast('Network error: ' + e.message, 'error');
  }
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
    document.getElementById('agents-list').innerHTML = agents.map(a => renderAgentCard(a)).join('');
  } catch (e) {}
}

function renderAgentCard(a) {
  const refKey = a.id;
  const actions = a.declaredInConfig
    ? `<span class="config-badge" title="Declared in application.properties — read-only">config</span>`
    : `<button class="card-btn" onclick="event.stopPropagation(); openAgentModal('${escapeAttr(refKey)}')">Edit</button>
       <button class="card-btn card-btn-danger" onclick="event.stopPropagation(); deleteAgent('${escapeAttr(refKey)}', '${escapeAttr(a.name)}')">Delete</button>`;

  return `
    <div class="agent-card" onclick="this.classList.toggle('expanded')">
      <div class="agent-card-header">
        <span class="agent-card-name">${escapeHtml(a.name)}</span>
        <span class="card-actions">${actions}</span>
      </div>
      <div class="agent-desc">
        ${a.id ? `<div class="agent-field"><span class="agent-field-label">ID</span><div class="agent-field-value" style="font-family:var(--mono);font-size:12px">${escapeHtml(a.id)}</div></div>` : ''}
        ${a.role ? `<div class="agent-field"><span class="agent-field-label">Role</span><div class="agent-field-value">${escapeHtml(a.role)}</div></div>` : ''}
        ${a.llm ? `<div class="agent-field"><span class="agent-field-label">LLM</span><div class="agent-field-value">${escapeHtml(a.llm)}</div></div>` : ''}
      </div>
    </div>`;
}

async function loadSkills() {
  try {
    const res = await fetch(API + '/skills');
    const skills = await res.json();
    _skillNameById = new Map(skills.map(s => [s.id, s.name]));
    skills.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    const list = document.getElementById('skills-list');

    if (skills.length === 0) {
      list.innerHTML = '<div class="card"><p style="color:var(--text-secondary)">No skills registered yet. Click “+ New skill” to add one.</p></div>';
      return;
    }

    list.innerHTML = skills.map(s => renderSkillCard(s)).join('');
  } catch (e) {}
}

function renderSkillCard(s) {
  const refKey = s.id;
  const actions = s.declaredInConfig
    ? `<span class="config-badge" title="Declared in application.properties — read-only">config</span>`
    : `<button class="card-btn" onclick="event.stopPropagation(); openSkillModal('${escapeAttr(refKey)}')">Edit</button>
       <button class="card-btn card-btn-danger" onclick="event.stopPropagation(); deleteSkill('${escapeAttr(refKey)}', '${escapeAttr(s.name)}')">Delete</button>`;

  return `
    <div class="agent-card" onclick="this.classList.toggle('expanded')">
      <div class="agent-card-header">
        <span class="agent-card-name">${escapeHtml(s.name)}</span>
        <span class="card-actions">${actions}</span>
      </div>
      <div class="agent-desc">
        ${s.id ? `<div class="agent-field"><span class="agent-field-label">ID</span><div class="agent-field-value" style="font-family:var(--mono);font-size:12px">${escapeHtml(s.id)}</div></div>` : ''}
        <div class="agent-field">
          <span class="agent-field-label">Instructions</span>
          <div class="agent-field-value" style="white-space:pre-wrap">${escapeHtml(s.instructions)}</div>
        </div>
      </div>
    </div>`;
}

// === Catalog modal (shared for agents + skills) ===

let _modalMode = null;  // 'agent' | 'skill'
let _modalEditRef = null;  // non-null when editing
let _catalogLlmChoices = []; // populated from /config on agent modal open

async function loadCatalogLlmChoices() {
  try {
    const res = await fetch(API + '/config');
    const props = await res.json();
    _catalogLlmChoices = props
      .filter(p => /^agentican\.llm\[\d+\]\.name$/.test(p.name))
      .map(p => (p.value || '').trim())
      .filter(Boolean);
  } catch (e) { _catalogLlmChoices = []; }
}

function openAgentModal(ref) {
  _modalMode = 'agent';
  _modalEditRef = ref || null;

  document.getElementById('catalog-modal-title').textContent = ref ? 'Edit agent' : 'New agent';
  document.getElementById('catalog-role-row').hidden = false;
  document.getElementById('catalog-instructions-row').hidden = true;
  document.getElementById('catalog-llm-row').hidden = false;

  document.getElementById('catalog-role').required = true;
  document.getElementById('catalog-instructions').required = false;

  _resetModalFields();
  loadCatalogLlmChoices();

  if (ref) {
    fetch(API + '/agents/' + encodeURIComponent(ref))
      .then(r => r.json())
      .then(a => {
        document.getElementById('catalog-id').value = a.id || '';
        document.getElementById('catalog-id').disabled = true;
        document.getElementById('catalog-name').value = a.name || '';
        document.getElementById('catalog-role').value = a.role || '';
        document.getElementById('catalog-llm-input').value = a.llm || '';
      })
      .catch(() => toast('Failed to load agent', 'error'));
  } else {
    document.getElementById('catalog-id').disabled = false;
  }

  document.getElementById('catalog-modal').hidden = false;
}

function openSkillModal(ref) {
  _modalMode = 'skill';
  _modalEditRef = ref || null;

  document.getElementById('catalog-modal-title').textContent = ref ? 'Edit skill' : 'New skill';
  document.getElementById('catalog-role-row').hidden = true;
  document.getElementById('catalog-instructions-row').hidden = false;
  document.getElementById('catalog-llm-row').hidden = true;

  document.getElementById('catalog-role').required = false;
  document.getElementById('catalog-instructions').required = true;

  _resetModalFields();

  if (ref) {
    fetch(API + '/skills/' + encodeURIComponent(ref))
      .then(r => r.json())
      .then(s => {
        document.getElementById('catalog-id').value = s.id || '';
        document.getElementById('catalog-id').disabled = true;
        document.getElementById('catalog-name').value = s.name || '';
        document.getElementById('catalog-instructions').value = s.instructions || '';
      })
      .catch(() => toast('Failed to load skill', 'error'));
  } else {
    document.getElementById('catalog-id').disabled = false;
  }

  document.getElementById('catalog-modal').hidden = false;
}

function _resetModalFields() {
  document.getElementById('catalog-form').reset();
  document.getElementById('catalog-error').hidden = true;
  document.getElementById('catalog-error').textContent = '';
}

function closeCatalogModal() {
  document.getElementById('catalog-modal').hidden = true;
  _modalMode = null;
  _modalEditRef = null;
}

async function submitCatalogForm(event) {
  event.preventDefault();

  const errEl = document.getElementById('catalog-error');
  errEl.hidden = true;

  const id   = document.getElementById('catalog-id').value.trim();
  const name = document.getElementById('catalog-name').value.trim();

  const isEdit = !!_modalEditRef;
  const resource = _modalMode === 'agent' ? 'agents' : 'skills';

  let body;
  if (_modalMode === 'agent') {
    const role = document.getElementById('catalog-role').value.trim();
    const llm  = document.getElementById('catalog-llm-input').value.trim();
    body = isEdit
      ? { name, role, llm: llm || null }
      : { id, name, role, llm: llm || null };
  } else {
    const instructions = document.getElementById('catalog-instructions').value.trim();
    body = isEdit
      ? { name, instructions }
      : { id, name, instructions };
  }

  const url = API + '/' + resource + (isEdit ? '/' + encodeURIComponent(_modalEditRef) : '');
  const method = isEdit ? 'PUT' : 'POST';

  try {
    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });

    if (res.ok) {
      closeCatalogModal();
      if (_modalMode === 'agent') loadAgents(); else loadSkills();
      toast(isEdit ? 'Updated' : 'Created', 'success');
      _modalMode = null;
      _modalEditRef = null;
      return;
    }

    const err = await res.json().catch(() => ({ message: 'Error ' + res.status }));
    errEl.textContent = err.message || 'Save failed';
    errEl.hidden = false;
  } catch (e) {
    errEl.textContent = 'Network error: ' + e.message;
    errEl.hidden = false;
  }
}

// --- Catalog LLM picker (same search style as canvas agent/skill pickers) ---

function catalogLlmPickerOpen(showAll) {
  const input = document.getElementById('catalog-llm-input');
  const list  = document.getElementById('catalog-llm-list');
  if (!input || !list) return;

  const q = input.value.trim().toLowerCase();
  const match = (t) => showAll || !q || (t || '').toLowerCase().includes(q);
  const items = _catalogLlmChoices.filter(match);

  const html = items.length === 0
    ? '<div class="canvas-picker-option canvas-picker-empty">No LLMs</div>'
    : items.map(l => `<div class="canvas-picker-option" data-value="${escapeAttr(l)}">${escapeHtml(l)}</div>`).join('');

  list.innerHTML = html;
  list.classList.add('canvas-picker-open');
  list.querySelectorAll('.canvas-picker-option[data-value]').forEach(el => {
    el.addEventListener('mousedown', e => {
      e.preventDefault();
      catalogLlmPickerSelect(el.dataset.value);
    });
  });
}

function catalogLlmPickerClose() {
  setTimeout(() => {
    const list = document.getElementById('catalog-llm-list');
    if (list) list.classList.remove('canvas-picker-open');
  }, 150);
}

function catalogLlmPickerSelect(value) {
  const input = document.getElementById('catalog-llm-input');
  const list  = document.getElementById('catalog-llm-list');
  if (input) { input.value = value || ''; input.blur(); }
  if (list) list.classList.remove('canvas-picker-open');
}

async function deleteAgent(ref, name) {
  if (!confirm('Delete agent “' + name + '”?')) return;

  try {
    const res = await fetch(API + '/agents/' + encodeURIComponent(ref), { method: 'DELETE' });

    if (res.status === 204) {
      loadAgents();
      toast('Deleted', 'success');
      return;
    }

    const err = await res.json().catch(() => ({}));
    if (err.code === 'referenced' && err.referring && err.referring.length) {
      toast('Cannot delete: referenced by ' + err.referring.join(', '), 'error');
    } else {
      toast(err.message || 'Delete failed', 'error');
    }
  } catch (e) {
    toast('Network error: ' + e.message, 'error');
  }
}

async function deleteSkill(ref, name) {
  if (!confirm('Delete skill “' + name + '”?')) return;

  try {
    const res = await fetch(API + '/skills/' + encodeURIComponent(ref), { method: 'DELETE' });

    if (res.status === 204) {
      loadSkills();
      toast('Deleted', 'success');
      return;
    }

    const err = await res.json().catch(() => ({}));
    if (err.code === 'referenced' && err.referring && err.referring.length) {
      toast('Cannot delete: referenced by ' + err.referring.join(', '), 'error');
    } else {
      toast(err.message || 'Delete failed', 'error');
    }
  } catch (e) {
    toast('Network error: ' + e.message, 'error');
  }
}

function escapeAttr(s) {
  return String(s).replace(/"/g, '&quot;').replace(/'/g, '&#39;');
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

    document.getElementById('knowledge-modal').hidden = false;
  } catch (e) { toast('Failed to load', 'error'); }
}

function closeKnowledgeModal() {
  document.getElementById('knowledge-modal').hidden = true;
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
  document.getElementById('turn-modal').hidden = true;
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

  document.getElementById('turn-modal').hidden = false;
}

// === Init ===
{ const { panel, ref } = parseHash(); activatePanel(panel, ref); }
// Prefetch so plan step badges can show display names (not ids) regardless of nav order.
fetch(API + '/agents').then(r => r.ok ? r.json() : []).then(agents => {
  _agentNameById = new Map(agents.map(a => [a.id, a.name]));
}).catch(() => {});
fetch(API + '/skills').then(r => r.ok ? r.json() : []).then(skills => {
  _skillNameById = new Map(skills.map(s => [s.id, s.name]));
}).catch(() => {});

// === Audit ===

async function loadAudit() {
  try {
    const typeFilter = document.getElementById('audit-filter-type');
    const type = typeFilter ? typeFilter.value : '';
    const qs = type ? ('?entityType=' + encodeURIComponent(type) + '&limit=200') : '?limit=200';

    const res = await fetch(API + '/audit' + qs);
    const entries = await res.json();
    const list = document.getElementById('audit-list');

    if (!entries || entries.length === 0) {
      list.innerHTML = '<div class="card"><p style="color:var(--text-secondary)">No audit entries yet. Mutations to agents, skills and plans are recorded here.</p></div>';
      return;
    }

    list.innerHTML = entries.map(e => renderAuditRow(e)).join('');
  } catch (err) {
    document.getElementById('audit-list').innerHTML =
      '<div class="card"><p style="color:var(--danger)">Failed to load audit entries.</p></div>';
  }
}

function renderAuditRow(e) {
  const when = new Date(e.createdAt).toLocaleString();
  const actionClass = 'audit-action-' + (e.action || 'unknown');

  const beforeBlock = e.beforeJson
    ? `<div class="audit-snapshot"><div class="audit-snapshot-label">Before</div><pre>${escapeHtml(prettyJson(e.beforeJson))}</pre></div>` : '';
  const afterBlock = e.afterJson
    ? `<div class="audit-snapshot"><div class="audit-snapshot-label">After</div><pre>${escapeHtml(prettyJson(e.afterJson))}</pre></div>` : '';

  return `
    <div class="audit-card" onclick="this.classList.toggle('expanded')">
      <div class="audit-row">
        <span class="audit-time">${escapeHtml(when)}</span>
        <span class="audit-type">${escapeHtml(e.entityType)}</span>
        <span class="audit-action ${actionClass}">${escapeHtml(e.action)}</span>
        <span class="audit-ref">${escapeHtml(e.entityRef || '')}</span>
        <span class="audit-actor">${escapeHtml(e.actor || '—')}</span>
      </div>
      <div class="audit-detail">${beforeBlock}${afterBlock}</div>
    </div>`;
}

function prettyJson(s) {
  if (!s) return '';
  try {
    return JSON.stringify(JSON.parse(s), null, 2);
  } catch (e) {
    return s;
  }
}

document.addEventListener('change', (e) => {
  if (e.target && e.target.id === 'audit-filter-type') loadAudit();
});
