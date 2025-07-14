/**
 * Modern, vanilla JS implementation of the infobox switcher.
 * Handles multiple, varied switcher formats on the same page.
 */
function initializePage() {
    try {
        const mainInfobox = document.querySelector('.infobox-switch');
        if (!mainInfobox) {
            window.OsrsWikiBridge?.log('Switcher: No main infobox found (.infobox-switch). Aborting initialization.');
            return;
        }
        const mainButtons = mainInfobox.querySelectorAll('.infobox-buttons .button');
        if (mainButtons.length === 0) {
            window.OsrsWikiBridge?.log('Switcher: Main infobox found, but it has no buttons. Aborting initialization.');
            return;
        }
        window.OsrsWikiBridge?.log(`Switcher: Found ${mainButtons.length} main buttons. Initializing...`);

        // (Point 3) Unify legacy switchers to use the main button attributes.
        const legacyTriggers = document.querySelectorAll('.switch-infobox-triggers');
        if (legacyTriggers.length > 0) {
            window.OsrsWikiBridge?.log(`Switcher (Legacy): Found ${legacyTriggers.length} legacy trigger containers.`);
        }
        legacyTriggers.forEach((triggerContainer, i) => {
            const legacyButtons = triggerContainer.querySelectorAll('.trigger.button');
            legacyButtons.forEach(legacyButton => {
                const id = legacyButton.getAttribute('data-id');
                const correspondingMainButton = mainButtons[parseInt(id, 10) - 1];
                if (correspondingMainButton) {
                    const index = correspondingMainButton.getAttribute('data-switch-index');
                    const anchor = correspondingMainButton.getAttribute('data-switch-anchor');
                    legacyButton.setAttribute('data-switch-index', index);
                    legacyButton.setAttribute('data-switch-anchor', anchor);
                    window.OsrsWikiBridge?.log(`Switcher (Legacy): Mapped legacy button (data-id: ${id}) to main button (data-switch-index: ${index})`);
                }
            });
            const parentBox = triggerContainer.closest('.switch-infobox');
            if (parentBox) {
                const loadingButton = parentBox.querySelector('.loading-button');
                if (loadingButton) {
                    loadingButton.style.display = 'none';
                    window.OsrsWikiBridge?.log(`Switcher (Legacy): Hid loading button for container ${i+1}.`);
                }
                triggerContainer.style.display = 'flex';
                triggerContainer.style.justifyContent = 'center';
                triggerContainer.style.gap = '5px';
            }
        });

        // (Point 2) Attach a single, global click handler.
        document.body.addEventListener('click', (event) => {
            const button = event.target.closest('.button');
            if (button && button.hasAttribute('data-switch-index')) {
                window.OsrsWikiBridge?.log(`Switcher (Click): Global handler fired for button with index: ${button.getAttribute('data-switch-index')}`);
                switchAllContent(button);
            }
        });

        // Perform initial switch.
        if (mainButtons.length > 0) {
            window.OsrsWikiBridge?.log('Switcher: Performing initial switch using the first main button.');
            switchAllContent(mainButtons[0]);
        }

    } catch (e) {
        window.OsrsWikiBridge?.log(`Switcher CRITICAL ERROR in initializePage: ${e.message}`);
    }
}

