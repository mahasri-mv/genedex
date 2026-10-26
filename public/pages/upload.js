/**
 * GeneDex — Dataset Upload & Registry Management Page
 * Contribution: Member 4 (sruthicasathish-stack)
 * 
 * Provides:
 * - FASTQ drag-and-drop file upload & direct sequence pasting
 * - Client-side format pre-validation (4-line syntax, quality match, valid bases)
 * - Active dataset switching, telemetry metrics, and deletion
 * 
 * @author sruthicasathish-stack
 */

import { api } from '../services/api.js';
import { Toast } from '../components/Toast.js';

export class UploadPage {
    constructor(containerId) {
        this.container = document.getElementById(containerId);
        this.activeTab = 'upload'; // 'upload' | 'paste'
        this.selectedFile = null;
        this.pastedName = '';
        this.pastedText = '';
        this.datasets = [];
        this.activeDataset = null;
        this.validationResults = null;
    }

    async render() {
        if (!this.container) return;

        // Check if tab parameter requested
        const hash = window.location.hash || '';
        if (hash.includes('tab=paste') || window.location.search.includes('tab=paste')) {
            this.activeTab = 'paste';
        }

        // Fetch datasets from centralized backend API
        await this.refreshDatasets();

        const activeStats = this.activeDataset && this.activeDataset.statistics ? this.activeDataset.statistics : this.activeDataset;
        const isReady = this.activeDataset && this.activeDataset.isLoaded !== false && ((this.activeDataset.totalReads || 0) > 0 || (activeStats && (activeStats.totalReads || 0) > 0));
        const activeDatasetName = this.activeDataset ? (this.activeDataset.name || this.activeDataset.datasetName) : null;

        // Formatted statistics with strict singular/plural checking
        const rawReads = this.activeDataset ? (this.activeDataset.totalReads || (activeStats && activeStats.totalReads) || 0) : 0;
        const totalReadsStr = isReady 
            ? `${rawReads.toLocaleString()} ${rawReads === 1 ? 'read' : 'reads'}`
            : "Not available";

        const rawBases = this.activeDataset ? (this.activeDataset.totalBases != null ? this.activeDataset.totalBases : (this.activeDataset.totalDnaBases || this.activeDataset.totalSymbols || (activeStats && (activeStats.totalDnaBases || activeStats.totalBases)) || 0)) : 0;
        const totalBasesStr = isReady ? `${rawBases.toLocaleString()} bp` : "Not available";

        // Average read length
        let avgReadLength = "Not available";
        if (isReady) {
            if (activeStats && activeStats.avgReadLength != null) {
                avgReadLength = activeStats.avgReadLength + " bp";
            } else if (rawReads > 0 && rawBases > 0) {
                avgReadLength = (rawBases / rawReads).toFixed(1) + " bp";
            }
        }

        // GC Content
        let gcContent = "Not available";
        if (isReady && activeStats && activeStats.overallGcPercentage != null) {
            gcContent = activeStats.overallGcPercentage + "%";
        }

        // Dataset File Size
        const fileSizeFormatted = isReady ? this.getDatasetFileSize(this.activeDataset) : "Not available";
        const datasetStatus = isReady ? "Ready for Analysis" : "Not available";

        this.container.innerHTML = `
            <div class="page-container">
                <!-- Top Section — Compact Centered Page Header -->
                <div class="section-header-centered" style="margin-bottom: 24px;">
                    <h1 class="section-title">Upload &amp; Manage DNA Datasets</h1>
                    <p class="section-subtitle">
                        Upload or paste your DNA sequencing data, validate your dataset, and start exploring your genomic analysis tools.
                    </p>
                </div>

                <!-- Error / Notice Banner Slot -->
                <div id="uploadErrorSlot"></div>

                <!-- Main Grid Layout (2x2 Grid with Shared Baselines) -->
                <div class="upload-main-grid">
                    <!-- Add a DNA Dataset Card with subtle decorative DNA SVG background -->
                    <div class="dataset-input-card">
                            <!-- Subtle decorative DNA double-helix illustration (opacity 0.055) -->
                            <svg class="dataset-input-card-dna-bg" viewBox="0 0 200 400" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                                <path d="M50 20 Q150 70 50 120 T50 220 T50 320 T50 420" stroke="#059669" stroke-width="3" stroke-linecap="round"/>
                                <path d="M150 20 Q50 70 150 120 T150 220 T150 320 T150 420" stroke="#0d9488" stroke-width="3" stroke-linecap="round"/>
                                <line x1="60" y1="35" x2="140" y2="35" stroke="#059669" stroke-width="2" stroke-dasharray="3 3"/>
                                <line x1="85" y1="70" x2="115" y2="70" stroke="#0d9488" stroke-width="2.5"/>
                                <line x1="60" y1="105" x2="140" y2="105" stroke="#059669" stroke-width="2" stroke-dasharray="3 3"/>
                                <line x1="60" y1="135" x2="140" y2="135" stroke="#059669" stroke-width="2" stroke-dasharray="3 3"/>
                                <line x1="85" y1="170" x2="115" y2="170" stroke="#0d9488" stroke-width="2.5"/>
                                <line x1="60" y1="205" x2="140" y2="205" stroke="#059669" stroke-width="2" stroke-dasharray="3 3"/>
                                <line x1="60" y1="235" x2="140" y2="235" stroke="#059669" stroke-width="2" stroke-dasharray="3 3"/>
                                <line x1="85" y1="270" x2="115" y2="270" stroke="#0d9488" stroke-width="2.5"/>
                                <line x1="60" y1="305" x2="140" y2="305" stroke="#059669" stroke-width="2" stroke-dasharray="3 3"/>
                                <line x1="60" y1="335" x2="140" y2="335" stroke="#059669" stroke-width="2" stroke-dasharray="3 3"/>
                                <line x1="85" y1="370" x2="115" y2="370" stroke="#0d9488" stroke-width="2.5"/>
                                <circle cx="50" cy="20" r="4" fill="#059669"/>
                                <circle cx="150" cy="20" r="4" fill="#0d9488"/>
                                <circle cx="50" cy="120" r="4" fill="#059669"/>
                                <circle cx="150" cy="120" r="4" fill="#0d9488"/>
                                <circle cx="50" cy="220" r="4" fill="#059669"/>
                                <circle cx="150" cy="220" r="4" fill="#0d9488"/>
                                <circle cx="50" cy="320" r="4" fill="#059669"/>
                                <circle cx="150" cy="320" r="4" fill="#0d9488"/>
                            </svg>

                            <div class="dataset-input-card-content">
                                <h2 style="font-size: 1.15rem; font-weight: 800; color: var(--text-heading); margin-bottom: 4px;">
                                    Add a DNA Dataset
                                </h2>
                                <p style="font-size: 0.84rem; color: var(--text-muted); margin-bottom: 12px;">
                                    Choose a FASTQ file from your device or paste FASTQ sequence data directly.
                                </p>

                                <!-- Two Tabs: Upload FASTQ File / Paste FASTQ Text -->
                                <div class="dataset-tabs-nav">
                                    <button type="button" class="dataset-tab-btn ${this.activeTab === 'upload' ? 'active' : ''}" id="tabBtnUpload">
                                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                                        <span>Upload FASTQ File</span>
                                    </button>
                                    <button type="button" class="dataset-tab-btn ${this.activeTab === 'paste' ? 'active' : ''}" id="tabBtnPaste">
                                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><polyline points="4 7 4 4 20 4 20 7"></polyline><line x1="9" y1="20" x2="15" y2="20"></line><line x1="12" y1="4" x2="12" y2="20"></line></svg>
                                        <span>Paste FASTQ Text</span>
                                    </button>
                                </div>

                                <!-- Tab 1 Content: Upload FASTQ File -->
                                <div id="tabContentUpload" style="display: ${this.activeTab === 'upload' ? 'block' : 'none'};">
                                    <div class="upload-drop-area" id="dropArea" style="margin-bottom: 0;">
                                        <!-- DNA & Sequencing Background Illustration Graphic -->
                                        <svg class="drop-area-dna-bg" viewBox="0 0 600 240" preserveAspectRatio="xMidYMid slice" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                                            <defs>
                                                <linearGradient id="dnaGrad1" x1="0%" y1="0%" x2="100%" y2="100%">
                                                    <stop offset="0%" stop-color="#059669" stop-opacity="0.14"/>
                                                    <stop offset="50%" stop-color="#10b981" stop-opacity="0.08"/>
                                                    <stop offset="100%" stop-color="#065f46" stop-opacity="0.12"/>
                                                </linearGradient>
                                                <linearGradient id="dnaGrad2" x1="0%" y1="100%" x2="100%" y2="0%">
                                                    <stop offset="0%" stop-color="#0d9488" stop-opacity="0.12"/>
                                                    <stop offset="50%" stop-color="#34d399" stop-opacity="0.06"/>
                                                    <stop offset="100%" stop-color="#047857" stop-opacity="0.14"/>
                                                </linearGradient>
                                                <linearGradient id="peakGreen" x1="0" y1="0" x2="0" y2="1">
                                                    <stop offset="0%" stop-color="#10b981" stop-opacity="0.18"/>
                                                    <stop offset="100%" stop-color="#10b981" stop-opacity="0.01"/>
                                                </linearGradient>
                                                <linearGradient id="peakBlue" x1="0" y1="0" x2="0" y2="1">
                                                    <stop offset="0%" stop-color="#3b82f6" stop-opacity="0.16"/>
                                                    <stop offset="100%" stop-color="#3b82f6" stop-opacity="0.01"/>
                                                </linearGradient>
                                                <linearGradient id="peakRed" x1="0" y1="0" x2="0" y2="1">
                                                    <stop offset="0%" stop-color="#ef4444" stop-opacity="0.14"/>
                                                    <stop offset="100%" stop-color="#ef4444" stop-opacity="0.01"/>
                                                </linearGradient>
                                                <linearGradient id="peakAmber" x1="0" y1="0" x2="0" y2="1">
                                                    <stop offset="0%" stop-color="#f59e0b" stop-opacity="0.14"/>
                                                    <stop offset="100%" stop-color="#f59e0b" stop-opacity="0.01"/>
                                                </linearGradient>
                                            </defs>

                                            <!-- Sequencing Chromatogram Peaks (Bottom trace) -->
                                            <path d="M 0 240 L 0 210 Q 30 150 60 215 T 120 220 Q 150 145 180 220 T 240 225 Q 270 160 300 220 T 360 225 Q 390 140 420 220 T 480 225 Q 510 155 540 220 T 600 215 L 600 240 Z" fill="url(#peakGreen)"/>
                                            <path d="M 0 240 L 0 225 Q 45 165 90 225 T 180 225 Q 225 150 270 225 T 360 225 Q 405 165 450 225 T 540 225 Q 570 170 600 225 L 600 240 Z" fill="url(#peakBlue)"/>
                                            <path d="M 0 240 L 0 220 Q 75 160 150 225 T 300 225 Q 345 155 390 225 T 480 225 Q 525 165 600 225 L 600 240 Z" fill="url(#peakAmber)"/>

                                            <!-- DNA Double-Helix Strands Sweeping across Background -->
                                            <path d="M -20 120 Q 60 30 140 120 T 300 120 T 460 120 T 620 120" stroke="url(#dnaGrad1)" stroke-width="3" stroke-linecap="round"/>
                                            <path d="M -20 120 Q 60 210 140 120 T 300 120 T 460 120 T 620 120" stroke="url(#dnaGrad2)" stroke-width="3" stroke-linecap="round"/>

                                            <!-- Base Pairs Connecting Strands -->
                                            <line x1="20" y1="65" x2="20" y2="175" stroke="#059669" stroke-width="1.8" stroke-dasharray="2 3" opacity="0.3"/>
                                            <line x1="60" y1="30" x2="60" y2="210" stroke="#10b981" stroke-width="2.2" opacity="0.4"/>
                                            <circle cx="60" cy="30" r="4" fill="#059669" opacity="0.5"/>
                                            <circle cx="60" cy="210" r="4" fill="#0d9488" opacity="0.5"/>
                                            <line x1="100" y1="65" x2="100" y2="175" stroke="#059669" stroke-width="1.8" stroke-dasharray="2 3" opacity="0.3"/>

                                            <line x1="180" y1="65" x2="180" y2="175" stroke="#0d9488" stroke-width="1.8" stroke-dasharray="2 3" opacity="0.3"/>
                                            <line x1="220" y1="30" x2="220" y2="210" stroke="#059669" stroke-width="2.2" opacity="0.4"/>
                                            <circle cx="220" cy="30" r="4" fill="#059669" opacity="0.5"/>
                                            <circle cx="220" cy="210" r="4" fill="#0d9488" opacity="0.5"/>
                                            <line x1="260" y1="65" x2="260" y2="175" stroke="#0d9488" stroke-width="1.8" stroke-dasharray="2 3" opacity="0.3"/>

                                            <line x1="340" y1="65" x2="340" y2="175" stroke="#059669" stroke-width="1.8" stroke-dasharray="2 3" opacity="0.3"/>
                                            <line x1="380" y1="30" x2="380" y2="210" stroke="#10b981" stroke-width="2.2" opacity="0.4"/>
                                            <circle cx="380" cy="30" r="4" fill="#059669" opacity="0.5"/>
                                            <circle cx="380" cy="210" r="4" fill="#0d9488" opacity="0.5"/>
                                            <line x1="420" y1="65" x2="420" y2="175" stroke="#059669" stroke-width="1.8" stroke-dasharray="2 3" opacity="0.3"/>

                                            <line x1="500" y1="65" x2="500" y2="175" stroke="#0d9488" stroke-width="1.8" stroke-dasharray="2 3" opacity="0.3"/>
                                            <line x1="540" y1="30" x2="540" y2="210" stroke="#059669" stroke-width="2.2" opacity="0.4"/>
                                            <circle cx="540" cy="30" r="4" fill="#059669" opacity="0.5"/>
                                            <circle cx="540" cy="210" r="4" fill="#0d9488" opacity="0.5"/>
                                            <line x1="580" y1="65" x2="580" y2="175" stroke="#0d9488" stroke-width="1.8" stroke-dasharray="2 3" opacity="0.3"/>

                                            <!-- Subtle Floating Base Nucleotide Letters -->
                                            <text x="35" y="45" font-family="'JetBrains Mono', monospace" font-size="28" font-weight="800" fill="#059669" opacity="0.10">A</text>
                                            <text x="135" y="195" font-family="'JetBrains Mono', monospace" font-size="24" font-weight="800" fill="#2563eb" opacity="0.08">C</text>
                                            <text x="295" y="40" font-family="'JetBrains Mono', monospace" font-size="26" font-weight="800" fill="#d97706" opacity="0.09">G</text>
                                            <text x="445" y="200" font-family="'JetBrains Mono', monospace" font-size="28" font-weight="800" fill="#dc2626" opacity="0.08">T</text>
                                            <text x="525" y="45" font-family="'JetBrains Mono', monospace" font-size="22" font-weight="800" fill="#059669" opacity="0.10">A</text>
                                        </svg>

                                        <!-- Interactive Foreground Content -->
                                        <div class="drop-area-foreground">
                                            <div class="drop-icon-circle">
                                                <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                                            </div>
                                            <div class="drop-headline">Drag and drop your FASTQ file here</div>
                                            <div class="drop-subtext">Or browse your device to select a file</div>
                                            <button type="button" class="btn-primary-hero" style="font-size: 0.88rem; padding: 9px 20px;" id="btnChooseFile">
                                                Choose File
                                            </button>
                                            <input type="file" id="fileUploadInput" accept=".fastq,.fq,.txt" style="display: none;">
                                            <div class="file-spec-note">Supported formats: .fastq, .fq, .txt (Max file size: 50 MB)</div>
                                        </div>
                                    </div>

                                    <!-- Selected File Information Bar & Action -->
                                    <div id="fileSelectionBarSlot" style="width: 100%; margin-top: 12px;">
                                        ${this.renderSelectedFileBar()}
                                    </div>
                                </div>

                                <!-- Tab 2 Content: Paste FASTQ Text -->
                                <div id="tabContentPaste" style="display: ${this.activeTab === 'paste' ? 'block' : 'none'};">
                                    <div style="margin-bottom: 12px;">
                                        <label for="pasteDatasetName" style="display: block; font-size: 0.82rem; font-weight: 700; color: var(--text-heading); margin-bottom: 4px;">
                                            Dataset Name
                                        </label>
                                        <input type="text" id="pasteDatasetName" class="dataset-name-input" placeholder="Enter a name for your dataset (e.g., My DNA Sample)" value="${this.escapeHtml(this.pastedName)}">
                                    </div>

                                    <div style="margin-bottom: 12px;">
                                        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px;">
                                            <label for="pasteFastqText" style="font-size: 0.82rem; font-weight: 700; color: var(--text-heading);">
                                                Paste FASTQ Data
                                            </label>
                                            <span id="pasteStatsCounter" style="font-size: 0.74rem; color: var(--text-muted);">
                                                ${this.getPasteStatsText()}
                                            </span>
                                        </div>
                                        <textarea id="pasteFastqText" class="paste-fastq-textarea" placeholder="@read1&#10;GATTACA&#10;+&#10;IIIIIII&#10;&#10;@read2&#10;CAGT&#10;+&#10;IIII">${this.escapeHtml(this.pastedText)}</textarea>
                                    </div>

                                    <div style="display: flex; gap: 10px; align-items: center; justify-content: flex-end;">
                                        <button type="button" class="btn-table-action" id="btnClearPaste">
                                            Clear Text
                                        </button>
                                        <button type="button" class="btn-primary-hero" style="font-size: 0.88rem; padding: 9px 20px;" id="btnSubmitPaste">
                                            Validate &amp; Add Dataset
                                        </button>
                                    </div>
                                </div>

                                <!-- Staged Progress Stepper -->
                                <div class="staged-progress-card" id="stagedProgressCard" style="display: none; margin-top: 16px;">
                                    <div style="font-size: 0.82rem; font-weight: 800; text-transform: uppercase; color: var(--text-heading); display: flex; justify-content: space-between;">
                                        <span>Dataset Processing Pipeline</span>
                                        <span id="pipelineStatusText" style="color: var(--brand-green);">Processing...</span>
                                    </div>
                                    <div class="stages-track">
                                        <div class="stage-step" id="stageStep1">
                                            <div class="stage-bullet">1</div>
                                            <span class="stage-label">Reading</span>
                                        </div>
                                        <div class="stage-step" id="stageStep2">
                                            <div class="stage-bullet">2</div>
                                            <span class="stage-label">Validating</span>
                                        </div>
                                        <div class="stage-step" id="stageStep3">
                                            <div class="stage-bullet">3</div>
                                            <span class="stage-label">Processing</span>
                                        </div>
                                        <div class="stage-step" id="stageStep4">
                                            <div class="stage-bullet">4</div>
                                            <span class="stage-label">Ready</span>
                                        </div>
                                    </div>
                                    <div id="pipelineInlineError" style="display: none; margin-top: 14px;"></div>
                                </div>
                            </div>
                        </div>

                        <!-- Card 2 (Row 1, Col 2): Dataset Overview Card -->
                        <div class="summary-card" style="margin-bottom: 0;">
                            <div class="summary-header">
                                <div>
                                    <div style="font-size: 1.05rem; font-weight: 800; color: var(--text-heading);">Dataset Overview</div>
                                    <div style="font-size: 0.78rem; color: var(--text-muted); margin-top: 2px;">
                                        Active Dataset: <strong id="sumActiveName" style="color: var(--brand-green);">${activeDatasetName ? activeDatasetName : 'None selected'}</strong>
                                    </div>
                                </div>
                                ${isReady 
                                    ? `<span class="badge-active">&#10004; Ready for Analysis</span>` 
                                    : `<span class="badge-ready">No Active Dataset</span>`}
                            </div>

