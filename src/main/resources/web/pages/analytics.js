/**
 * GeneDex - Genomic Analytics & Sequence Composition Page
 * 
 * Provides practical, user-friendly nucleotide composition analysis, GC and AT content
 * profiling, read-length distributions, and sequencing quality assessment.
 * 
 * Features:
 * - Analytics scope selector (Entire Dataset, Individual Read, Selected Sequence Range)
 * - 5 clean summary metric cards (DNA bases analyzed, GC content, AT content, GC-to-AT ratio, Sequencing quality)
 * - Dynamic, high-resolution chart downloads (Nucleotide frequency, GC vs AT donut, Read length histogram, Quality profile)
 * - Interactive Dataset Read Profiles catalog table with "Analyze Read" navigation
 * - Dynamic CSV export reflecting current dataset and active analysis scope
 */

import { api } from '../services/api.js';
import { Toast } from '../components/Toast.js';

export class GenomicAnalyticsPage {
    constructor(containerId) {
        this.container = document.getElementById(containerId);
        this.activeDataset = null;
        this.analyticsData = null;
        this.readsList = [];
        
        // Scope State
        this.scopeType = 'whole'; // 'whole' | 'read' | 'custom'
        this.selectedReadIndex = 0;
        this.customStart = 0;
        this.customEnd = 14;
        this.currentComposition = null;
    }

    async render() {
        if (!this.container) return;

        // Fetch active dataset state
        const dsRes = await api.getDatasets();
        this.activeDataset = (dsRes && dsRes.success) ? dsRes.activeDataset : null;

        if (!this.activeDataset || (!this.activeDataset.isLoaded && !this.activeDataset.indexLoaded) || (this.activeDataset.totalReads || 0) === 0) {
            this.renderEmptyState();
            return;
        }

        // Fetch overall analytics & reads catalog
        const [anRes, readsRes] = await Promise.all([
            api.getAnalytics(),
            api.getReads(1, 100)
        ]);

        this.analyticsData = (anRes && anRes.success) ? anRes : null;
        this.readsList = (readsRes && readsRes.success) ? (readsRes.reads || []) : [];

        // Clamp read index within bounds of new active dataset
        const totalReads = this.activeDataset.totalReads || 0;
        if (this.selectedReadIndex >= totalReads || this.selectedReadIndex < 0) {
            this.selectedReadIndex = 0;
        }

        // Set default custom range limits based on total symbols
        const totalLen = (this.activeDataset.totalSymbols || 14);
        this.customStart = 0;
        this.customEnd = totalLen;

        // Load initial composition (whole dataset)
        await this.loadComposition();
        this.renderView();
    }

    renderEmptyState() {
        this.container.innerHTML = `
            <div class="page-container" style="padding-top: 48px; padding-bottom: 64px;">
                <div class="empty-state-box">
                    <div class="empty-state-icon">
                        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                            <line x1="18" y1="20" x2="18" y2="10"></line>
                            <line x1="12" y1="20" x2="12" y2="4"></line>
                            <line x1="6" y1="20" x2="6" y2="14"></line>
                        </svg>
                    </div>
                    <h2 class="empty-state-title">No Genomic Dataset Loaded</h2>
                    <p class="empty-state-desc">
                        To compute nucleotide composition, GC and AT content, read-length distributions,
                        and sequencing quality metrics, please load or upload a FASTQ dataset.
                    </p>
                    <div style="display: flex; gap: 12px; justify-content: center; flex-wrap: wrap;">
                        <button type="button" class="btn-primary-hero" id="btnLoadDemoInEmpty" style="font-size: 0.88rem; padding: 10px 22px;">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polygon points="5 3 19 12 5 21 5 21"></polygon></svg>
                            Load Built-In Demo (GATTACAGATTACA)
                        </button>
                        <a href="#/upload" class="btn-secondary-hero" style="font-size: 0.88rem; padding: 10px 22px;">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                            Go to Upload Dataset
                        </a>
                    </div>
                </div>
            </div>
        `;

        const btnLoad = this.container.querySelector('#btnLoadDemoInEmpty');
        if (btnLoad) {
            btnLoad.addEventListener('click', async () => {
                btnLoad.disabled = true;
                btnLoad.textContent = 'Loading Demo...';
                await api.setActiveDataset('demo_report');
                Toast.success('Loaded built-in report demo dataset: GATTACAGATTACA');
                await this.render();
            });
        }
    }

    async loadComposition() {
        let params = {};
        if (this.scopeType === 'whole') {
            params = {}; // Default is whole dataset
        } else if (this.scopeType === 'read') {
            params = { readIndex: this.selectedReadIndex };
        } else if (this.scopeType === 'custom') {
            params = { s: this.customStart, e: this.customEnd };
        }

        try {
            const res = await api.getComposition(params);
            if (res && res.success) {
                this.currentComposition = res;
            } else {
                Toast.error('Could not compute composition: ' + (res.error || 'Unknown error'));
            }
        } catch (err) {
            console.error('Composition error:', err);
            Toast.error('Error computing composition: ' + err.message);
        }
    }

