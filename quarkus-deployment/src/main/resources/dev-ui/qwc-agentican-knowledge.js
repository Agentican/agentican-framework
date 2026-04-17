import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';

export class QwcAgenticanKnowledge extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        .knowledge-table { width: 100%; border-collapse: collapse; }
        .knowledge-table th, .knowledge-table td { padding: 8px 12px; text-align: left; border-bottom: 1px solid var(--lumo-contrast-10pct); }
        .knowledge-table th { font-weight: 600; color: var(--lumo-secondary-text-color); }
        .status-indexed { color: var(--lumo-success-text-color); }
        .refresh-btn { cursor: pointer; padding: 4px 12px; border-radius: 4px; border: 1px solid var(--lumo-contrast-20pct); background: transparent; }
    `;

    static properties = {
        _entries: { state: true }
    };

    constructor() {
        super();
        this._entries = [];
    }

    connectedCallback() {
        super.connectedCallback();
        this._refresh();
    }

    _refresh() {
        this.jsonRpc.getKnowledge().then(result => { this._entries = result.result; });
    }

    render() {
        return html`
            <h3>Knowledge Base <button class="refresh-btn" @click=${() => this._refresh()}>Refresh</button></h3>
            <table class="knowledge-table">
                <thead><tr><th>Name</th><th>Status</th><th>Facts</th></tr></thead>
                <tbody>
                    ${this._entries.map(entry => html`
                        <tr>
                            <td><strong>${entry.name}</strong></td>
                            <td class="status-indexed">${entry.status}</td>
                            <td>${entry.factCount}</td>
                        </tr>
                    `)}
                </tbody>
            </table>
            ${this._entries.length === 0 ? html`<p>No knowledge entries.</p>` : ''}
        `;
    }
}

customElements.define('qwc-agentican-knowledge', QwcAgenticanKnowledge);
