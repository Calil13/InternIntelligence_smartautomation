const state = {
    user: null,
    mode: 'folder', // folder | starred | category | label | search
    value: 'INBOX',
    page: 0,
    size: 15,
    totalPages: 0,
    emails: [],
    selectedEmailId: null,
    searchQuery: ''
};

const CATEGORIES = ['PRIMARY', 'WORK', 'PROMOTIONS', 'SOCIAL', 'UPDATES', 'SPAM'];

const el = (id) => document.getElementById(id);

function showToast(message) {
    const toast = document.createElement('div');
    toast.className = 'toast';
    toast.textContent = message;
    el('toastContainer').appendChild(toast);
    setTimeout(() => toast.remove(), 3500);
}

async function api(url, options = {}) {
    const config = {
        credentials: 'same-origin',
        headers: {
            'Content-Type': 'application/json',
            ...(options.headers || {})
        },
        ...options
    };
    const response = await fetch(url, config);
    if (!response.ok) {
        let msg = `Request failed (${response.status})`;
        try {
            const err = await response.json();
            msg = err.message || err.error || JSON.stringify(err.exceptionMessage || err);
        } catch (_) {
            // ignore parse error
        }
        throw new Error(msg);
    }
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
        return response.json();
    }
    return response.text();
}

async function bootstrap() {
    const params = new URLSearchParams(window.location.search);
    if (params.get('oauthError')) {
        el('authErrorHint').textContent = 'Google OAuth 2.0 login failed or was cancelled. Check GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET or use Demo Workspace.';
    }
    try {
        const auth = await api('/api/auth/me');
        if (auth && auth.authenticated) {
            state.user = auth;
            showWorkspace();
            await refreshAll();
            return;
        }
        if (auth && auth.demoModeEnabled === false) {
            el('demoLoginBtn').classList.add('hidden');
        }
    } catch (_) {
        // show auth screen
    }
    el('authView').classList.remove('hidden');
    el('appView').classList.add('hidden');
}

function showWorkspace() {
    el('authView').classList.add('hidden');
    el('appView').classList.remove('hidden');
    el('userName').textContent = state.user.fullName || state.user.email;
    const badge = el('authBadge');
    if (state.user.oauthConnected) {
        badge.textContent = 'Google OAuth 2.0';
        badge.className = 'badge badge-success';
        el('syncGmailBtn').disabled = false;
        el('pushGmailLabelsBtn').classList.remove('hidden');
    } else {
        badge.textContent = 'Demo Workspace';
        badge.className = 'badge badge-info';
        el('pushGmailLabelsBtn').classList.add('hidden');
    }
    if (state.user.avatarUrl) {
        el('userAvatar').src = state.user.avatarUrl;
        el('userAvatar').classList.remove('hidden');
    } else {
        el('userAvatar').classList.add('hidden');
    }
}

async function refreshAll() {
    await Promise.all([
        loadSidebarData(),
        loadEmails()
    ]);
}

async function loadSidebarData() {
    const uid = state.user.userId;
    const [sidebar, categorized, labels] = await Promise.all([
        api(`/api/emails/sidebar-summary/${uid}`),
        api(`/api/emails/categorized/${uid}`),
        api(`/api/emails/labels/${uid}`)
    ]);

    el('count-INBOX').textContent = sidebar.unreadInboxCount;
    el('count-STARRED').textContent = sidebar.starredCount;
    el('count-SENT').textContent = sidebar.sentCount;
    el('count-DRAFT').textContent = sidebar.draftCount;
    el('count-TRASH').textContent = sidebar.trashCount;

    const catContainer = el('categoryNavList');
    catContainer.innerHTML = '';
    const counts = categorized.categoryCounts || {};
    CATEGORIES.forEach((cat) => {
        const btn = document.createElement('button');
        btn.className = `nav-item ${state.mode === 'category' && state.value === cat ? 'active' : ''}`;
        btn.dataset.mode = 'category';
        btn.dataset.value = cat;
        btn.innerHTML = `<span>${cat}</span><span class="count-pill">${counts[cat] || 0}</span>`;
        btn.addEventListener('click', () => switchView('category', cat));
        catContainer.appendChild(btn);
    });

    const labelContainer = el('labelNavList');
    labelContainer.innerHTML = '';
    if (!labels || labels.length === 0) {
        labelContainer.innerHTML = '<div class="muted" style="padding:4px 8px;">No labels yet</div>';
    } else {
        labels.forEach((item) => {
            const btn = document.createElement('button');
            btn.className = `nav-item ${state.mode === 'label' && state.value === item.label ? 'active' : ''}`;
            btn.dataset.mode = 'label';
            btn.dataset.value = item.label;
            btn.innerHTML = `<span>#${escapeHtml(item.label)}</span><span class="count-pill">${item.count}</span>`;
            btn.addEventListener('click', () => switchView('label', item.label));
            labelContainer.appendChild(btn);
        });
    }
}