                            ${isReady ? `
                                <div class="summary-grid">
                                    <div class="summary-box">
                                        <div class="sum-val" id="sumReads">${totalReadsStr}</div>
                                        <div class="sum-lbl">Total DNA Reads</div>
                                    </div>
                                    <div class="summary-box">
                                        <div class="sum-val" id="sumBases">${totalBasesStr}</div>
                                        <div class="sum-lbl">Total DNA Bases</div>
                                    </div>
                                    <div class="summary-box">
                                        <div class="sum-val" id="sumFileSize">${fileSizeFormatted}</div>
                                        <div class="sum-lbl">Dataset File Size</div>
                                    </div>
                                    <div class="summary-box">
                                        <div class="sum-val" id="sumAvgLength">${avgReadLength}</div>
                                        <div class="sum-lbl">Average Read Length</div>
                                    </div>
                                    <div class="summary-box">
                                        <div class="sum-val" id="sumGcContent">${gcContent}</div>
                                        <div class="sum-lbl">GC Content</div>
                                    </div>
                                    <div class="summary-box">
                                        <div class="sum-val" id="sumStatus" style="color: var(--brand-green); font-size: 1.05rem;">${datasetStatus}</div>
                                        <div class="sum-lbl">Dataset Status</div>
                                    </div>
                                </div>
                            ` : `
                                <div class="empty-dataset-state">
                                    <div style="font-size: 1rem; font-weight: 700; color: var(--text-heading); margin-bottom: 4px;">
                                        No active dataset selected.
                                    </div>
                                    <p style="font-size: 0.82rem; color: var(--text-muted); margin-bottom: 0;">
                                        Upload a FASTQ file, paste FASTQ data, or choose a sample dataset below to begin.
                                    </p>
                                </div>
                            `}
                        </div>