    renderView() {
        const comp = this.currentComposition || {
            counts: { A: 0, C: 0, G: 0, T: 0, N: 0 },
            percentages: { A: 0, C: 0, G: 0, T: 0, N: 0 },
            totalDnaBases: 0,
            separatorCount: 0,
            gcPercentage: 0,
            atPercentage: 0,
            startPos: 0,
            endPos: 0,
            rangeLength: 0
        };

        const an = this.analyticsData || {};
        const qSummary = an.qualitySummary || null;

        // Check if genuine quality score data exists
        const hasQualityData = Boolean(
            qSummary &&
            qSummary.hasQualityData !== false &&
            (qSummary.totalQualityBases > 0 || (typeof qSummary.meanPhredScore === 'number' && qSummary.meanPhredScore > 0 && qSummary.meanPhredScore !== 40.0))
        );

        const counts = comp.counts || {};
        const pcts = comp.percentages || {};
        const totalBases = comp.totalDnaBases || 0;
        const separatorCount = comp.separatorCount || 0;
        const gcCount = (counts.G || 0) + (counts.C || 0);
        const atCount = (counts.A || 0) + (counts.T || 0);
        const nCount = counts.N || 0;

        // Accurate GC and AT percentage calculations
        const gcPct = comp.gcPercentage != null ? comp.gcPercentage : (totalBases > 0 ? Math.round((gcCount / totalBases) * 10000) / 100 : 0);
        const atPct = comp.atPercentage != null ? comp.atPercentage : (totalBases > 0 ? Math.round((atCount / totalBases) * 10000) / 100 : 0);

        // Safe GC-to-AT ratio calculation (prevent Infinity / NaN)
        let gcAtRatioDisplay = '0.00';
        let gcAtRatioSub = 'Ratio of (G + C) to (A + T) bases';
        let gcAtRatioColor = 'var(--brand-purple)';
        if (atCount > 0) {
            gcAtRatioDisplay = (gcCount / atCount).toFixed(2);
            gcAtRatioSub = `${gcCount} G+C bases / ${atCount} A+T bases`;
        } else if (gcCount > 0 && atCount === 0) {
            gcAtRatioDisplay = 'Not defined';
            gcAtRatioSub = 'No Adenine or Thymine bases present in selected scope';
            gcAtRatioColor = '#d97706';
        } else {
            gcAtRatioDisplay = '0.00';
            gcAtRatioSub = 'No nucleotide bases present in scope';
        }

        // Scope description banner & summary text
        const totalReads = this.activeDataset.totalReads || 1;
        const totalSymbols = this.activeDataset.totalSymbols || comp.endPos || 14;

        let scopeBadgeText = 'Entire Dataset';
        let scopeDetailText = `All ${totalReads} read(s) &bull; Coordinates [0, ${totalSymbols})`;
        let scopeSummaryHtml = `Analyzing <strong>${totalBases} DNA base${totalBases === 1 ? '' : 's'}</strong> from <strong>${totalReads} read${totalReads === 1 ? '' : 's'}</strong>.`;

        if (this.scopeType === 'read') {
            const r = this.readsList.find(x => x.readIndex === this.selectedReadIndex);
            const hdr = r ? r.header : `@read${this.selectedReadIndex}`;
            scopeBadgeText = `Read #${this.selectedReadIndex} (${hdr})`;
            scopeDetailText = `Coordinates [${comp.startPos}, ${comp.endPos}) &bull; Length: ${comp.rangeLength} bp`;
            scopeSummaryHtml = `Analyzing <strong>${totalBases} DNA base${totalBases === 1 ? '' : 's'}</strong> from <strong>Read #${this.selectedReadIndex}</strong> (<code>${this.escapeHtml(hdr)}</code>).`;
        } else if (this.scopeType === 'custom') {
            scopeBadgeText = `Sequence Range [${comp.startPos}, ${comp.endPos})`;
            scopeDetailText = `Span: ${comp.rangeLength} symbols &bull; ${totalBases} DNA bases`;
            scopeSummaryHtml = `Analyzing <strong>${totalBases} DNA base${totalBases === 1 ? '' : 's'}</strong> from coordinates <strong>[${comp.startPos}, ${comp.endPos})</strong>.`;
        }

        if (separatorCount > 0) {
            scopeSummaryHtml += ` <span style="display: inline-block; background: #fef3c7; color: #92400e; padding: 2px 8px; border-radius: 4px; font-size: 0.78rem; font-weight: 600; margin-left: 8px;">${separatorCount} read separator${separatorCount === 1 ? '' : 's'} (#) excluded</span>`;
        }

        // Reads dropdown options for Individual Read scope
        const readsOptions = this.readsList.map(r => `
            <option value="${r.readIndex}" ${r.readIndex === this.selectedReadIndex ? 'selected' : ''}>
                Read #${r.readIndex}: ${this.escapeHtml(r.header)} (${r.length} bp, GC ${r.gcPercentage}%)
            </option>
        `).join('');

        this.container.innerHTML = `
            <div class="page-container" style="padding-top: 32px; padding-bottom: 64px;">
                <!-- Header Strip -->
                <div class="page-header" style="margin-bottom: 24px;">
                    <div class="breadcrumb" style="font-size: 0.82rem; color: var(--text-muted); margin-bottom: 8px;">
                        <a href="#/home" style="color: var(--text-muted); text-decoration: none;">Home</a> /
                        <span style="color: var(--brand-purple); font-weight: 600;">Genomic Analytics</span>
                    </div>
                    <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 14px;">
                        <div>
                            <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap;">
                                <h1 style="font-size: 1.85rem; font-weight: 800; color: var(--text-heading); margin: 0;">
                                    Genomic Analytics &amp; Sequence Composition
                                </h1>
                                <span class="badge-analytics-scope">Nucleotide Composition Analysis</span>
                            </div>
                            <p style="color: var(--text-muted); font-size: 0.92rem; margin: 6px 0 0 0;">
                                Explore the nucleotide composition, GC and AT content, read lengths, and sequencing quality of your uploaded DNA dataset.
                            </p>
                        </div>
                        <div class="active-ds-indicator" style="background: #ffffff; border: 1px solid var(--border-color); border-radius: var(--radius-pill); padding: 6px 14px; font-size: 0.8rem; display: flex; align-items: center; gap: 8px; box-shadow: var(--shadow-sm);">
                            <span class="status-dot-pulse"></span>
                            <span>Active: <strong>${this.escapeHtml(this.activeDataset.name)}</strong> (${totalReads} read${totalReads === 1 ? '' : 's'})</span>
                        </div>
                    </div>
                </div>

                <!-- Scope Selector Control Panel -->
                <div class="scope-panel-card" style="background: #ffffff; border: 1px solid var(--border-color); border-radius: var(--radius-card); padding: 22px 24px; margin-bottom: 24px; box-shadow: var(--shadow-sm);">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; flex-wrap: wrap; gap: 10px;">
                        <div>
                            <span style="font-size: 0.78rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-muted); display: block;">
                                ANALYTICS SCOPE SELECTOR
                            </span>
                            <span style="font-size: 0.82rem; color: var(--text-muted);">
                                Select the level of sequence resolution for nucleotide composition analysis.
                            </span>
                        </div>
                        <!-- Active Scope Status Banner -->
                        <div style="font-size: 0.82rem; background: var(--bg-surface-alt); padding: 6px 14px; border-radius: var(--radius-pill); border: 1px solid var(--border-color);">
                            Scope: <strong style="color: var(--brand-purple);">${scopeBadgeText}</strong> &nbsp;|&nbsp; <span>${scopeDetailText}</span>
                        </div>
                    </div>

                    <!-- Scope Mode Pills -->
                    <div style="display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap;">
                        <button type="button" class="btn-scope-pill ${this.scopeType === 'whole' ? 'active' : ''}" id="btnScopeWhole" title="Analyze all DNA reads in the selected dataset">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></svg>
                            <span>Entire Dataset</span>
                        </button>
                        <button type="button" class="btn-scope-pill ${this.scopeType === 'read' ? 'active' : ''}" id="btnScopeRead" title="Analyze one specific DNA read">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line></svg>
                            <span>Individual Read</span>
                        </button>
                        <button type="button" class="btn-scope-pill ${this.scopeType === 'custom' ? 'active' : ''}" id="btnScopeCustom" title="Analyze a selected section of the DNA sequence">
                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><line x1="9" y1="3" x2="9" y2="21"></line><line x1="15" y1="3" x2="15" y2="21"></line></svg>
                            <span>Selected Sequence Range</span>
                        </button>
                    </div>

                    <!-- Scope Explanations & Sub-Controllers -->
                    ${this.scopeType === 'whole' ? `
                        <div class="scope-desc-card">
                            <strong>Entire Dataset:</strong> Analyze all DNA reads in the selected dataset (${totalReads} read${totalReads === 1 ? '' : 's'}, ${totalBases} DNA bases).
                        </div>
                    ` : ''}

                    ${this.scopeType === 'read' ? `
                        <div style="background: #f8fafc; border: 1px solid var(--border-color); border-radius: 8px; padding: 14px 18px; display: flex; align-items: center; gap: 14px; flex-wrap: wrap;">
                            <label for="scopeReadSelect" style="font-size: 0.85rem; font-weight: 700; color: var(--text-heading); white-space: nowrap;">
                                Select Individual Read:
                            </label>
                            <select id="scopeReadSelect" class="form-input" style="max-width: 460px; font-size: 0.86rem; padding: 7px 12px; border-radius: 6px;">
                                ${readsOptions}
                            </select>
                            <span style="font-size: 0.8rem; color: var(--text-muted);">
                                Analyze one specific DNA read from the dataset.
                            </span>
                        </div>
                    ` : ''}

                    ${this.scopeType === 'custom' ? `
                        <div style="background: #f8fafc; border: 1px solid var(--border-color); border-radius: 8px; padding: 14px 18px; display: flex; flex-direction: column; gap: 10px;">
                            <div style="display: flex; align-items: center; gap: 14px; flex-wrap: wrap;">
                                <span style="font-size: 0.85rem; font-weight: 700; color: var(--text-heading);">
                                    Sequence Coordinates:
                                </span>
                                <div style="display: flex; align-items: center; gap: 8px;">
                                    <label for="inputCustomStart" style="font-size: 0.82rem; color: var(--text-muted); font-weight: 600;">Start Position:</label>
                                    <input type="number" id="inputCustomStart" class="form-input" style="width: 110px; font-family: var(--font-mono); font-size: 0.85rem; padding: 6px 10px; border-radius: 6px;" min="0" max="${totalSymbols}" value="${this.customStart}" />
                                </div>
                                <div style="display: flex; align-items: center; gap: 8px;">
                                    <label for="inputCustomEnd" style="font-size: 0.82rem; color: var(--text-muted); font-weight: 600;">End Position:</label>
                                    <input type="number" id="inputCustomEnd" class="form-input" style="width: 110px; font-family: var(--font-mono); font-size: 0.85rem; padding: 6px 10px; border-radius: 6px;" min="1" max="${totalSymbols}" value="${this.customEnd}" />
                                </div>
                                <button type="button" class="btn-primary-hero" id="btnApplyCustomRange" style="font-size: 0.82rem; padding: 7px 18px; border-radius: 6px;">
                                    Apply Range
                                </button>
                            </div>
                            <div style="font-size: 0.8rem; color: var(--text-muted); line-height: 1.4;">
                                <strong>Range Explanation:</strong> The start position is included and the end position is excluded: <code>[Start, End)</code>. Valid coordinate indices: 0 to ${totalSymbols}.
                            </div>
                        </div>
                    ` : ''}

                    <!-- Active Scope Summary Message Strip -->
                    <div class="scope-summary-strip">
                        <div>${scopeSummaryHtml}</div>
                        <div style="font-size: 0.78rem; color: var(--text-muted);">
                            Refreshed from current dataset state
                        </div>
                    </div>
                </div>

                <!-- Primary Metric Strip (5 Clean Cards) -->
                <div class="analytics-metrics-grid" id="analyticsSummaryMetrics">
                    <!-- Card 1: DNA Bases Analyzed -->
                    <div class="metric-card">
                        <div class="metric-label">DNA BASES ANALYZED</div>
                        <div class="metric-val" style="color: var(--text-heading);">
                            ${totalBases} <span style="font-size: 0.85rem; font-weight: 600; color: var(--text-muted);">bp</span>
                        </div>
                        <div class="metric-sub">
                            ${this.scopeType === 'whole' ? `All ${totalReads} read(s)` : (this.scopeType === 'read' ? `Read #${this.selectedReadIndex} (${comp.rangeLength} bp)` : `Coordinates [${comp.startPos}, ${comp.endPos})`)}
                            ${separatorCount > 0 ? `<div style="font-size: 0.74rem; color: #b45309; margin-top: 3px;">(${separatorCount} '#' separator${separatorCount === 1 ? '' : 's'} excluded)</div>` : ''}
                            ${nCount > 0 ? `<div style="font-size: 0.74rem; color: #64748b; margin-top: 3px;">${nCount} ambiguous (N) base${nCount === 1 ? '' : 's'}</div>` : ''}
                        </div>
                    </div>

                    <!-- Card 2: GC Content -->
                    <div class="metric-card">
                        <div class="metric-label">GC CONTENT</div>
                        <div class="metric-val" style="color: ${gcPct >= 50 ? '#059669' : '#d97706'};">
                            ${gcPct}%
                        </div>
                        <div class="metric-sub">
                            ${gcCount} of ${totalBases} DNA bases
                            <div style="font-size: 0.74rem; font-weight: 600; color: ${gcPct < 40 ? '#d97706' : (gcPct > 60 ? '#059669' : '#0284c7')}; margin-top: 3px;">
                                ${gcPct < 40 ? 'AT-Rich Genome' : (gcPct > 60 ? 'GC-Rich Genome' : 'Balanced Composition')}
                            </div>
                        </div>
                    </div>

                    <!-- Card 3: AT Content -->
                    <div class="metric-card">
                        <div class="metric-label">AT CONTENT</div>
                        <div class="metric-val" style="color: #0284c7;">
                            ${atPct}%
                        </div>
                        <div class="metric-sub">
                            ${atCount} of ${totalBases} DNA bases
                            <div style="font-size: 0.74rem; color: var(--text-muted); margin-top: 3px;">
                                Adenine + Thymine fraction
                            </div>
                        </div>
                    </div>

                    <!-- Card 4: GC-to-AT Ratio -->
                    <div class="metric-card">
                        <div class="metric-label">GC-TO-AT RATIO</div>
                        <div class="metric-val" style="color: ${gcAtRatioColor}; font-size: ${gcAtRatioDisplay === 'Not defined' ? '1.35rem' : '1.8rem'};">
                            ${gcAtRatioDisplay}
                        </div>
                        <div class="metric-sub">
                            ${gcAtRatioSub}
                        </div>
                    </div>

                    <!-- Card 5: Average Sequencing Quality -->
                    <div class="metric-card">
                        <div class="metric-label">AVERAGE SEQUENCING QUALITY</div>
                        ${hasQualityData ? `
                            <div class="metric-val" style="color: #059669;">
                                Q${qSummary.meanPhredScore}
                            </div>
                            <div class="metric-sub">
                                ${qSummary.q30Percentage}% bases &ge; Q30 (99.9% accuracy)
                                <div style="font-size: 0.74rem; color: #059669; font-weight: 600; margin-top: 3px;">
                                    High sequencing fidelity
                                </div>
                            </div>
                        ` : `
                            <div class="metric-val" style="color: #94a3b8; font-size: 1.5rem;">
                                N/A
                            </div>
                            <div class="metric-sub">
                                Sequencing quality data is not available for this dataset.
                            </div>
                        `}
                    </div>
                </div>

                <!-- Visual Charts Grid (2 Columns) -->
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(460px, 1fr)); gap: 24px; margin-bottom: 32px;">
                    <!-- Chart 1: Nucleotide Frequency Distribution Bar Chart -->
                    <div class="chart-box-card" style="background: #ffffff; border: 1px solid var(--border-color); border-radius: var(--radius-card); box-shadow: var(--shadow-card); overflow: hidden; display: flex; flex-direction: column;">
                        <div class="chart-header" style="padding: 16px 20px; background: #fafbfc; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center;">
                            <div>
                                <h3 style="font-size: 1.05rem; font-weight: 700; color: var(--text-heading); margin: 0;">
                                    Nucleotide Frequency Distribution
                                </h3>
                                <div style="font-size: 0.78rem; color: var(--text-muted); margin-top: 2px;">
                                    A, C, G, T, N counts and relative percentages for ${scopeBadgeText}
                                </div>
                            </div>
                            <button type="button" class="btn-download-png" id="btnDownloadBarChartPng" title="Export bar chart as high-resolution PNG">
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                                Download PNG
                            </button>
                        </div>
                        <div class="chart-body" style="padding: 24px; flex: 1; display: flex; flex-direction: column; justify-content: space-between;">
                            <!-- Interactive Frequency Bars -->
                            <div class="bars-container" style="display: flex; flex-direction: column; gap: 14px;">
                                ${this.renderBarItem('A', 'Adenine', counts.A || 0, pcts.A || 0, '#10b981')}
                                ${this.renderBarItem('C', 'Cytosine', counts.C || 0, pcts.C || 0, '#0284c7')}
                                ${this.renderBarItem('G', 'Guanine', counts.G || 0, pcts.G || 0, '#f59e0b')}
                                ${this.renderBarItem('T', 'Thymine', counts.T || 0, pcts.T || 0, '#ef4444')}
                                ${this.renderBarItem('N', 'Ambiguous N', counts.N || 0, pcts.N || 0, '#64748b')}
                            </div>
                            <div style="font-size: 0.78rem; color: var(--text-muted); margin-top: 18px; padding-top: 12px; border-top: 1px solid #f1f5f9; display: flex; justify-content: space-between;">
                                <span>Total DNA Bases: <strong>${totalBases} bp</strong></span>
                                <span>${separatorCount > 0 ? `${separatorCount} read boundary '#' excluded` : 'Pure DNA sequence'}</span>
                            </div>
                        </div>
                    </div>