function updateActiveNav() {
    document.querySelectorAll('.sidebar .nav-item').forEach((btn) => {
        const match = btn.dataset.mode === state.mode && btn.dataset.value === state.value;
        btn.classList.toggle('active', match);
    });
}

function switchView(mode, value) {
    state.mode = mode;
    state.value = value;
    state.page = 0;
    updateActiveNav();
    loadEmails();
}

async function loadEmails() {
    const uid = state.user.userId;
    const params = `page=${state.page}&size=${state.size}&sort=createdAt,desc`;
    let url;
    let title;

    if (state.mode === 'folder') {
        url = `/api/emails/folder/${uid}?folder=${state.value}&${params}`;
        title = state.value;
    } else if (state.mode === 'starred') {
        url = `/api/emails/starred/${uid}?${params}`;
        title = 'Starred Emails';
    } else if (state.mode === 'category') {
        url = `/api/emails/category/${uid}?category=${state.value}&${params}`;
        title = `Category: ${state.value}`;
    } else if (state.mode === 'label') {
        url = `/api/emails/label/${uid}?label=${encodeURIComponent(state.value)}&${params}`;
        title = `Label: #${state.value}`;
    } else {
        url = `/api/emails/search/${uid}?q=${encodeURIComponent(state.searchQuery)}&${params}`;
        title = `Search: "${state.searchQuery}"`;
    }

    el('listTitle').textContent = title;
    const pageData = await api(url);
    state.emails = pageData.content || [];
    state.totalPages = pageData.totalPages || 0;

    el('listSubtitle').textContent = `${pageData.totalElements || 0} message(s)`;
    el('pageIndicator').textContent = `Page ${state.totalPages === 0 ? 0 : state.page + 1} of ${state.totalPages}`;
    el('prevPageBtn').disabled = state.page <= 0;
    el('nextPageBtn').disabled = state.page + 1 >= state.totalPages;

    renderEmailList();
}

function renderEmailList() {
    const container = el('emailListContainer');
    container.innerHTML = '';

    if (state.emails.length === 0) {
        container.innerHTML = '<div class="empty-state"><p class="muted">No emails found in this view.</p></div>';
        return;
    }

    state.emails.forEach((email) => {
        const row = document.createElement('div');
        row.className = `email-row ${!email.isRead ? 'unread' : ''} ${state.selectedEmailId === email.id ? 'selected' : ''}`;
        const labelsHtml = (email.labels || []).slice(0, 3)
            .map((l) => `<span class="label-chip">#${escapeHtml(l)}</span>`).join('');
        const snippet = email.summary || (email.body || '').slice(0, 95);

        row.innerHTML = `
            <div class="row-top">
                <span class="row-sender">
                    ${!email.isRead ? '<span class="unread-dot"></span>' : ''}
                    ${escapeHtml(email.senderName || email.senderEmail)}
                </span>
                <div>
                    <span class="muted">${formatDate(email.createdAt)}</span>
                    <button class="star-btn ${email.isStarred ? 'starred' : ''}" data-star="${email.id}" title="Toggle Star">
                        ${email.isStarred ? '★' : '☆'}
                    </button>
                </div>
            </div>
            <div class="row-subject">${escapeHtml(email.subject)}</div>
            <div class="row-snippet">${escapeHtml(snippet)}</div>
            <div class="row-tags">
                <span class="badge badge-cat">${escapeHtml(email.category)}</span>
                ${labelsHtml}
            </div>
        `;

        row.addEventListener('click', (e) => {
            if (e.target.dataset.star) {
                e.stopPropagation();
                toggleStar(email.id);
                return;
            }
            selectEmail(email.id);
        });
        container.appendChild(row);
    });
}