                        <!-- Card 3 (Row 2, Col 1): FASTQ File Requirements Card -->
                        <div class="fastq-requirements-card" style="margin-bottom: 0;">
                            <div class="checklist-title">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 11 12 14 22 4"></polyline><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path></svg>
                                <span>FASTQ File Requirements</span>
                            </div>
                            <ul class="checklist-items" id="requirementsList">
                                ${this.renderRequirementsList()}
                            </ul>
                        </div>

                        <!-- Card 4 (Row 2, Col 2): Next Steps & Analysis Tools Shortcuts (2x2 Grid) -->
                        <div class="workflow-shortcuts-card" style="margin-bottom: 0;">
                            <div style="font-size: 0.84rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-heading); display: flex; align-items: center; gap: 8px;">
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon></svg>
                                <span>Next Steps &amp; Analysis Tools</span>
                            </div>
                            <p style="font-size: 0.8rem; color: var(--text-muted); margin-top: 4px; margin-bottom: 12px;">
                                ${isReady 
                                    ? "Your active dataset is ready. Jump directly to any sequence analysis tool:" 
                                    : "Analysis tools will be unlocked once a dataset is uploaded or activated."}
                            </p>
                            <div class="workflow-shortcuts-grid">
                                <a href="#/search" class="workflow-shortcut-item ${isReady ? '' : 'disabled'}">
                                    <div class="shortcut-icon" style="color: var(--brand-green);">
                                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
                                    </div>
                                    <div>
                                        <div style="font-weight: 700; font-size: 0.86rem;">Search DNA Patterns</div>
                                        <div style="font-size: 0.74rem; color: var(--text-muted);">Locate nucleotide motifs &amp; matches</div>
                                    </div>
                                </a>
                                <a href="#/explorer" class="workflow-shortcut-item ${isReady ? '' : 'disabled'}">
                                    <div class="shortcut-icon" style="color: #2563eb;">
                                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                                    </div>
                                    <div>
                                        <div style="font-weight: 700; font-size: 0.86rem;">Explore DNA Reads</div>
                                        <div style="font-size: 0.74rem; color: var(--text-muted);">Inspect individual reads &amp; quality</div>
                                    </div>
                                </a>
                                <a href="#/analytics" class="workflow-shortcut-item ${isReady ? '' : 'disabled'}">
                                    <div class="shortcut-icon" style="color: #7c3aed;">
                                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><line x1="18" y1="20" x2="18" y2="10"></line><line x1="12" y1="20" x2="12" y2="4"></line><line x1="6" y1="20" x2="6" y2="14"></line></svg>
                                    </div>
                                    <div>
                                        <div style="font-weight: 700; font-size: 0.86rem;">Genomic Analytics</div>
                                        <div style="font-size: 0.74rem; color: var(--text-muted);">Composition, GC% &amp; distributions</div>
                                    </div>
                                </a>
                                <a href="#/reports" class="workflow-shortcut-item ${isReady ? '' : 'disabled'}">
                                    <div class="shortcut-icon" style="color: #059669;">
                                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="12" y1="18" x2="12" y2="12"></line><line x1="9" y1="15" x2="15" y2="15"></line></svg>
                                    </div>
                                    <div>
                                        <div style="font-weight: 700; font-size: 0.86rem;">Generate Report</div>
                                        <div style="font-size: 0.74rem; color: var(--text-muted);">Export personalized analysis report</div>
                                    </div>
                                </a>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Bottom Full-Width Section 1: Try a Sample Dataset -->
                <div class="sample-datasets-card">
                    <div style="font-size: 0.84rem; font-weight: 800; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text-heading); margin-bottom: 4px;">
                        Try a Sample Dataset
                    </div>
                    <p style="font-size: 0.82rem; color: var(--text-muted); margin-bottom: 12px;">
                        New to GeneDex? Select a sample dataset to explore the available analysis tools.
                    </p>
                    <div class="sample-datasets-grid">
                        <div class="sample-ds-card" id="btnLoadReportDemo">
                            <div>
                                <div style="font-size: 0.92rem; font-weight: 800; color: var(--text-heading); margin-bottom: 2px;">
                                    Small Demo Dataset
                                </div>
                                <div style="font-size: 0.78rem; color: var(--text-muted);">
                                    Single read demo sequence (14 bp)
                                </div>
                            </div>
                            <div style="font-size: 0.76rem; font-weight: 700; color: var(--brand-green); margin-top: 6px;">
                                Click to Load &amp; Activate &rarr;
                            </div>
                        </div>