                    <!-- Chart 2: GC vs AT Donut Chart -->
                    <div class="chart-box-card" style="background: #ffffff; border: 1px solid var(--border-color); border-radius: var(--radius-card); box-shadow: var(--shadow-card); overflow: hidden; display: flex; flex-direction: column;">
                        <div class="chart-header" style="padding: 16px 20px; background: #fafbfc; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center;">
                            <div>
                                <h3 style="font-size: 1.05rem; font-weight: 700; color: var(--text-heading); margin: 0;">
                                    GC vs. AT Content
                                </h3>
                                <div style="font-size: 0.78rem; color: var(--text-muted); margin-top: 2px;">
                                    Proportion of Guanine-Cytosine vs. Adenine-Thymine pairs
                                </div>
                            </div>
                            <button type="button" class="btn-download-png" id="btnDownloadDonutChartPng" title="Export donut chart as high-resolution PNG">
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                                Download PNG
                            </button>
                        </div>
                        <div class="chart-body" style="padding: 24px; flex: 1; display: flex; align-items: center; justify-content: space-around; flex-wrap: wrap; gap: 20px;">
                            <!-- SVG Donut Chart -->
                            <div style="position: relative; width: 170px; height: 170px;">
                                ${this.renderSvgDonut(gcPct, atPct, pcts.N || 0)}
                                <div style="position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); text-align: center; pointer-events: none;">
                                    <span style="font-size: 1.45rem; font-weight: 800; color: var(--text-heading); display: block; line-height: 1;">${gcPct}%</span>
                                    <span style="font-size: 0.72rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase;">GC Content</span>
                                </div>
                            </div>

                            <!-- Donut Legend & Breakdown Cards -->
                            <div style="display: flex; flex-direction: column; gap: 10px; min-width: 190px;">
                                <div style="background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 8px; padding: 10px 14px;">
                                    <div style="display: flex; align-items: center; gap: 6px; font-size: 0.82rem; font-weight: 700; color: #0369a1;">
                                        <span style="width: 12px; height: 12px; background: #0284c7; border-radius: 3px;"></span>
                                        GC Bases (G + C)
                                    </div>
                                    <div style="font-size: 1.15rem; font-weight: 800; color: #0c4a6e; margin-top: 4px;">
                                        ${gcCount} <span style="font-size: 0.8rem; font-weight: 600;">(${gcPct}%)</span>
                                    </div>
                                </div>

