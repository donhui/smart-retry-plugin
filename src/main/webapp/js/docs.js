/* Smart Retry docs page interactions */
(function () {
    'use strict';

    var MAX_TAB_INIT_ATTEMPTS = 20;
    var isActivatingTabFromHash = false;

    function normalizeHash(hash) {
        if (hash.startsWith('failure-type-')) {
            return 'detail-' + hash;
        }
        return hash;
    }

    function getTabPanes() {
        return Array.from(document.querySelectorAll('.jenkins-tab-pane'));
    }

    function getTabs() {
        return Array.from(document.querySelectorAll('#main-panel > .tabBar .tab'));
    }

    function getTabAnchorId(tabPane) {
        if (!tabPane) { return null; }
        var anchor = tabPane.querySelector('.sr-tab-anchor');
        return anchor ? anchor.id : null;
    }

    function activateTabPane(tabPane) {
        if (!tabPane) { return; }
        var panes = getTabPanes();
        var tabs = getTabs();
        var index = panes.indexOf(tabPane);
        if (index < 0 || !tabs[index]) { return; }
        tabs[index].dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    }

    function activateTabForTarget(target) {
        if (!target) { return; }
        var tabPane = target.closest('.jenkins-tab-pane');
        if (tabPane) {
            activateTabPane(tabPane);
        }
    }

    function scrollToTarget(target) {
        if (!target) { return; }
        setTimeout(function () {
            target.scrollIntoView({ block: 'start' });
        }, 0);
    }

    function activateHashTarget() {
        var hash = normalizeHash(window.location.hash.replace('#', ''));
        var target = hash ? document.getElementById(hash) : null;
        if (!target) { return true; }

        isActivatingTabFromHash = true;
        activateTabForTarget(target);
        isActivatingTabFromHash = false;
        scrollToTarget(target);
        return true;
    }

    function initTabAnchors(attempt) {
        var currentAttempt = attempt || 0;
        var hash = window.location.hash.replace('#', '');

        if (!hash) { return; }

        if (activateHashTarget() && (getTabs().length > 0 || getTabPanes().length === 0)) {
            return;
        }

        if (currentAttempt >= MAX_TAB_INIT_ATTEMPTS) {
            return;
        }

        setTimeout(function () {
            initTabAnchors(currentAttempt + 1);
        }, 50);
    }

    function initTabLinks() {
        document.querySelectorAll('a.sr-tab-link[data-tab-target]').forEach(function (link) {
            link.addEventListener('click', function (e) {
                e.preventDefault();

                var targetId = link.dataset.tabTarget;
                var target = targetId ? document.getElementById(targetId) : null;
                var filterValue = link.dataset.filter || '';

                if (target) {
                    activateTabForTarget(target);
                }

                var select = document.getElementById('sr-rules-filter');
                if (select && filterValue) {
                    select.value = filterValue;
                    select.dispatchEvent(new Event('change'));
                }

                var targetTabPane = target ? target.closest('.jenkins-tab-pane') : null;
                var targetAnchorId = getTabAnchorId(targetTabPane);
                if (targetAnchorId && window.location.hash !== '#' + targetAnchorId) {
                    history.replaceState(null, '', '#' + targetAnchorId);
                }

                scrollToTarget(target);
            });
        });

        window.addEventListener('hashchange', function () {
            initTabAnchors(0);
        });
    }

    function initTabUrlSync(attempt) {
        var currentAttempt = attempt || 0;
        var panes = getTabPanes();
        var tabs = getTabs();

        if (panes.length === 0 || tabs.length === 0 || panes.length !== tabs.length) {
            if (currentAttempt >= MAX_TAB_INIT_ATTEMPTS) {
                return;
            }
            setTimeout(function () {
                initTabUrlSync(currentAttempt + 1);
            }, 50);
            return;
        }

        tabs.forEach(function (tab, index) {
            if (tab.dataset.srUrlSyncBound === 'true') {
                return;
            }
            tab.dataset.srUrlSyncBound = 'true';
            tab.addEventListener('click', function () {
                if (isActivatingTabFromHash) {
                    return;
                }
                var anchorId = getTabAnchorId(panes[index]);
                if (!anchorId) { return; }
                if (window.location.hash !== '#' + anchorId) {
                    history.replaceState(null, '', '#' + anchorId);
                }
            });
        });
    }

    /* ---- Master-Detail ---- */
    function initMasterDetail() {
        var container = document.querySelector('.sr-master-detail');
        if (!container) { return; }
        var masterItems  = container.querySelectorAll('.sr-master-item');
        var detailPanels = container.querySelectorAll('.sr-detail-panel');
        var isUpdatingFromHash = false;

        function activateItem(id, options) {
            var settings = options || {};
            masterItems.forEach(function (item) {
                item.classList.toggle('sr-master-active', item.dataset.detail === id);
            });
            detailPanels.forEach(function (panel) {
                panel.classList.toggle('sr-detail-visible', panel.id === id);
            });

            if (settings.updateHash === false) {
                return;
            }

            if (isUpdatingFromHash) {
                return;
            }

            if (window.location.hash !== '#' + id) {
                history.replaceState(null, '', '#' + id);
            }
        }

        function activateItemForHash() {
            var hash = normalizeHash(window.location.hash.replace('#', ''));
            if (!hash) { return; }

            isUpdatingFromHash = true;
            if (hash.startsWith('detail-failure-type-')) {
                activateItem(hash, { updateHash: false });
                isUpdatingFromHash = false;
                return;
            }
            isUpdatingFromHash = false;
        }

        masterItems.forEach(function (item) {
            item.addEventListener('click', function () { activateItem(item.dataset.detail); });
        });

        if (masterItems.length > 0) {
            activateItem(masterItems[0].dataset.detail, { updateHash: false });
        }

        activateItemForHash();

        window.addEventListener('hashchange', function () {
            activateItemForHash();
        });
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
            initMasterDetail(); initRulesFilter(); clearFilterForRuleAnchor(); initTabLinks(); initTabUrlSync(0); initTabAnchors();
        });
    } else {
        initMasterDetail(); initRulesFilter(); clearFilterForRuleAnchor(); initTabLinks(); initTabUrlSync(0); initTabAnchors();
    }
}());