                        <div class="sample-ds-card" id="btnLoadStandardSample">
                            <div>
                                <div style="font-size: 0.92rem; font-weight: 800; color: var(--text-heading); margin-bottom: 2px;">
                                    Standard Sample — 3 Reads
                                </div>
                                <div style="font-size: 0.78rem; color: var(--text-muted);">
                                    Standard sample dataset (3 reads, 16 bp)
                                </div>
                            </div>
                            <div style="font-size: 0.76rem; font-weight: 700; color: var(--brand-green); margin-top: 6px;">
                                Click to Load &amp; Activate &rarr;
                            </div>
                        </div>

                        <div class="sample-ds-card" id="btnLoadMultiSample">
                            <div>
                                <div style="font-size: 0.92rem; font-weight: 800; color: var(--text-heading); margin-bottom: 2px;">
                                    Multi-Read Sample — 10 Reads
                                </div>
                                <div style="font-size: 0.78rem; color: var(--text-muted);">
                                    Multi-read dataset for comprehensive analysis (10 reads, 61 bp)
                                </div>
                            </div>
                            <div style="font-size: 0.76rem; font-weight: 700; color: var(--brand-green); margin-top: 6px;">
                                Click to Load &amp; Activate &rarr;
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Bottom Full-Width Section 2: My Datasets Table (Occupies Full Page Width) -->
                <div class="full-width-table-card">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px;">
                        <div>
                            <h3 style="font-size: 1.05rem; font-weight: 800; color: var(--text-heading);">My Datasets</h3>
                            <div style="font-size: 0.78rem; color: var(--text-muted); margin-top: 2px;">
                                Manage and activate your sequencing datasets for pattern search and sequence exploration.
                            </div>
                        </div>
                        <button type="button" class="btn-table-action" id="btnRefreshDatasetsTable">&#8635; Refresh Datasets</button>
                    </div>
                    <div class="table-responsive">
                        <table class="data-table" id="datasetsTable">
                            <thead>
                                <tr>
                                    <th>Dataset Name</th>
                                    <th>Source</th>
                                    <th>DNA Reads</th>
                                    <th>DNA Bases</th>
                                    <th>Status</th>
                                    <th style="text-align: right;">Actions</th>
                                </tr>
                            </thead>
                            <tbody id="datasetsTableBody">
                                ${this.renderDatasetsTableRows()}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        `;

        this.bindEvents();
    }

    renderRequirementsList() {
        const reqs = [
            { key: 'recordCheck', label: 'The file must contain valid FASTQ records.' },
            { key: 'structureCheck', label: 'Each record must include a read identifier, DNA sequence, separator line, and quality scores.' },
            { key: 'nucleotideCheck', label: 'DNA sequences must contain supported nucleotide characters.' },
            { key: 'lengthCheck', label: 'Each sequence must have a matching number of quality characters.' },
            { key: 'sizeCheck', label: 'The file must be within the supported upload size limit.' }
        ];

        return reqs.map(r => {
            if (!this.validationResults) {
                return `
                    <li class="checklist-item informational" id="req_${r.key}">
                        <div class="check-icon informational">&#10004;</div>
                        <span>${r.label}</span>
                    </li>
                `;
            }

            const state = this.validationResults[r.key];
            if (!state) {
                return `
                    <li class="checklist-item informational" id="req_${r.key}">
                        <div class="check-icon informational">&#10004;</div>
                        <span>${r.label}</span>
                    </li>
                `;
            }

            if (state.pass) {
                return `
                    <li class="checklist-item pass" id="req_${r.key}">
                        <div class="check-icon pass">&#10004;</div>
                        <div>
                            <span>${r.label}</span>
                            ${state.detail ? `<div style="font-size: 0.74rem; color: var(--brand-green); margin-top: 2px;">Passed &bull; ${state.detail}</div>` : ''}
                        </div>
                    </li>
                `;
            } else {
                return `
                    <li class="checklist-item fail" id="req_${r.key}">
                        <div class="check-icon fail">&#10008;</div>
                        <div>
                            <span style="font-weight: 600;">${r.label}</span>
                            ${state.detail ? `<div style="font-size: 0.74rem; color: #dc2626; margin-top: 2px;">Failed &bull; ${state.detail}</div>` : ''}
                        </div>
                    </li>
                `;
            }
        }).join('');
    }

    renderDatasetsTableRows() {
        const tableDatasets = this.datasets || [];

        if (tableDatasets.length === 0) {
            return `<tr><td colspan="6" style="text-align: center; color: var(--text-muted); padding: 22px;">No datasets available. Upload a FASTQ file or select a sample dataset above.</td></tr>`;
        }

        let html = '';
        tableDatasets.forEach(ds => {
            const isActive = ds.active === true || ds.isActive === true;
            const statusBadge = isActive 
                ? '<span class="badge-active">&#10004; Active</span>' 
                : '<span class="badge-ready">Ready</span>';

            const rawReads = ds.reads || 0;
            const readsCount = `${rawReads.toLocaleString()} ${rawReads === 1 ? 'read' : 'reads'}`;
            const basesCount = `${(ds.symbols || ds.totalBases || 0).toLocaleString()} bp`;
            const sourceLabel = ds.source || (ds.id === 'uploaded' ? 'User Input' : 'Built-in Sample');

            const isBuiltIn = ds.id === 'demo_report' || ds.id === 'sample' || ds.id === 'extended';

            html += `
                <tr>
                    <td>
                        <strong style="color: var(--text-heading);">${this.escapeHtml(ds.name)}</strong>
                        <div style="font-size: 0.72rem; color: var(--text-muted);">${this.escapeHtml(ds.description || '')}</div>
                    </td>
                    <td><span style="font-size: 0.78rem; background: var(--bg-surface-alt); padding: 3px 8px; border-radius: 4px; border: 1px solid var(--border-color);">${sourceLabel}</span></td>
                    <td>${readsCount}</td>
                    <td>${basesCount}</td>
                    <td>${statusBadge}</td>
                    <td style="text-align: right;">
                        <div class="table-actions-group" style="justify-content: flex-end;">
                            ${!isActive ? `<button type="button" class="btn-table-action primary btn-set-active" data-id="${ds.id}">Set Active</button>` : ''}
                            <button type="button" class="btn-table-action btn-reprocess-ds" data-id="${ds.id}">Reprocess</button>
                            <a href="/api/datasets/download?id=${ds.id}" class="btn-table-action" download="${ds.name}">Download</a>
                            ${!isBuiltIn ? `<button type="button" class="btn-table-action danger btn-delete-ds" data-id="${ds.id}">Delete</button>` : ''}
                        </div>
                    </td>
                </tr>
            `;
        });
        return html;
    }

    renderSelectedFileBar() {
        if (!this.selectedFile) return '';
        return `
            <div class="file-selection-bar">
                <div style="display: flex; align-items: center; gap: 8px;">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color: var(--brand-green);"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline></svg>
                    <div>
                        <strong style="color: var(--text-heading); font-size: 0.86rem;">${this.escapeHtml(this.selectedFile.name)}</strong>
                        <div style="font-size: 0.72rem; color: var(--text-muted);">${this.formatFileSize(this.selectedFile.size)}</div>
                    </div>
                </div>
                <div style="display: flex; gap: 8px; align-items: center;">
                    <button type="button" class="btn-table-action" id="btnChangeSelectedFile">Change File</button>
                    <button type="button" class="btn-primary-hero" style="font-size: 0.82rem; padding: 6px 14px;" id="btnUploadSelectedFile">Validate &amp; Upload Dataset</button>
                </div>
            </div>
        `;
    }

    getPasteStatsText() {
        if (!this.pastedText) return "0 lines • 0 characters";
        const lines = this.pastedText.split(/\r?\n/).length;
        const chars = this.pastedText.length;
        return `${lines} lines • ${chars.toLocaleString()} chars`;
    }

    async refreshDatasets() {
        const res = await api.getDatasets();
        if (res && res.success) {
            this.datasets = res.datasets || [];
            this.activeDataset = res.activeDataset || null;
        }
    }

    getDatasetFileSize(dataset) {
        if (!dataset) return "Not available";

        const dsName = dataset.name || dataset.datasetName || dataset.id || '';

        // 1. Check if dataset already carries an explicit fileSize
        if (dataset.fileSize) {
            return typeof dataset.fileSize === 'number' ? this.formatFileSize(dataset.fileSize) : dataset.fileSize;
        }

        // 2. Check if user uploaded file size was stored in session or local storage
        try {
            if (dsName) {
                const stored = sessionStorage.getItem('genedex_file_size_' + dsName) || 
                               localStorage.getItem('genedex_file_size_' + dsName);
                if (stored) {
                    return this.formatFileSize(parseInt(stored, 10));
                }
            }
        } catch (e) {}

        // 3. Predefined sizes for standard and known datasets
        if (dsName.includes("demo_report")) return "48 B";
        if (dsName.includes("sample.fastq")) return "138 B";
        if (dsName.includes("extended_sample")) return "420 B";
        if (dsName.includes("2_OHara")) return "6.1 MB";
        if (dsName.includes("1_control_ITS2")) return "8.1 MB";
        if (dsName.includes("1_control_18S")) return "10.6 MB";

        // 4. Calculate accurately from DNA bases and reads count
        // In standard 4-line FASTQ: sequence + quality = 2 * bases; header + separator ~ 65 chars per read
        const totalBases = dataset.totalBases || dataset.totalDnaBases || (dataset.statistics && (dataset.statistics.totalDnaBases || dataset.statistics.totalBases)) || 0;
        const totalReads = dataset.totalReads || dataset.reads || (dataset.statistics && dataset.statistics.totalReads) || 0;
        if (totalBases > 0 && totalReads > 0) {
            const estimatedBytes = (totalBases * 2) + (totalReads * 65);
            return this.formatFileSize(estimatedBytes);
        }

        // 5. Fallback from total indexed symbols if available
        const symbols = dataset.totalSymbols || dataset.totalIndexedSymbols || (dataset.statistics && dataset.statistics.totalIndexedSymbols) || 0;
        if (symbols > 0) {
            return this.formatFileSize(symbols * 2.5);
        }

        return "Not available";
    }

    saveFileSize(name, bytes) {
        if (!name || !bytes) return;
        try {
            sessionStorage.setItem('genedex_file_size_' + name, String(bytes));
            localStorage.setItem('genedex_file_size_' + name, String(bytes));
        } catch (e) {}
    }

    formatFileSize(bytes) {
        if (!bytes || isNaN(bytes) || bytes <= 0) return 'Not available';
        if (bytes < 1024) return bytes + ' B';
        const kb = bytes / 1024;
        if (kb < 1024) return kb.toFixed(1) + ' KB';
        const mb = kb / 1024;
        return mb.toFixed(1) + ' MB';
    }

    escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    bindEvents() {
        // Tab switching
        const tabUpload = this.container.querySelector('#tabBtnUpload');
        const tabPaste = this.container.querySelector('#tabBtnPaste');
        const contentUpload = this.container.querySelector('#tabContentUpload');
        const contentPaste = this.container.querySelector('#tabContentPaste');

        tabUpload?.addEventListener('click', () => {
            this.activeTab = 'upload';
            tabUpload.classList.add('active');
            tabPaste?.classList.remove('active');
            if (contentUpload) contentUpload.style.display = 'block';
            if (contentPaste) contentPaste.style.display = 'none';
        });

        tabPaste?.addEventListener('click', () => {
            this.activeTab = 'paste';
            tabPaste.classList.add('active');
            tabUpload?.classList.remove('active');
            if (contentUpload) contentUpload.style.display = 'none';
            if (contentPaste) contentPaste.style.display = 'block';
        });

        // Tab 1: File Upload Elements
        const dropArea = this.container.querySelector('#dropArea');
        const fileInput = this.container.querySelector('#fileUploadInput');
        const btnChoose = this.container.querySelector('#btnChooseFile');

        btnChoose?.addEventListener('click', (e) => {
            e.stopPropagation();
            fileInput?.click();
        });

        dropArea?.addEventListener('click', (e) => {
            if (e.target.closest('#btnChooseFile') || e.target.closest('.file-selection-bar')) return;
            fileInput?.click();
        });

        fileInput?.addEventListener('change', (e) => {
            if (e.target.files && e.target.files.length > 0) {
                this.handleFileSelected(e.target.files[0]);
            }
        });

        dropArea?.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropArea.classList.add('dragover');
        });
        dropArea?.addEventListener('dragleave', () => {
            dropArea.classList.remove('dragover');
        });
        dropArea?.addEventListener('drop', (e) => {
            e.preventDefault();
            dropArea.classList.remove('dragover');
            if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
                this.handleFileSelected(e.dataTransfer.files[0]);
            }
        });

        // File Bar actions
        this.container.querySelector('#btnChangeSelectedFile')?.addEventListener('click', () => {
            fileInput?.click();
        });

        this.container.querySelector('#btnUploadSelectedFile')?.addEventListener('click', () => {
            if (this.selectedFile) {
                this.processAndUploadFile(this.selectedFile);
            }
        });

        // Tab 2: Paste Elements
        const pasteNameInput = this.container.querySelector('#pasteDatasetName');
        const pasteTextArea = this.container.querySelector('#pasteFastqText');
        const counterEl = this.container.querySelector('#pasteStatsCounter');

        pasteNameInput?.addEventListener('input', (e) => {
            this.pastedName = e.target.value;
        });

        pasteTextArea?.addEventListener('input', (e) => {
            this.pastedText = e.target.value;
            if (counterEl) counterEl.textContent = this.getPasteStatsText();
        });

        this.container.querySelector('#btnClearPaste')?.addEventListener('click', () => {
            this.pastedText = '';
            if (pasteTextArea) pasteTextArea.value = '';
            if (counterEl) counterEl.textContent = this.getPasteStatsText();
            this.clearError();
        });

        this.container.querySelector('#btnSubmitPaste')?.addEventListener('click', () => {
            this.handlePasteSubmit();
        });

        // Sample Dataset Loader Cards (Full-Width Section)
        this.container.querySelector('#btnLoadReportDemo')?.addEventListener('click', async () => {
            await api.setActiveDataset('demo_report');
            Toast.success("Loaded Small Demo Dataset.");
            this.validationResults = null;
            await this.render();
        });

        this.container.querySelector('#btnLoadStandardSample')?.addEventListener('click', async () => {
            await api.setActiveDataset('sample');
            Toast.success("Loaded Standard Sample — 3 Reads.");
            this.validationResults = null;
            await this.render();
        });

        this.container.querySelector('#btnLoadMultiSample')?.addEventListener('click', async () => {
            await api.setActiveDataset('extended');
            Toast.success("Loaded Multi-Read Sample — 10 Reads.");
            this.validationResults = null;
            await this.render();
        });

        // Refresh Datasets Table
        this.container.querySelector('#btnRefreshDatasetsTable')?.addEventListener('click', async () => {
            await this.render();
            Toast.info("Dataset table refreshed.");
        });

        // Datasets Table Event Delegation
        this.container.addEventListener('click', async (e) => {
            // Set Active
            const setActiveBtn = e.target.closest('.btn-set-active');
            if (setActiveBtn) {
                const id = setActiveBtn.getAttribute('data-id');
                await api.setActiveDataset(id);
                Toast.success("Switched active dataset.");
                await this.render();
                return;
            }

            // Reprocess
            const reprocessBtn = e.target.closest('.btn-reprocess-ds');
            if (reprocessBtn) {
                reprocessBtn.disabled = true;
                reprocessBtn.textContent = "Reprocessing...";
                const res = await api.rebuildDataset();
                if (res && res.success) {
                    Toast.success("Dataset reprocessed successfully.");
                } else {
                    Toast.error(res?.error || "Reprocess failed.");
                }
                await this.render();
                return;
            }

            // Delete
            const deleteBtn = e.target.closest('.btn-delete-ds');
            if (deleteBtn) {
                if (confirm("Are you sure you want to delete this dataset? This action cannot be undone.")) {
                    const dsId = deleteBtn.getAttribute('data-id');
                    const res = await api.deleteDataset(dsId);
                    if (res && res.success) {
                        Toast.info("Dataset deleted.");
                        await this.render();
                    } else {
                        Toast.error(res?.error || "Delete failed.");
                    }
                }
                return;
            }
        });
    }

    handleFileSelected(file) {
        this.clearError();
        this.selectedFile = file;

        // Render file bar with Validate & Upload button
        const slot = this.container.querySelector('#fileSelectionBarSlot');
        if (slot) {
            slot.innerHTML = this.renderSelectedFileBar();
            // Re-bind file bar action buttons
            this.container.querySelector('#btnChangeSelectedFile')?.addEventListener('click', () => {
                this.container.querySelector('#fileUploadInput')?.click();
            });
            this.container.querySelector('#btnUploadSelectedFile')?.addEventListener('click', () => {
                this.processAndUploadFile(file);
            });
        }
    }

    async processAndUploadFile(file) {
        this.clearError();

        const results = {
            recordCheck: { pass: false, detail: null },
            structureCheck: { pass: false, detail: null },
            nucleotideCheck: { pass: false, detail: null },
            lengthCheck: { pass: false, detail: null },
            sizeCheck: { pass: false, detail: null }
        };

        // 1. Check file size (max 50 MB)
        const maxBytes = 50 * 1024 * 1024;
        const progressCard = this.container.querySelector('#stagedProgressCard');

        if (file.size <= 0) {
            results.sizeCheck.pass = false;
            results.sizeCheck.detail = "Selected file is empty (0 bytes).";
            this.validationResults = results;
            this.updateRequirementsUI();
            if (progressCard) progressCard.style.display = 'block';
            this.setStage(1, true, "Selected file is empty (0 bytes). Please provide a valid FASTQ file.");
            return;
        }

        if (file.size > maxBytes) {
            const sizeMB = (file.size / (1024 * 1024)).toFixed(1);
            const errMsg = `File size (${sizeMB} MB) exceeds the 50 MB limit. Please select a smaller file.`;
            results.sizeCheck.pass = false;
            results.sizeCheck.detail = errMsg;
            this.validationResults = results;
            this.updateRequirementsUI();
            if (progressCard) progressCard.style.display = 'block';
            this.setStage(1, true, errMsg);
            return;
        }

        results.sizeCheck.pass = true;
        results.sizeCheck.detail = `Within size limit (${this.formatFileSize(file.size)}).`;

        // Show Staged Progress Card
        if (progressCard) progressCard.style.display = 'block';

        // Stage 1: Reading file from disk (asynchronous FileReader event milestone)
        this.setStage(1);

        const reader = new FileReader();

        reader.onerror = () => {
            this.setStage(1, true, "Failed to read the file from disk. Please verify file access permissions and try selecting the file again.");
        };

        reader.onload = async (event) => {
            const content = event.target.result;

            // Stage 2: Validating FASTQ syntax and record integrity
            this.setStage(2);

            const val = this.validateFastq(content, results);
            this.validationResults = results;
            this.updateRequirementsUI();

            if (!val.valid) {
                this.setStage(2, true, val.errorDetail || "The selected file contains an invalid FASTQ record.");
                return;
            }

            // Stage 3: Server-side Ingestion, BWT & Wavelet Tree Indexing
            this.setStage(3);

            try {
                const res = await api.uploadFastq(content, file.name);
                if (res && res.success) {
                    // Stage 4: Ready for analysis
                    this.setStage(4);
                    const uploadedName = res.datasetName || file.name;
                    this.saveFileSize(uploadedName, file.size);
                    if (file.name && file.name !== uploadedName) {
                        this.saveFileSize(file.name, file.size);
                    }
                    Toast.success("Your dataset has been added successfully and is ready for analysis.");
                    await this.sleep(1400);
                    this.selectedFile = null;
                    await this.render();
                } else {
                    this.setStage(3, true, res?.error || "Server processing failed.");
                }
            } catch (err) {
                this.setStage(3, true, "Network connection error: " + (err.message || "Failed to reach the GeneDex server."));
            }
        };

        reader.readAsText(file);
    }

    async handlePasteSubmit() {
        this.clearError();

        const nameInput = this.container.querySelector('#pasteDatasetName');
        const textInput = this.container.querySelector('#pasteFastqText');
        const rawName = nameInput ? nameInput.value.trim() : this.pastedName.trim();
        const rawContent = textInput ? textInput.value.trim() : this.pastedText.trim();

        if (!rawName) {
            this.showError("Dataset Name Required", "Please enter a descriptive name for your dataset before submitting.", "Enter a dataset name in the input field above.");
            nameInput?.focus();
            return;
        }

        if (!rawContent) {
            this.showError("FASTQ Sequence Data Required", "Please paste at least one valid FASTQ record into the text area.", "Paste your 4-line FASTQ records into the text area.");
            textInput?.focus();
            return;
        }

        const results = {
            recordCheck: { pass: false, detail: null },
            structureCheck: { pass: false, detail: null },
            nucleotideCheck: { pass: false, detail: null },
            lengthCheck: { pass: false, detail: null },
            sizeCheck: { pass: false, detail: null }
        };

        const progressCard = this.container.querySelector('#stagedProgressCard');
        if (progressCard) progressCard.style.display = 'block';

        // Size check for text
        const byteSize = new Blob([rawContent]).size;
        const maxBytes = 50 * 1024 * 1024;
        if (byteSize > maxBytes) {
            const sizeMB = (byteSize / (1024 * 1024)).toFixed(1);
            const errMsg = `Pasted content (${sizeMB} MB) exceeds the 50 MB limit.`;
            results.sizeCheck.pass = false;
            results.sizeCheck.detail = errMsg;
            this.validationResults = results;
            this.updateRequirementsUI();
            this.setStage(1, true, errMsg);
            return;
        }

        results.sizeCheck.pass = true;
        results.sizeCheck.detail = `Content size: ${this.formatFileSize(byteSize)}`;

        // Stage 1: Reading / buffer ingest
        this.setStage(1);

        // Stage 2: Validating
        this.setStage(2);

        const val = this.validateFastq(rawContent, results);
        this.validationResults = results;
        this.updateRequirementsUI();

        if (!val.valid) {
            this.setStage(2, true, val.errorDetail || "The pasted content contains an invalid FASTQ record.");
            return;
        }

        // Stage 3: Server Ingestion & Indexing
        this.setStage(3);

        try {
            const res = await api.uploadFastq(rawContent, rawName);
            if (res && res.success) {
                this.setStage(4);
                const uploadedName = res.datasetName || (rawName.endsWith('.fastq') ? rawName : rawName + '.fastq');
                this.saveFileSize(uploadedName, byteSize);
                Toast.success("Your dataset has been added successfully and is ready for analysis.");
                await this.sleep(1400);
                this.pastedText = '';
                this.pastedName = '';
                await this.render();
            } else {
                this.setStage(3, true, res?.error || "Server processing failed.");
            }
        } catch (err) {
            this.setStage(3, true, "Network connection error: " + (err.message || "Failed to reach the GeneDex server."));
        }
    }

    validateFastq(text, results) {
        if (!results) {
            results = {
                recordCheck: { pass: false, detail: null },
                structureCheck: { pass: false, detail: null },
                nucleotideCheck: { pass: false, detail: null },
                lengthCheck: { pass: false, detail: null },
                sizeCheck: { pass: true, detail: null }
            };
        }

        if (!text || !text.trim()) {
            results.recordCheck.pass = false;
            results.recordCheck.detail = "No records found.";
            return { valid: false, errorDetail: "The provided content is empty or contains only whitespace." };
        }

        const rawLines = text.split(/\r?\n/);
        const lines = [];
        for (let i = 0; i < rawLines.length; i++) {
            const line = rawLines[i].trim();
            if (line.length > 0) {
                lines.push({ text: line, rawLineNumber: i + 1 });
            }
        }

        if (lines.length === 0) {
            results.recordCheck.pass = false;
            results.recordCheck.detail = "No non-empty lines found.";
            return { valid: false, errorDetail: "The provided content contains no non-empty lines." };
        }

        if (lines.length % 4 !== 0) {
            results.recordCheck.pass = false;
            results.recordCheck.detail = `Total non-empty lines (${lines.length}) is not a multiple of 4.`;
            return {
                valid: false,
                errorDetail: `Total line count (${lines.length}) is not a valid 4-line FASTQ format. Each complete record requires 4 lines: identifier (@), sequence, separator (+), and quality string. Check if the final record is truncated.`
            };
        }

        results.recordCheck.pass = true;
        const recordCount = lines.length / 4;
        results.recordCheck.detail = `${recordCount.toLocaleString()} complete 4-line records verified.`;

        let structurePass = true;
        let nucleotidePass = true;
        let lengthPass = true;
        let firstError = null;

        for (let r = 0; r < recordCount; r++) {
            const idx = r * 4;
            const header = lines[idx];
            const seq = lines[idx + 1];
            const plus = lines[idx + 2];
            const qual = lines[idx + 3];

            if (!header.text.startsWith('@')) {
                structurePass = false;
                results.structureCheck.detail = `Line ${header.rawLineNumber}: Record ${r + 1} header must start with '@'.`;
                firstError = `Header at line ${header.rawLineNumber} (Record ${r + 1}) is missing the required '@' prefix. Found: "${header.text.substring(0, 32)}"`;
                break;
            }

            if (!plus.text.startsWith('+')) {
                structurePass = false;
                results.structureCheck.detail = `Line ${plus.rawLineNumber}: Record ${r + 1} separator must start with '+'.`;
                firstError = `Separator line at line ${plus.rawLineNumber} (Record ${r + 1}) is missing the required '+' prefix. Found: "${plus.text.substring(0, 32)}"`;
                break;
            }

            if (seq.text.length !== qual.text.length) {
                lengthPass = false;
                results.lengthCheck.detail = `Record ${r + 1} (line ${seq.rawLineNumber}): Sequence length (${seq.text.length}) does not match quality length (${qual.text.length}).`;
                firstError = `Record ${r + 1} has mismatched sequence and quality lengths at line ${seq.rawLineNumber} (sequence: ${seq.text.length} bp, quality scores: ${qual.text.length} characters).`;
                break;
            }

            if (!/^[ACGTNacgtn]+$/.test(seq.text)) {
                nucleotidePass = false;
                results.nucleotideCheck.detail = `Line ${seq.rawLineNumber}: Contains non-standard characters. Supported nucleotides: A, C, G, T, N.`;
                firstError = `DNA sequence at line ${seq.rawLineNumber} (Record ${r + 1}) contains invalid nucleotide characters. Only A, C, G, T, and N are supported.`;
                break;
            }
        }

        results.structureCheck.pass = structurePass;
        if (structurePass) {
            results.structureCheck.detail = "All records contain valid identifiers and separator lines.";
        }

        results.nucleotideCheck.pass = nucleotidePass;
        if (nucleotidePass) {
            results.nucleotideCheck.detail = "All sequences contain valid nucleotide characters (A, C, G, T, N).";
        }

        results.lengthCheck.pass = lengthPass;
        if (lengthPass) {
            results.lengthCheck.detail = "All read sequences match their quality score lengths.";
        }

        const valid = results.recordCheck.pass && structurePass && nucleotidePass && lengthPass;
        return {
            valid: valid,
            readCount: recordCount,
            errorDetail: firstError
        };
    }

    updateRequirementsUI() {
        const listEl = this.container.querySelector('#requirementsList');
        if (listEl) {
            listEl.innerHTML = this.renderRequirementsList();
        }
    }

    setStage(stageNum, isError = false, rawError = null) {
        const stageNames = ["", "Reading", "Validating", "Processing", "Ready"];
        const stageDescriptions = [
            "",
            "Reading sequencing reads from file...",
            "Validating FASTQ syntax, headers & quality scores...",
            "Indexing reads with BWT & Wavelet Tree on backend...",
            "Your dataset has been added successfully and is ready for analysis."
        ];

        const inlineErr = this.container.querySelector('#pipelineInlineError');

        for (let i = 1; i <= 4; i++) {
            const el = this.container.querySelector(`#stageStep${i}`);
            if (!el) continue;
            const bullet = el.querySelector('.stage-bullet');
            el.classList.remove('active', 'done', 'error');

