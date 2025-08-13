(function ($, mw, rs) {

    'use strict';
	
	function createOOUIWindowManager() {
		if (window.OOUIWindowManager == undefined) {
	        window.OOUIWindowManager = new OO.ui.WindowManager();
	    	$( 'body' ).append( window.OOUIWindowManager.$element );
		}
    	return window.OOUIWindowManager;
	}

    /**
     * Reusable functions
     *
     * These are available under the `rswiki` global variable.
     * @example `rswiki.addCommas`
     * The alias `rs` is also available in place of `rswiki`.
     */
    var util = {
        /**
         * Formats a number string with commas.
         *
         * @todo fully replace this with Number.protoype.toLocaleString
         *       > 123456.78.toLocaleString('en')
         *
         * @example 123456.78 -> 123,456.78
         *
         * @param num {Number|String} The number to format.
         * @return {String} The formated number.
         */
        addCommas: function (num) {
            if (typeof num === 'number') {
                return num.toLocaleString('en');
            }

            // @todo chuck this into parseFloat first and then to toLocaleString?
            num += '';

            var x = num.split('.'),
                x1 = x[0],
                x2 = x.length > 1 ?
                    '.' + x[1] :
                    '',
                rgx = /(\d+)(\d{3})/;

            while (rgx.test(x1)) {
                x1 = x1.replace(rgx, '$1,$2');
            }

            return x1 + x2;
        },

        /**
         * Extracts parameter-argument pairs from templates.
         *
         * @todo Fix for multiple templates
         *
         * @param tpl {String} Template to extract data from.
         * @param text {String} Text to look for template in.
         * @return {Object} Object containing parameter-argument pairs
         */
        parseTemplate: function (tpl, text) {
            var rgx = new RegExp(
                    '\\{\\{(template:)?' + tpl.replace(/[ _]/g, '[ _]') + '\\s*(\\||\\}\\})',
                    'i'
                ),
                exec = rgx.exec(text),
                // splits template into |arg=param or |param
                paramRgx = /\|(.*?(\{\{.+?\}\})?)(?=\s*\||$)/g,
                args = {},
                params,
                i,
                j;

            // happens if the template is not found in the text
            if (exec === null) {
                return false;
            }

            text = text.substring(exec.index + 2);

            // used to account for nested templates
            j = 0;

            // this purposefully doesn't use regex
            // as it became very difficult to make it work properly
            for (i = 0; i < text.length; i += 1) {
                if (text[i] === '{') {
                    j += 1;
                } else if (text[i] === '}') {
                    if (j > 0) {
                        j -= 1;
                    } else {
                        break;
                    }
                }
            }

            // cut off where the template ends
            text = text.substring(0, i);
            // remove template name as we're not interested in it past this point
            text = text.substring(text.indexOf('|')).trim();
            // separate params and args into an array
            params = text.match(paramRgx);

            // handle no params/args
            if (params !== null) {
                // used as an index for unnamed params
                i = 1;

                params.forEach(function (el) {
                    var str = el.trim().substring(1),
                        eq = str.indexOf('='),
                        tpl = str.indexOf('{{'),
                        param,
                        val;

                    // checks if the equals is after opening a template
                    // to catch unnamed args that have templates with named args as params
                    if (eq > -1 && (tpl === -1 || eq < tpl)) {
                        param = str.substring(0, eq).trim().toLowerCase();
                        val = str.substring(eq + 1).trim();
                    } else {
                        param = i;
                        val = str.trim();
                        i += 1;
                    }

                    args[param] = val;
                });
            }

            return args;
        },

        /**
         * Alternate version of `parseTemplate` for parsing exchange module data.
         *
         * @notes Only works for key-value pairs
         *
         * @param text {String} Text to parse.
         * @return {Object} Object containing parameter-argument pairs.
         */
        parseExchangeModule: function (text) {

                // strip down to just key-value pairs
            var str = text
                    .replace(/return\s*\{/, '')
                    .replace(/\}\s*$/, '')
                    .trim(),
                rgx = /\s*(.*?\s*=\s*(?:\{[\s\S]*?\}|.*?))(?=,?\n|$)/g,
                args = {},
                params = str.match(rgx);

            if (params !== null) {
                params.forEach(function (elem) {
                    var str = elem.trim(),
                        eq = str.indexOf('='),
                        param = str.substring(0, eq).trim().toLowerCase(),
                        val = str.substring(eq + 1).trim();

                    args[param] = val;
                });
            }

            return args;
        },

        /**
         * Helper for making cross domain requests to RuneScape's APIs.
         * If the APIs ever enable CORS, we can ditch this and do the lookup directly.
         *
         * @param url {string} The URL to look up
         * @param via {string} One of 'anyorigin', 'whateverorigin' or 'crossorigin'. Defaults to 'anyorigin'.
         *
         * @return {string} The URLto use to make the API request.
         */
        crossDomain: function (url, via) {
            switch (via) {
            case 'crossorigin':
                url = 'http://crossorigin.me/' + url;
                break;

            case 'whateverorigin':
                url = 'http://whateverorigin.org/get?url=' + encodeURIComponent( url ) + '&callback=?';
                break;

            case 'anyorigin':
            default:
                url = 'http://anyorigin.com/go/?url=' + encodeURIComponent( url ) + '&callback=?';
                break;
            }

            return url;
        },
        /**
         * Returns the OOUI window manager as a Promise. Will load OOUI (core and windows) and create the manager, if necessary.
         * 
         * @return {jQuery.Deferred} A jQuery Promise where window.OOUIWindowManager is will be defined
         * Chaining a .then will pass OOUIWindowManager to the function argument
         */
        withOOUIWindowManager: function() {
        	return mw.loader.using(['oojs-ui-core','oojs-ui-windows']).then(createOOUIWindowManager);
        },
        
        /**
         * Helper for creating and initializing a new OOUI Dialog object
         * After init, the window is added to the global Window Manager.
         * 
         * Will automatically load OOUI (core and windows) and create the window manager, if necessary. window.OOUIWindowManager will be defined within this.
         * 
         * @author JaydenKieran
         * 
         * @param name {string} The symbolic name of the window
         * @param title {string} The title of the window
         * @param winconfig {object} Object containing params for the OO.ui.Dialog obj
         * @param init {function} Function to be called to initialise the object
         * @param openNow {boolean} Whether the window should be opened instantly
         * @param autoClose {boolean} Autoclose when the user clicks outside of the modal
         *
         * @return {jquery.Deferred} The jQuery Promise returned by mw.loader.using
         * Chaining a .then will pass the created {OO.ui.Dialog} object as the function argument
         */
        createOOUIWindow: function(name, title, winconfig, init, openNow, autoClose) {
        	return mw.loader.using(['oojs-ui-core','oojs-ui-windows']).then(function(){
		    	createOOUIWindowManager();
		    	winconfig = winconfig || {};
		    	
				function myModal( config ) {
					myModal.super.call( this, config );
				}
				OO.inheritClass( myModal, OO.ui.Dialog ); 
				
				myModal.static.name = name;
				myModal.static.title = title;
				
				myModal.prototype.initialize = function () {
					myModal.super.prototype.initialize.call( this );
					init(this);
				}
				
				var modal = new myModal(winconfig);
				
				console.debug('Adding ' + myModal.static.name + ' to WindowManager');
				window.OOUIWindowManager.addWindows( [ modal ] );
				
				if (openNow) {
					window.OOUIWindowManager.openWindow(name);
				}
				
				if (autoClose) {
					$(document).on('click', function (e) {
						if (modal && modal.isVisible() && e.target.classList.contains('oo-ui-window-active')) {
							modal.close();
						};
					});
				}
				
				return modal;
        	});
        },
        
        /**
         * Helper for checking if the user's browser supports desktop notifications
         * @author JaydenKieran
         */
        canSendBrowserNotifs: function () {
		    if (!("Notification" in window)) {
		        console.warn("This browser does not support desktop notifications");
		        return false;
		    } else {
		        return true;
		    }
        },
        
        /**
         * Send a desktop/browser notification to a user, requires the page to be open
         * @author JaydenKieran
         * 
         * @param https://developer.mozilla.org/en-US/docs/Web/API/Notification/Notification
         * 
         * @return Notification object or null
         */
        sendBrowserNotif: function (title, opts) {
        	if (rs.canSendBrowserNotifs == false) {
        		return null;
        	}
			Notification.requestPermission().then(function(result) {
			    if (result === "granted") {
			    	console.debug('Firing desktop notification');
			    	var notif = new Notification(title, opts);
			    	notif.onclick = function(e) {
			    		window.focus();
			    	}
			    	return notif;
			    } else {
			        return null;
			    }
			});
        },
        
        /**
         * Check if the browser has support for localStorage
         * @author JaydenKieran
         * 
         * @return boolean
         **/
        hasLocalStorage: function() {
		    try {
		      localStorage.setItem('test', 'test')
		      localStorage.removeItem('test')
		      return true
		    } catch (e) {
		      return false
		    }
        },
        
        /**
         * Check if user is using dark mode
         * @author JaydenKieran
         * 
         * @return boolean
         **/
        isUsingDarkmode: function() {
        	if (typeof $.cookie('darkmode') === 'undefined') {
        		return false
        	} else {
        		return $.cookie('darkmode') === 'true'
        	}
        },
        
        /**
         * Gets a query string parameter from given URL or current href
         * @author JaydenKieran
         * 
         * @return string or null
         **/
         qsp: function(name, url) {
		    if (!url) url = window.location.href;
		    name = name.replace(/[\[\]]/g, '\\$&');
		    var regex = new RegExp('[?&]' + name + '(=([^&#]*)|&|#|$)'),
		        results = regex.exec(url);
		    if (!results) return null;
		    if (!results[2]) return '';
		    return decodeURIComponent(results[2].replace(/\+/g, ' '));
    	},

        /**
         * Get the URL for a file on the wiki, aganst the endpoint that is actually cached and fast.
         * Should probably not be used for images we expect to change frequently.
         * @author cookmeplox
         * 
         * @return string
         **/
        getFileURLCached: function(filename) {
            var base = window.location.origin;
            filename = filename.replace(/ /g,"_");
            filename = filename.replace(/\(/g, '%28').replace(/\)/g, '%29');
            var cb = '48781';
            return base + '/images/' + filename + '?' + cb;
        },
    	
    	isUsingStickyHeader: function() {
    		return ($('body').hasClass('wgl-stickyheader'))
    	}
    };

    function init() {
        $.extend(rs, util, {});
        // add rs as a global alias
        window.rs = rs;
    }

	init();

}(this.jQuery, this.mediaWiki, this.rswiki = this.rswiki || {}));