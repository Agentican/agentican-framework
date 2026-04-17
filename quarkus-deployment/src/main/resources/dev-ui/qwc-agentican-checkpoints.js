import { LitElement, html, css } from 'lit';
import { JsonRpc } from 'jsonrpc';

export class QwcAgenticanCheckpoints extends LitElement {

    jsonRpc = new JsonRpc(this);

    static styles = css`
        .checkpoints-table { width: 100%; border-collapse: collapse; }
        .checkpoints-table th, .checkpoints-table td { padding: 8px 12px; text-align: left; border-bottom: 1px solid var(--lumo-contrast-10pct); }
        .checkpoints-table th { font-weight: 600; color: var(--lumo-secondary-text-color); }
        .type-badge { display: inline-block; padding: 2px 8px; border-radius: 12px; background: var(--lumo-warning-color-10pct); color: var(--lumo-warning-text-color); font-size: 0.85em; }
        .refresh-btn { cursor: pointer; padding: 4px 12px; border-radius: 4px; border: 1px solid var(--lumo-contrast-20pct); background: transparent; }
    `;

    static properties = {
        _checkpoints: { state: true }
    };

    constructor() {
        super();
        this._checkpoints = [];
    }

    connectedCallback() {
        super.connectedCallback();
        this._refresh();
    }

    _refresh() {
        this.jsonRpc.getCheckpoints().then(result => { this._checkpoints = result.result; });
    }

    render() {
        return html`
            <h3>Pending HITL Checkpoints <button class="refresh-btn" @click=${() => this._refresh()}>Refresh</button></h3>
            <table class="checkpoints-table">
                <thead><tr><th>ID</th><th>Type</th><th>Step</th><th>Description</th></tr></thead>
                <tbody>
                    ${this._checkpoints.map(cp => html`
                        <tr>
                            <td><code>${cp.id}</code></td>
                            <td><span class="type-badge">${cp.type}</span></td>
                            <td>${cp.stepName}</td>
                            <td>${cp.description}</td>
                        </tr>
                    `)}
                </tbody>
            </table>
            ${this._checkpoints.length === 0 ? html`<p>No pending checkpoints.</p>` : ''}
        `;
    }
}

customElements.define('qwc-agentican-checkpoints', QwcAgenticanCheckpoints);