function switchAllContent(clickedButton) {
    // (Point 1) Use the index, not the anchor text.
    const switchIndex = clickedButton.getAttribute('data-switch-index');
    if (!switchIndex) {
        window.OsrsWikiBridge?.log('Switcher ERROR: Clicked button is missing data-switch-index. Cannot switch.');
        return;
    }
    window.OsrsWikiBridge?.log(`Switcher: Starting global switch for index: ${switchIndex}`);

    // Part 1: Update all button states.
    document.querySelectorAll('.infobox-buttons, .switch-infobox-triggers').forEach(container => {
        container.querySelectorAll('.button, .trigger').forEach(btn => btn.classList.remove('button-selected'));
        const btnToSelect = container.querySelector(`[data-switch-index="${switchIndex}"]`);
        if (btnToSelect) btnToSelect.classList.add('button-selected');
    });
    window.OsrsWikiBridge?.log(`Switcher: Updated all button highlights for index ${switchIndex}.`);

    // (Point 5) Part 2: Populate placeholders in all infoboxes, using their specific data source.
    const infoboxesToUpdate = document.querySelectorAll('.infobox-switch[data-resource-class]');
    window.OsrsWikiBridge?.log(`Switcher (Data Sourcing): Found ${infoboxesToUpdate.length} placeholder-based infoboxes to update.`);
    infoboxesToUpdate.forEach(infobox => {
        const resourceClass = infobox.getAttribute('data-resource-class');
        if (resourceClass) {
            // THE FIX IS HERE: Use resourceClass directly, without prepending a dot.
            const resources = document.querySelector(resourceClass);
            if (resources) {
                window.OsrsWikiBridge?.log(`Switcher (Data Sourcing): For infobox, found data source div '${resourceClass}'. Populating placeholders...`);
                populatePlaceholders(infobox, resources, switchIndex);
            } else {
                window.OsrsWikiBridge?.log(`Switcher WARNING (Data Sourcing): Infobox requested resource '${resourceClass}', but it was not found in the DOM.`);
            }
        }
    });

    // Part 3: Update all synced-switch image galleries.
    const syncedSwitches = document.querySelectorAll('.rsw-synced-switch');
    if (syncedSwitches.length > 0) {
        window.OsrsWikiBridge?.log(`Switcher (Sync): Found ${syncedSwitches.length} synced galleries to update.`);
    }
    syncedSwitches.forEach(syncedSwitch => {
        const allItems = syncedSwitch.querySelectorAll('.rsw-synced-switch-item');
        allItems.forEach(item => item.classList.remove('showing'));

        const itemIndexToShow = parseInt(switchIndex, 10);
        if (allItems.length > itemIndexToShow) {
            const itemToShow = allItems[itemIndexToShow];
            if (itemToShow) {
                const originalHtml = itemToShow.innerHTML;
                const fixedHtml = fixUrls(originalHtml);
                itemToShow.innerHTML = fixedHtml;
                itemToShow.classList.add('showing');
                if(originalHtml !== fixedHtml) {
                    window.OsrsWikiBridge?.log(`Switcher (Sync): URL fixed in synced gallery.`);
                }
            }
        }
    });
}

function populatePlaceholders(infobox, resources, switchIndex) {
    const placeholders = infobox.querySelectorAll('[data-attr-param]');
    window.OsrsWikiBridge?.log(`Switcher (Populate): Found ${placeholders.length} placeholders in an infobox.`);
    placeholders.forEach(placeholder => {
        const paramName = placeholder.getAttribute('data-attr-param');
        if (!paramName) return;

        const resourceGroup = resources.querySelector(`[data-attr-param="${paramName}"]`);
        if (resourceGroup) {
            let newContentElement = resourceGroup.querySelector(`[data-attr-index="${switchIndex}"]`);
            if (!newContentElement) {
                newContentElement = resourceGroup.querySelector('[data-attr-index="0"]');
            }
            if (newContentElement) {
                const originalContent = newContentElement.innerHTML;
                const fixedContent = fixUrls(originalContent);
                placeholder.innerHTML = fixedContent;

                if (originalContent !== fixedContent) {
                    window.OsrsWikiBridge?.log(`Switcher (Populate FIX): URLs fixed for param '${paramName}'.`);
                } else {
                     window.OsrsWikiBridge?.log(`Switcher (Populate): Set content for param '${paramName}'. No URL fix needed.`);
                }
            }
        } else {
             window.OsrsWikiBridge?.log(`Switcher WARNING (Populate): Could not find resource group for param '${paramName}'.`);
        }
    });
}

const remoteWikiDomain = "https://oldschool.runescape.wiki";

const fixUrls = (htmlString) => {
    if (!htmlString) return "";
    // Regex to find src="/images/..." or srcset="/images/..." and prepend the domain.
    const regex = /(src|srcset)\s*=\s*['"](\/images\/[^'"]*)['"]/g;
    return htmlString.replace(regex, `$1="${remoteWikiDomain}$2"`);
};

// This hook is called by the bootstrap script after the page is loaded.
mw.hook('wikipage.content').add(initializePage);
