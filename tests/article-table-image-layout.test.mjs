import assert from "node:assert/strict";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import test from "node:test";

const scriptPath = fileURLToPath(import.meta.url);
const repoRoot = path.resolve(path.dirname(scriptPath), "../../..");
const androidPkg = path.join(repoRoot, "platforms/android/package.json");
const androidRequire = createRequire(androidPkg);

const ANDROID_ASSETS = path.join(repoRoot, "platforms/android/app/src/main/assets");
const IOS_ASSETS = path.join(repoRoot, "platforms/ios/osrswiki/Assets");

const CRITICAL_STYLES = [
  "styles/themes.css",
  "styles/base.css",
  "styles/fonts.css",
  "styles/layout.css",
  "styles/components.css",
  "web/collapsible_tables.css",
  "web/collapsible_sections.css",
  "web/switch_infobox_styles.css",
  "styles/gadget_calc.css",
  "styles/fixes.css",
];

const DEFERRED_SHARED = ["styles/wiki-integration.css", "styles/navbox_styles.css"];

const PLATFORMS = {
  android: {
    assets: ANDROID_ASSETS,
    aesthetics: "styles/android-article-aesthetics.css",
  },
  ios: {
    assets: IOS_ASSETS,
    aesthetics: "styles/ios-article-aesthetics.css",
  },
};

const VIEWPORT = { width: 375, height: 812 };
const MAX_OVERFLOW_PX = 2;
const ASPECT_EPSILON = 0.08;
const AREA_CAP_RATIO = 0.5;

function evidenceDir() {
  return (
    process.env.OSRS_TABLE_IMAGE_LAYOUT_EVIDENCE_DIR ||
    process.env.OSRS_SESSION_ARTIFACT_DIR ||
    path.join(path.dirname(scriptPath), "output")
  );
}

async function readAsset(assetRoot, relativePath) {
  return readFile(path.join(assetRoot, relativePath), "utf8");
}

