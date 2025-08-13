var DARKMODE_TEXT = (($('body.wgl-theme-browntown').length) ? '#f4eaea' : '#cbd9f4');

function RSWChart(i, dataElement) {
    var self = this;
    this.is_error = false;
    this.index = i;

    function error(t) {
        dataElement.removeClass('rsw-chartjs-config').addClass("rsw-chart-parsed rsw-chart-error").text(t);
        self.is_error = true;
    }

    function parseData() {
        mw.log('parsing data for ' + self.index);
        if (self.is_error) return;
        var c = {};
        mw.log(dataElement.text());
        try {
            var txt = dataElement.text();
            txt = txt.replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/=/g, '&equals;').replace(/\\/g, '&bsol;').replace(/\//g, '&sol;');
            return JSON.parse(txt);
        } catch (e) {
            error("Failed to parse JSON. " + e.name + ": " + e.message);
        }
    }

    function addFunctions() {
        var opts = self.config.options;
        if (opts.tooltips && opts.tooltips.format == "skillingSuccess") {
            opts.tooltips.callbacks = opts.tooltips.callbacks || {}
            opts.tooltips.callbacks.label = function(tooltipItem) {
                var label = tooltipItem.dataset.label.trim() || '';
                if (label) {
                    label += ': ';
                }
                label += "Level: " + tooltipItem.label + "; Chance: ";
                label += (tooltipItem.dataPoint.y * 100).toFixed(2) + "%";
                return label;
            }
        } else if (opts.tooltips && opts.tooltips.format == "harvestLives") {
        	opts.tooltips.callbacks = opts.tooltips.callbacks || {}
            opts.tooltips.callbacks.label = function(tooltipItem) {
                var label = tooltipItem.dataset.label.trim() || '';
                if (label) {
                    label += ': ';
                }
                label += "Level: " + tooltipItem.label + "; Yield: ";
                label += tooltipItem.dataPoint.y.toFixed(2);
                return label;
            }
        }

        if (opts.scales && opts.scales.y && opts.scales.y.ticks && opts.scales.y.ticks.format == "percent") {
            opts.scales.y.ticks.callback = function(value) {
                return Math.round(value * 100) + "%"
            }
        }

        opts.datasetsPerGroup = opts.datasetsPerGroup || 1;
        if (opts.datasetsPerGroup !== 1) {
            opts.tooltips.filter = function(tooltipItem) {
                return tooltipItem.datasetIndex % opts.datasetsPerGroup == 0 || tooltipItem.dataIndex > 0;
            }

            opts.legend = opts.legend || {};
            opts.legend.labels = opts.legend.labels || {};
            opts.legend.labels.filter = function(legendItem, data) {
                return legendItem.datasetIndex % 3 == 0;
            }

            opts.legend.onClick = function(e, legendItem) {
                var index = legendItem.datasetIndex;

                var ci = this.chart;
                [
                    ci.getDatasetMeta(index - index % 3 + 0),
                    ci.getDatasetMeta(index - index % 3 + 1),
                    ci.getDatasetMeta(index - index % 3 + 2)
                ].forEach(function(meta) {
                    meta.hidden = meta.hidden === null ? !ci.data.datasets[index].hidden : null;
                });
                ci.update();
            };
        }

        var legendIconWidth = 25;
        var legendIconHeight = 25;
        var legendIconBorder = 2;
        self.config.data.datasets.forEach(function(dataset, i) {
            if (i % opts.datasetsPerGroup == 0 && dataset.pointStyleImg !== undefined) {
                opts.legend = opts.legend || {};
                opts.legend.labels = opts.legend.labels || {};
                opts.legend.labels.usePointStyle = true;
                opts.legend.labels.font = opts.legend.labels.font || {};
                opts.legend.labels.font.size = 14;
                var baseColor = dataset.baseColor || dataset.borderColor;
                var canvas = document.createElement("canvas")
                canvas.width = legendIconWidth;
                canvas.height = legendIconHeight;
                var myctx = canvas.getContext("2d");
                myctx.fillStyle = baseColor;
                myctx.fillRect(0, 0, canvas.width, canvas.height);
                myctx.fillStyle = "white";
                myctx.fillRect(legendIconBorder, legendIconBorder, canvas.width - 2 * legendIconBorder, canvas.height - 2 * legendIconBorder);
                var colorData = myctx.getImageData(0, 0, 1, 1).data;
                var translColor = "rgba(" + colorData[0] + ", " + colorData[1] + ", " + colorData[2] + ", 0.15)";
                myctx.fillStyle = translColor;
                myctx.fillRect(0, 0, canvas.width, canvas.height);
                var image = new Image()
                image.src = rs.getFileURLCached(dataset.pointStyleImg);
                image.crossOrigin = "anonymous"
                image.onload = function() {
                    var imgSize = legendIconWidth - legendIconBorder * 2;
                    var imgScale = Math.min(1.0, imgSize/image.width, imgSize/image.height);
                    var imgWidth = imgScale * image.width;
                    var imgHeight = imgScale * image.height
                    myctx.drawImage(image, (legendIconWidth - imgWidth)/2, (legendIconHeight - imgHeight)/2, imgWidth, imgHeight);
                }
                dataset.pointStyle = canvas;

                for (var j = 0; j < opts.datasetsPerGroup; j++) {
                    self.config.data.datasets[i + j].borderColor = baseColor;
                    self.config.data.datasets[i + j].backgroundColor = translColor;
                    self.config.data.datasets[i + j].hoverBorderColor = baseColor;
                    self.config.data.datasets[i + j].hoverBackgroundColor = translColor;
                    self.config.data.datasets[i + j].hoverRadius = 0;
                    if (j == 0) {
                        self.config.data.datasets[i+j].borderColor = translColor;
                        self.config.data.datasets[i+j].hoverBorderColor = translColor;
                    }
                }
            }
        })
    }

    function makeChart() {
        mw.log('making chart for ' + self.index);
        if (self.is_error) return;

        var canvas = $('<canvas class="rsw-chartjs-canvas">');
        canvas.attr('id', 'rsw-chartjs-chart-' + self.index);
        dataElement.empty().append(canvas).removeClass('rsw-chartjs-config').addClass("rsw-chart-parsed");
        self.chart = new Chart(canvas, self.config);
    }
    this.config = parseData();
    addFunctions()
    makeChart();
    self.chart.update();
}

function init() {
    mw.log('chart init');
    // force all text to be darkmode happy by default
    if ($('body.wgl-theme-dark').length || $('body.wgl-theme-browntown').length) {
        Chart.defaults.font.color = DARKMODE_TEXT;
        Chart.defaults.color = 'rgba(255,255,255,0.1)';
        Chart.defaults.scale.gridLines.color = 'rgba(255,255,255,0.1)';
    }
    //other defaults
    Chart.defaults.plugins['legend'].labels.usePointStyle = true;
    Chart.defaults.locale = 'en';

    window.charts = [];
    $('.rsw-chartjs-config').each(function(i, e) {
        mw.log('creating chart ' + i);
        window.charts.push(new RSWChart(i, $(e)));
    });
    window.RSWChart = RSWChart;
}

function addHook() {
    mw.hook('rscalc.submit').add(init);
    if (!window.charts) {
        init();
    }
}

$.when($.ready, $.getScript("https://cdnjs.cloudflare.com/ajax/libs/Chart.js/3.0.0-beta/chart.min.js")).then(addHook, function() {
    console.error("Failed to load chart.js");
});