async function selectEmail(emailId) {
    state.selectedEmailId = emailId;
    renderEmailList();
    const [detail, summary] = await Promise.all([
        api(`/api/emails/message/${emailId}`),
        api(`/api/emails/${emailId}/summary`)
    ]);
    renderDetail(detail, summary);
    await loadSidebarData();
    await loadEmails();
}

function renderDetail(detail, summary) {
    el('detailEmpty').classList.add('hidden');
    el('detailContent').classList.remove('hidden');

    el('detailFolderBadge').textContent = detail.folder;
    el('detailCategoryBadge').textContent = detail.category;
    el('detailGmailBadge').classList.toggle('hidden', !detail.gmailMessageId);
    el('detailStarBtn').textContent = detail.isStarred ? '★ Starred' : '☆ Star';
    el('detailReadBtn').textContent = detail.isRead ? 'Mark Unread' : 'Mark Read';

    el('detailSubject').textContent = detail.subject;
    el('detailSender').textContent = detail.senderName || detail.senderEmail;
    el('detailSenderEmail').textContent = `<${detail.senderEmail}>`;
    el('detailDate').textContent = formatDateTime(detail.createdAt);
    el('detailRecipient').textContent = `To: ${detail.recipientName ? detail.recipientName + ' ' : ''}<${detail.recipientEmail}>`;
    el('detailBody').textContent = detail.body || '';

    // Summary
    const pct = summary.compressionPercentage || 0;
    el('compressionBadge').textContent = `${pct}% compressed`;
    el('detailSummaryText').textContent = summary.summary || detail.summary || 'No summary generated yet.';
    el('compressionFill').style.width = `${Math.min(100, Math.max(0, pct))}%`;
    el('wordStats').textContent = `Original: ${summary.originalWordCount || 0} words • Summary: ${summary.summaryWordCount || 0} words`;

    const takeaways = summary.keyTakeaways || [];
    const takeawaysBlock = el('takeawaysBlock');
    const takeawaysList = el('takeawaysList');
    takeawaysList.innerHTML = '';
    if (takeaways.length > 0) {
        takeawaysBlock.classList.remove('hidden');
        takeaways.forEach((item) => {
            const li = document.createElement('li');
            li.textContent = item;
            takeawaysList.appendChild(li);
        });
    } else {
        takeawaysBlock.classList.add('hidden');
    }

    // Labels
    const labelsList = el('detailLabelsList');
    labelsList.innerHTML = '';
    (detail.labels || []).forEach((label) => {
        const chip = document.createElement('span');
        chip.className = 'label-chip';
        chip.innerHTML = `#${escapeHtml(label)} <button title="Remove">×</button>`;
        chip.querySelector('button').addEventListener('click', () => removeLabel(detail.id, label));
        labelsList.appendChild(chip);
    });
}

async function toggleStar(emailId) {
    await api(`/api/emails/${emailId}/toggle-star`, { method: 'PATCH' });
    await refreshAll();
    if (state.selectedEmailId === emailId) {
        const detail = await api(`/api/emails/message/${emailId}`);
        el('detailStarBtn').textContent = detail.isStarred ? '★ Starred' : '☆ Star';
    }
}

async function removeLabel(emailId, label) {
    await api(`/api/emails/${emailId}/labels?label=${encodeURIComponent(label)}`, { method: 'DELETE' });
    showToast(`Removed label #${label}`);
    await selectEmail(emailId);
}

async function loadRules() {
    const rules = await api('/api/emails/rules');
    const list = el('rulesList');
    list.innerHTML = '';
    Object.entries(rules).forEach(([label, keywords]) => {
        const row = document.createElement('div');
        row.className = 'rule-row';
        row.innerHTML = `
            <div>
                <strong>#${escapeHtml(label)}</strong>
                <div class="muted">${escapeHtml((keywords || []).join(', '))}</div>
            </div>
            <button class="btn btn-danger btn-xs">Delete</button>
        `;
        row.querySelector('button').addEventListener('click', async () => {
            await api(`/api/emails/rules/${encodeURIComponent(label)}`, { method: 'DELETE' });
            showToast(`Deleted rule ${label}`);
            await loadRules();
        });
        list.appendChild(row);
    });
}

