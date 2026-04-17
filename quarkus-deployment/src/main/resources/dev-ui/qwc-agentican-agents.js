import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';

export class QwcAgenticanAgents extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        .agents-table { width: 100%; border-collapse: collapse; }
        .agents-table th, .agents-table td { padding: 8px 12px; text-align: left; border-bottom: 1px solid var(--lumo-contrast-10pct); }
        .agents-table th { font-weight: 600; color: var(--lumo-secondary-text-color); }
        .skill-badge { display: inline-block; padding: 2px 8px; margin: 2px; border-radius: 12px; background: var(--lumo-primary-color-10pct); color: var(--lumo-primary-text-color); font-size: 0.85em; }
    `;

    static properties = {
        _agents: { state: true }
    };

    constructor() {
        super();
        this._agents = [];
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getAgents().then(result => { this._agents = result.result; });
    }

    render() {
        return html`
            <h3>Registered Agents</h3>
            <table class="agents-table">
                <thead><tr><th>Name</th><th>Role</th><th>Skills</th></tr></thead>
                <tbody>
                    ${this._agents.map(agent => html`
                        <tr>
                            <td><strong>${agent.name}</strong></td>
                            <td>${agent.role}</td>
                            <td>${(agent.skills || []).map(s => html`<span class="skill-badge">${s}</span>`)}</td>
                        </tr>
                    `)}
                </tbody>
            </table>
            ${this._agents.length === 0 ? html`<p>No agents registered.</p>` : ''}
        `;
    }
}

customElements.define('qwc-agentican-agents', QwcAgenticanAgents);