            if (isError) {
                if (i < stageNum) {
                    el.classList.add('done');
                    if (bullet) bullet.textContent = '✓';
                } else if (i === stageNum) {
                    el.classList.add('error');
                    if (bullet) bullet.textContent = '✕';
                } else {
                    if (bullet) bullet.textContent = `${i}`;
                }
            } else {
                if (i < stageNum || (stageNum === 4 && i <= 4)) {
                    el.classList.add('done');
                    if (bullet) bullet.textContent = '✓';
                } else if (i === stageNum) {
                    el.classList.add('active');
                    if (bullet) bullet.textContent = `${i}`;
                } else {
                    if (bullet) bullet.textContent = `${i}`;
                }
            }
        }

        const statusText = this.container.querySelector('#pipelineStatusText');
        if (statusText) {
            if (isError) {
                statusText.textContent = `${stageNames[stageNum] || 'Processing'} Failed`;
                statusText.style.color = "#dc2626";
            } else if (stageNum === 4) {
                statusText.textContent = "Ready for Analysis";
                statusText.style.color = "var(--brand-green)";
            } else {
                statusText.textContent = stageDescriptions[stageNum] || "Processing...";
                statusText.style.color = "var(--brand-green)";
            }
        }

        if (isError) {
            const errorInfo = this.formatErrorDetails(rawError, stageNames[stageNum] || "Processing");
            if (inlineErr) {
                inlineErr.innerHTML = this.renderErrorBanner(errorInfo);
                inlineErr.style.display = 'block';
            }
            this.showError(errorInfo);
        } else {
            if (inlineErr) {
                inlineErr.innerHTML = '';
                inlineErr.style.display = 'none';
            }
            if (stageNum === 4) {
                this.clearError();
            }
        }
    }

    formatErrorDetails(rawError, failedStageName = "Processing") {
        let headline = `${failedStageName} Failed`;
        let explanation = "An unexpected error occurred while processing the genomic dataset.";
        let correctiveAction = "Please check your FASTQ file structure, verify line endings, and ensure all records are complete.";
        let technical = (typeof rawError === 'string' ? rawError : (rawError?.message || JSON.stringify(rawError || {})));

        const errStr = technical || "";

        if (errStr.includes("StackOverflowError")) {
            headline = "Backend Processing Stack Overflow";
            explanation = "The server exceeded its call-stack depth while parsing the dataset payload.";
            correctiveAction = "Ensure server software is running the updated linear JSON parser.";
        } else if (errStr.includes("OutOfMemoryError") || errStr.toLowerCase().includes("memory")) {
            headline = "Server Memory Limit Exceeded";
            explanation = "The server ran out of available JVM heap memory while constructing the Wavelet Tree index.";
            correctiveAction = "Try uploading a smaller subset or launch the server with increased heap memory (e.g., -Xmx2g).";
        } else if (errStr.includes("exceeds the 50 MB") || errStr.toLowerCase().includes("file size") || errStr.toLowerCase().includes("too large")) {
            headline = "File Size Exceeds 50 MB Limit";
            explanation = "The selected file exceeds the 50 MB maximum file size allowed by this GeneDex instance.";
            correctiveAction = "Downsample or split your sequencing run to under 50 MB before uploading.";
        } else if (errStr.includes("mismatch") || errStr.includes("Sequence length") || errStr.includes("quality length")) {
            headline = "Sequence and Quality Length Mismatch";
            explanation = "A sequencing read has a base sequence length that differs from its Phred quality score string length.";
            correctiveAction = "Check the line number mentioned in the error details to ensure sequence and quality characters align 1-to-1.";
        } else if (errStr.includes("prefix") || errStr.includes("missing the required '@'") || errStr.includes("must start with '@'") || errStr.includes("separator")) {
            headline = "Invalid FASTQ Format Structure";
            explanation = "FASTQ records must strictly follow the standard 4-line format: @header, sequence, +separator, and quality score.";
            correctiveAction = "Inspect the line number in the details. Ensure every read has all 4 lines and starts with '@' and '+'.";
        } else if (errStr.includes("unsupported characters") || errStr.includes("nucleotide") || errStr.includes("Invalid nucleotide")) {
            headline = "Unsupported Nucleotide Characters";
            explanation = "The read sequence contains characters other than standard DNA nucleotides (A, C, G, T, N).";
            correctiveAction = "Ensure the file contains DNA sequences and replace or filter non-standard IUPAC ambiguity codes.";
        } else if (errStr.includes("Total non-empty lines") || errStr.includes("multiple of 4") || errStr.includes("Incomplete record")) {
            headline = "Incomplete FASTQ Record";
            explanation = "The line count in the file is not a multiple of 4, indicating a truncated read or missing quality line at the end.";
            correctiveAction = "Inspect the end of your FASTQ file to ensure the final record was fully saved and not cut off.";
        } else if (errStr.toLowerCase().includes("session") || errStr.toLowerCase().includes("unauthorized") || errStr.includes("401")) {
            headline = "Authentication Session Expired";
            explanation = "Your active session has expired or authentication credentials could not be verified.";
            correctiveAction = "Please sign in to GeneDex again and re-attempt the upload.";
        } else if (errStr.toLowerCase().includes("network") || errStr.toLowerCase().includes("failed to fetch") || errStr.toLowerCase().includes("connection")) {
            headline = "Network / Server Connection Error";
            explanation = "The browser could not communicate with the GeneDex server backend on port 8080.";
            correctiveAction = "Ensure the GeneDex server is running and your network connection is active.";
        } else if (errStr.includes("empty") || errStr.includes("whitespace")) {
            headline = "Empty Dataset Content";
            explanation = "The selected file or pasted input contained no sequencing reads or text.";
            correctiveAction = "Please select a valid FASTQ file containing sequencing reads.";
        } else if (failedStageName === "Reading") {
            headline = "File Reading Error";
            explanation = "The browser failed to read the file from disk.";
            correctiveAction = "Ensure the file exists, is not open exclusively by another application, and try selecting it again.";
        }

        return { headline, explanation, correctiveAction, technical };
    }

    renderErrorBanner(info) {
        return `
            <div class="error-banner" style="margin-top: 14px; border-left: 4px solid #ef4444; background: #fef2f2; border: 1px solid #fecaca; border-radius: 8px; padding: 14px 16px; text-align: left;">
                <div style="display: flex; gap: 12px; align-items: flex-start;">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#dc2626" stroke-width="2.2" style="flex-shrink: 0; margin-top: 2px;">
                        <circle cx="12" cy="12" r="10"></circle>
                        <line x1="12" y1="8" x2="12" y2="12"></line>
                        <line x1="12" y1="16" x2="12.01" y2="16"></line>
                    </svg>
                    <div style="flex: 1;">
                        <strong style="color: #991b1b; font-size: 0.95rem; display: block; margin-bottom: 4px;">${this.escapeHtml(info.headline)}</strong>
                        <p style="color: #7f1d1d; font-size: 0.85rem; margin: 0 0 8px 0; line-height: 1.4;">${this.escapeHtml(info.explanation)}</p>
                        <div style="font-size: 0.82rem; color: #374151; background: #ffffff; border: 1px solid #fee2e2; border-radius: 6px; padding: 7px 12px; margin-bottom: 6px;">
                            <strong style="color: #1f2937;">Suggested Action:</strong> ${this.escapeHtml(info.correctiveAction)}
                        </div>
                        ${info.technical ? `
                        <details style="margin-top: 6px; font-size: 0.78rem;">
                            <summary style="cursor: pointer; color: #b91c1c; font-weight: 600;">Technical Details</summary>
                            <pre style="background: #ffffff; border: 1px solid #fee2e2; border-radius: 4px; padding: 8px; margin-top: 4px; overflow-x: auto; color: #374151; font-family: monospace; font-size: 0.75rem; white-space: pre-wrap;">${this.escapeHtml(info.technical)}</pre>
                        </details>
                        ` : ''}
                    </div>
                </div>
            </div>
        `;
    }

    showError(headlineOrInfo, explanation = null, correctiveAction = null, technical = null) {
        const slot = this.container.querySelector('#uploadErrorSlot');
        if (!slot) return;

        let info;
        if (typeof headlineOrInfo === 'object' && headlineOrInfo !== null) {
            info = headlineOrInfo;
        } else {
            info = {
                headline: headlineOrInfo || "Upload Error",
                explanation: explanation || "",
                correctiveAction: correctiveAction || "Please verify the input and try again.",
                technical: technical || ""
            };
        }

        slot.innerHTML = this.renderErrorBanner(info);
    }

    clearError() {
        const slot = this.container.querySelector('#uploadErrorSlot');
        if (slot) slot.innerHTML = '';
        const inlineErr = this.container.querySelector('#pipelineInlineError');
        if (inlineErr) {
            inlineErr.innerHTML = '';
            inlineErr.style.display = 'none';
        }
    }

    sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
}
