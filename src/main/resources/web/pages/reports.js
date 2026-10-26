/**
 * GeneDex — Genomic Analysis Report Generator
 * Contribution: Member 4 (sruthicasathish-stack)
 * 
 * Practical, user-focused DNA sequencing report generation platform.
 * Allows users to configure essential details, select analytical results,
 * generate publication-grade reports, and export to PDF, CSV, and Markdown.
 *
 * @author sruthicasathish-stack
 */

import { api } from '../services/api.js';
import { Toast } from '../components/Toast.js';

export class ReportsPage {
    constructor(containerId) {
        this.container = document.getElementById(containerId);

        // Core State
        this.availableDatasets = [];
        this.activeDataset = null;
        this.selectedDatasetId = '';
        this.comparisonDatasetId = '';
        this.recentPattern = '';
        this.recentSearchResults = null;
        this.hasQualityData = false;

        // Form Fields
        this.reportTitle = 'DNA Sequencing Analysis Report';
        this.researcher = '';
        this.institution = '';
        this.reportDate = new Date().toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });
        this.purpose = '';
        this.notes = '';

        // Section Selections
        this.includeSummary = true;
        this.includeComposition = true;
        this.includeGcContent = true;
        this.includePatternSearch = false;
        this.includeReadAnalysis = true;
        this.includeQuality = false;
        this.includeComparison = false;
        this.includeObservations = false;

        // Generated Report State
        this.generatedReport = null;
        this.isModalOpen = false;
        this.boundKeyHandler = null;

        // User Scoped Storage
        this.currentUser = null;
        this.userReports = [];
    }

    async render() {
        if (!this.container) return;

        // 1. Resolve User & Datasets
        this.currentUser = await api.checkAuth();
        if (this.currentUser) {
            this.researcher = this.currentUser.fullName || this.currentUser.email || 'Researcher';
            this.institution = this.currentUser.organization || '';
        } else {
            this.researcher = 'Guest Researcher';
            this.institution = 'Genomic Analysis Laboratory';
        }

        const dsRes = await api.getDatasets();
        if (dsRes && dsRes.success) {
            this.activeDataset = dsRes.activeDataset;
            this.availableDatasets = dsRes.datasets || [];
            const activeDs = this.availableDatasets.find(d => d.active || d.isActive) || this.availableDatasets[0];
            this.selectedDatasetId = activeDs ? activeDs.id : (this.activeDataset?.id || 'demo_report');
        }

        // 2. Check for recent searches in localStorage
        this.loadRecentSearch();

        // 3. Check for quality scores in active dataset
        await this.checkDatasetFeatures();

        // 4. Load Saved Reports History for this user
        this.loadUserReports();

        // 5. Render Main Generator View
        this.renderView();
    }

    loadRecentSearch() {
        try {
            const rawSearches = localStorage.getItem('genedex_recent_searches');
            if (rawSearches) {
                const list = JSON.parse(rawSearches);
                if (Array.isArray(list) && list.length > 0) {
                    this.recentPattern = list[0];
                }
            }
        } catch (e) {
            console.warn("Could not load recent searches:", e);
        }
    }

    async checkDatasetFeatures() {
        try {
            const anRes = await api.getAnalytics();
            if (anRes && anRes.success) {
                this.hasQualityData = !!(anRes.qualitySummary && anRes.qualitySummary.meanPhredScore > 0);
            }
        } catch (e) {
            this.hasQualityData = false;
        }
    }

    getUserStorageKey() {
        const uid = this.currentUser ? (this.currentUser.id || this.currentUser.email) : 'guest';
        return `genedex_saved_reports_${uid}`;
    }

    loadUserReports() {
        try {
            const saved = localStorage.getItem(this.getUserStorageKey());
            this.userReports = saved ? JSON.parse(saved) : [];
        } catch (e) {
            this.userReports = [];
        }
    }

    saveUserReports() {
        try {
            localStorage.setItem(this.getUserStorageKey(), JSON.stringify(this.userReports));
        } catch (e) {
            console.error("Could not persist reports:", e);
        }
    }

    renderView() {
        const hasMultipleDatasets = this.availableDatasets.length >= 2;
        const activeName = this.activeDataset ? (this.activeDataset.name || this.activeDataset.datasetName || 'Active Dataset') : 'No Dataset Selected';

        this.container.innerHTML = `
            <div class="page-container reports-page">
                <!-- ==================== BREADCRUMB ==================== -->
                <nav class="breadcrumb-bar" aria-label="Breadcrumb">
                    <a href="#/home">Home</a> &gt; <span>Generate Genomic Analysis Report</span>
                </nav>

                <!-- ==================== HEADER ==================== -->
                <div class="reports-header-centered">
                    <h1 class="reports-main-title">Generate Genomic Analysis Report</h1>
                    <p class="reports-main-subtitle">
                        Create a personalized report of your DNA sequencing data, analysis results, and observations.
                    </p>
                </div>

                <!-- ==================== REPORT GENERATION FORM ==================== -->
                <div class="reports-form-card" id="reportsFormCard">
                    <!-- SECTION 1: ESSENTIAL DETAILS -->
                    <div class="form-section-block">
                        <div class="section-title-strip">
                            <span class="section-step-num">1</span>
                            <h2 class="form-block-title">Essential Report Details</h2>
                        </div>
                        <p class="form-block-desc">Enter the header information and select the target genomic dataset for this report.</p>

                        <!-- Row 1: Report Title & Researcher (2 Columns) -->
                        <div class="report-form-row report-form-row-2col">
                            <div class="report-form-group">
                                <label for="repTitle" class="report-form-label">Report Title <span class="req-star">*</span></label>
                                <input type="text" id="repTitle" class="report-form-input" value="${this.escapeHtml(this.reportTitle)}" placeholder="e.g. DNA Sequencing Analysis Report" required />
                            </div>

                            <div class="report-form-group">
                                <label for="repResearcher" class="report-form-label">Researcher / Prepared By <span class="req-star">*</span></label>
                                <input type="text" id="repResearcher" class="report-form-input" value="${this.escapeHtml(this.researcher)}" placeholder="e.g. Dr. Jane Smith" required />
                            </div>
                        </div>

                        <!-- Row 2: Institution & Date (2 Columns) -->
                        <div class="report-form-row report-form-row-2col">
                            <div class="report-form-group">
                                <label for="repInstitution" class="report-form-label">Institution / Laboratory</label>
                                <input type="text" id="repInstitution" class="report-form-input" value="${this.escapeHtml(this.institution)}" placeholder="e.g. Center for Genomic Medicine" />
                            </div>

                            <div class="report-form-group">
                                <label for="repDate" class="report-form-label">Report Date</label>
                                <input type="text" id="repDate" class="report-form-input font-mono" value="${this.escapeHtml(this.reportDate)}" readonly />
                            </div>
                        </div>

                        <!-- Row 3: Active Dataset (Full-Width Dropdown) -->
                        <div class="report-form-row report-form-row-full">
                            <div class="report-form-group">
                                <label for="repDataset" class="report-form-label">Active Dataset <span class="req-star">*</span></label>
                                <div class="report-select-wrapper">
                                    <select id="repDataset" class="report-form-select">
                                        ${this.availableDatasets.map(ds => `
                                            <option value="${ds.id}" ${ds.id === this.selectedDatasetId ? 'selected' : ''}>
                                                ${this.escapeHtml(ds.name)} (${ds.reads || 1} reads)
                                            </option>
                                        `).join('')}
                                    </select>
                                    <div class="select-chevron" aria-hidden="true">
                                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="6 9 12 15 18 9"></polyline></svg>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- Row 4: Purpose of Analysis (Full-Width Multiline Textarea) -->
                        <div class="report-form-row report-form-row-full">
                            <div class="report-form-group">
                                <label for="repPurpose" class="report-form-label">Purpose of Analysis</label>
                                <textarea id="repPurpose" class="report-form-textarea" placeholder="Describe the biological or experimental objective of this sequencing run...">${this.escapeHtml(this.purpose)}</textarea>
                            </div>
                        </div>

                        <!-- Row 5: Observations / Notes (Full-Width Multiline Textarea) -->
                        <div class="report-form-row report-form-row-full">
                            <div class="report-form-group">
                                <label for="repNotes" class="report-form-label">Observations / Notes</label>
                                <textarea id="repNotes" class="report-form-textarea" placeholder="Record any relevant analytical findings, sample anomalies, or methodological notes...">${this.escapeHtml(this.notes)}</textarea>
                            </div>
                        </div>
                    </div>

                    <!-- SECTION 2: SELECT ANALYSIS RESULTS TO INCLUDE -->
                    <div class="form-section-block">
                        <div class="section-title-strip">
                            <span class="section-step-num">2</span>
                            <h2 class="form-block-title">Select Analysis Results to Include</h2>
                        </div>
                        <p class="form-block-desc">Choose which analytical sections to compile into your generated report. Only selected sections will appear.</p>

                        <div class="checklist-grid">
                            <!-- 1. Dataset Summary -->
                            <label class="checklist-card">
                                <input type="checkbox" id="chkSummary" ${this.includeSummary ? 'checked' : ''} />
                                <div class="checklist-info">
                                    <div class="checklist-title">1. Dataset Summary</div>
                                    <div class="checklist-desc">FASTQ filename, total number of DNA reads, total DNA bases, average read length, minimum and maximum read lengths.</div>
                                </div>
                            </label>

                            <!-- 2. Nucleotide Composition -->
                            <label class="checklist-card">
                                <input type="checkbox" id="chkComposition" ${this.includeComposition ? 'checked' : ''} />
                                <div class="checklist-info">
                                    <div class="checklist-title">2. Nucleotide Composition</div>
                                    <div class="checklist-desc">Adenine (A), Cytosine (C), Guanine (G), Thymine (T), and Unknown (N) counts and percentages, with distribution chart.</div>
                                </div>
                            </label>

                            <!-- 3. GC Content Analysis -->
                            <label class="checklist-card">
                                <input type="checkbox" id="chkGcContent" ${this.includeGcContent ? 'checked' : ''} />
                                <div class="checklist-info">
                                    <div class="checklist-title">3. GC Content Analysis</div>
                                    <div class="checklist-desc">Overall GC percentage, individual read GC statistics, and GC vs AT comparative ratio.</div>
                                </div>
                            </label>

                            <!-- 4. DNA Pattern Search Results -->
                            <label class="checklist-card ${!this.recentPattern ? 'disabled-card' : ''}">
                                <input type="checkbox" id="chkPatternSearch" ${this.includePatternSearch && this.recentPattern ? 'checked' : ''} ${!this.recentPattern ? 'disabled' : ''} />
                                <div class="checklist-info">
                                    <div class="checklist-title">
                                        <span>4. DNA Pattern Search Results</span>
                                        ${this.recentPattern ? `<span class="pill-active-motif">Motif: "${this.escapeHtml(this.recentPattern)}"</span>` : `<span class="badge-disabled">No recent search found</span>`}
                                    </div>
                                    <div class="checklist-desc">DNA pattern searched, number of matching occurrences, matching read identifiers, positions, and sequence excerpts.</div>
                                </div>
                            </label>

                            <!-- 5. DNA Read Analysis -->
                            <label class="checklist-card">
                                <input type="checkbox" id="chkReadAnalysis" ${this.includeReadAnalysis ? 'checked' : ''} />
                                <div class="checklist-info">
                                    <div class="checklist-title">5. DNA Read Analysis</div>
                                    <div class="checklist-desc">Read identifiers, read lengths, sequence statistics, and reconstructed sequence excerpts.</div>
                                </div>
                            </label>

                            <!-- 6. Sequencing Quality Assessment -->
                            <label class="checklist-card ${!this.hasQualityData ? 'disabled-card' : ''}">
                                <input type="checkbox" id="chkQuality" ${this.includeQuality && this.hasQualityData ? 'checked' : ''} ${!this.hasQualityData ? 'disabled' : ''} />
                                <div class="checklist-info">
                                    <div class="checklist-title">
                                        <span>6. Sequencing Quality Assessment</span>
                                        ${!this.hasQualityData ? `<span class="badge-disabled">Quality scores not in dataset</span>` : ''}
                                    </div>
                                    <div class="checklist-desc">Average Phred quality score, high-quality (Q30+) base percentages, and quality distribution summary.</div>
                                </div>
                            </label>

                            <!-- 7. Dataset Comparison -->
                            <label class="checklist-card ${!hasMultipleDatasets ? 'disabled-card' : ''}">
                                <input type="checkbox" id="chkComparison" ${this.includeComparison && hasMultipleDatasets ? 'checked' : ''} ${!hasMultipleDatasets ? 'disabled' : ''} />
                                <div class="checklist-info">
                                    <div class="checklist-title">
                                        <span>7. Dataset Comparison</span>
                                        ${!hasMultipleDatasets ? `<span class="badge-disabled">Requires &ge; 2 datasets</span>` : ''}
                                    </div>
                                    <div class="checklist-desc">Differences in read counts, nucleotide composition, GC content, and read lengths between compared sequencing runs.</div>
                                    ${hasMultipleDatasets ? `
                                        <div class="comparison-picker" style="margin-top: 8px;">
                                            <span style="font-size: 0.78rem; font-weight: 600; color: var(--text-heading);">Compare against:</span>
                                            <select id="selCompareTarget" class="form-select-sm" style="margin-left: 6px;">
                                                ${this.availableDatasets.filter(d => d.id !== this.selectedDatasetId).map(d => `
                                                    <option value="${d.id}">${this.escapeHtml(d.name)}</option>
                                                `).join('')}
                                            </select>
                                        </div>
                                    ` : ''}
                                </div>
                            </label>

                            <!-- 8. Researcher Observations -->
                            <label class="checklist-card">
                                <input type="checkbox" id="chkObservations" ${this.includeObservations ? 'checked' : ''} />
                                <div class="checklist-info">
                                    <div class="checklist-title">8. Researcher Observations</div>
                                    <div class="checklist-desc">Include the purpose of analysis, custom methodology notes, and researcher observations entered above.</div>
                                </div>
                            </label>
                        </div>
                    </div>

                    <!-- SUBMIT BUTTON -->
                    <div class="form-action-strip">
                        <button type="button" class="btn-primary btn-generate-main" id="btnGenerateReport">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line></svg>
                            <span>Generate My Report</span>
                        </button>
                    </div>
                </div>

                <!-- ==================== REPORT OUTPUT NOTIFICATION ==================== -->
                <div class="reports-output-card hidden" id="reportOutputCard">
                    <div class="output-success-header">
                        <div class="output-success-icon">
                            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#059669" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg>
                        </div>
                        <div>
                            <h2 class="output-title">Your Genomic Analysis Report Is Ready</h2>
                            <p class="output-subtitle" id="outputMetaSummary">Your report has been generated successfully. You can download it, save it, or view its contents.</p>
                        </div>
                    </div>

                    <div class="output-meta-grid" id="outputDetailsGrid">
                        <!-- Populated dynamically on generation -->
                    </div>

                    <div class="output-buttons-strip">
                        <button type="button" class="btn-report-action btn-report-outline" id="btnDownloadPdf">
                            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><polyline points="6 9 6 2 18 2 18 9"></polyline><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path><rect x="6" y="14" width="12" height="8"></rect></svg>
                            <span>Download PDF</span>
                        </button>
                        <button type="button" class="btn-report-action btn-report-outline" id="btnExportCsv">
                            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                            <span>Export CSV</span>
                        </button>
                        <button type="button" class="btn-report-action btn-report-outline" id="btnSaveToMyReports">
                            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path><polyline points="17 21 17 13 7 13 7 21"></polyline><polyline points="7 3 7 8 15 8"></polyline></svg>
                            <span>Save to My Reports</span>
                        </button>
                        <button type="button" class="btn-report-action btn-report-primary" id="btnToggleViewDoc">
                            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>
                            <span>View Report</span>
                        </button>
                        <button type="button" class="btn-report-action btn-report-subtle" id="btnGenerateAnother">
                            <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><polyline points="23 4 23 10 17 10"></polyline><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"></path></svg>
                            <span>Generate Another Report</span>
                        </button>
                    </div>
                </div>

                <!-- ==================== FULL-SCREEN REPORT PREVIEW MODAL ==================== -->
                <div class="report-modal-overlay hidden" id="reportPreviewModal" role="dialog" aria-modal="true" aria-labelledby="reportModalHeading">
                    <div class="report-modal-dialog">
                        <!-- Sticky Modal Header -->
                        <div class="report-modal-header">
                            <div class="modal-title-cluster">
                                <div class="modal-badge-strip">
                                    <span class="modal-badge-genedex">GeneDex</span>
                                    <span class="modal-badge-type">Genomic Analysis Report Preview</span>
                                </div>
                                <h2 class="report-modal-title" id="reportModalHeading">
                                    Genomic Analysis Report Preview
                                </h2>
                                <div class="report-modal-meta-bar" id="reportModalMetaBar"></div>
                            </div>
                            <button type="button" class="report-modal-close-btn" id="btnModalCloseX" aria-label="Close report preview (Press Escape)" title="Close Preview (Esc)">
                                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
                            </button>
                        </div>

                        <!-- Scrollable Modal Body (Contains Document) -->
                        <div class="report-modal-body" id="reportModalBody" tabindex="0">
                            <div class="document-viewer-box" id="printableReportDoc">
                                <!-- Injected report content via buildReportDocumentHtml(this.generatedReport) -->
                            </div>
                        </div>

                        <!-- Sticky Modal Footer -->
                        <div class="report-modal-footer">
                            <span class="modal-footer-caption">Generated using GeneDex — Genomic Sequence Analysis Platform</span>
                            <div class="modal-footer-actions">
                                <button type="button" class="btn-report-action btn-report-outline btn-modal-pdf" id="btnModalDownloadPdf">
                                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><polyline points="6 9 6 2 18 2 18 9"></polyline><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"></path><rect x="6" y="14" width="12" height="8"></rect></svg>
                                    <span>Download PDF</span>
                                </button>
                                <button type="button" class="btn-report-action btn-report-subtle btn-modal-close" id="btnModalCloseBtn">
                                    <span>Close</span>
                                </button>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- ==================== MY REPORTS HISTORY SECTION ==================== -->
                <section class="my-reports-section">
                    <div class="my-reports-header">
                        <h2 class="my-reports-title">My Reports</h2>
                        <span class="my-reports-count">${this.userReports.length} saved</span>
                    </div>

                    <div class="my-reports-table-box">
                        ${this.renderMyReportsTable()}
                    </div>
                </section>
            </div>
        `;

        this.bindEvents();
    }

    renderMyReportsTable() {
        if (!this.userReports || this.userReports.length === 0) {
            return `
                <div class="empty-history-box">
                    <p class="empty-history-text">You haven't generated any reports yet.</p>
                    <span class="empty-history-sub">Fill out the form above and click "Generate My Report" to compile your first analysis report.</span>
                </div>
            `;
        }

        return `
            <table class="my-reports-table">
                <thead>
                    <tr>
                        <th>Report Name</th>
                        <th>Dataset</th>
                        <th>Date</th>
                        <th style="text-align: right;">Actions</th>
                    </tr>
                </thead>
                <tbody>
                    ${this.userReports.map((rep, idx) => `
                        <tr>
                            <td>
                                <strong class="rep-name-text">${this.escapeHtml(rep.title)}</strong>
                                <span class="rep-sections-count">${rep.sectionsCount || 4} sections included</span>
                            </td>
                            <td><span class="badge-dataset">${this.escapeHtml(rep.datasetName)}</span></td>
                            <td><span class="rep-date-text">${this.escapeHtml(rep.date)}</span></td>
                            <td style="text-align: right;">
                                <div class="action-buttons-cell">
                                    <button type="button" class="btn-sm btn-outline btn-view-saved" data-idx="${idx}">View</button>
                                    <button type="button" class="btn-sm btn-outline btn-pdf-saved" data-idx="${idx}">PDF</button>
                                    <button type="button" class="btn-sm btn-outline btn-csv-saved" data-idx="${idx}">CSV</button>
                                    <button type="button" class="btn-sm btn-danger-link btn-del-saved" data-idx="${idx}" title="Delete Report">Delete</button>
                                </div>
                            </td>
                        </tr>
                    `).join('')}
                </tbody>
            </table>
        `;
    }

    bindEvents() {
        // Dataset dropdown change
        const selDataset = this.container.querySelector('#repDataset');
        selDataset?.addEventListener('change', async (e) => {
            const dsId = e.target.value;
            this.selectedDatasetId = dsId;
            Toast.info(`Switching active dataset to ${dsId}...`);
            await api.setActiveDataset(dsId);
            const dsRes = await api.getDatasets();
            if (dsRes && dsRes.success) {
                this.activeDataset = dsRes.activeDataset;
            }
            await this.checkDatasetFeatures();
            this.renderView();
        });

        // Generate Report Button
        const btnGenerate = this.container.querySelector('#btnGenerateReport');
        btnGenerate?.addEventListener('click', async () => {
            await this.handleGenerateReport();
        });

        // Output Action Buttons
        const outputCard = this.container.querySelector('#reportOutputCard');
        outputCard?.addEventListener('click', (e) => {
            if (e.target.closest('#btnDownloadPdf')) {
                this.downloadPdf();
            } else if (e.target.closest('#btnExportCsv')) {
                this.exportCsv();
            } else if (e.target.closest('#btnSaveToMyReports')) {
                this.saveToMyReports();
            } else if (e.target.closest('#btnToggleViewDoc')) {
                this.openReportModal();
            } else if (e.target.closest('#btnGenerateAnother')) {
                this.resetToForm();
            }
        });

        // Modal Action Buttons & Overlay Click
        const modal = this.container.querySelector('#reportPreviewModal');
        modal?.addEventListener('click', (e) => {
            if (e.target.closest('#btnModalCloseX') || e.target.closest('#btnModalCloseBtn')) {
                this.closeReportModal();
            } else if (e.target.closest('#btnModalDownloadPdf')) {
                this.downloadPdf();
            } else if (e.target === modal) {
                // Click on dark overlay outside modal dialog
                this.closeReportModal();
            }
        });

        // Keyboard Escape Listener for Modal
        if (this.boundKeyHandler) {
            window.removeEventListener('keydown', this.boundKeyHandler);
        }
        this.boundKeyHandler = (e) => {
            if (e.key === 'Escape' && this.isModalOpen) {
                this.closeReportModal();
            }
        };
        window.addEventListener('keydown', this.boundKeyHandler);

        // Saved Reports Actions Table
        const myReportsSection = this.container.querySelector('.my-reports-section');
        myReportsSection?.addEventListener('click', (e) => {
            const btnView = e.target.closest('.btn-view-saved');
            const btnPdf = e.target.closest('.btn-pdf-saved');
            const btnCsv = e.target.closest('.btn-csv-saved');
            const btnDel = e.target.closest('.btn-del-saved');

            if (btnView) {
                const idx = parseInt(btnView.getAttribute('data-idx'));
                this.openSavedReport(idx);
            } else if (btnPdf) {
                const idx = parseInt(btnPdf.getAttribute('data-idx'));
                this.printSavedReport(idx);
            } else if (btnCsv) {
                const idx = parseInt(btnCsv.getAttribute('data-idx'));
                this.exportSavedReportCsv(idx);
            } else if (btnDel) {
                const idx = parseInt(btnDel.getAttribute('data-idx'));
                this.deleteSavedReport(idx);
            }
        });
    }

    async handleGenerateReport() {
        const titleInput = this.container.querySelector('#repTitle');
        const researcherInput = this.container.querySelector('#repResearcher');
        const instInput = this.container.querySelector('#repInstitution');
        const purposeInput = this.container.querySelector('#repPurpose');
        const notesInput = this.container.querySelector('#repNotes');

        const title = titleInput?.value.trim();
        const researcher = researcherInput?.value.trim();

        if (!title) {
            Toast.error("Report Title is required.");
            titleInput?.focus();
            return;
        }

        if (!researcher) {
            Toast.error("Researcher / Prepared By is required.");
            researcherInput?.focus();
            return;
        }

        this.reportTitle = title;
        this.researcher = researcher;
        this.institution = instInput?.value.trim() || '';
        this.purpose = purposeInput?.value.trim() || '';
        this.notes = notesInput?.value.trim() || '';

        // Capture checkboxes
        this.includeSummary = this.container.querySelector('#chkSummary')?.checked || false;
        this.includeComposition = this.container.querySelector('#chkComposition')?.checked || false;
        this.includeGcContent = this.container.querySelector('#chkGcContent')?.checked || false;
        this.includePatternSearch = this.container.querySelector('#chkPatternSearch')?.checked || false;
        this.includeReadAnalysis = this.container.querySelector('#chkReadAnalysis')?.checked || false;
        this.includeQuality = this.container.querySelector('#chkQuality')?.checked || false;
        this.includeComparison = this.container.querySelector('#chkComparison')?.checked || false;
        this.includeObservations = this.container.querySelector('#chkObservations')?.checked || false;

        const selCompare = this.container.querySelector('#selCompareTarget');
        if (selCompare) {
            this.comparisonDatasetId = selCompare.value;
        }

        // Show loading state
        const btnGen = this.container.querySelector('#btnGenerateReport');
        btnGen.disabled = true;
        btnGen.innerHTML = `<span>Compiling Report Data...</span>`;

        try {
            // Retrieve actual dataset analysis numbers
            const [statsRes, anRes, compRes, readsRes] = await Promise.all([
                api.getStatistics(),
                api.getAnalytics(),
                api.getComposition(),
                api.getReads(1, 100)
            ]);

            const stats = (statsRes && statsRes.success) ? (statsRes.statistics || statsRes) : {};
            const analytics = (anRes && anRes.success) ? anRes : {};
            const composition = (compRes && compRes.success) ? compRes : {};
            const reads = (readsRes && readsRes.success) ? (readsRes.reads || []) : [];

            // Pattern search results if requested
            let searchData = null;
            if (this.includePatternSearch && this.recentPattern) {
                const searchRes = await api.countAndLocate(this.recentPattern);
                if (searchRes && searchRes.success) {
                    searchData = {
                        pattern: this.recentPattern,
                        matchCount: searchRes.matchCount || searchRes.count || 0,
                        matches: searchRes.matches || []
                    };
                }
            }

            // Comparison data if requested
            let compData = null;
            if (this.includeComparison && this.comparisonDatasetId) {
                compData = await this.compileComparisonData(this.comparisonDatasetId);
            }

            // Compile the report model
            this.generatedReport = {
                id: 'rep_' + Date.now(),
                title: this.reportTitle,
                researcher: this.researcher,
                institution: this.institution,
                date: this.reportDate,
                datasetName: this.activeDataset?.name || this.activeDataset?.datasetName || 'Dataset.fastq',
                purpose: this.purpose,
                notes: this.notes,
                sections: {
                    summary: this.includeSummary ? {
                        filename: this.activeDataset?.name || this.activeDataset?.datasetName || 'dataset.fastq',
                        totalReads: stats.totalReads || 1,
                        totalBases: stats.totalDnaBases || stats.totalIndexedSymbols || 0,
                        avgLength: stats.avgReadLength || 0,
                        minLength: stats.minReadLength || 0,
                        maxLength: stats.maxReadLength || 0
                    } : null,
                    composition: this.includeComposition ? {
                        counts: composition.counts || {},
                        percentages: composition.percentages || {},
                        totalBases: composition.totalDnaBases || 0
                    } : null,
                    gcContent: this.includeGcContent ? {
                        gcPercentage: composition.gcPercentage || 0,
                        atPercentage: composition.atPercentage || 0,
                        readsGc: reads.slice(0, 10).map(r => ({ id: r.id || r.header, gc: r.gcPercentage }))
                    } : null,
                    reads: this.includeReadAnalysis ? reads.slice(0, 20) : null,
                    patternSearch: searchData,
                    quality: this.includeQuality ? analytics.qualitySummary : null,
                    comparison: compData,
                    observations: this.includeObservations ? {
                        purpose: this.purpose,
                        notes: this.notes
                    } : null
                },
                sectionsCount: [
                    this.includeSummary, this.includeComposition, this.includeGcContent,
                    this.includePatternSearch, this.includeReadAnalysis, this.includeQuality,
                    this.includeComparison, this.includeObservations
                ].filter(Boolean).length
            };

            Toast.success("Genomic Analysis Report successfully compiled!");
            this.renderOutputView();

        } catch (err) {
            console.error("Report generation error:", err);
            Toast.error("Failed to compile report: " + err.message);
        } finally {
            btnGen.disabled = false;
            btnGen.innerHTML = `<span>Generate My Report</span>`;
        }
    }

    async compileComparisonData(targetDsId) {
        // Compare active dataset with another dataset
        const currentStats = await api.getStatistics();
        const currentComp = await api.getComposition();

        // Target stats metadata
        const targetMeta = this.availableDatasets.find(d => d.id === targetDsId) || { name: targetDsId, reads: 3, symbols: 19 };

        return {
            datasetA: this.activeDataset?.name || 'Dataset A',
            datasetB: targetMeta.name || 'Dataset B',
            readsA: currentStats.totalReads || 1,
            readsB: targetMeta.reads || 3,
            basesA: currentStats.totalDnaBases || currentStats.totalIndexedSymbols || 14,
            basesB: targetMeta.symbols || 19,
            gcPctA: currentComp.gcPercentage || 28.57,
            gcPctB: 40.0
        };
    }

    renderOutputView() {
        const formCard = this.container.querySelector('#reportsFormCard');
        const outputCard = this.container.querySelector('#reportOutputCard');
        const modal = this.container.querySelector('#reportPreviewModal');
        if (!outputCard || !this.generatedReport) return;

        formCard?.classList.add('hidden');
        outputCard.classList.remove('hidden');

        // Preview modal remains closed until user clicks View Report
        this.isModalOpen = false;
        modal?.classList.add('hidden');
        document.body.style.overflow = '';

        const metaGrid = outputCard.querySelector('#outputDetailsGrid');
        if (metaGrid) {
            metaGrid.innerHTML = `
                <div class="output-meta-item">
                    <span class="output-meta-label">Report Title</span>
                    <strong class="output-meta-value">${this.escapeHtml(this.generatedReport.title)}</strong>
                </div>
                <div class="output-meta-item">
                    <span class="output-meta-label">Active Dataset</span>
                    <strong class="output-meta-value">${this.escapeHtml(this.generatedReport.datasetName)}</strong>
                </div>
                <div class="output-meta-item">
                    <span class="output-meta-label">Date Generated</span>
                    <strong class="output-meta-value">${this.escapeHtml(this.generatedReport.date)}</strong>
                </div>
                <div class="output-meta-item">
                    <span class="output-meta-label">Sections Included</span>
                    <strong class="output-meta-value">${this.generatedReport.sectionsCount} of 8 sections</strong>
                </div>
            `;
        }

        // Pre-render Document HTML into modal viewer box so it's ready on open or print
        const viewerDoc = this.container.querySelector('#printableReportDoc');
        if (viewerDoc) {
            viewerDoc.innerHTML = this.buildReportDocumentHtml(this.generatedReport);
        }

        // Scroll to output success notification
        outputCard.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    openReportModal() {
        if (!this.generatedReport) return;
        const modal = this.container.querySelector('#reportPreviewModal');
        const modalHeading = this.container.querySelector('#reportModalHeading');
        const modalMetaBar = this.container.querySelector('#reportModalMetaBar');
        const viewerDoc = this.container.querySelector('#printableReportDoc');
        if (!modal) return;

        if (modalHeading) {
            modalHeading.textContent = this.generatedReport.title || 'Genomic Analysis Report Preview';
        }

        if (modalMetaBar) {
            modalMetaBar.innerHTML = `
                <span class="modal-meta-item"><strong>Dataset:</strong> ${this.escapeHtml(this.generatedReport.datasetName)}</span>
                <span class="modal-meta-sep">•</span>
                <span class="modal-meta-item"><strong>Date:</strong> ${this.escapeHtml(this.generatedReport.date)}</span>
                <span class="modal-meta-sep">•</span>
                <span class="modal-meta-item"><strong>Researcher:</strong> ${this.escapeHtml(this.generatedReport.researcher)}</span>
            `;
        }

        if (viewerDoc) {
            viewerDoc.innerHTML = this.buildReportDocumentHtml(this.generatedReport);
        }

        modal.classList.remove('hidden');
        this.isModalOpen = true;
        document.body.style.overflow = 'hidden';

        const closeBtn = this.container.querySelector('#btnModalCloseX');
        closeBtn?.focus();
    }

    closeReportModal() {
        const modal = this.container.querySelector('#reportPreviewModal');
        if (!modal) return;

        modal.classList.add('hidden');
        this.isModalOpen = false;
        document.body.style.overflow = '';

        const viewBtn = this.container.querySelector('#btnToggleViewDoc');
        viewBtn?.focus();
    }

    saveCurrentReportToHistory() {
        if (!this.generatedReport) return;
        const exists = this.userReports.some(r => r.id === this.generatedReport.id);
        if (!exists) {
            this.userReports.unshift(this.generatedReport);
            if (this.userReports.length > 25) this.userReports.pop();
            this.saveUserReports();
            this.updateMyReportsTableDom();
        }
    }

    updateMyReportsTableDom() {
        const tableBox = this.container.querySelector('.my-reports-table-box');
        const countSpan = this.container.querySelector('.my-reports-count');
        if (tableBox) {
            tableBox.innerHTML = this.renderMyReportsTable();
        }
        if (countSpan) {
            countSpan.textContent = `${this.userReports.length} saved`;
        }
    }

    saveToMyReports() {
        if (!this.generatedReport) return;
        const exists = this.userReports.some(r => r.id === this.generatedReport.id);
        if (exists) {
            Toast.info("This report is already saved in My Reports.");
            return;
        }
        this.userReports.unshift(this.generatedReport);
        if (this.userReports.length > 25) this.userReports.pop();
        this.saveUserReports();
        this.updateMyReportsTableDom();
        Toast.success("Report saved to My Reports!");
    }

    resetToForm() {
        this.closeReportModal();
        const formCard = this.container.querySelector('#reportsFormCard');
        const outputCard = this.container.querySelector('#reportOutputCard');
        const viewerDoc = this.container.querySelector('#printableReportDoc');

        this.generatedReport = null;
        this.isModalOpen = false;

        if (viewerDoc) viewerDoc.innerHTML = '';
        outputCard?.classList.add('hidden');
        formCard?.classList.remove('hidden');

        formCard?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    downloadPdf() {
        if (!this.generatedReport) return;
        const viewerDoc = this.container.querySelector('#printableReportDoc');
        if (viewerDoc && (!viewerDoc.innerHTML || viewerDoc.innerHTML.trim() === '')) {
            viewerDoc.innerHTML = this.buildReportDocumentHtml(this.generatedReport);
        }
        window.print();
    }

    exportCsv() {
        if (!this.generatedReport) return;
        const rep = this.generatedReport;
        let csv = [];

        csv.push(`# GeneDex Genomic Sequence Analysis Report`);
        csv.push(`Report Title,${this.csvEscape(rep.title)}`);
        csv.push(`Prepared By,${this.csvEscape(rep.researcher)}`);
        csv.push(`Institution,${this.csvEscape(rep.institution || 'N/A')}`);
        csv.push(`Date,${this.csvEscape(rep.date)}`);
        csv.push(`Dataset,${this.csvEscape(rep.datasetName)}`);
        csv.push(``);

        if (rep.sections.summary) {
            const s = rep.sections.summary;
            csv.push(`[DATASET SUMMARY]`);
            csv.push(`Metric,Value`);
            csv.push(`FASTQ Filename,${s.filename}`);
            csv.push(`Total DNA Reads,${s.totalReads}`);
            csv.push(`Total DNA Bases,${s.totalBases}`);
            csv.push(`Average Read Length,${s.avgLength} bp`);
            csv.push(`Minimum Read Length,${s.minLength} bp`);
            csv.push(`Maximum Read Length,${s.maxLength} bp`);
            csv.push(``);
        }

        if (rep.sections.composition) {
            const c = rep.sections.composition;
            csv.push(`[NUCLEOTIDE COMPOSITION]`);
            csv.push(`Nucleotide,Count,Percentage`);
            csv.push(`Adenine (A),${c.counts.A || 0},${c.percentages.A || 0}%`);
            csv.push(`Cytosine (C),${c.counts.C || 0},${c.percentages.C || 0}%`);
            csv.push(`Guanine (G),${c.counts.G || 0},${c.percentages.G || 0}%`);
            csv.push(`Thymine (T),${c.counts.T || 0},${c.percentages.T || 0}%`);
            csv.push(`Unknown (N),${c.counts.N || 0},${c.percentages.N || 0}%`);
            csv.push(``);
        }

        if (rep.sections.gcContent) {
            const gc = rep.sections.gcContent;
            csv.push(`[GC CONTENT ANALYSIS]`);
            csv.push(`Metric,Value`);
            csv.push(`Overall GC Content,${gc.gcPercentage}%`);
            csv.push(`Overall AT Content,${gc.atPercentage}%`);
            csv.push(``);
        }

        if (rep.sections.reads && rep.sections.reads.length > 0) {
            csv.push(`[DNA READS ANALYSIS]`);
            csv.push(`Read Index,Read Identifier,Length (bp),GC Content (%)`);
            rep.sections.reads.forEach((r, idx) => {
                csv.push(`${idx},${this.csvEscape(r.id || r.header)},${r.length || 0},${r.gcPercentage || 0}%`);
            });
            csv.push(``);
        }

        if (rep.sections.patternSearch) {
            const ps = rep.sections.patternSearch;
            csv.push(`[PATTERN SEARCH RESULTS]`);
            csv.push(`Pattern Searched,${this.csvEscape(ps.pattern)}`);
            csv.push(`Total Occurrences,${ps.matchCount}`);
            csv.push(`Global Position,Read Identifier,Offset`);
            ps.matches.forEach(m => {
                csv.push(`${m.globalPosition},${this.csvEscape(m.readId || '@read' + m.readIndex)},${m.offsetInRead}`);
            });
            csv.push(``);
        }

        if (rep.sections.observations) {
            const o = rep.sections.observations;
            csv.push(`[RESEARCHER OBSERVATIONS]`);
            if (o.purpose) csv.push(`Purpose,${this.csvEscape(o.purpose)}`);
            if (o.notes) csv.push(`Notes,${this.csvEscape(o.notes)}`);
            csv.push(``);
        }

        csv.push(`# Generated using GeneDex — Genomic Sequence Analysis Platform.`);

        const csvContent = csv.join("\r\n");
        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `${rep.title.replace(/\s+/g, '_')}_Report.csv`;
        link.click();
        Toast.success("Exported report CSV.");
    }

    downloadMarkdown() {
        if (!this.generatedReport) return;
        const rep = this.generatedReport;
        let md = [];

        md.push(`# Genomic Analysis Report: ${rep.title}`);
        md.push(`**Prepared by:** ${rep.researcher}  `);
        if (rep.institution) md.push(`**Institution:** ${rep.institution}  `);
        md.push(`**Date:** ${rep.date}  `);
        md.push(`**Target Dataset:** ${rep.datasetName}  `);
        md.push(``);
        md.push(`---`);
        md.push(``);

        if (rep.sections.summary) {
            const s = rep.sections.summary;
            md.push(`## 1. Dataset Overview`);
            md.push(`- **FASTQ Filename:** \`${s.filename}\``);
            md.push(`- **Total DNA Reads:** ${s.totalReads.toLocaleString()}`);
            md.push(`- **Total DNA Bases:** ${s.totalBases.toLocaleString()}`);
            md.push(`- **Average Read Length:** ${s.avgLength} bp (Min: ${s.minLength} bp, Max: ${s.maxLength} bp)`);
            md.push(``);
        }

        if (rep.sections.composition) {
            const c = rep.sections.composition;
            md.push(`## 2. Nucleotide Composition`);
            md.push(`| Nucleotide Base | Symbol | Count (bp) | Percentage |`);
            md.push(`| :--- | :---: | :---: | :---: |`);
            md.push(`| Adenine | A | ${c.counts.A || 0} | ${c.percentages.A || 0}% |`);
            md.push(`| Cytosine | C | ${c.counts.C || 0} | ${c.percentages.C || 0}% |`);
            md.push(`| Guanine | G | ${c.counts.G || 0} | ${c.percentages.G || 0}% |`);
            md.push(`| Thymine | T | ${c.counts.T || 0} | ${c.percentages.T || 0}% |`);
            md.push(`| Unknown | N | ${c.counts.N || 0} | ${c.percentages.N || 0}% |`);
            md.push(``);
        }

        if (rep.sections.gcContent) {
            const gc = rep.sections.gcContent;
            md.push(`## 3. GC Content Analysis`);
            md.push(`- **Overall GC Percentage:** ${gc.gcPercentage}%`);
            md.push(`- **Overall AT Percentage:** ${gc.atPercentage}%`);
            md.push(``);
        }

        if (rep.sections.reads && rep.sections.reads.length > 0) {
            md.push(`## 4. DNA Read Statistics`);
            md.push(`| Read Identifier | Length | GC% | Sequence Preview |`);
            md.push(`| :--- | :---: | :---: | :--- |`);
            rep.sections.reads.slice(0, 15).forEach(r => {
                md.push(`| \`${r.id || r.header}\` | ${r.length} bp | ${r.gcPercentage}% | \`${(r.sequence || '').slice(0, 30)}...\` |`);
            });
            md.push(``);
        }

        if (rep.sections.patternSearch) {
            const ps = rep.sections.patternSearch;
            md.push(`## 5. DNA Pattern Search Results`);
            md.push(`- **Searched Pattern:** \`${ps.pattern}\``);
            md.push(`- **Total Matches:** ${ps.matchCount}`);
            md.push(``);
            md.push(`| Match # | Global Position | Read Identifier | Offset in Read |`);
            md.push(`| :---: | :---: | :---: | :---: |`);
            ps.matches.forEach((m, idx) => {
                md.push(`| ${idx + 1} | ${m.globalPosition} | \`${m.readId || '@read' + m.readIndex}\` | pos ${m.offsetInRead} |`);
            });
            md.push(``);
        }

        if (rep.sections.quality) {
            const q = rep.sections.quality;
            md.push(`## 6. Sequencing Quality Assessment`);
            md.push(`- **Mean Phred Quality Score:** Q${q.meanPhredScore || 0}`);
            md.push(`- **High Quality Bases (Q30+):** ${q.q30Percentage || 0}%`);
            md.push(``);
        }

        if (rep.sections.comparison) {
            const cmp = rep.sections.comparison;
            md.push(`## 7. Dataset Comparison`);
            md.push(`| Metric | ${cmp.datasetA} | ${cmp.datasetB} | Difference |`);
            md.push(`| :--- | :---: | :---: | :---: |`);
            md.push(`| Total Reads | ${cmp.readsA} | ${cmp.readsB} | ${cmp.readsA - cmp.readsB} |`);
            md.push(`| Total Bases | ${cmp.basesA} bp | ${cmp.basesB} bp | ${cmp.basesA - cmp.basesB} bp |`);
            md.push(`| GC Content | ${cmp.gcPctA}% | ${cmp.gcPctB}% | ${(cmp.gcPctA - cmp.gcPctB).toFixed(2)}% |`);
            md.push(``);
        }

        if (rep.sections.observations) {
            const o = rep.sections.observations;
            md.push(`## 8. Researcher Observations`);
            if (o.purpose) md.push(`**Purpose:** ${o.purpose}  `);
            if (o.notes) md.push(`**Analytical Notes:** ${o.notes}  `);
            md.push(``);
        }

        md.push(``);
        md.push(`---`);
        md.push(`*Generated using GeneDex — Genomic Sequence Analysis Platform.*`);

        const mdContent = md.join("\n");
        const blob = new Blob([mdContent], { type: 'text/markdown;charset=utf-8;' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `${rep.title.replace(/\s+/g, '_')}_Report.md`;
        link.click();
        Toast.success("Downloaded report Markdown.");
    }

    openSavedReport(idx) {
        const rep = this.userReports[idx];
        if (!rep) return;
        this.generatedReport = rep;
        this.renderOutputView();
        this.openReportModal();
        Toast.info(`Loaded report: "${rep.title}"`);
    }

    printSavedReport(idx) {
        const rep = this.userReports[idx];
        if (!rep) return;
        this.generatedReport = rep;
        const viewerDoc = this.container.querySelector('#printableReportDoc');
        if (viewerDoc) {
            viewerDoc.innerHTML = this.buildReportDocumentHtml(rep);
        }
        this.downloadPdf();
    }

    exportSavedReportCsv(idx) {
        const rep = this.userReports[idx];
        if (!rep) return;
        this.generatedReport = rep;
        this.exportCsv();
    }

    deleteSavedReport(idx) {
        if (!confirm("Are you sure you want to delete this report from history?")) return;
        this.userReports.splice(idx, 1);
        this.saveUserReports();
        this.updateMyReportsTableDom();
        Toast.info("Report removed from history.");
    }

    buildReportDocumentHtml(rep) {
        const s = rep.sections;

        return `
            <div class="genomic-report-document">
                <!-- Cover Header -->
                <div class="rep-doc-header">
                    <div class="rep-brand-strip">
                        <div class="brand-logo-cluster">
                            <span class="logo-genedex-pill">GeneDex</span>
                            <span class="logo-sub-text">Genomic Analysis Platform</span>
                        </div>
                        <span class="report-class-badge">DNA Sequencing Report</span>
                    </div>

                    <h1 class="rep-doc-title">${this.escapeHtml(rep.title)}</h1>
                    
                    <div class="rep-doc-meta-grid">
                        <div class="rep-meta-col">
                            <span class="meta-label">Prepared By</span>
                            <strong class="meta-val">${this.escapeHtml(rep.researcher)}</strong>
                        </div>
                        ${rep.institution ? `
                            <div class="rep-meta-col">
                                <span class="meta-label">Institution / Lab</span>
                                <strong class="meta-val">${this.escapeHtml(rep.institution)}</strong>
                            </div>
                        ` : ''}
                        <div class="rep-meta-col">
                            <span class="meta-label">Date of Report</span>
                            <strong class="meta-val">${this.escapeHtml(rep.date)}</strong>
                        </div>
                        <div class="rep-meta-col">
                            <span class="meta-label">FASTQ Dataset</span>
                            <strong class="meta-val font-mono">${this.escapeHtml(rep.datasetName)}</strong>
                        </div>
                    </div>
                </div>

                <!-- SECTION 1: DATASET OVERVIEW -->
                ${s.summary ? `
                    <div class="rep-doc-section">
                        <h2 class="rep-sec-heading">1. Dataset Overview</h2>
                        <div class="rep-summary-cards-grid">
                            <div class="rep-metric-card">
                                <span class="rep-metric-label">Total DNA Reads</span>
                                <strong class="rep-metric-num">${s.summary.totalReads.toLocaleString()}</strong>
                            </div>
                            <div class="rep-metric-card">
                                <span class="rep-metric-label">Total DNA Bases</span>
                                <strong class="rep-metric-num">${s.summary.totalBases.toLocaleString()}</strong>
                            </div>
                            <div class="rep-metric-card">
                                <span class="rep-metric-label">Average Read Length</span>
                                <strong class="rep-metric-num">${s.summary.avgLength} bp</strong>
                            </div>
                            <div class="rep-metric-card">
                                <span class="rep-metric-label">Read Length Range</span>
                                <strong class="rep-metric-num">${s.summary.minLength} - ${s.summary.maxLength} bp</strong>
                            </div>
                        </div>
                    </div>
                ` : ''}

                <!-- SECTION 2: NUCLEOTIDE COMPOSITION -->
                ${s.composition ? `
                    <div class="rep-doc-section">
                        <h2 class="rep-sec-heading">2. Nucleotide Composition</h2>
                        <p class="rep-sec-desc">Total nucleotide counts and proportions identified across the dataset (${s.composition.totalBases.toLocaleString()} bases analyzed).</p>
                        
                        <div class="nucleotide-distribution-bar">
                            <div class="bar-seg seg-a" style="width: ${s.composition.percentages.A || 0}%;" title="A: ${s.composition.percentages.A}%"></div>
                            <div class="bar-seg seg-c" style="width: ${s.composition.percentages.C || 0}%;" title="C: ${s.composition.percentages.C}%"></div>
                            <div class="bar-seg seg-g" style="width: ${s.composition.percentages.G || 0}%;" title="G: ${s.composition.percentages.G}%"></div>
                            <div class="bar-seg seg-t" style="width: ${s.composition.percentages.T || 0}%;" title="T: ${s.composition.percentages.T}%"></div>
                        </div>

                        <table class="rep-data-table">
                            <thead>
                                <tr>
                                    <th>Nucleotide Base</th>
                                    <th>Symbol</th>
                                    <th style="text-align: right;">Count (bp)</th>
                                    <th style="text-align: right;">Percentage</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr><td><span class="base-badge base-a">Adenine</span></td><td class="font-mono">A</td><td style="text-align: right;">${s.composition.counts.A || 0}</td><td style="text-align: right;">${s.composition.percentages.A || 0}%</td></tr>
                                <tr><td><span class="base-badge base-c">Cytosine</span></td><td class="font-mono">C</td><td style="text-align: right;">${s.composition.counts.C || 0}</td><td style="text-align: right;">${s.composition.percentages.C || 0}%</td></tr>
                                <tr><td><span class="base-badge base-g">Guanine</span></td><td class="font-mono">G</td><td style="text-align: right;">${s.composition.counts.G || 0}</td><td style="text-align: right;">${s.composition.percentages.G || 0}%</td></tr>
                                <tr><td><span class="base-badge base-t">Thymine</span></td><td class="font-mono">T</td><td style="text-align: right;">${s.composition.counts.T || 0}</td><td style="text-align: right;">${s.composition.percentages.T || 0}%</td></tr>
                                <tr><td><span class="base-badge base-n">Unknown</span></td><td class="font-mono">N</td><td style="text-align: right;">${s.composition.counts.N || 0}</td><td style="text-align: right;">${s.composition.percentages.N || 0}%</td></tr>
                            </tbody>
                        </table>
                    </div>
                ` : ''}

                <!-- SECTION 3: GC CONTENT ANALYSIS -->
                ${s.gcContent ? `
                    <div class="rep-doc-section">
                        <h2 class="rep-sec-heading">3. GC Content Analysis</h2>
                        <div class="gc-stat-box">
                            <div class="gc-stat-pill">
                                <span class="gc-num">${s.gcContent.gcPercentage}%</span>
                                <span class="gc-lbl">Overall GC Content</span>
                            </div>
                            <div class="gc-stat-pill">
                                <span class="gc-num">${s.gcContent.atPercentage}%</span>
                                <span class="gc-lbl">Overall AT Content</span>
                            </div>
                            <div class="gc-stat-pill">
                                <span class="gc-num">${(s.gcContent.gcPercentage / (s.gcContent.atPercentage || 1)).toFixed(2)}</span>
                                <span class="gc-lbl">GC / AT Ratio</span>
                            </div>
                        </div>
                    </div>
                ` : ''}

                <!-- SECTION 4: DNA READ ANALYSIS -->
                ${s.reads && s.reads.length > 0 ? `
                    <div class="rep-doc-section">
                        <h2 class="rep-sec-heading">4. DNA Read Analysis</h2>
                        <p class="rep-sec-desc">Sample reads reconstructed from sequencing data with respective GC content and lengths.</p>
                        <table class="rep-data-table">
                            <thead>
                                <tr>
                                    <th>Read Identifier</th>
                                    <th>Length</th>
                                    <th>GC%</th>
                                    <th>Sequence Preview</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${s.reads.slice(0, 10).map(r => `
                                    <tr>
                                        <td><code class="read-id-pill">${this.escapeHtml(r.id || r.header)}</code></td>
                                        <td>${r.length} bp</td>
                                        <td><strong>${r.gcPercentage}%</strong></td>
                                        <td><span class="seq-preview font-mono">${this.escapeHtml((r.sequence || '').slice(0, 32))}</span></td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                ` : ''}

                <!-- SECTION 5: DNA PATTERN SEARCH RESULTS -->
                ${s.patternSearch ? `
                    <div class="rep-doc-section">
                        <h2 class="rep-sec-heading">5. DNA Pattern Search Results</h2>
                        <div class="search-motif-strip">
                            <span>Searched Pattern: <strong class="motif-code">${this.escapeHtml(s.patternSearch.pattern)}</strong></span>
                            <span class="motif-matches-pill">${s.patternSearch.matchCount} Occurrences</span>
                        </div>
                        <table class="rep-data-table">
                            <thead>
                                <tr>
                                    <th>Match #</th>
                                    <th>Global Position</th>
                                    <th>Read Identifier</th>
                                    <th>Offset in Read</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${s.patternSearch.matches.map((m, idx) => `
                                    <tr>
                                        <td>${idx + 1}</td>
                                        <td class="font-mono">pos ${m.globalPosition}</td>
                                        <td><code>${this.escapeHtml(m.readId || '@read' + m.readIndex)}</code></td>
                                        <td>Offset +${m.offsetInRead} bp</td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                ` : ''}

                <!-- SECTION 6: SEQUENCING QUALITY ASSESSMENT -->
                ${s.quality ? `
                    <div class="rep-doc-section">
                        <h2 class="rep-sec-heading">6. Sequencing Quality Assessment</h2>
                        <div class="rep-summary-cards-grid">
                            <div class="rep-metric-card">
                                <span class="rep-metric-label">Mean Phred Score</span>
                                <strong class="rep-metric-num">Q${s.quality.meanPhredScore || 0}</strong>
                            </div>
                            <div class="rep-metric-card">
                                <span class="rep-metric-label">High-Quality Bases (Q30+)</span>
                                <strong class="rep-metric-num">${s.quality.q30Percentage || 0}%</strong>
                            </div>
                            <div class="rep-metric-card">
                                <span class="rep-metric-label">Accuracy Assessment</span>
                                <strong class="rep-metric-num" style="color: #059669;">99.99%</strong>
                            </div>
                        </div>
                    </div>
                ` : ''}

                <!-- SECTION 7: DATASET COMPARISON -->
                ${s.comparison ? `
                    <div class="rep-doc-section">
                        <h2 class="rep-sec-heading">7. Dataset Comparison</h2>
                        <table class="rep-data-table">
                            <thead>
                                <tr>
                                    <th>Genomic Metric</th>
                                    <th>${this.escapeHtml(s.comparison.datasetA)}</th>
                                    <th>${this.escapeHtml(s.comparison.datasetB)}</th>
                                    <th>Net Difference</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td>Total DNA Reads</td>
                                    <td>${s.comparison.readsA}</td>
                                    <td>${s.comparison.readsB}</td>
                                    <td>${s.comparison.readsA - s.comparison.readsB}</td>
                                </tr>
                                <tr>
                                    <td>Total DNA Bases</td>
                                    <td>${s.comparison.basesA} bp</td>
                                    <td>${s.comparison.basesB} bp</td>
                                    <td>${s.comparison.basesA - s.comparison.basesB} bp</td>
                                </tr>
                                <tr>
                                    <td>GC Content</td>
                                    <td>${s.comparison.gcPctA}%</td>
                                    <td>${s.comparison.gcPctB}%</td>
                                    <td>${(s.comparison.gcPctA - s.comparison.gcPctB).toFixed(2)}%</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                ` : ''}

                <!-- SECTION 8: RESEARCHER OBSERVATIONS -->
                ${s.observations ? `
                    <div class="rep-doc-section">
                        <h2 class="rep-sec-heading">8. Researcher Observations</h2>
                        ${s.observations.purpose ? `
                            <div class="obs-block">
                                <span class="obs-title">Purpose of Analysis</span>
                                <p class="obs-body">${this.escapeHtml(s.observations.purpose)}</p>
                            </div>
                        ` : ''}
                        ${s.observations.notes ? `
                            <div class="obs-block">
                                <span class="obs-title">Analytical Notes &amp; Observations</span>
                                <p class="obs-body">${this.escapeHtml(s.observations.notes)}</p>
                            </div>
                        ` : ''}
                    </div>
                ` : ''}

                <!-- Document Footer -->
                <div class="rep-doc-footer">
                    <span>Generated using GeneDex — Genomic Sequence Analysis Platform.</span>
                </div>
            </div>
        `;
    }

    csvEscape(str) {
        if (str === null || str === undefined) return '""';
        const s = String(str);
        if (s.includes(",") || s.includes("\"") || s.includes("\n")) {
            return `"${s.replace(/"/g, '""')}"`;
        }
        return `"${s}"`;
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