                                <div style="background: #ecfdf5; border: 1px solid #a7f3d0; border-radius: 8px; padding: 10px 14px;">
                                    <div style="display: flex; align-items: center; gap: 6px; font-size: 0.82rem; font-weight: 700; color: #065f46;">
                                        <span style="width: 12px; height: 12px; background: #10b981; border-radius: 3px;"></span>
                                        AT Bases (A + T)
                                    </div>
                                    <div style="font-size: 1.15rem; font-weight: 800; color: #064e3b; margin-top: 4px;">
                                        ${atCount} <span style="font-size: 0.8rem; font-weight: 600;">(${atPct}%)</span>
                                    </div>
                                </div>

                                ${nCount > 0 ? `
                                    <div style="background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 8px; padding: 10px 14px;">
                                        <div style="display: flex; align-items: center; gap: 6px; font-size: 0.82rem; font-weight: 700; color: #475569;">
                                            <span style="width: 12px; height: 12px; background: #64748b; border-radius: 3px;"></span>
                                            Ambiguous Bases (N)
                                        </div>
                                        <div style="font-size: 1.15rem; font-weight: 800; color: #334155; margin-top: 4px;">
                                            ${nCount} <span style="font-size: 0.8rem; font-weight: 600;">(${pcts.N || 0}%)</span>
                                        </div>
                                    </div>
                                ` : ''}
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Secondary Visualizations Grid (Read Length Histogram + Quality Summary) -->
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(460px, 1fr)); gap: 24px; margin-bottom: 32px;">
                    <!-- Chart 3: Read-Length Distribution Histogram -->
                    <div class="chart-box-card" style="background: #ffffff; border: 1px solid var(--border-color); border-radius: var(--radius-card); box-shadow: var(--shadow-card); overflow: hidden; display: flex; flex-direction: column;">
                        <div class="chart-header" style="padding: 16px 20px; background: #fafbfc; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center;">
                            <div>
                                <h3 style="font-size: 1.05rem; font-weight: 700; color: var(--text-heading); margin: 0;">
                                    Read-Length Distribution
                                </h3>
                                <div style="font-size: 0.78rem; color: var(--text-muted); margin-top: 2px;">
                                    Min: ${an.minReadLength || 0} bp &bull; Max: ${an.maxReadLength || 0} bp &bull; Avg: ${an.avgReadLength || 0} bp
                                </div>
                            </div>
                            <button type="button" class="btn-download-png" id="btnDownloadHistogramPng" title="Export length distribution histogram as PNG">
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                                Download PNG
                            </button>
                        </div>
                        <div class="chart-body" style="padding: 24px; flex: 1; display: flex; flex-direction: column; justify-content: center;">
                            ${this.renderLengthHistogram(an.lengthDistribution || {})}
                        </div>
                    </div>

                    <!-- Chart 4: Phred Quality Score Profile Summary -->
                    <div class="chart-box-card" style="background: #ffffff; border: 1px solid var(--border-color); border-radius: var(--radius-card); box-shadow: var(--shadow-card); overflow: hidden; display: flex; flex-direction: column;">
                        <div class="chart-header" style="padding: 16px 20px; background: #fafbfc; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center;">
                            <div>
                                <h3 style="font-size: 1.05rem; font-weight: 700; color: var(--text-heading); margin: 0;">
                                    Sequencing Quality Profile
                                </h3>
                                <div style="font-size: 0.78rem; color: var(--text-muted); margin-top: 2px;">
                                    Phred sequencing accuracy and quality distribution
                                </div>
                            </div>
                            ${hasQualityData ? `
                                <button type="button" class="btn-download-png" id="btnDownloadQualityPng" title="Export quality score profile as PNG">
                                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                                    Download PNG
                                </button>
                            ` : ''}
                        </div>
                        <div class="chart-body" style="padding: 24px; flex: 1; display: flex; flex-direction: column; justify-content: space-between; gap: 16px;">
                            ${hasQualityData ? `
                                <!-- Stacked Quality Bar -->
                                <div>
                                    <div style="display: flex; justify-content: space-between; font-size: 0.8rem; font-weight: 700; margin-bottom: 6px;">
                                        <span>Accuracy Distribution</span>
                                        <span style="color: #059669;">Mean Score: Q${qSummary.meanPhredScore}</span>
                                    </div>
                                    <div style="display: flex; height: 16px; border-radius: 6px; overflow: hidden; box-shadow: inset 0 1px 2px rgba(0,0,0,0.1);">
                                        <div style="width: ${qSummary.q30Percentage}%; background: #10b981;" title="Q ≥ 30 (${qSummary.q30Percentage}%)"></div>
                                        <div style="width: ${qSummary.q20Percentage}%; background: #0284c7;" title="Q 20-29 (${qSummary.q20Percentage}%)"></div>
                                        <div style="width: ${qSummary.qUnder20Percentage}%; background: #ef4444;" title="Q < 20 (${qSummary.qUnder20Percentage}%)"></div>
                                    </div>
                                </div>

                                <!-- Quality Thresholds Breakdown -->
                                <div style="display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; text-align: center;">
                                    <div style="background: #ecfdf5; border: 1px solid #a7f3d0; border-radius: 6px; padding: 10px 8px;">
                                        <div style="font-size: 0.74rem; font-weight: 700; color: #065f46;">Q &ge; 30 (High)</div>
                                        <div style="font-size: 1.1rem; font-weight: 800; color: #047857; margin-top: 2px;">${qSummary.q30Percentage}%</div>
                                        <div style="font-size: 0.68rem; color: #065f46;">&ge; 99.9% accuracy</div>
                                    </div>
                                    <div style="background: #f0f9ff; border: 1px solid #bae6fd; border-radius: 6px; padding: 10px 8px;">
                                        <div style="font-size: 0.74rem; font-weight: 700; color: #0369a1;">Q 20-29 (Good)</div>
                                        <div style="font-size: 1.1rem; font-weight: 800; color: #0284c7; margin-top: 2px;">${qSummary.q20Percentage}%</div>
                                        <div style="font-size: 0.68rem; color: #0369a1;">99.0% - 99.9%</div>
                                    </div>
                                    <div style="background: #fef2f2; border: 1px solid #fecaca; border-radius: 6px; padding: 10px 8px;">
                                        <div style="font-size: 0.74rem; font-weight: 700; color: #991b1b;">Q &lt; 20 (Low)</div>
                                        <div style="font-size: 1.1rem; font-weight: 800; color: #ef4444; margin-top: 2px;">${qSummary.qUnder20Percentage}%</div>
                                        <div style="font-size: 0.68rem; color: #991b1b;">&lt; 99.0% accuracy</div>
                                    </div>
                                </div>

                                <!-- Phred Scale Note -->
                                <div style="font-size: 0.78rem; color: var(--text-muted); background: #f8fafc; border: 1px solid var(--border-color); border-radius: 6px; padding: 10px 14px; line-height: 1.45;">
                                    <strong>Phred Quality Standard:</strong> Q40 corresponds to 99.99% accuracy (1 error in 10,000 bases), while Q30 represents 99.90% accuracy (1 error in 1,000 bases).
                                </div>
                            ` : `
                                <!-- Clean Empty Quality Notice -->
                                <div style="text-align: center; padding: 36px 20px; color: var(--text-muted);">
                                    <svg width="42" height="42" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" style="margin-bottom: 12px; color: #94a3b8;">
                                        <circle cx="12" cy="12" r="10"></circle>
                                        <line x1="12" y1="8" x2="12" y2="12"></line>
                                        <line x1="12" y1="16" x2="12.01" y2="16"></line>
                                    </svg>
                                    <div style="font-size: 0.96rem; font-weight: 700; color: var(--text-heading); margin-bottom: 6px;">
                                        Sequencing quality data is not available for this dataset.
                                    </div>
                                    <div style="font-size: 0.82rem; color: var(--text-muted); max-width: 380px; margin: 0 auto; line-height: 1.45;">
                                        No Phred quality scores were detected in the loaded dataset records. Upload a FASTQ file with standard quality score lines to view quality metrics.
                                    </div>
                                </div>
                            `}
                        </div>
                    </div>
                </div>

                <!-- Per-Read Profiles Catalog Table -->
                <div class="read-profiles-section" id="datasetReadProfilesSection" style="background: #ffffff; border: 1px solid var(--border-color); border-radius: var(--radius-card); box-shadow: var(--shadow-sm); overflow: hidden;">
                    <div style="padding: 18px 22px; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; background: #fafbfc;">
                        <div>
                            <h3 style="font-size: 1.15rem; font-weight: 800; color: var(--text-heading); margin: 0;">
                                Dataset Read Profiles &amp; GC Composition
                            </h3>
                            <div style="font-size: 0.82rem; color: var(--text-muted); margin-top: 2px;">
                                Individual read boundary statistics and sequence previews. Click "Analyze Read" to isolate any read.
                            </div>
                        </div>
                        <div style="display: flex; gap: 10px;">
                            <button type="button" class="btn-secondary-action" id="btnExportCompositionCsv" style="text-decoration: none; display: inline-flex; align-items: center; gap: 6px;">
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                                Export Composition CSV
                            </button>
                        </div>
                    </div>

                    <div style="overflow-x: auto;">
                        <table class="data-table" style="width: 100%; border-collapse: collapse; font-size: 0.85rem;">
                            <thead>
                                <tr style="background: #f1f5f9; text-align: left; color: var(--text-muted); font-size: 0.76rem; font-weight: 700; text-transform: uppercase;">
                                    <th style="padding: 12px 18px;">Read ID</th>
                                    <th style="padding: 12px 18px;">FASTQ Header</th>
                                    <th style="padding: 12px 18px;">Sequence Position Range</th>
                                    <th style="padding: 12px 18px;">Read Length</th>
                                    <th style="padding: 12px 18px;">G+C Count</th>
                                    <th style="padding: 12px 18px;">GC Percentage</th>
                                    <th style="padding: 12px 18px;">Sequence Preview</th>
                                    <th style="padding: 12px 18px; text-align: right;">Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${(an.readProfiles || []).map(p => {
                                    const isCurrentRead = (this.scopeType === 'read' && this.selectedReadIndex === p.readIndex);
                                    return `
                                        <tr class="table-row ${isCurrentRead ? 'row-selected' : ''}" id="readRow_${p.readIndex}">
                                            <td style="font-family: var(--font-mono); font-weight: 700; color: var(--brand-purple);">#${p.readIndex}</td>
                                            <td style="font-family: var(--font-mono); font-weight: 600;">${this.escapeHtml(p.header)}</td>
                                            <td style="font-family: var(--font-mono); color: var(--text-muted);">[${p.startPos}, ${p.endPos})</td>
                                            <td><strong>${p.length}</strong> bp</td>
                                            <td>${(p.gCount || 0) + (p.cCount || 0)} bp</td>
                                            <td>
                                                <span class="gc-badge" style="background: ${p.gcPercentage >= 50 ? '#ecfdf5' : '#fffbeb'}; color: ${p.gcPercentage >= 50 ? '#059669' : '#b45309'}; border: 1px solid ${p.gcPercentage >= 50 ? '#a7f3d0' : '#fde68a'}; padding: 3px 9px; border-radius: var(--radius-pill); font-size: 0.78rem; font-weight: 700;">
                                                    ${p.gcPercentage}%
                                                </span>
                                            </td>
                                            <td style="font-family: var(--font-mono); font-size: 0.8rem; color: #475569;">
                                                <code>${this.escapeHtml(p.sequencePreview)}</code>
                                            </td>
                                            <td style="text-align: right;">
                                                <button type="button" class="btn-table-action btn-select-read-scope ${isCurrentRead ? 'active-scope' : ''}" data-index="${p.readIndex}" title="Isolate this read in Analytics Scope">
                                                    ${isCurrentRead ? 'Active Read' : 'Analyze Read'}
                                                </button>
                                            </td>
                                        </tr>
                                    `;
                                }).join('')}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        `;

        this.bindEvents();
    }

    renderBarItem(symbol, name, count, pct, color) {
        return `
            <div class="bar-row" style="display: flex; flex-direction: column; gap: 4px;">
                <div style="display: flex; justify-content: space-between; font-size: 0.82rem;">
                    <div style="display: flex; align-items: center; gap: 6px;">
                        <span class="legend-swatch base-${symbol}" style="font-weight: 700; display: inline-block; width: 18px; text-align: center;">${symbol}</span>
                        <strong style="color: var(--text-heading);">${name}</strong>
                    </div>
                    <div>
                        <strong style="color: var(--text-heading);">${count}</strong> bases 
                        <span style="color: var(--text-muted); font-size: 0.78rem;">(${pct}%)</span>
                    </div>
                </div>
                <div style="height: 12px; background: #e2e8f0; border-radius: 4px; overflow: hidden;">
                    <div style="width: ${pct}%; height: 100%; background: ${color}; border-radius: 4px; transition: width 0.4s ease;"></div>
                </div>
            </div>
        `;
    }

    renderSvgDonut(gcPct, atPct, nPct = 0) {
        // Circumference = 2 * pi * 50 = 314.16
        const c = 314.16;
        const total = (gcPct + atPct + nPct) || 100;
        const gcDash = (gcPct / total) * c;
        const atDash = (atPct / total) * c;
        const nDash = (nPct / total) * c;

        const gcOffset = 0;
        const atOffset = -gcDash;
        const nOffset = -(gcDash + atDash);

        return `
            <svg width="170" height="170" viewBox="0 0 120 120">
                <circle cx="60" cy="60" r="50" fill="none" stroke="#e2e8f0" stroke-width="14" />
                <!-- GC arc (Cyan) -->
                <circle cx="60" cy="60" r="50" fill="none" stroke="#0284c7" stroke-width="14"
                        stroke-dasharray="${gcDash} ${c - gcDash}" stroke-dashoffset="${gcOffset}"
                        transform="rotate(-90 60 60)" style="transition: stroke-dasharray 0.4s ease;" />
                <!-- AT arc (Emerald) -->
                <circle cx="60" cy="60" r="50" fill="none" stroke="#10b981" stroke-width="14"
                        stroke-dasharray="${atDash} ${c - atDash}" stroke-dashoffset="${atOffset}"
                        transform="rotate(-90 60 60)" style="transition: stroke-dasharray 0.4s ease;" />
                ${nPct > 0 ? `
                    <!-- N arc (Slate) -->
                    <circle cx="60" cy="60" r="50" fill="none" stroke="#64748b" stroke-width="14"
                            stroke-dasharray="${nDash} ${c - nDash}" stroke-dashoffset="${nOffset}"
                            transform="rotate(-90 60 60)" style="transition: stroke-dasharray 0.4s ease;" />
                ` : ''}
            </svg>
        `;
    }

    renderLengthHistogram(dist) {
        const entries = Object.entries(dist);
        if (entries.length === 0) {
            return `<div style="text-align: center; color: var(--text-muted); padding: 24px;">No length distribution data available.</div>`;
        }

        const maxCount = Math.max(...entries.map(([_, count]) => count), 1);
        const isSingleEntry = (entries.length === 1);

        const barsHtml = entries.map(([len, count]) => {
            const heightPct = Math.max(16, Math.round((count / maxCount) * 100));
            const barWidth = isSingleEntry ? '64px' : '32px';
            return `
                <div class="histogram-col" style="display: flex; flex-direction: column; align-items: center; justify-content: flex-end; flex: ${isSingleEntry ? '0 0 auto' : '1'}; height: 140px; margin: 0 ${isSingleEntry ? 'auto' : '4px'};" title="${count} read(s) with length ${len} bp">
                    <span style="font-size: 0.74rem; font-weight: 700; color: var(--text-muted); margin-bottom: 6px;">${count} read${count === 1 ? '' : 's'}</span>
                    <div style="width: ${barWidth}; height: ${heightPct}%; background: var(--brand-purple); border-radius: 6px 6px 0 0; transition: height 0.3s ease; box-shadow: 0 2px 4px rgba(124, 58, 237, 0.2);"></div>
                    <span style="font-family: var(--font-mono); font-size: 0.8rem; font-weight: 700; color: var(--text-heading); margin-top: 8px;">${len} bp</span>
                </div>
            `;
        }).join('');

        return `
            <div>
                <div style="display: flex; align-items: flex-end; justify-content: ${isSingleEntry ? 'center' : 'space-around'}; gap: 12px; height: 160px; border-bottom: 2px solid #cbd5e1; padding: 10px 4px 0 4px;">
                    ${barsHtml}
                </div>
                <div style="display: flex; justify-content: space-between; font-size: 0.75rem; color: var(--text-muted); margin-top: 8px;">
                    <span>X-Axis: Read Length (bp)</span>
                    <span>Y-Axis: Frequency Count</span>
                </div>
            </div>
        `;
    }

    bindEvents() {
        // Scope switcher pills
        const btnWhole = this.container.querySelector('#btnScopeWhole');
        if (btnWhole) {
            btnWhole.addEventListener('click', async () => {
                this.scopeType = 'whole';
                await this.loadComposition();
                this.renderView();
                Toast.info('Switched Analytics Scope to Entire Dataset');
            });
        }

        const btnRead = this.container.querySelector('#btnScopeRead');
        if (btnRead) {
            btnRead.addEventListener('click', async () => {
                this.scopeType = 'read';
                await this.loadComposition();
                this.renderView();
                Toast.info(`Switched Analytics Scope to Read #${this.selectedReadIndex}`);
            });
        }

        const btnCustom = this.container.querySelector('#btnScopeCustom');
        if (btnCustom) {
            btnCustom.addEventListener('click', async () => {
                this.scopeType = 'custom';
                await this.loadComposition();
                this.renderView();
                Toast.info(`Switched Analytics Scope to Range [${this.customStart}, ${this.customEnd})`);
            });
        }

        // Single read dropdown change
        const readSelect = this.container.querySelector('#scopeReadSelect');
        if (readSelect) {
            readSelect.addEventListener('change', async (e) => {
                this.selectedReadIndex = parseInt(e.target.value, 10);
                await this.loadComposition();
                this.renderView();
                Toast.info(`Active read updated to Read #${this.selectedReadIndex}`);
            });
        }

        // Custom range apply button with full validation
        const btnApplyCustom = this.container.querySelector('#btnApplyCustomRange');
        if (btnApplyCustom) {
            btnApplyCustom.addEventListener('click', async () => {
                const sInput = this.container.querySelector('#inputCustomStart');
                const eInput = this.container.querySelector('#inputCustomEnd');
                const s = parseInt(sInput.value, 10);
                const e = parseInt(eInput.value, 10);
                const maxLen = this.activeDataset.totalSymbols || 1000;

                if (isNaN(s) || isNaN(e)) {
                    Toast.error('Please enter valid numeric start and end coordinates.');
                    return;
                }
                if (s < 0) {
                    Toast.error('Start position cannot be negative.');
                    return;
                }
                if (e <= s) {
                    Toast.error('End position must be greater than start position: [Start, End).');
                    return;
                }
                if (e > maxLen) {
                    Toast.error(`End position (${e}) cannot exceed total sequence length (${maxLen}).`);
                    return;
                }

                this.customStart = s;
                this.customEnd = e;
                await this.loadComposition();
                this.renderView();
                Toast.success(`Applied sequence range [${s}, ${e})`);
            });
        }

        // Table "Analyze Read" buttons
        this.container.querySelectorAll('.btn-select-read-scope').forEach(btn => {
            btn.addEventListener('click', async () => {
                const idx = parseInt(btn.getAttribute('data-index'), 10);
                this.scopeType = 'read';
                this.selectedReadIndex = idx;
                await this.loadComposition();
                this.renderView();

                // Smooth scroll to summary metrics card
                const metricsElem = this.container.querySelector('#analyticsSummaryMetrics');
                if (metricsElem) {
                    metricsElem.scrollIntoView({ behavior: 'smooth', block: 'start' });
                } else {
                    window.scrollTo({ top: 180, behavior: 'smooth' });
                }
                Toast.success(`Now analyzing Read #${idx}`);
            });
        });

        // Dynamic Scoped CSV Export
        const btnExportCsv = this.container.querySelector('#btnExportCompositionCsv');
        if (btnExportCsv) {
            btnExportCsv.addEventListener('click', () => {
                this.exportCompositionCsv();
            });
        }

        // Download PNG Buttons
        const btnDownloadBar = this.container.querySelector('#btnDownloadBarChartPng');
        if (btnDownloadBar) {
            btnDownloadBar.addEventListener('click', () => {
                this.downloadBarChartPng();
            });
        }

        const btnDownloadDonut = this.container.querySelector('#btnDownloadDonutChartPng');
        if (btnDownloadDonut) {
            btnDownloadDonut.addEventListener('click', () => {
                this.downloadDonutChartPng();
            });
        }

        const btnDownloadHist = this.container.querySelector('#btnDownloadHistogramPng');
        if (btnDownloadHist) {
            btnDownloadHist.addEventListener('click', () => {
                this.downloadHistogramPng();
            });
        }

        const btnDownloadQuality = this.container.querySelector('#btnDownloadQualityPng');
        if (btnDownloadQuality) {
            btnDownloadQuality.addEventListener('click', () => {
                this.downloadQualityPng();
            });
        }
    }

    // =========================================================================
    // Dynamic Scoped CSV Export (Client-Side)
    // =========================================================================

    exportCompositionCsv() {
        const comp = this.currentComposition || {};
        const counts = comp.counts || { A: 0, C: 0, G: 0, T: 0, N: 0 };
        const pcts = comp.percentages || { A: 0, C: 0, G: 0, T: 0, N: 0 };
        const totalBases = comp.totalDnaBases || 0;
        const separatorCount = comp.separatorCount || 0;
        const gcPct = comp.gcPercentage || 0;
        const atPct = comp.atPercentage || 0;

        const gcCount = (counts.G || 0) + (counts.C || 0);
        const atCount = (counts.A || 0) + (counts.T || 0);
        const ratio = atCount > 0 ? (gcCount / atCount).toFixed(2) : 'Not defined';

        let scopeLabel = 'Entire Dataset';
        if (this.scopeType === 'read') {
            const r = this.readsList.find(x => x.readIndex === this.selectedReadIndex);
            scopeLabel = `Read #${this.selectedReadIndex} (${r ? r.header : ''})`;
        } else if (this.scopeType === 'custom') {
            scopeLabel = `Sequence Range [${comp.startPos}, ${comp.endPos})`;
        }

        let csv = 'GENEDEX GENOMIC ANALYTICS - NUCLEOTIDE COMPOSITION REPORT\n';
        csv += `Dataset Name,"${this.activeDataset.name}"\n`;
        csv += `Analytics Scope,"${scopeLabel}"\n`;
        csv += `Coordinate Start,${comp.startPos || 0}\n`;
        csv += `Coordinate End,${comp.endPos || 0}\n`;
        csv += `Total DNA Bases,${totalBases}\n`;
        csv += `Excluded Read Separators (#),${separatorCount}\n`;
        csv += `GC Content,${gcPct}%\n`;
        csv += `AT Content,${atPct}%\n`;
        csv += `GC-to-AT Ratio,${ratio}\n`;
        csv += `Export Timestamp,"${new Date().toISOString()}"\n\n`;

        csv += 'Symbol,Nucleotide Name,Count (bp),Percentage (%)\n';
        csv += `A,"Adenine",${counts.A || 0},${pcts.A || 0}%\n`;
        csv += `C,"Cytosine",${counts.C || 0},${pcts.C || 0}%\n`;
        csv += `G,"Guanine",${counts.G || 0},${pcts.G || 0}%\n`;
        csv += `T,"Thymine",${counts.T || 0},${pcts.T || 0}%\n`;
        csv += `N,"Ambiguous N",${counts.N || 0},${pcts.N || 0}%\n`;

        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const safeDs = (this.activeDataset.name || 'genedex').replace(/[^a-zA-Z0-9_-]/g, '_');
        a.download = `genedex_composition_${safeDs}_${this.scopeType}.csv`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        Toast.success(`Exported nucleotide composition CSV for ${scopeLabel}`);
    }

    // =========================================================================
    // Client-Side Canvas PNG Generation (User-Facing Branding, No Technical Jargon)
    // =========================================================================

    downloadBarChartPng() {
        const comp = this.currentComposition || {};
        const counts = comp.counts || { A: 0, C: 0, G: 0, T: 0, N: 0 };
        const pcts = comp.percentages || { A: 0, C: 0, G: 0, T: 0, N: 0 };

        const canvas = document.createElement('canvas');
        canvas.width = 1200;
        canvas.height = 700;
        const ctx = canvas.getContext('2d');

        // Background
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // Title
        ctx.fillStyle = '#0f172a';
        ctx.font = 'bold 32px system-ui, -apple-system, sans-serif';
        ctx.fillText('GeneDex — Nucleotide Frequency Distribution', 60, 70);

        // Subtitle
        ctx.fillStyle = '#64748b';
        ctx.font = '18px system-ui, -apple-system, sans-serif';
        const scopeDesc = this.scopeType === 'whole' ? 'Entire Dataset' : (this.scopeType === 'read' ? `Read #${this.selectedReadIndex}` : `Sequence Range [${comp.startPos}, ${comp.endPos})`);
        ctx.fillText(`Dataset: ${this.activeDataset.name} | Scope: ${scopeDesc} | Total DNA Bases: ${comp.totalDnaBases} bp`, 60, 110);

        // Divider
        ctx.strokeStyle = '#e2e8f0';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(60, 135);
        ctx.lineTo(1140, 135);
        ctx.stroke();

        // Draw Bars
        const items = [
            { sym: 'A', name: 'Adenine', count: counts.A || 0, pct: pcts.A || 0, color: '#10b981' },
            { sym: 'C', name: 'Cytosine', count: counts.C || 0, pct: pcts.C || 0, color: '#0284c7' },
            { sym: 'G', name: 'Guanine', count: counts.G || 0, pct: pcts.G || 0, color: '#f59e0b' },
            { sym: 'T', name: 'Thymine', count: counts.T || 0, pct: pcts.T || 0, color: '#ef4444' },
            { sym: 'N', name: 'Ambiguous N', count: counts.N || 0, pct: pcts.N || 0, color: '#64748b' }
        ];

        let startY = 180;
        const barHeight = 44;
        const maxBarWidth = 680;

        items.forEach(item => {
            // Label
            ctx.fillStyle = '#0f172a';
            ctx.font = 'bold 22px system-ui, -apple-system, sans-serif';
            ctx.fillText(`${item.sym} (${item.name})`, 60, startY + 30);

            // Background track
            ctx.fillStyle = '#f1f5f9';
            ctx.beginPath();
            ctx.roundRect(280, startY, maxBarWidth, barHeight, 6);
            ctx.fill();

            // Colored fill
            const barW = Math.max(8, (item.pct / 100) * maxBarWidth);
            ctx.fillStyle = item.color;
            ctx.beginPath();
            ctx.roundRect(280, startY, barW, barHeight, 6);
            ctx.fill();

            // Value text
            ctx.fillStyle = '#0f172a';
            ctx.font = 'bold 20px monospace';
            ctx.fillText(`${item.count} bases (${item.pct}%)`, 280 + maxBarWidth + 20, startY + 30);

            startY += 85;
        });

        // Clean Footer (No Algorithm Details)
        ctx.fillStyle = '#94a3b8';
        ctx.font = '16px system-ui, -apple-system, sans-serif';
        ctx.fillText('Generated by GeneDex Genomic Sequence Indexing Platform', 60, 660);

        this.triggerCanvasDownload(canvas, `genedex_nucleotide_composition_${this.activeDataset.name}.png`);
    }

    downloadDonutChartPng() {
        const comp = this.currentComposition || {};
        const gcPct = comp.gcPercentage || 0;
        const atPct = comp.atPercentage || 0;
        const counts = comp.counts || {};
        const nPct = (comp.percentages && comp.percentages.N) || 0;

        const canvas = document.createElement('canvas');
        canvas.width = 1000;
        canvas.height = 700;
        const ctx = canvas.getContext('2d');

        // Background
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // Title
        ctx.fillStyle = '#0f172a';
        ctx.font = 'bold 32px system-ui, -apple-system, sans-serif';
        ctx.fillText('GeneDex — GC vs. AT Content Analysis', 60, 70);

        // Subtitle
        ctx.fillStyle = '#64748b';
        ctx.font = '18px system-ui, -apple-system, sans-serif';
        const scopeDesc = this.scopeType === 'whole' ? 'Entire Dataset' : (this.scopeType === 'read' ? `Read #${this.selectedReadIndex}` : `Sequence Range [${comp.startPos}, ${comp.endPos})`);
        ctx.fillText(`Dataset: ${this.activeDataset.name} | Scope: ${scopeDesc} | Total DNA Bases: ${comp.totalDnaBases} bp`, 60, 110);

        // Divider
        ctx.strokeStyle = '#e2e8f0';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(60, 135);
        ctx.lineTo(940, 135);
        ctx.stroke();

        // Draw Donut
        const centerX = 340;
        const centerY = 390;
        const radius = 170;
        const innerRadius = 110;

        const totalPct = (gcPct + atPct + nPct) || 100;
        const gcAngle = (gcPct / totalPct) * (2 * Math.PI);
        const atAngle = (atPct / totalPct) * (2 * Math.PI);

        // Arc GC (Cyan)
        ctx.fillStyle = '#0284c7';
        ctx.beginPath();
        ctx.arc(centerX, centerY, radius, -Math.PI / 2, -Math.PI / 2 + gcAngle);
        ctx.arc(centerX, centerY, innerRadius, -Math.PI / 2 + gcAngle, -Math.PI / 2, true);
        ctx.closePath();
        ctx.fill();

        // Arc AT (Emerald)
        ctx.fillStyle = '#10b981';
        ctx.beginPath();
        ctx.arc(centerX, centerY, radius, -Math.PI / 2 + gcAngle, -Math.PI / 2 + gcAngle + atAngle);
        ctx.arc(centerX, centerY, innerRadius, -Math.PI / 2 + gcAngle + atAngle, -Math.PI / 2 + gcAngle, true);
        ctx.closePath();
        ctx.fill();

        // Arc N (if N present)
        if (nPct > 0) {
            const nAngle = (nPct / totalPct) * (2 * Math.PI);
            ctx.fillStyle = '#64748b';
            ctx.beginPath();
            ctx.arc(centerX, centerY, radius, -Math.PI / 2 + gcAngle + atAngle, -Math.PI / 2 + gcAngle + atAngle + nAngle);
            ctx.arc(centerX, centerY, innerRadius, -Math.PI / 2 + gcAngle + atAngle + nAngle, -Math.PI / 2 + gcAngle + atAngle, true);
            ctx.closePath();
            ctx.fill();
        }

        // Center readout
        ctx.fillStyle = '#0f172a';
        ctx.font = 'bold 44px system-ui, -apple-system, sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText(`${gcPct}%`, centerX, centerY + 8);
        ctx.fillStyle = '#64748b';
        ctx.font = 'bold 18px system-ui, -apple-system, sans-serif';
        ctx.fillText('GC CONTENT', centerX, centerY + 40);
        ctx.textAlign = 'left';

        // Legend Cards on Right
        const legendX = 580;

        // GC Card
        ctx.fillStyle = '#f0f9ff';
        ctx.beginPath();
        ctx.roundRect(legendX, 230, 340, 95, 10);
        ctx.fill();
        ctx.strokeStyle = '#bae6fd';
        ctx.stroke();

        ctx.fillStyle = '#0284c7';
        ctx.fillRect(legendX + 20, 255, 18, 18);
        ctx.fillStyle = '#0369a1';
        ctx.font = 'bold 19px system-ui, -apple-system, sans-serif';
        ctx.fillText('GC Bases (G + C)', legendX + 50, 270);
        ctx.font = 'bold 24px monospace';
        ctx.fillText(`${(counts.G || 0) + (counts.C || 0)} bp (${gcPct}%)`, legendX + 50, 305);

        // AT Card
        ctx.fillStyle = '#ecfdf5';
        ctx.beginPath();
        ctx.roundRect(legendX, 345, 340, 95, 10);
        ctx.fill();
        ctx.strokeStyle = '#a7f3d0';
        ctx.stroke();

        ctx.fillStyle = '#10b981';
        ctx.fillRect(legendX + 20, 370, 18, 18);
        ctx.fillStyle = '#065f46';
        ctx.font = 'bold 19px system-ui, -apple-system, sans-serif';
        ctx.fillText('AT Bases (A + T)', legendX + 50, 385);
        ctx.font = 'bold 24px monospace';
        ctx.fillText(`${(counts.A || 0) + (counts.T || 0)} bp (${atPct}%)`, legendX + 50, 420);

        // N Card (if N bases exist)
        if (counts.N && counts.N > 0) {
            ctx.fillStyle = '#f8fafc';
            ctx.beginPath();
            ctx.roundRect(legendX, 460, 340, 95, 10);
            ctx.fill();
            ctx.strokeStyle = '#cbd5e1';
            ctx.stroke();

            ctx.fillStyle = '#64748b';
            ctx.fillRect(legendX + 20, 485, 18, 18);
            ctx.fillStyle = '#334155';
            ctx.font = 'bold 19px system-ui, -apple-system, sans-serif';
            ctx.fillText('Ambiguous Bases (N)', legendX + 50, 500);
            ctx.font = 'bold 24px monospace';
            ctx.fillText(`${counts.N} bp (${nPct}%)`, legendX + 50, 535);
        }

        // Clean Footer
        ctx.fillStyle = '#94a3b8';
        ctx.font = '16px system-ui, -apple-system, sans-serif';
        ctx.fillText('Generated by GeneDex Genomic Sequence Indexing Platform', 60, 660);

        this.triggerCanvasDownload(canvas, `genedex_gc_content_${this.activeDataset.name}.png`);
    }

    downloadHistogramPng() {
        const an = this.analyticsData || {};
        const dist = an.lengthDistribution || {};
        const entries = Object.entries(dist);

        const canvas = document.createElement('canvas');
        canvas.width = 1000;
        canvas.height = 650;
        const ctx = canvas.getContext('2d');

        // Background
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // Title
        ctx.fillStyle = '#0f172a';
        ctx.font = 'bold 32px system-ui, -apple-system, sans-serif';
        ctx.fillText('GeneDex — Read-Length Distribution Histogram', 60, 70);

        // Subtitle
        ctx.fillStyle = '#64748b';
        ctx.font = '18px system-ui, -apple-system, sans-serif';
        ctx.fillText(`Dataset: ${this.activeDataset.name} | Total Reads: ${this.activeDataset.totalReads || 1} | Avg Length: ${an.avgReadLength || 0} bp`, 60, 110);

        // Divider
        ctx.strokeStyle = '#e2e8f0';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(60, 135);
        ctx.lineTo(940, 135);
        ctx.stroke();

        // Draw Histogram
        const maxCount = Math.max(...entries.map(([_, count]) => count), 1);
        const baselineY = 520;
        const chartHeight = 320;
        const isSingle = (entries.length === 1);

        const totalW = 720;
        const colWidth = isSingle ? 120 : Math.min(80, totalW / Math.max(entries.length, 1));
        const startX = isSingle ? (canvas.width - colWidth) / 2 : 140;

        entries.forEach(([len, count], idx) => {
            const h = Math.max(30, (count / maxCount) * chartHeight);
            const x = isSingle ? startX : (startX + idx * (colWidth + 24));
            const y = baselineY - h;

            // Bar
            ctx.fillStyle = '#7c3aed';
            ctx.beginPath();
            ctx.roundRect(x, y, colWidth, h, 6);
            ctx.fill();

            // Count label
            ctx.fillStyle = '#0f172a';
            ctx.font = 'bold 20px system-ui, -apple-system, sans-serif';
            ctx.textAlign = 'center';
            ctx.fillText(`${count} read${count === 1 ? '' : 's'}`, x + colWidth / 2, y - 12);

            // Length label
            ctx.fillStyle = '#475569';
            ctx.font = 'bold 18px monospace';
            ctx.fillText(`${len} bp`, x + colWidth / 2, baselineY + 32);
        });

        // Baseline
        ctx.strokeStyle = '#94a3b8';
        ctx.lineWidth = 3;
        ctx.beginPath();
        ctx.moveTo(60, baselineY);
        ctx.lineTo(940, baselineY);
        ctx.stroke();

        // Clean Footer
        ctx.textAlign = 'left';
        ctx.fillStyle = '#94a3b8';
        ctx.font = '16px system-ui, -apple-system, sans-serif';
        ctx.fillText('Generated by GeneDex Genomic Sequence Indexing Platform', 60, 610);

        this.triggerCanvasDownload(canvas, `genedex_length_histogram_${this.activeDataset.name}.png`);
    }

    downloadQualityPng() {
        const an = this.analyticsData || {};
        const qSummary = an.qualitySummary || {};

        const canvas = document.createElement('canvas');
        canvas.width = 1000;
        canvas.height = 650;
        const ctx = canvas.getContext('2d');

        // Background
        ctx.fillStyle = '#ffffff';
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // Title
        ctx.fillStyle = '#0f172a';
        ctx.font = 'bold 32px system-ui, -apple-system, sans-serif';
        ctx.fillText('GeneDex — Sequencing Quality Score Profile', 60, 70);

        // Subtitle
        ctx.fillStyle = '#64748b';
        ctx.font = '18px system-ui, -apple-system, sans-serif';
        ctx.fillText(`Dataset: ${this.activeDataset.name} | Mean Phred Score: Q${qSummary.meanPhredScore || 'N/A'}`, 60, 110);

        // Divider
        ctx.strokeStyle = '#e2e8f0';
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(60, 135);
        ctx.lineTo(940, 135);
        ctx.stroke();

        // Accuracy Distribution Bar
        const barX = 100;
        const barY = 220;
        const barW = 800;
        const barH = 36;

        ctx.fillStyle = '#0f172a';
        ctx.font = 'bold 20px system-ui, -apple-system, sans-serif';
        ctx.fillText('Accuracy Distribution Across Bases', barX, barY - 14);

        const q30W = ((qSummary.q30Percentage || 0) / 100) * barW;
        const q20W = ((qSummary.q20Percentage || 0) / 100) * barW;
        const qLowW = ((qSummary.qUnder20Percentage || 0) / 100) * barW;

        ctx.fillStyle = '#10b981';
        ctx.fillRect(barX, barY, q30W, barH);
        ctx.fillStyle = '#0284c7';
        ctx.fillRect(barX + q30W, barY, q20W, barH);
        ctx.fillStyle = '#ef4444';
        ctx.fillRect(barX + q30W + q20W, barY, qLowW, barH);

        // 3 Cards Below
        const cardY = 320;
        const cardW = 240;
        const gap = 40;

        // Q >= 30
        ctx.fillStyle = '#ecfdf5';
        ctx.beginPath();
        ctx.roundRect(barX, cardY, cardW, 140, 8);
        ctx.fill();
        ctx.strokeStyle = '#a7f3d0';
        ctx.stroke();

        ctx.fillStyle = '#065f46';
        ctx.font = 'bold 18px system-ui, -apple-system, sans-serif';
        ctx.fillText('Q ≥ 30 (High)', barX + 20, cardY + 36);
        ctx.font = 'bold 32px system-ui, -apple-system, sans-serif';
        ctx.fillText(`${qSummary.q30Percentage || 0}%`, barX + 20, cardY + 80);
        ctx.fillStyle = '#047857';
        ctx.font = '14px system-ui, -apple-system, sans-serif';
        ctx.fillText('≥ 99.9% base accuracy', barX + 20, cardY + 112);

        // Q 20-29
        const card2X = barX + cardW + gap;
        ctx.fillStyle = '#f0f9ff';
        ctx.beginPath();
        ctx.roundRect(card2X, cardY, cardW, 140, 8);
        ctx.fill();
        ctx.strokeStyle = '#bae6fd';
        ctx.stroke();

        ctx.fillStyle = '#0369a1';
        ctx.font = 'bold 18px system-ui, -apple-system, sans-serif';
        ctx.fillText('Q 20-29 (Good)', card2X + 20, cardY + 36);
        ctx.font = 'bold 32px system-ui, -apple-system, sans-serif';
        ctx.fillText(`${qSummary.q20Percentage || 0}%`, card2X + 20, cardY + 80);
        ctx.fillStyle = '#0284c7';
        ctx.font = '14px system-ui, -apple-system, sans-serif';
        ctx.fillText('99.0% - 99.9% accuracy', card2X + 20, cardY + 112);

        // Q < 20
        const card3X = card2X + cardW + gap;
        ctx.fillStyle = '#fef2f2';
        ctx.beginPath();
        ctx.roundRect(card3X, cardY, cardW, 140, 8);
        ctx.fill();
        ctx.strokeStyle = '#fecaca';
        ctx.stroke();

        ctx.fillStyle = '#991b1b';
        ctx.font = 'bold 18px system-ui, -apple-system, sans-serif';
        ctx.fillText('Q < 20 (Low)', card3X + 20, cardY + 36);
        ctx.font = 'bold 32px system-ui, -apple-system, sans-serif';
        ctx.fillText(`${qSummary.qUnder20Percentage || 0}%`, card3X + 20, cardY + 80);
        ctx.fillStyle = '#ef4444';
        ctx.font = '14px system-ui, -apple-system, sans-serif';
        ctx.fillText('< 99.0% accuracy', card3X + 20, cardY + 112);

        // Clean Footer
        ctx.fillStyle = '#94a3b8';
        ctx.font = '16px system-ui, -apple-system, sans-serif';
        ctx.fillText('Generated by GeneDex Genomic Sequence Indexing Platform', 60, 610);

        this.triggerCanvasDownload(canvas, `genedex_quality_profile_${this.activeDataset.name}.png`);
    }

    triggerCanvasDownload(canvas, filename) {
        canvas.toBlob((blob) => {
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = filename;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);
            Toast.success(`Downloaded high-resolution chart: ${filename}`);
        }, 'image/png');
    }

    escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }
}
