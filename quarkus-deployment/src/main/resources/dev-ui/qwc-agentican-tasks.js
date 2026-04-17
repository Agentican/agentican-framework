import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';

export class QwcAgenticanTasks extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        .tasks-table { width: 100%; border-collapse: collapse; }
        .tasks-table th, .tasks-table td { padding: 8px 12px; text-align: left; border-bottom: 1px solid var(--lumo-contrast-10pct); }
        .tasks-table th { font-weight: 600; color: var(--lumo-secondary-text-color); }
        .status-completed { color: var(--lumo-success-text-color); }
        .status-failed { color: var(--lumo-error-text-color); }
        .status-running { color: var(--lumo-primary-text-color); }
        .refresh-btn { cursor: pointer; padding: 4px 12px; border-radius: 4px; border: 1px solid var(--lumo-contrast-20pct); background: transparent; }
    `;

    static properties = {
        _tasks: { state: true }
    };

    constructor() {
        super();
        this._tasks = [];
    }

    connectedCallback() {
        super.connectedCallback();
        this._refresh();
    }

    _refresh() {
        this.jsonRpc.getTasks().then(result => { this._tasks = result.result; });
    }

    _statusClass(status) {
        return status === 'COMPLETED' ? 'status-completed' : status === 'FAILED' ? 'status-failed' : 'status-running';
    }

    render() {
        return html`
            <h3>Tasks <button class="refresh-btn" @click=${() => this._refresh()}>Refresh</button></h3>
            <table class="tasks-table">
                <thead><tr><th>ID</th><th>Name</th><th>Status</th><th>Input Tokens</th><th>Output Tokens</th></tr></thead>
                <tbody>
                    ${this._tasks.map(task => html`
                        <tr>
                            <td><code>${task.taskId}</code></td>
                            <td>${task.taskName}</td>
                            <td class="${this._statusClass(task.status)}">${task.status}</td>
                            <td>${task.inputTokens}</td>
                            <td>${task.outputTokens}</td>
                        </tr>
                    `)}
                </tbody>
            </table>
            ${this._tasks.length === 0 ? html`<p>No tasks yet.</p>` : ''}
        `;
    }
}

customElements.define('qwc-agentican-tasks', QwcAgenticanTasks);
