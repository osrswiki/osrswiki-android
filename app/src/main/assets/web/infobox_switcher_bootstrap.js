window.rswiki = {
    initQtyBox: function() {}
};

window.mw = {
    log: function(message) {
        try {
            if (typeof message === 'object') {
                OsrsWikiBridge.log('Gadget Log: ' + JSON.stringify(message));
            } else {
                OsrsWikiBridge.log('Gadget Log: ' + message);
            }
        } catch (e) { /* Bridge may not be available. */ }
    },
    hook: function(eventName) {
        return {
            add: function(callback) {
                if (eventName === 'wikipage.content') {
                    OsrsWikiBridge.log('Bootstrap: mw.hook.add called. Storing callback.');
                    window.infoboxSwitcherCallback = callback;
                }
            },
            fire: function() {}
        };
    }
};

function initializeInfoboxSwitcher() {
    OsrsWikiBridge.log('Bootstrap: Initializer called.');
    // The only dependency now is that the callback function has been stored.
    if (typeof window.infoboxSwitcherCallback === 'function') {
        OsrsWikiBridge.log('Bootstrap: Dependencies met. Preparing to fire callback.');
        try {
            // The callback no longer needs jQuery. It finds the content itself.
            window.infoboxSwitcherCallback(document);
            OsrsWikiBridge.log('Bootstrap: Callback fired successfully.');

        } catch (e) {
            OsrsWikiBridge.log('Bootstrap ERROR: ' + e.message);
        }
    } else {
        var status = 'callback=' + (typeof window.infoboxSwitcherCallback === 'function');
        OsrsWikiBridge.log('Bootstrap: Dependencies not met (' + status + '). Retrying in 100ms...');
        setTimeout(initializeInfoboxSwitcher, 100);
    }
}
