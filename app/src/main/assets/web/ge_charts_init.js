// Minimal GE charts initializer for OSRS Wiki pages in the app
// Finds elements with .GEChartBox and renders a Highcharts stock chart
// using the OSRS Wiki price API.

(function () {
  const LOG_TAG = 'GEChartInit';

  function log(level, msg, obj) {
    try {
      const text = `[${LOG_TAG}] ${msg}`;
      if (level === 'e') console.error(text, obj || '');
      else if (level === 'w') console.warn(text, obj || '');
      else console.log(text, obj || '');
    } catch (e) {}
  }

  function toMidPrice(point) {
    const hi = point.avgHighPrice;
    const lo = point.avgLowPrice;
    if (typeof hi === 'number' && typeof lo === 'number') return Math.round((hi + lo) / 2);
    if (typeof hi === 'number') return hi;
    if (typeof lo === 'number') return lo;
    return null;
  }

  async function fetchSeries(itemId) {
    const url = `https://prices.runescape.wiki/api/v1/osrs/timeseries?timestep=24h&id=${encodeURIComponent(itemId)}`;
    const resp = await fetch(url, { credentials: 'omit', mode: 'cors', cache: 'force-cache' });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const json = await resp.json();
    const data = Array.isArray(json?.data) ? json.data : [];
    const series = [];
    for (const p of data) {
      const mid = toMidPrice(p);
      if (typeof mid === 'number' && typeof p.timestamp === 'number') {
        // API timestamp in seconds
        series.push([p.timestamp * 1000, mid]);
      }
    }
    return series;
  }

  function renderChart(container, series) {
    if (!window.Highcharts || !window.Highcharts.stockChart) {
      log('e', 'Highcharts.stockChart not available');
      return;
    }
    const id = container.id || undefined;
    const opts = {
      chart: {
        height: container.clientHeight || 160,
        backgroundColor: 'white',
      },
      credits: { enabled: false },
      rangeSelector: { enabled: false },
      navigator: { enabled: false },
      scrollbar: { enabled: true },
      xAxis: { ordinal: false },
      yAxis: { title: { text: null } },
      series: [{
        type: 'line',
        name: 'Price',
        data: series,
        tooltip: { valueDecimals: 0 }
      }]
    };
    try {
      if (id) {
        window.Highcharts.stockChart(id, opts);
      } else {
        window.Highcharts.stockChart(container, opts);
      }
    } catch (e) {
      log('e', 'Failed to render chart', e);
    }
  }

  function initOne(box) {
    try {
      const dataEl = box.querySelector('.GEdataprices');
      const chartEl = box.querySelector('.GEdatachart');
      if (!dataEl || !chartEl) return;
      const itemId = dataEl.getAttribute('data-itemid');
      if (!itemId) return;
      // Prevent duplicate renders
      if (chartEl.dataset.rendered === '1') return;
      chartEl.dataset.rendered = '1';
      fetchSeries(itemId)
        .then(series => {
          if (series.length) renderChart(chartEl, series);
          else log('w', 'No series data for item', itemId);
        })
        .catch(err => log('e', `fetchSeries failed for ${itemId}`, err));
    } catch (e) {
      log('e', 'initOne error', e);
    }
  }

  function initAll() {
    if (!document || !document.body) return;
    const boxes = document.querySelectorAll('.GEChartBox');
    if (!boxes || boxes.length === 0) return;
    if (!window.Highcharts) {
      log('w', 'Highcharts not yet loaded; delaying init');
      setTimeout(initAll, 100);
      return;
    }
    boxes.forEach(initOne);
  }

  function ready(fn) {
    if (document.readyState === 'complete' || document.readyState === 'interactive') {
      setTimeout(fn, 0);
    } else {
      document.addEventListener('DOMContentLoaded', fn);
    }
  }

  // Kickoff
  ready(initAll);
  // Also observe late-added content
  const mo = new MutationObserver(() => initAll());
  ready(() => mo.observe(document.body, { childList: true, subtree: true }));
})();

