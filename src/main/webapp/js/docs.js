/* Smart Retry docs page interactions */
(function () {
    'use strict';

    /* ---- Tab switching ---- */
    function initTabs() {
        var tabBar = document.querySelector('.sr-tabs');
        if (!tabBar) { return; }
        var buttons = tabBar.querySelectorAll('.sr-tab-btn');
        var panels  = document.querySelectorAll('.sr-tab-panel');

        function activateTab(id) {
            buttons.forEach(function (btn) {
                btn.classList.toggle('sr-tab-active', btn.dataset.tab === id);
            });
            panels.forEach(function (panel) {
                panel.classList.toggle('sr-tab-visible', panel.id === id);
            });
        }

        buttons.forEach(function (btn) {
            btn.addEventListener('click', function () { activateTab(btn.dataset.tab); });
        });

        /* Resolve which tab to activate on load, and optionally scroll to an anchor. */
        var hash   = window.location.hash.replace('#', '');
        var target = hash ? document.getElementById(hash) : null;
        var initial;
        if (target) {
            /* Check if the hash is itself a tab panel id */
            var isTabPanel = Array.from(panels).some(function (p) { return p.id === hash; });
            if (isTabPanel) {
                initial = hash;
            } else {
                /* Find the closest ancestor tab panel */
                var parentPanel = target.closest('.sr-tab-panel');
                initial = parentPanel ? parentPanel.id : (buttons[0] ? buttons[0].dataset.tab : null);
            }
        } else {
            initial = buttons[0] ? buttons[0].dataset.tab : null;
        }
        if (initial) { activateTab(initial); }
        /* Scroll to the anchor element after tab is shown */
        if (target && initial) {
            setTimeout(function () { target.scrollIntoView({ block: 'start' }); }, 0);
        }
    }

    /* ---- Master-Detail ---- */
    function initMasterDetail() {
        var container = document.querySelector('.sr-master-detail');
        if (!container) { return; }
        var masterItems  = container.querySelectorAll('.sr-master-item');
        var detailPanels = container.querySelectorAll('.sr-detail-panel');

        function activateItem(id) {
            masterItems.forEach(function (item) {
                item.classList.toggle('sr-master-active', item.dataset.detail === id);
            });
            detailPanels.forEach(function (panel) {
                panel.classList.toggle('sr-detail-visible', panel.id === id);
            });
        }

        masterItems.forEach(function (item) {
            item.addEventListener('click', function () { activateItem(item.dataset.detail); });
        });

        if (masterItems.length > 0) {
            activateItem(masterItems[0].dataset.detail);
        }
    }

    /* ---- Rules filter ---- */
    function initRulesFilter() {
        var select = document.getElementById('sr-rules-filter');
        var table  = document.querySelector('.sr-rules-table');
        if (!select || !table) { return; }

        function applyFilter() {
            var value = select.value;
            table.querySelectorAll('tbody tr').forEach(function (row) {
                var match = !value || row.dataset.failureType === value;
                row.classList.toggle('sr-row-hidden', !match);
            });
        }

        select.addEventListener('change', applyFilter);
        applyFilter();
    }

    /* ---- Cross-tab links (detail → rules with pre-set filter) ---- */
    function initCrossTabLinks() {
        document.querySelectorAll('a.sr-tab-link[data-tab]').forEach(function (link) {
            link.addEventListener('click', function (e) {
                e.preventDefault();
                var targetTab = link.dataset.tab;
                var filterValue = link.dataset.filter || '';

                /* switch tab */
                document.querySelectorAll('.sr-tab-btn').forEach(function (btn) {
                    btn.classList.toggle('sr-tab-active', btn.dataset.tab === targetTab);
                });
                document.querySelectorAll('.sr-tab-panel').forEach(function (panel) {
                    panel.classList.toggle('sr-tab-visible', panel.id === targetTab);
                });

                /* set filter */
                var select = document.getElementById('sr-rules-filter');
                if (select && filterValue) {
                    select.value = filterValue;
                    select.dispatchEvent(new Event('change'));
                }
            });
        });
    }

    /* ---- Clear rules filter when arriving via a rule-* anchor ---- */
    function clearFilterForRuleAnchor() {
        var hash = window.location.hash.replace('#', '');
        if (!hash.startsWith('rule-')) { return; }
        var select = document.getElementById('sr-rules-filter');
        if (!select) { return; }
        select.value = '';
        select.dispatchEvent(new Event('change'));
    }

    /* ---- Boot ---- */
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            initTabs(); initMasterDetail(); initRulesFilter(); clearFilterForRuleAnchor(); initCrossTabLinks();
        });
    } else {
        initTabs(); initMasterDetail(); initRulesFilter(); clearFilterForRuleAnchor(); initCrossTabLinks();
    }
}());