function svgDataUri(width, height, fill) {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><rect width="100%" height="100%" fill="${fill}"/></svg>`;
  return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

function kartographerCell({ id, caption, color }) {
  return `<td>
    <div class="mw-kartographer-container thumb tnone center">
      <div class="thumbinner" style="width: 300px;">
        <a id="${id}" class="mw-kartographer-map"
          style="width: 300px; height: 300px; background:${color}; display:block;"
          data-mw-kartographer="" data-width="300" data-height="300"></a>
        <div class="thumbcaption">${caption}</div>
      </div>
    </div>
  </td>`;
}

function seaShantyVersionsTable() {
  return `
    <table id="sea-versions" class="wikitable embed-audio-links" style="max-width:580px">
      <tbody>
        <tr><th>Version</th><th>Date</th><th>Music</th></tr>
        <tr>
          <td>1</td>
          <td id="sea-date-v1" style="max-width: 130px"><a title="1 June">1&nbsp;June</a>&nbsp;<a title="2004">2004</a></td>
          <td style="width: 312px"><div style="width:300px;height:24px;background:#888;"></div></td>
        </tr>
        <tr><td colspan="3">Little percussion changes</td></tr>
        <tr>
          <td>2</td>
          <td id="sea-date-v2" style="max-width: 130px">17 May – 6 June 2005</td>
          <td style="width: 312px"><div style="width:300px;height:24px;background:#888;"></div></td>
        </tr>
        <tr>
          <td>3</td>
          <td id="sea-date-v3" style="max-width: 130px"><a title="26 September">26&nbsp;September</a>&nbsp;<a title="2005">2005</a></td>
          <td><i>Unreleased</i></td>
        </tr>
        <tr><td colspan="3">No audible change</td></tr>
        <tr>
          <td>4</td>
          <td id="sea-date-v4" style="max-width: 130px"><a title="26 September">26&nbsp;September</a>&nbsp;<a title="2005">2005</a></td>
          <td style="width: 312px"><div style="width:300px;height:24px;background:#888;"></div></td>
        </tr>
      </tbody>
    </table>`;
}

function harmonyVersionsTable() {
  return `
    <table id="harmony-versions" class="wikitable embed-audio-links">
      <tbody>
        <tr><th>Version</th><th>Release</th><th>Music Track</th></tr>
        <tr>
          <td>1</td>
          <td id="harmony-date-v1"><a title="1 June">1 June</a> <a title="2004">2004</a></td>
          <td style="width: 312px"><div style="width:300px;height:24px;background:#888;"></div></td>
        </tr>
        <tr>
          <td>3</td>
          <td id="harmony-date-v3" class="nowrap">7 September – 5 October 2017</td>
          <td style="width: 312px"><div style="width:300px;height:24px;background:#888;"></div></td>
        </tr>
        <tr>
          <td>4</td>
          <td id="harmony-date-v4"><a title="26 September">26&nbsp;September</a>&nbsp;<a title="2005">2005</a></td>
          <td style="width: 312px"><div style="width:300px;height:24px;background:#888;"></div></td>
        </tr>
      </tbody>
    </table>`;
}

function locationTable(tableId, firstId, secondId) {
  return `
    <table id="${tableId}" class="floatleft">
      <tbody><tr>
        ${kartographerCell({ id: firstId, caption: "Location before 2021", color: "#5a7a4a" })}
        ${kartographerCell({ id: secondId, caption: "Location on Modern mode", color: "#4a6a8a" })}
      </tr></tbody>
    </table>`;
}

function galleryPair() {
  const srcA = svgDataUri(300, 200, "#6b4f2a");
  const srcB = svgDataUri(300, 200, "#2a4f6b");
  return `
    <ul id="lead-gallery" class="gallery mw-gallery-traditional">
      <li class="gallerybox" style="width: 335px">
        <div style="width: 335px">
          <div class="thumb" style="width: 330px;">
            <div style="margin:15px auto;">
              <img id="gallery-img-a" class="mw-file-element" src="${srcA}" width="300" height="200" alt="gallery a">
            </div>
          </div>
          <div class="gallerytext">Lead west</div>
        </div>
      </li>
      <li class="gallerybox" style="width: 335px">
        <div style="width: 335px">
          <div class="thumb" style="width: 330px;">
            <div style="margin:15px auto;">
              <img id="gallery-img-b" class="mw-file-element" src="${srcB}" width="300" height="200" alt="gallery b">
            </div>
          </div>
          <div class="gallerytext">Lead east</div>
        </div>
      </li>
    </ul>`;
}

function largeContentImage() {
  const src = svgDataUri(800, 800, "#3d2b1f");
  return `
    <figure id="large-content-figure" typeof="mw:File/Thumb">
      <a>
        <img id="large-content-image" src="${src}" width="800" height="800" alt="large scenic">
      </a>
      <figcaption>Large scenic that must honor the 50% viewport-area cap</figcaption>
    </figure>`;
}

async function buildDocument({ assetRoot, aesthetics, themeClass, polish, tableNormalize, imageAreaCap }) {
  const sheets = [...CRITICAL_STYLES, ...DEFERRED_SHARED, aesthetics];
  const css = (await Promise.all(sheets.map((rel) => readAsset(assetRoot, rel)))).join("\n\n");
  return `<!doctype html>
<html class="${themeClass}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Table and image layout fixture</title>
  <style>${css}</style>
  <style>
    html, body { margin: 0; width: 375px; }
    body { visibility: visible !important; font-size: 16px; padding: 12px; box-sizing: border-box; }
    .mw-parser-output { max-width: 100%; }
  </style>
</head>
<body class="${themeClass}">
  <div id="bodyContent" class="mw-body-content">
    <div class="mw-parser-output">
      <div class="mw-heading mw-heading2"><h2 id="Versions">Versions</h2></div>
      ${seaShantyVersionsTable()}
      ${harmonyVersionsTable()}
      <div class="mw-heading mw-heading2"><h2 id="Location">Location</h2></div>
      ${locationTable("sea-location", "sea-map-a", "sea-map-b")}
      ${locationTable("harmony-location", "harmony-map-a", "harmony-map-b")}
      ${galleryPair()}
      ${largeContentImage()}
    </div>
  </div>
  <script>${tableNormalize}</script>
  <script>${imageAreaCap}</script>
  <script>${polish}</script>
</body>
</html>`;
}

function loadChromium() {
  try {
    const { chromium } = androidRequire("@playwright/test");
    return chromium;
  } catch (error) {
    error.playwrightMissing = true;
    throw error;
  }
}

async function recordPlaywrightUnavailable(error) {
  const dir = evidenceDir();
  await mkdir(dir, { recursive: true });
  const logPath = path.join(dir, "playwright-unavailable.log");
  await writeFile(
    logPath,
    `${new Date().toISOString()}\n${error && error.stack ? error.stack : error}\n`,
  );
  return logPath;
}

async function measure(page) {
  return page.evaluate(() => {
    function textRects(el) {
      const range = document.createRange();
      range.selectNodeContents(el);
      return [...range.getClientRects()].map((r) => ({
        left: r.left,
        right: r.right,
        top: r.top,
        bottom: r.bottom,
        width: r.width,
        height: r.height,
      }));
    }
    function cellMetrics(id) {
      const cell = document.getElementById(id);
      const box = cell.getBoundingClientRect();
      const next = cell.nextElementSibling;
      const nextBox = next ? next.getBoundingClientRect() : null;
      const table = cell.closest("table");
      const tableBox = table.getBoundingClientRect();
      return {
        id,
        text: (cell.textContent || "").replace(/\s+/g, " ").trim(),
        whiteSpace: getComputedStyle(cell).whiteSpace,
        cell: { left: box.left, right: box.right, top: box.top, bottom: box.bottom, width: box.width, height: box.height },
        next: nextBox
          ? { left: nextBox.left, right: nextBox.right, top: nextBox.top, bottom: nextBox.bottom }
          : null,
        tableRight: tableBox.right,
        tableWidth: tableBox.width,
        rects: textRects(cell),
      };
    }
    function imageMetrics(id) {
      const el = document.getElementById(id);
      const box = el.getBoundingClientRect();
      const attrW = Number(el.getAttribute("width") || el.getAttribute("data-width") || 0);
      const attrH = Number(el.getAttribute("height") || el.getAttribute("data-height") || 0);
      const naturalW = el.naturalWidth || attrW;
      const naturalH = el.naturalHeight || attrH;
      return {
        id,
        left: box.left,
        right: box.right,
        top: box.top,
        bottom: box.bottom,
        width: box.width,
        height: box.height,
        attrW,
        attrH,
        naturalW,
        naturalH,
        areaCapped: el.dataset.osrsAreaCapped === "true",
      };
    }
    if (typeof window.OSRSApplyArticlePolish === "function") {
      window.OSRSApplyArticlePolish();
    }
    if (window.OSRSImageAreaCap && typeof window.OSRSImageAreaCap.process === "function") {
      window.OSRSImageAreaCap.process();
    }
    const viewport = {
      width: document.documentElement.clientWidth,
      height: document.documentElement.clientHeight,
    };
    return {
      viewport,
      seaDates: ["sea-date-v2", "sea-date-v3", "sea-date-v4"].map(cellMetrics),
      harmonyDates: ["harmony-date-v1", "harmony-date-v3", "harmony-date-v4"].map(cellMetrics),
      seaMaps: ["sea-map-a", "sea-map-b"].map(imageMetrics),
      harmonyMaps: ["harmony-map-a", "harmony-map-b"].map(imageMetrics),
      gallery: ["gallery-img-a", "gallery-img-b"].map(imageMetrics),
      large: imageMetrics("large-content-image"),
      seaLocationTable: document.getElementById("sea-location").className,
    };
  });
}

function assertTextInsideCell(label, cell, tolerance) {
  assert.ok(cell.rects.length > 0, `${label}: missing text rects`);
  for (const rect of cell.rects) {
    assert.ok(
      rect.left >= cell.cell.left - tolerance,
      `${label}: text left ${rect.left.toFixed(1)} bleeds past cell left ${cell.cell.left.toFixed(1)}`,
    );
    assert.ok(
      rect.right <= cell.cell.right + tolerance,
      `${label}: text right ${rect.right.toFixed(1)} bleeds past cell right ${cell.cell.right.toFixed(1)}`,
    );
    assert.ok(
      rect.right <= cell.tableRight + tolerance,
      `${label}: text right ${rect.right.toFixed(1)} past table ${cell.tableRight.toFixed(1)}`,
    );
    if (cell.next) {
      assert.ok(
        rect.right <= cell.next.left + tolerance,
        `${label}: text right ${rect.right.toFixed(1)} overlaps next cell at ${cell.next.left.toFixed(1)}`,
      );
    }
  }
}

function assertImageInViewport(label, image, viewport, tolerance) {
  assert.ok(image.width > 0 && image.height > 0, `${label}: missing box`);
  assert.ok(
    image.right <= viewport.width + tolerance,
    `${label}: right edge ${image.right.toFixed(1)} exceeds viewport ${viewport.width}`,
  );
  assert.ok(
    image.left >= -tolerance,
    `${label}: left edge ${image.left.toFixed(1)} is off-screen`,
  );
}

function assertAspect(label, image) {
  const srcW = image.naturalW || image.attrW;
  const srcH = image.naturalH || image.attrH;
  assert.ok(srcW > 0 && srcH > 0, `${label}: missing source size`);
  const expected = srcW / srcH;
  const actual = image.width / image.height;
  assert.ok(
    Math.abs(actual - expected) <= ASPECT_EPSILON,
    `${label}: aspect ${actual.toFixed(3)} vs source ${expected.toFixed(3)}`,
  );
}

function assertLaunchPair(label, first, second) {
  const keys = ["width", "height", "right"];
  for (const key of keys) {
    assert.ok(
      Math.abs(first[key] - second[key]) <= 1,
      `${label}: ${key} inconsistent across launches (${first[key]} vs ${second[key]})`,
    );
  }
}

test("shared article CSS keeps date cells contained and image rows in-viewport", async (t) => {
  let chromium;
  try {
    chromium = loadChromium();
  } catch (error) {
    const logPath = await recordPlaywrightUnavailable(error);
    assert.fail(`Playwright/Chromium unavailable; wrote ${logPath}: ${error}`);
  }

  const dir = evidenceDir();
  await mkdir(dir, { recursive: true });
  const allMetrics = [];
  let browser;
  try {
    browser = await chromium.launch({ headless: true });
  } catch (error) {
    const logPath = await recordPlaywrightUnavailable(error);
    assert.fail(`Chromium failed to launch; wrote ${logPath}: ${error}`);
  }

  try {
    for (const [platform, spec] of Object.entries(PLATFORMS)) {
      for (const theme of ["theme-osrs-light", "theme-osrs-dark"]) {
        const polish = await readAsset(spec.assets, "web/mobile_article_polish.js");
        const tableNormalize = await readAsset(spec.assets, "web/table_column_normalize.js");
        const imageAreaCap = await readAsset(spec.assets, "web/image_area_cap.js");
        const html = await buildDocument({
          assetRoot: spec.assets,
          aesthetics: spec.aesthetics,
          themeClass: theme,
          polish,
          tableNormalize,
          imageAreaCap,
        });
        const launches = [];
        for (let launch = 0; launch < 2; launch += 1) {
          const context = await browser.newContext({
            viewport: VIEWPORT,
            deviceScaleFactor: 2,
            isMobile: true,
          });
          const page = await context.newPage();
          await page.setContent(html, { waitUntil: "load" });
          await page.waitForFunction(() => {
            const img = document.getElementById("large-content-image");
            return img && (img.naturalWidth > 0 || img.complete);
          });
          const metrics = await measure(page);
          launches.push(metrics);

          const caseId = `${platform}-${theme.replace("theme-osrs-", "")}`;
          if (launch === 1) {
            await page.locator("#sea-versions").screenshot({
              path: path.join(dir, `sea-shanty-2-versions-${caseId}.png`),
            });
            await page.locator("#sea-location").screenshot({
              path: path.join(dir, `sea-shanty-2-location-${caseId}.png`),
            });
            await page.locator("#harmony-versions").screenshot({
              path: path.join(dir, `harmony-versions-${caseId}.png`),
            });
            await page.locator("#harmony-location").screenshot({
              path: path.join(dir, `harmony-location-${caseId}.png`),
            });
            if (caseId === "android-light") {
              await page.screenshot({
                path: path.join(dir, "article-table-image-layout.png"),
                fullPage: true,
              });
            }
          }
          await context.close();
        }

        const metrics = launches[1];
        const caseId = `${platform}-${theme.replace("theme-osrs-", "")}`;
        allMetrics.push({ caseId, ...metrics });

        for (const cell of metrics.seaDates) {
          assertTextInsideCell(`${caseId} Sea Shanty 2 ${cell.id}`, cell, MAX_OVERFLOW_PX);
        }
        for (const cell of metrics.harmonyDates) {
          assertTextInsideCell(`${caseId} Harmony ${cell.id}`, cell, MAX_OVERFLOW_PX);
        }

        const images = [
          ...metrics.seaMaps.map((img) => [`${caseId} Sea location ${img.id}`, img]),
          ...metrics.harmonyMaps.map((img) => [`${caseId} Harmony location ${img.id}`, img]),
          ...metrics.gallery.map((img) => [`${caseId} gallery ${img.id}`, img]),
          [`${caseId} large content`, metrics.large],
        ];
        for (const [label, image] of images) {
          assertImageInViewport(label, image, metrics.viewport, MAX_OVERFLOW_PX);
          assertAspect(label, image);
        }

        const viewportArea = metrics.viewport.width * metrics.viewport.height;
        const largeArea = metrics.large.width * metrics.large.height;
        assert.ok(
          largeArea <= viewportArea * AREA_CAP_RATIO + 1,
          `${caseId}: large image area ${largeArea.toFixed(0)} exceeds 50% of ${viewportArea}`,
        );

        assertLaunchPair(
          `${caseId} sea date v2`,
          launches[0].seaDates[0].cell,
          launches[1].seaDates[0].cell,
        );
        assertLaunchPair(`${caseId} sea map b`, launches[0].seaMaps[1], launches[1].seaMaps[1]);
        assertLaunchPair(`${caseId} large`, launches[0].large, launches[1].large);
      }
    }
  } finally {
    await browser.close();
    await writeFile(
      path.join(dir, "article-table-image-layout.json"),
      `${JSON.stringify(allMetrics, null, 2)}\n`,
    );
  }

  t.diagnostic(`wrote evidence to ${dir}`);
});