async function loadMetrics() {
    const [perf, gmail] = await Promise.all([
        api('/api/metrics/performance'),
        api('/api/gmail/status')
    ]);
    const grid = el('metricsGrid');
    const cards = [
        ['Cache Hit Rate', `${perf.cacheHitRatePercent}%`],
        ['Cache Hits / Misses', `${perf.cacheHits} / ${perf.cacheMisses}`],
        ['API Calls Saved', `${perf.gmailApiCallsSaved}`],
        ['Gmail API Calls Made', `${perf.gmailApiCallsMade}`],
        ['Emails Processed (NLP)', `${perf.emailsSmartProcessed}`],
        ['Avg Smart Pipeline Time', `${perf.avgProcessingMs} ms`],
        ['OAuth 2.0 Connected', gmail.oauthConnected ? 'Yes' : 'Demo Mode'],
        ['Incremental historyId', gmail.historyId || 'Not synced yet'],
        ['Last Synced', gmail.lastSyncedAt ? formatDateTime(gmail.lastSyncedAt) : 'Never']
    ];
    grid.innerHTML = cards.map(([label, val]) => `
        <div class="metric-box">
            <div class="metric-value">${escapeHtml(String(val))}</div>
            <div class="metric-label">${escapeHtml(label)}</div>
        </div>
    `).join('');
}

