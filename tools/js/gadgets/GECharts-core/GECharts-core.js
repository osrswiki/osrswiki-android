/** <nowiki>
 * Grand Exchange Charts
 * Displays price data of item(s) in a chart
 *
 * Highstock docs <https://api.highcharts.com/highstock/>
 * Highstock change log <https://www.highcharts.com/blog/changelog/#highstock>
 *
 * @author Joeytje50
 * @author Cqm
 * @author JaydenKieran
 *
 * @todo move Highcharts to a core ResourceLoader module
 *
 * @todo use a consistent variable for the chart id
 *       currently it's one of c, i or id
 * @todo remove script URLs (javascript:func) in favour of onclick events
 *       may require attaching the events after the some parts have loaded
 * @todo fix averages
 */

/*global jQuery, mediaWiki, rswiki, Highcharts, wgPageName, wgTitle, wgNamespaceNumber */

'use strict';

/**
 * Cache mw.config variables
 */
var conf = mw.config.get([
        'wgNamespaceNumber',
        'wgPageName',
        'wgTitle',
        'wgSiteName'
    ]),

    // Are we on OSRS? Impacts selectors and volume labels / multipliers
    isOSRS = conf.wgSiteName == "Old School RuneScape Wiki",

    // Volume label depends on which wiki we're on
    volumeLabel = isOSRS ? "Daily volume" : "7-day volume",
    gameVersion = isOSRS ? 'osrs' : 'rs',

    /**
     * <doc>
     *
     * @todo replace `_GEC` wih this
     */
    gec = {},

    // @todo document each of these
    _GEC = {
        AIQueue: [],
        AILoaded: [],
        AIData: [],
        addedData: [],
        average: parseInt((location.hash.match(/#a=([^#]*)/) || [])[1], 10) || '',
        urlCache: {}
    },

    /**
     * Startup methods
     */
    self = {
        /**
         * Loads and implements any required dependencies
         */
        deps: function () {
            if (!mw.loader.getState('rs.highcharts')) {
                mw.loader.implement(
                    'rs.highcharts',
                    ['https://chisel.weirdgloop.org/static/highcharts-stock.js'],
                    {}, {}
                );
            }

            mw.loader.using(['mediawiki.util', 'mediawiki.api', 'rs.highcharts', 'oojs-ui-core', 'oojs-ui.styles.icons-media'], self.init);
        },

        /**
         * Initial loading function
         */
        init: function (req) {
            window.Highcharts = req('rs.highcharts');
            (function () {
                var newhash = location.hash
                    .replace(/\.([0-9a-f]{2})/gi, function (_, first) {
                        return String.fromCharCode(parseInt(first, 16));
                    })
                    .replace(/ /g, '_');
                if (newhash && newhash.match(/#[aiz]=/)) {
                    location.hash = newhash;
                }
            }());

            $('.GEdatachart').attr('id', function (c) {
                return 'GEdatachart' + c;
            });
            $('.GEdataprices').attr('id', function (c) {
                return 'GEdataprices' + c;
            });
            $('.GEChartBox').each(function (c) {
                $(this).find('.GEChartItems').attr('id', 'GEChartItems' + c);
            });

            Highcharts.setOptions({
                lang: {
                    // @todo can this be done with CSS?
                    resetZoom: null,
                    numericSymbols: ['K', 'M', 'B', 'T', 'Qd', 'Qt'],
                }
            });

            // globals to maintain javascript hrefs
            window._GEC = _GEC;
            window.popupChart = popupChart;
            window.addItem = chart.addItem;
            window.removeGraphItem = chart.removeItem;

            self.buildPopup();
            self.setupCharts();
        },

        /**
         * <doc>
         */
        makeOOUI: function (c) {
            var averageRangeInput, addItemInput, submitButton, resetButton, fieldset, permalink;
            averageRangeInput = new OO.ui.NumberInputWidget({
                min: 1,
                value: 30,
                id: 'average' + c
            });
            averageRangeInput.$element.data('ooui-elem', averageRangeInput);
            addItemInput = new OO.ui.TextInputWidget({
                id: 'extraItem' + c
            });
            addItemInput.$element.data('ooui-elem', addItemInput);
            submitButton = new OO.ui.ButtonInputWidget({
                label: 'Submit',
                flags: ['primary', 'progressive']
            });
            resetButton = new OO.ui.ButtonInputWidget({
                label: 'Reset'
            });
            permalink = new OO.ui.ButtonInputWidget({
                label: 'Permanent link',
                title: 'Permanent link to the current chart settings and items. Right click to copy the url.',
                id: 'GEPermLink' + c
            });
            permalink.$element.data('ooui-elem', permalink);
            permalink.setData('/w/RuneScape:Grand_Exchange_Market_Watch/Chart');
            permalink.on('click', function () {
                window.open(permalink.getData(), '_blank');
            });

            averageRangeInput.on('enter', function () {
                addItem(c);
            });
            addItemInput.on('enter', function () {
                addItem(c);
            });
            submitButton.on('click', function () {
                addItem(c);
            });

            resetButton.on('click', function () {
                addItemInput.setValue('');
                averageRangeInput.setValue(30);
            });

            fieldset = new OO.ui.FieldsetLayout();
            fieldset.addItems([
                new OO.ui.FieldLayout(averageRangeInput, {label: 'Average (days)'}),
                new OO.ui.FieldLayout(addItemInput, {label: 'Add new item'})
            ]);
            fieldset.$element.append(submitButton.$element).append(resetButton.$element).append(permalink.$element);

            fieldset.$element.css('max-width', '320px');
            return fieldset.$element;
        },
        buildPopup: function () {
            var close;
            close = new OO.ui.ButtonWidget({
                icon: 'close'
            });
            close.on('click', function () {
                popupChart(false);
            });


            $('body').append(
                $('<div>')
                    .attr('id', 'GEchartpopup')
                    .css('display', 'none')
                    .append(
                        $('<div>')
                            .attr('id', 'closepopup')
                            .append(close.$element),
                        self.makeOOUI('popup'),
                        $('<div>')
                            .attr('id', 'addedItemspopup'),
                        $('<div>')
                            .attr('id', 'GEpopupchart')
                    )
            );
        },

        /**
         * <doc>
         */
        setupCharts: function () {

            $('div.GEdatachart').each(function (c) {

                var $dataPrices = $('#GEdataprices' + c),
                    $dataChart = $('#GEdatachart' + c),
                    dataItem = $dataPrices.attr('data-item'),
                    isSmall = $dataChart.hasClass('smallChart'),
                    isMedium = $dataChart.hasClass('mediumChart'),
                    isIndexChart = /index/i.test(dataItem),
                    selector = isOSRS ? '.infobox *, .infobar *, .infobox-switch-resources.infobox-resources-Infobox_Item *' : '.infobox *, .infobar *, .rsw-infobox *, .infobox-switch-resources.infobox-resources-Infobox_Item *',
                    isInfobox = $dataPrices.is(selector),
                    itemName = dataItem || conf.wgTitle.split('/')[0],
                    dataList,
                    yAxis,
                    zoom;


                if (!$dataPrices.length) {
                    return;
                }

                // setting up the form and chart elements
                if (!isSmall && !isMedium) {
                    $dataChart.before(
                        self.makeOOUI(c),
                        $('<div>')
                            .attr('id', 'addedItems' + c)
                    );
                }

                getData(c, isSmall, isMedium, undefined, function(data) {
                    var dataList = data[0];
                    var yAxis = data[1];
                    if (itemName.toLowerCase() !== 'blank') {
                        zoom = parseInt((location.hash.match(/#z=([^#]*)/) || [])[1]);
                        zoom = zoom && zoom <= 6 && zoom >= 0 ?
                            zoom - 1 :
                            (zoom === 0 ?
                                0 :
                                2);
                    }
                    
                    var enlarge = new OO.ui.ButtonWidget( { 
						label: 'Enlarge chart',
						icon: 'fullScreen',
						id: 'gec-enlarge-' + c
					} );
					
					enlarge.$element.css("font-size", "13px");
                    	
                    // @todo this doesn't do anything on small charts
                    //       is it supposed to?
                    //var zoomOut = '<a href="javascript:_GEC.chart' + c + '.zoomOut();" style="text-decoration:underline;color:inherit;font-size:inherit;">Zoom out</a>';

                    //generating the chart
                    _GEC['chart' + c] = new Highcharts.StockChart({
                        chart: {
                            renderTo: 'GEdatachart' + c,
                            backgroundColor: 'white',
                            plotBackgroundColor: 'white',
                            zoomType: '',
                            //height: isSmall?210:null,
                            events: {
                                redraw: function () {
                                    _GEC.thisid = this.renderTo.id.replace('GEdatachart', '').replace('GEpopupchart', 'popup');
                                    setTimeout(function () {
                                        setChartExtremes(_GEC.thisid);
                                    }, 0);
                                }
                            },
                            marginBottom: 0,
                        },
                        legend: {
                            enabled: !isSmall && !isMedium,
                            backgroundColor: 'white',
                            align: 'right',
                            layout: 'vertical',
                            verticalAlign: 'top',
                            y: 85
                        },
                        responsive: {
                            rules: [{
                                condition: {
                                    //maxWidth: 500
                                },
                                chartOptions: {
                                    legend: {
                                        align: 'center',
                                        verticalAlign: 'bottom',
                                        layout: 'horizontal'
                                    }
                                }
                            }]
                        },
                        title: {
                            text: (isSmall || isMedium) ? ((isInfobox || isMedium) ? enlarge.$element[0].outerHTML : itemName) : 'Grand Exchange Market Watch',
                            useHTML: true,
                            style: {
                                color: 'black',
                                fontSize: isSmall ? (enlarge ? '13px' : '15px') : '18px',
                            },
                        },
                        subtitle: {
                            text: isSmall ? (isInfobox ? '' : enlarge.$element[0].outerHTML) : (itemName.toLowerCase() == 'blank' ? 'Historical chart' : itemName),
                            useHTML: true,
                            y: 50,
                            style: {
                                color: '#666',
                                fontSize: isSmall ? '13px' : '15px',
                            },
                        },
                        rangeSelector: {
                            enabled: !isSmall && !isMedium,
                            selected: zoom,
                            inputBoxStyle: {
                                right: '15px',
                                display: (isSmall || isMedium) ? 'none' : 'block'
                            },
                            inputStyle: {
                                width: '100px',
                            },
                            inputDateFormat: "%e-%b-%Y",
                            buttonTheme: {
                                class: 'zoomButton',
                            },
                            buttons: [{
                                type: 'month',
                                count: 1,
                                text: '1m'
                            }, {
                                type: 'month',
                                count: 2,
                                text: '2m'
                            }, {
                                type: 'month',
                                count: 3,
                                text: '3m'
                            }, {
                                type: 'month',
                                count: 6,
                                text: '6m'
                            }, {
                                type: 'year',
                                count: 1,
                                text: '1y'
                            }, {
                                type: 'all',
                                text: 'All'
                            }]
                        },
                        plotOptions: {
                            series: {
                                enableMouseTracking: !isSmall,
                                dataGrouping: {
                                    dateTimeLabelFormats: {
                                        day: ['%A, %e %B %Y', '%A, %e %B', '-%A, %e %B %Y'],
                                        week: ['Week from %A, %e %B %Y', '%A, %e %B', '-%A, %e %B %Y'],
                                        month: ['%B %Y', '%B', '-%B %Y'],
                                        year: ['%Y', '%Y', '-%Y']
                                    }
                                }
                            }
                        },
                        tooltip: {
                            enabled: !isSmall,
                            valueDecimals: isIndexChart ? 2 : 0,
                            headerFormat: '<span style="font-size: 12px">{point.key}</span><br/>',
                            xDateFormat: "%A, %e %B %Y",
                        },
                        navigator: {
                            xAxis: {
                                dateTimeLabelFormats: {
                                    day: "%e-%b",
                                    week: "%e-%b",
                                    month: "%b-%Y",
                                    year: "%Y",
                                },
                                minTickInterval: 24 * 3600 * 1000, //1 day
                            },
                            maskFill: 'none',
                            enabled: !(isSmall || isMedium)
                        },
                        credits: {
                            enabled: false,
                        },
                        xAxis: [{
                            lineColor: '#666',
                            tickColor: '#666',
                            dateTimeLabelFormats: {
                                day: "%e-%b",
                                week: "%e-%b",
                                month: "%b-%Y",
                                year: "%Y",
                            },
                            minTickInterval: 24 * 3600 * 1000, //1 day
                            scrollbar: {
                                enabled: false,
                                showFull: false
                            },
                        }],
                        yAxis: yAxis,
                        series: dataList,
                        colors: window.GEMWChartColors || ['#4572A7', '#AA4643', '#89A54E', '#80699B', '#3D96AE', '#DB843D', '#92A8CD', '#A47D7C', '#B5CA92']
                    });

                    var items = ($('#GEChartItems' + c).html() || '').split(',');
                    var noAdd = [];
                    var i;

                    for (i = 0; i < items.length; i++) {
                        items[i] = items[i].trim();

                        if (items[i]) {
                            addItem(c, items[i]);
                        } else {
                            noAdd.push(1);
                        }
                    }
                    if (items.length == noAdd.length && _GEC['chart' + c].series[0].name.toLowerCase() != 'blank') setChartRange(c);
                    
                    //adjusting the axes extremes (initial load)
                    setChartExtremes(c);

                    //loading the chart and additional price info when the page is ready
                    if (((conf.wgNamespaceNumber == 112 && conf.wgTitle.split('/')[1] == 'Data') || conf.wgPageName == 'RuneScape:Grand_Exchange_Market_Watch/Chart') && location.hash.match('#i=') !== null) {
                        var hash = location.hash;
                        items = decodeURIComponent((hash.match(/#i=([^#]*)/) || [])[1] || '').replace(/_/g, ' ').split(',');
                        for (i = 0; i < items.length; i++) {
                            if (items[i].match(/^\s*$/) === null) addItem(0, items[i]);
                        }
                    }
                    
                    var $enlargeEle = $("#gec-enlarge-" + c);
                    if ($enlargeEle.length) {
	                	$enlargeEle.on("click", function () {
	                		popupChart(c);
	                	});
                    };
                });

            });

        }
    },

    /**
     * General helper methods
     */
    util = {
        /**
         * <doc>
         *
         * @todo replace with $.extend
         *
         * @param a {object}
         * @param b {object} (optional)
         *
         * @return {object}
         */
        cloneObj: function (a, b) {
            if (typeof a !== 'object') {
                return '';
            }

            if (typeof b !== 'object') {
                b = {};
            }

            for (var key in a) {
                if (a.hasOwnProperty(key)) {
                    b[key] = a[key];
                }
            }

            return b;
        },

        /**
         * Averages prices across a specified time interval
         *
         * @param arr {array} Array of arrays, where each member of `arr`
         *                    is in the format [time, price]
         *                    Which is how we store the price data
         *                    @example [x-coord, y-coord]
         * @param amt {number} Interval to average across in days
         * @param round {number} (optional) Number of decimal places to round to
         *                       Defaults to 0
         *
         * @return {array} Array of arrays, where each member of the return array
         *                 is in the format [time, price] (as above)
         *                 and
         */
        avg: function (arr, amt, round) {
            amt = amt || arr.length;
            // convert `round` into a number we can use for rounding
            round = Math.pow(10, round || 0);

            var avgs = [],
                list = [],
                i;

            // adds each price to `list`
            // when `amt` is reached, average the contents of `list`
            //
            // each iteration after `amt` is reached averages the contents of `list`
            // which is continuously being updated as each iteration
            // after `amt` is reached replaces a member of `list`
            // @example when `i` is 31 the current price replaces `list[1]`
            //          when `i` is 35 the current price replaces `list[5]`
            for (i = 0; i < arr.length; i++) {
                list[i % amt] = arr[i][1];

                if (i >= amt) {
                    avgs.push([
                        // don't modify the time (y-coord)
                        arr[i][0],
                        Math.round((util.sum(list) / list.length) * round) / round
                    ]);
                }
            }

            return avgs;
        },

        /**
         * Finds the sum of numbers in an array
         * Only called by `util.avg`
         *
         * @param arr {array} Array of number to find the sum of
         *
         * @return {number} Sum of the numbers in `arr`
         */
        sum: function (arr) {
            var total = 0,
                i;

            for (i = 0; i < arr.length; i++) {
                total += parseFloat(arr[i], 10);
            }

            return total;
        },

        /**
         * Rounds and formats numbers
         *
         * @example 12345        -> 12.3K
         * @example 1234567      -> 1.2M
         * @example 123456789012 -> 123.4M
         *
         * @param num {number|string} Number to format
         *
         * @return {string} Formatted number
         */
        toKMB: function (num) {
            // strip commas from number string
            // as `parseInt` will interpret them as a decimal separator
            // pass numbers and string to `parseInt` to convert floats too
            num = parseInt((typeof num === 'string' ? num.replace(/,/g, '') : num), 10);
            var neg = num < 0 ? '-' : '';

            num = Math.abs(num);

            // `1eX` is shorthand for `Math.pow( 10, X )`
            if (num >= 1e10) {
                num = Math.round(num / 1e8) / 10;
                num += 'B';
            } else if (num >= 1e7) {
                num = Math.round(num / 1e5) / 10;
                num += 'M';
            } else if (num >= 1e4) {
                num = Math.round(num / 100) / 10;
                num += 'K';
            }

            return rs.addCommas(neg + num);
        },

        /**
         * Capitalises first character of a string
         *
         * @source <https://stackoverflow.com/a/1026087>
         *
         * @param str {string}
         *
         * @return {string}
         */
        ucFirst: function (str) {
            return str.charAt(0).toUpperCase() + str.slice(1);
        },

        /**
         * Sort data points in the graph data before passing it to the charts api
         */
        sortPoints: function (a, b) {
            a = a.replace(/'/g, '').split(':')[0];
            b = b.replace(/'/g, '').split(':')[0];

            return a - b;
        }
    },

    /**
     * Chart methods
     */
    chart = {
        /**
         * <doc>
         *
         * @param id {string|number}
         * @param match {string} is normally the 'line' that isn't an item's price data
         *                       such as average or volume
         *
         * @return {number}
         */
        getSeriesIndex: function (id, match) {
            var chart = _GEC['chart' + id],
                series = chart.series,
                i;

            if (chart) {
                for (i = 0; i < series.length; i++) {
                    if (series[i].name.match(match)) {
                        return i;
                    }
                }

                return -1;
            }

            // @todo what happens if !chart
        },

        /**
         * Creates a URL with preset options
         *
         * @todo change to url params
         * @todo document the individual params/options
         *
         * @param id {number|string}
         *
         * @return {string}
         */
        permLinkUrl: function (id) {
            var chart = _GEC['chart' + id],
                xt = chart.xAxis[0].getExtremes(),
                series = chart.series,
                minDate = (new Date(xt.min))
                    .toDateString()
                    .split(' ')
                    .slice(1)
                    .join('_'),
                maxDate = (new Date(xt.max))
                    .toDateString()
                    .split(' ')
                    .slice(1)
                    .join('_'),
                inputAvg = $('#average' + id).data('ooui-elem').getNumericValue(),
                urlHash = '#t=' + minDate + ',' + maxDate,
                items = '',
                i;

            if (!isNaN(inputAvg)) {
                urlHash += '#a=' + inputAvg;
            }

            for (i = 0; i < series.length; i++) {
                if (series[i].name == 'Navigator' || series[i].name.match('average')) {
                    continue;
                }

                // separate items with commas
                if (items) {
                    items += ',';
                }

                // @todo url encode this?
                items += series[i].name.replace(/ /g, '_');
            }

            urlHash += '#i=' + items;

            // @todo hide the redirect h2
            return '/w/RuneScape:Grand_Exchange_Market_Watch/Chart' + urlHash;
        },

        /**
         * Add a new item to the chart
         *
         * @param i
         * @param it {string} (optional)
         */
        addItem: function (i, it) {
            _GEC.chartid = i;
            var OOUIextraItemPresent = $('#extraItem' + i).length > 0,
                OOUIextraItem = $('#extraItem' + i).data('ooui-elem'),
                item = (it || '').trim() || OOUIextraItem.getValue(),
                dataItems = [
                    '#addedItems' + i + ' [data-item]',
                    '#GEdataprices' + i + '[data-item]'
                ],
                $dataItems = $(dataItems.join(',')).map(function () {
                    return $(this).attr('data-item').toLowerCase();
                }),
                $addedItems = $('#addedItems' + i),
                id,
                data,
                series,
                seriesIndex,
                gecchartid = i,
                index;

            if (item && item.length) {
                index = -1;
                for (var i2 = 0; i2 < _GEC.AIQueue.length; i2++) {
                    if (_GEC.AIQueue[i2] == item.toLowerCase()) {
                        index = i2;
                        break;
                    }
                }

                if (
                    // @todo should a number passed to .get()
                    $dataItems.get().indexOf(item.toLowerCase()) !== -1 ||
                    index !== -1
                ) {
                    if (!it) {
                        alert(item + ' is already in the graph.');
                    }

                    if (OOUIextraItemPresent) { 
                        OOUIextraItem.setValue(''); 
                    }

                    return false;
                }

                if (OOUIextraItemPresent) { 
                    OOUIextraItem.setDisabled(true);    
                }

                $.get(
                    '/api.php',
                    {
                        action: 'query',
                        prop: 'revisions',
                        rvprop: 'content',
                        format: 'json',
                        titles: 'Module:Exchange/' + util.ucFirst(item)
                    }
                ).then(function(data, textStatus) {
                    var OOUIextraItem = $('#extraItem' + gecchartid).data('ooui-elem'),
                        pages = data.query.pages;
                    if (textStatus !== 'success') {
                        alert('An error occured while loading ' + item);
                        mw.log(data);
                    }
                    var matches = []
                    var pageMissing = false;
                    if (pages[-1]) {
                        pageMissing = true;
                    } else {
                        var exchangeData = pages[Object.keys(pages)[0]]
                                            .revisions[0]['*'];
                        matches = exchangeData.match(/itemId\D*(\d*)/);
                        if (matches.length !== 2) {
                            pageMissing = true;
                        }
                    }
                    // page not found
                    if (pageMissing) {
                        if (OOUIextraItem.getValue().length) {
                            alert('The item ' + item + ' doesn\'t exist on our Grand Exchange database.');
                            OOUIextraItem.setDisabled(false).setValue('');
                            return false;
                        }

                        _GEC.AILoaded.push(false);

                        if (
                            _GEC.AIData.length &&
                            _GEC.AIQueue.length == _GEC.AILoaded.length
                        ) {
                            loadChartsQueueComplete(gecchartid);
                        } else if (!_GEC.AIData.length) {
                            setChartRange(gecchartid);
                        }

                        OOUIextraItem.setDisabled(false).setValue('');

                        return false;
                    }

                    var itemId = matches[1];
                    return $.getJSON("https://api.weirdgloop.org/exchange/history/" + gameVersion + "/all?compress=true&id=" + itemId);
                }).then(function(data, textStatus) {
                    if (data === false) return;
                    _GEC.AIData.push({
                        name: item,
                        data: Object.values(data)[0],
                        id: item,
                        gecchartid: gecchartid,
                        lineWidth: 2
                    });

                    _GEC.AILoaded.push(item);

                    if (getSeriesIndex(gecchartid, 'average') !== -1) {
                        _GEC['chart' + gecchartid]
                            .series[getSeriesIndex(gecchartid, 'average')]
                            .remove();
                    }

                    if (_GEC.AIQueue.length === _GEC.AILoaded.length) {
                        // This is always true when only 1 item is being loaded.
                        loadChartsQueueComplete(gecchartid);
                    }
                })

                _GEC.AIQueue.push({item: item.toLowerCase(), chart: gecchartid});

                // @todo when does this happen
                /* This happens when there are no further items added to the charts, i.e. when the original item is the only one.
                   This is indeed a flawed test, since it won't work on GEMW/C, where there is no original item in the chart.
                   This should be replaced with another test that also works on GEMW/C.
                 */
            } else if (
                $addedItems.html().match(/^\s*$/) ||
                (
                    conf.wgPageName == 'RuneScape:Grand_Exchange_Market_Watch/Chart' &&
                    $addedItems.find('a').length === 1
                )
            ) {
                id = (i === 'popup' ? $('#GEchartpopup').attr('data-chartid') : i);
                getData(id, false, false, i, function(data) {
                    series = _GEC['chart' + i].series;
                    seriesIndex = getSeriesIndex(i, 'average');

                    //remove an average line if it already exists
                    if (seriesIndex !== -1) {
                        series[seriesIndex].remove();
                    }

                    //add average line when there is only 1 item in the chart
                    _GEC['chart' + i].addSeries(data[0][1]);
                });
            }
        },

        /**
         * <doc>
         *
         * @param c {number|string}
         */
        loadQueueComplete: function (cin, addeditembyscript) {
            var cnum = (cin !== 'popup'),  //if cin is a number, we're probably at initial load of one/many charts on a page, so we need to iterate over the entire queue
                c = cnum ? _GEC.AIQueue.length : cin, //if not a number, its almost certainly 'popup', for which we only need to reload the popup
                id,
                chartdata,
                isSmall = [],
                isMedium = [],
                data = [],
                i,
                index,
                itemhash,
                $addedItems,
                iname,
                hadBlank;

            if (cnum) { //this structure repeats throughout the method: if cnum then loop else do once. probably a better way to do this
                for (i = 0; i < c; i++) {
                    isSmall[i] = $('#GEdatachart' + i).hasClass('smallChart');
                    isMedium[i] = $('#GEdatachart' + i).hasClass('mediumChart');
                }
            } else {
                isSmall = $('#GEdatachart' + c).hasClass('smallChart');
                isMedium = $('#GEdatachart' + c).hasClass('mediumChart');
            }

            if (cnum) {
                for (i = 0; i < c; i++) {
                    if (getSeriesIndex(_GEC.AIQueue[i].chart, volumeLabel) !== -1) {
                        id = i === 'popup' ? $('#GEchartpopup').attr('data-chartid') : i;
                        getData(id, true, undefined, undefined, function(data) {
                            data[1].title.text = 'Price history';

                            reloadChart(i, {
                                series: data[0],
                                yAxis: data[1]
                            });
                        });
                    }
                }
            } else {
                if (getSeriesIndex(c, volumeLabel) !== -1) {
                    id = c === 'popup' ? $('#GEchartpopup').attr('data-chartid') : c;
                    getData(id, true, undefined, undefined, function(data) {
                        data[1].title.text = 'Price history';
                        reloadChart(c, {
                            series: data[0],
                            yAxis: data[1]
                        });
                    });
                }
            }

            for (i = 0; i < _GEC.AIData.length; i++) {
                index = -1;
                for (var i2 = 0; i2 < _GEC.AIQueue.length; i2++) {
                    if (_GEC.AIQueue[i2].item === (_GEC.AIData[i] || {name: ''}).name.toLowerCase()) {
                        index = i2;
                        break;
                    }
                }
                data[index !== -1 ? index : data.length] = _GEC.AIData[i];
            }

            // @todo should this be `Array.isArray`
            //       or should it default to `{}`
            // @todo test if isSmall is needed in the conditional
            if (cnum) {
                for (i = 0; i < c; i++) {
                    if (data[i] === undefined) continue;
                    if ((isSmall[data[i].gecchartid] && isMedium[data[i].gecchartid]) && typeof Array.isArray(_GEC.addedData[data[i].gecchartid])) {
                        _GEC.addedData[data[i].gecchartid] = [];
                    }
                }
            } else {
                if ((isSmall || isMedium) && typeof Array.isArray(_GEC.addedData[data[c].gecchartid])) {
                    _GEC.addedData[data[c].gecchartid] = [];
                }

            }

            for (i = 0; i < data.length; i++) {
                if (data[i]) {
                    _GEC['chart' + data[i].gecchartid].addSeries(data[i]);
                }

                if (cnum && isSmall[data[i].gecchartid]) {
                    _GEC.addedData[data[i].gecchartid][i] = data[i];
                }
            }

            if (cnum) {
                for (i = 0; i < c; i++) {
                    setChartExtremes(data[i].gecchartid);
                    $('#extraItem' + data[i].gecchartid).data('ooui-elem').setDisabled(false).setValue('');
                }
            } else {
                setChartExtremes(c);
                $('#extraItem' + c).data('ooui-elem').setDisabled(false).setValue('');
            }
            itemhash = (location.hash.match(/#i=[^#]*/) || [])[0] || location.hash + '#i=';
            $addedItems = $('#addedItems' + c);

            for (i = 0; i < data.length; i++) {
                if (!data[i]) {
                    continue;
                }

                iname = data[i].name;

                if (!$addedItems.text().trim()) {
                    $addedItems.append(
                        'Remove items from graph: ',
                        $('<a>')
                            .attr({
                                href: 'javascript:removeGraphItem("' + iname + '","' + c + '")',
                                'data-item': iname
                            })
                            .text(iname)
                    );
                    itemhash = '#i=' + iname;
                } else {
                    $addedItems.append(
                        ', ',
                        $('<a>')
                            .attr({
                                href: 'javascript:removeGraphItem("' + iname + '","' + c + '")',
                                'data-item': iname
                            })
                            .text(iname)
                    );
                    itemhash += ',' + iname;
                }
            }

            if (location.hash.match(/#i=/)) {
                itemhash = location.hash
                    .replace(/#i=[^#]*/, itemhash)
                    .replace(/ /g, '_');
            } else {
                itemhash = location.hash + itemhash;
            }

            if (
                (
                    conf.wgNamespaceNumber == 112 && conf.wgTitle.split('/')[1] == 'Data' ||
                    conf.wgPageName == 'RuneScape:Grand_Exchange_Market_Watch/Chart'
                ) &&
                itemhash.replace('#i=', '').length
            ) {
                location.hash = itemhash;
            }

            _GEC.AIQueue = [];
            _GEC.AILoaded = [];
            _GEC.AIData = [];

            if (cnum) {
                for (i = 0; i < c; i++) {
                    hadBlank = removeGraphItem('Blank', data[i].gecchartid);

                    if (hadBlank) {
                        setChartRange(data[i].gecchartid);
                    }
                }
            } else {
                hadBlank = removeGraphItem('Blank', c);

                if (hadBlank) {
                    setChartRange(c);
                }
            }
        },

        /**
         * <doc>
         *
         * @param c {number|string}
         *
         * @return {boolean}
         */
        setRange: function (c) {
            var zoom = parseInt((location.hash.match(/#z=([^#]*)/) || [])[1], 10);
            zoom = zoom && zoom <= 6 && zoom >= 0 ? zoom - 1 : (zoom === 0 ? 0 : 2);
            var hash = location.hash;
            var hasT = (conf.wgNamespaceNumber === 112 && conf.wgTitle.split('/')[1] === 'Data') || conf.wgPageName === 'RuneScape:Grand_Exchange_Market_Watch/Chart';
            if (typeof c === 'number' && (hasT && !hash.match('#t=') || !hasT)) {
                $('#GEdatachart' + c + ' .zoomButton').eq(zoom).click();
                return true;
            }

            var timespan = decodeURIComponent((hash.match(/#t=([^#]*)/) || [])[1] || '')
                .replace(/_/g, ' ')
                .split(',');
            var dates = [new Date(timespan[0]), new Date(timespan[1])];
            var d = new Date(timespan[0]);
            var extremes = _GEC['chart' + c].xAxis[0].getExtremes();

            if (dates[0] !== 'Invalid Date' && dates[1] === 'Invalid Date' && typeof zoom === 'number') {
                var button = _GEC['chart' + c].rangeSelector.buttonOptions[zoom];

                if (button.type === 'month') {
                    d.setMonth(d.getMonth() + button.count);
                } else if (button.type === 'year') {
                    d.setYear(d.getFullYear() + button.count);
                } else if (button.type === 'all') {
                    d = new Date(extremes.dataMax);
                }

                dates[1] = d;
            }

            if (dates[0] !== 'Invalid Date' && dates[1] !== 'Invalid Date') {
                _GEC['chart' + c].xAxis[0].setExtremes(dates[0].getTime(), dates[1].getTime());
                return true;
            }

            return false;
        },

        /**
         * <doc>
         *
         * @param c {number|string}
         * @param change {object}
         */
        reload: function (c, change) {
            var options = _GEC['chart' + c].options;

            if (!options) {
                // @todo do we need to return `false` here
                // @todo when does this happen
                return false;
            }

            $.extend(options, change);
            _GEC['chart' + c] = new Highcharts.StockChart(options);
        },

        /**
         * <doc>
         *
         * @param item {string}
         * @param c {number|string}
         *
         * @return {boolean}
         */
        removeItem: function (item, c) {
            var series = _GEC['chart' + c].series,
                id,
                i,
                newhash,
                data;

            // find the item we want to remove
            for (i = 0; i < series.length; i++) {
                if (series[i].name.match(item)) {
                    id = i;
                }
            }

            // @todo when does this happen
            //       when we can't find the item?
            if (typeof id !== 'number') {
                return false;
            }

            // remove item from url hash
            newhash = location.hash
                .replace(/_/g, ' ')
                .replace(new RegExp('(#i=[^#]*),?' + item, 'i'), '$1')
                .replace(/,,/g, ',')
                .replace(/,#/g, '#')
                .replace(/#i=,/g, '#i=')
                .replace(/#i=($|#)/, '$1')
                .replace(/ /g, '_');

            if (newhash.replace('#i=', '').length) {
                location.hash = newhash;
            } else if (location.hash.length) {
                location.hash = '';
            }

            // remove the item from the chart
            series[id].remove();
            // reset extremes?
            setChartExtremes(c);

            // @todo can we cache #addedItems somehow
            // remove item from list at top of graph
            $('#addedItems' + c + ' [data-item="' + item + '"]').remove();
            // cleanup list
            $('#addedItems' + c).html(
                $('#addedItems' + c)
                    .html()
                    .replace(/, , /g, ', ')
                    .replace(/, $/, '')
                    .replace(': , ', ': ')
            );

            // if the list is empty show average, volume and item stats again
            if (!$('#addedItems' + c + ' [data-item]').length) {
                $('#addedItems' + c).empty();
                id = c == 'popup' ? $('#GEchartpopup').attr('data-chartid') : c;
                data = getData(id, false, false, 'popup', function(data) {
                    reloadChart(c, {
                        series: data[0],
                        yAxis: data[1]
                    });
                });

            }

            return true;
        },

        /**
         * <doc>
         *
         * @param i {number|string}
         */
        popup: function () {
        },

        /**
         * <doc>
         *
         * @param i
         */
        setExtremes: function (i) {
            var ch = _GEC['chart' + i],
                exts = _GEC['chart' + i].yAxis[0].getExtremes();

            if (
                exts.dataMin * 0.95 !== exts.userMin ||
                exts.dataMax * 1.05 !== exts.userMax
            ) {
                ch.yAxis[0].setExtremes(exts.dataMin * 0.95, exts.dataMax * 1.05);

                if (ch.yAxis[2]) {
                    exts = ch.yAxis[1].getExtremes();
                    ch.yAxis[1].setExtremes(0, exts.dataMax * 1.05);
                }
            }

            if (i === 'popup') {
                // @todo use onclick event
                $('#GEPermLink' + i).data('ooui-elem').setData(chartPermLinkUrl(i));
            }
        },

        /**
         * <doc>
         *
         * @param c {number|string}
         * @param isSmall {boolean}
         * @param avginput {number|string} (optional)
         *        number component of input element used for altering the average interval
         *        when the interval is in days
         *        when is this different to `c`?
         *
         * @return {array} 2 item array containing X and Y respectively
         *                 @todo expand on what X and Y are
         */
        getData: function () {
        }
    },

    // map old functions to new locations until uses are fixed
    getSeriesIndex = chart.getSeriesIndex,
    chartPermLinkUrl = chart.permLinkUrl,
    addItem = chart.addItem,
    removeGraphItem = chart.removeItem,
    reloadChart = chart.reload,
    setChartRange = chart.setRange,
    setChartExtremes = chart.setExtremes,
    loadChartsQueueComplete = chart.loadQueueComplete;
// popupChart = chart.popup;
// getData = chart.getData;

// chart-related general functions

function popupChart(i) {
    var $popup = $('#GEchartpopup'),
        $overlay = $('#overlay'),
        options,
        data,
        n;

    if (!$popup.length) {
        return false;
    }

    if ($overlay.length) {
        $overlay.toggle();
    } else {
        $popup.before(
            $('<div>')
                .attr('id', 'overlay')
                .css('display', 'block')
        );
        $overlay = $('#overlay');
    }

    $overlay.on('click', function () {
        popupChart(false);
    });

    if (typeof i === 'number') {
        $(document).keydown(function (e) {
            // Esc
            if (e.which === 27) {
                popupChart(false);
            }
        });
    } else {
        // @todo only remove our event
        $(document).off('keydown');
    }

    if (typeof i === 'boolean' && !i) {
        $popup.hide();
        $('#addedItemspopup').html('');
    } else {
        $popup.toggle();
    }

    if (typeof i === 'number' && $popup.attr('data-chartid') !== i) {
        $('#averagepopup').data('ooui-elem').setValue(_GEC.average);
        $popup.attr('data-chartid', i);

        options = {};
        getData(i, false, false, 'popup', function(data) {
            var dataList = data[0];
            var yAxis = data[1];
            // @todo can this be replaced with $.extend?
            // @todo what is this supposed to do?
            util.cloneObj(_GEC['chart' + i].options, options);

            options.chart.renderTo = 'GEpopupchart';
            options.legend.enabled = true;
            options.title.text = 'Grand Exchange Market Watch';
            options.title.style.fontSize = '18px';
            options.subtitle.text = options.series[0].name;
            options.subtitle.y = 35;
            options.subtitle.style.fontSize = '15px;';
            options.chart.zoomType = '';
            options.rangeSelector.enabled = true;
            options.rangeSelector.inputBoxStyle.display = 'block';
            options.plotOptions.series.enableMouseTracking = true;
            options.tooltip.enabled = true;
            options.navigator.enabled = true;
            options.credits.enabled = false;
            options.series = [{}];
            options.series = _GEC.addedData[i] ? [dataList[0]] : dataList;
            options.yAxis = yAxis;

            _GEC.chartpopup = new Highcharts.StockChart(options);

            if (_GEC.addedData[i]) {
                for (n = 0; n < _GEC.addedData[i].length; n++) {
                    _GEC.chartpopup.addSeries(_GEC.addedData[i][n]);
                }
            }

            setChartExtremes('popup');
            _GEC.chartpopup.redraw();
            $('#GEPermLinkpopup').data('ooui-elem').setData(chartPermLinkUrl('popup'));
        });
    }
}

function rg(num) {
    var colour = 'red';

    if (num > 0) {
        colour = 'green';
    } else if (num === 0) {
        colour = 'blue';
    }

    return colour;
}

function getData(cin, isSmall, isMedium, avginput, callback) {
    var c = cin === 'popup' ? $('#GEchartpopup').attr('data-chartid') : cin,
        $dataPrices = $('#GEdataprices' + c),
        dataItem = $dataPrices.attr('data-item'),
        dataItemId = $dataPrices.attr('data-itemId') || ('GE ' + dataItem),
        isIndexChart = /index/i.test(dataItem),
        itemName = dataItem || conf.wgTitle.split('/')[0],
        ch = _GEC['chart' + c],
        chartLoaded = !!(ch && ch.series && ch.series.length),
        prices = [],
        i,
        data = [],
        thisprice,
        volumes = [],
        dataList,
        inputAvg,
        newhash,
        yAxis,
        chartPageData;

    // happens when the first chart isSmall
    // and the average input id is actually the popup chart
    // the chart's id is popup, but the input's id is 0
    avginput = avginput || cin;

    var pricesToDataList = function(prices) {
    	_GEC.urlCache[url] = prices;
    	prices = Object.values(prices)[0];
        var volumeMultiplier = isOSRS ? 1 : 1000000
        for (i = 0; i < prices.length; i++) {
            data.push([
                // time
                prices[i][0],
                // @todo should this be parseInt?
                // price
                prices[i][1]
            ]);

            if (prices[i][2] && !isSmall) {
                volumes.push([
                    // time
                    prices[i][0],
                    // volume
                    // volumes are in millions
                    prices[i][2] * volumeMultiplier
                ]);
            }
        }

        // datalist's elements are essentially each line on the chart
        // so price, 30-day-average and volume
        dataList = [{
            name: itemName,
            data: data,
            lineWidth: isSmall ? 2 : 3
        }];

        if (itemName.toLowerCase() === 'blank' && !chartLoaded) {
            dataList[0].color = '#000000';
        }

        if (!isSmall && !isMedium && (itemName.toLowerCase() !== 'blank' || chartLoaded)) {
            inputAvg = $('#average' + avginput).data('ooui-elem').getNumericValue();

            // @todo should this be isNaN?
            if (inputAvg) {
                newhash = location.hash
                    .replace(/#a=[^#]*|$/, '#a=' + inputAvg)
                    .replace(/ /g, '_');

                if (newhash.length) {
                    location.hash = newhash;
                }
            }

            inputAvg = inputAvg || 30;
            dataList.push({
                name: inputAvg + '-day average',
                data: util.avg(data, inputAvg, isIndexChart ? 2 : 0),
                lineWidth: 2,
                dashStyle: 'shortdash',
            });

            if (volumes.length >= 10) {
                dataList.push({
                    name: volumeLabel,
                    data: volumes,
                    type: 'area',
                    color: '#cc8400',
                    fillColor: {
                        linearGradient: {
                            x1: 0,
                            y1: 0,
                            x2: 0,
                            y2: 1
                        },
                        stops: [
                            [0, '#ffa500'],
                            [1, 'white']
                        ],
                    },
                    // display on separate y-axis
                    yAxis: 1,
                });
            }
        }

        // create y-axis for price data
        yAxis = {
            title: {
                text: isSmall ? null : (isIndexChart ? 'Index history' : 'Price history'),
                offset: 60,
                rotation: 270,
                style: {
                    color: 'black',
                    fontSize: '12px',
                },
            },
            opposite: false,
            labels: {
                align: 'right',
                x: -8,
                y: 4,
            },
            allowDecimals: false,
            // 1 coin
            minTickInterval: 1,
            showLastLabel: 1,
            lineWidth: 1,
            lineColor: '#E0E0E0'
        };

        // volume data is plotted on a seperate y-axis
        if (volumes.length >= 10 && !isSmall) {
            // set height to allow room for second y-axis
            yAxis.height = 200;

            // convert to array and add volume data
            yAxis = [yAxis, {
                title: {
                    text: volumeLabel,
                    offset: 60,
                    rotation: 270,
                    style: {
                        color: 'black',
                        fontSize: '12px'
                    }
                },
                opposite: false,
                labels: {
                    align: 'right',
                    x: -8,
                    y: 4,
                },
                showEmpty: 0,
                showLastLabel: 1,
                offset: 0,
                lineWidth: 1,
                lineColor: '#E0E0E0',
                height: 50,
                top: 325,
                min: 0
            }];
        }
        return [dataList, yAxis];
    }
	
	var isPopup = !isSmall && !isMedium;
	var dataType = isPopup ? 'all' : 'sample';
    var url = "https://api.weirdgloop.org/exchange/history/" + gameVersion + "/" + dataType + "?compress=true&id=" + dataItemId;
    var pricesPromise;
    if (chartLoaded && itemName.toLowerCase() === 'blank') {
        chartPageData = _GEC['chart' + c].series[
            getSeriesIndex(c, $('#addedItems' + c).find('a').data('item'))
            ];

        for (i = 0; i < chartPageData.xData.length; i++) {
            prices.push(chartPageData.xData[i] + ':' + chartPageData.yData[i]);
        }
        pricesPromise = Promise.resolve(prices);
    } else {
        if (_GEC.urlCache[url]) {
            return callback(pricesToDataList(_GEC.urlCache[url]))
        }
        $.getJSON(url).then(pricesToDataList).then(callback)
    }

}

$(self.deps);