function formatDate(iso) {
    if (!iso) return '';
    return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

function formatDateTime(iso) {
    if (!iso) return '';
    return new Date(iso).toLocaleString();
}

function escapeHtml(str) {
    return String(str ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// Wire Events
document.addEventListener('DOMContentLoaded', () => {
    el('demoLoginBtn').addEventListener('click', async () => {
        try {
            state.user = await api('/api/auth/demo-login', { method: 'POST' });
            showWorkspace();
            await refreshAll();
            showToast('Demo workspace loaded!');
        } catch (err) {
            el('authErrorHint').textContent = err.message;
        }
    });

    el('logoutBtn').addEventListener('click', async () => {
        await api('/api/auth/logout', { method: 'POST' });
        state.user = null;
        state.selectedEmailId = null;
        el('appView').classList.add('hidden');
        el('authView').classList.remove('hidden');
    });

    document.querySelectorAll('.sidebar .nav-item[data-mode]').forEach((btn) => {
        btn.addEventListener('click', () => switchView(btn.dataset.mode, btn.dataset.value));
    });

    el('searchForm').addEventListener('submit', (e) => {
        e.preventDefault();
        const q = el('searchInput').value.trim();
        if (!q) return;
        state.searchQuery = q;
        el('clearSearchBtn').classList.remove('hidden');
        switchView('search', q);
    });

    el('clearSearchBtn').addEventListener('click', () => {
        el('searchInput').value = '';
        el('clearSearchBtn').classList.add('hidden');
        switchView('folder', 'INBOX');
    });

    el('prevPageBtn').addEventListener('click', () => {
        if (state.page > 0) { state.page--; loadEmails(); }
    });
    el('nextPageBtn').addEventListener('click', () => {
        if (state.page + 1 < state.totalPages) { state.page++; loadEmails(); }
    });

    el('syncGmailBtn').addEventListener('click', async () => {
        try {
            const res = await api('/api/gmail/sync', { method: 'POST' });
            showToast(`Synced ${res.fetchedCount} new emails (skipped ${res.skippedDuplicateCount} duplicates, ${res.durationMs}ms)`);
            await refreshAll();
        } catch (err) {
            showToast(err.message);
        }
    });

    el('smartAllBtn').addEventListener('click', async () => {
        const msg = await api(`/api/emails/process-smart-all/${state.user.userId}`, { method: 'POST' });
        showToast(msg);
        await refreshAll();
    });

    el('categorizeAllBtn').addEventListener('click', async () => {
        const msg = await api(`/api/emails/categorize-all/${state.user.userId}`, { method: 'POST' });
        showToast(msg);
        await refreshAll();
    });

    el('labelAllBtn').addEventListener('click', async () => {
        const msg = await api(`/api/emails/label-all/${state.user.userId}`, { method: 'POST' });
        showToast(msg);
        await refreshAll();
    });

    el('summarizeAllBtn').addEventListener('click', async () => {
        const msg = await api(`/api/emails/summarize-all/${state.user.userId}`, { method: 'POST' });
        showToast(msg);
        await refreshAll();
    });

    // Detail actions
    el('detailStarBtn').addEventListener('click', () => state.selectedEmailId && toggleStar(state.selectedEmailId));
    el('detailReadBtn').addEventListener('click', async () => {
        if (!state.selectedEmailId) return;
        await api(`/api/emails/${state.selectedEmailId}/toggle-read`, { method: 'PATCH' });
        await selectEmail(state.selectedEmailId);
    });
    el('detailTrashBtn').addEventListener('click', async () => {
        if (!state.selectedEmailId) return;
        await api(`/api/emails/${state.selectedEmailId}/trash`, { method: 'PATCH' });
        state.selectedEmailId = null;
        el('detailContent').classList.add('hidden');
        el('detailEmpty').classList.remove('hidden');
        showToast('Moved to Trash');
        await refreshAll();
    });
    el('resummarizeBtn').addEventListener('click', async () => {
        if (!state.selectedEmailId) return;
        await api(`/api/emails/${state.selectedEmailId}/summarize`, { method: 'POST' });
        showToast('Summary refreshed');
        await selectEmail(state.selectedEmailId);
    });
    el('detailCategorizeBtn').addEventListener('click', async () => {
        if (!state.selectedEmailId) return;
        await api(`/api/emails/${state.selectedEmailId}/categorize`, { method: 'POST' });
        showToast('Email re-categorized');
        await selectEmail(state.selectedEmailId);
    });
    el('detailAutoLabelBtn').addEventListener('click', async () => {
        if (!state.selectedEmailId) return;
        await api(`/api/emails/${state.selectedEmailId}/label`, { method: 'POST' });
        showToast('Auto-labels updated');
        await selectEmail(state.selectedEmailId);
    });
    el('detailSmartBtn').addEventListener('click', async () => {
        if (!state.selectedEmailId) return;
        await api(`/api/emails/${state.selectedEmailId}/process-smart`, { method: 'POST' });
        showToast('Full smart pipeline executed');
        await selectEmail(state.selectedEmailId);
    });
    el('pushGmailLabelsBtn').addEventListener('click', async () => {
        if (!state.selectedEmailId) return;
        try {
            const msg = await api(`/api/gmail/${state.selectedEmailId}/push-labels`, { method: 'POST' });
            showToast(msg);
        } catch (err) {
            showToast(err.message);
        }
    });
    el('addLabelForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const label = el('newLabelInput').value.trim();
        if (!label || !state.selectedEmailId) return;
        await api(`/api/emails/${state.selectedEmailId}/labels?label=${encodeURIComponent(label)}`, { method: 'POST' });
        el('newLabelInput').value = '';
        showToast(`Added label #${label.toUpperCase()}`);
        await selectEmail(state.selectedEmailId);
    });

    // Modals
    el('composeBtn').addEventListener('click', () => el('composeModal').classList.remove('hidden'));
    el('manageRulesBtn').addEventListener('click', async () => {
        await loadRules();
        el('rulesModal').classList.remove('hidden');
    });
    el('metricsBtn').addEventListener('click', async () => {
        await loadMetrics();
        el('metricsModal').classList.remove('hidden');
    });

    document.querySelectorAll('[data-close]').forEach((btn) => {
        btn.addEventListener('click', () => el(btn.dataset.close).classList.add('hidden'));
    });

    el('composeForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        await api('/api/emails/send', {
            method: 'POST',
            body: JSON.stringify({
                senderId: state.user.userId,
                senderEmail: state.user.email,
                recipientEmail: el('composeTo').value.trim(),
                subject: el('composeSubject').value.trim(),
                body: el('composeBody').value.trim()
            })
        });
        el('composeForm').reset();
        el('composeModal').classList.add('hidden');
        showToast('Email sent & smart-processed!');
        await refreshAll();
    });

    el('saveDraftBtn').addEventListener('click', async () => {
        await api('/api/emails/draft', {
            method: 'POST',
            body: JSON.stringify({
                userId: state.user.userId,
                userEmail: state.user.email,
                recipientEmail: el('composeTo').value.trim(),
                subject: el('composeSubject').value.trim(),
                body: el('composeBody').value.trim()
            })
        });
        el('composeForm').reset();
        el('composeModal').classList.add('hidden');
        showToast('Draft saved!');
        await refreshAll();
    });

    el('addRuleForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const label = el('ruleLabelInput').value.trim();
        const keywords = el('ruleKeywordsInput').value.split(',').map((k) => k.trim()).filter(Boolean);
        await api('/api/emails/rules', {
            method: 'POST',
            body: JSON.stringify({ label, keywords })
        });
        el('addRuleForm').reset();
        showToast(`Saved rule #${label.toUpperCase()}`);
        await loadRules();
    });

    bootstrap();
});
