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

const VIEWPORT = { width: 390, height: 844 };
const MAX_EXTRA_LINE_BOX_PX = 10;
const MAX_REFERENCE_ITEM_EM = 3.2;

function evidenceDir() {
  return (
    process.env.OSRS_SUP_DESCENDER_EVIDENCE_DIR ||
    process.env.OSRS_SESSION_ARTIFACT_DIR ||
    path.join(path.dirname(scriptPath), "output")
  );
}

async function readAsset(assetRoot, relativePath) {
  return readFile(path.join(assetRoot, relativePath), "utf8");
}

function fixtureBody() {
  return `
<div id="bodyContent" class="mw-body-content">
  <div class="mw-parser-output">
    <p id="wrap-cite-prose">
      hanging pygmy hanging pygmy hanging pygmy hanging pygmy hanging pygmy hanging pygmy hanging pygmy hanging pygmy
      <span id="wrap-descender">pyggy</span>
      <sup id="wrap-cite" class="reference"><a href="#cite_note-1">[1]</a></sup>
      hanging pygmy season notes continue after the citation with more hanging pygmy glyphs.
    </p>
    <p id="wrap-nocite-twin">
      hanging pygmy hanging pygmy hanging pygmy hanging pygmy hanging pygmy hanging pygmy hanging pygmy hanging pygmy
      <span>pyggy</span>
      hanging pygmy season notes continue after the citation with more hanging pygmy glyphs.
    </p>
    <p id="forced-break-prose">
      Archive notes hanging pygmy hanging pygmy hanging pygmy
      <span id="forced-descender">pyggy hanging</span><br>
      <sup id="forced-cite" class="reference"><a href="#cite_note-2">[2]</a></sup>
      continues with hanging pygmy text after the cite.
    </p>
    <p id="archive-cluster-prose">
      Dense hanging pygmy patch notes hanging pygmy hanging pygmy
      <span id="cluster-descender">gyp hanging</span><br>
      <sup id="cluster-cite-1" class="reference"><a href="#cite_note-3">[1]</a></sup><sup id="cluster-cite-2" class="reference"><a href="#cite_note-4">[2]</a></sup><sup id="cluster-cite-3" class="reference"><a href="#cite_note-5">[3]</a></sup>
      hanging pygmy follow-up text.
    </p>
    <ol class="references" id="footnote-list">
      <li id="cite_note-1">
        <span class="mw-cite-backlink"><a href="#wrap-cite">↑</a></span>
        <span class="reference-text">News post, 6 August 2007.</span>
      </li>
      <li id="cite_note-2">
        <span class="mw-cite-backlink"><sup class="reference"><a href="#forced-cite">[2]</a></sup></span>
        <span class="reference-text">Patch notes archive entry.</span>
      </li>
      <li id="cite_note-3">
        <span class="mw-cite-backlink"><a href="#cluster-cite-1">↑</a></span>
        <span class="reference-text">Third-party citation.</span>
      </li>
    </ol>
  </div>
</div>`;
}

async function buildDocument({ assetRoot, aesthetics, themeClass }) {
  const sheets = [...CRITICAL_STYLES, ...DEFERRED_SHARED, aesthetics];
  const css = (await Promise.all(sheets.map((rel) => readAsset(assetRoot, rel)))).join(
    "\n\n",
  );
  return `<!doctype html>
<html class="${themeClass}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Citation descender fixture</title>
  <style>${css}</style>
  <style>
    body { margin: 16px; visibility: visible !important; font-size: 16px; }
    .mw-parser-output { max-width: 22rem; }
  </style>
</head>
<body class="${themeClass}">
  ${fixtureBody()}
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

async function measure(page) {
  return page.evaluate(() => {
    const box = (el) => {
      if (!el) return null;
      const r = el.getBoundingClientRect();
      return {
        top: r.top,
        bottom: r.bottom,
        left: r.left,
        right: r.right,
        width: r.width,
        height: r.height,
      };
    };

    const lineBoxes = (el) => {
      const range = document.createRange();
      range.selectNodeContents(el);
      const unique = [];
      for (const r of range.getClientRects()) {
        const prev = unique.find((item) => Math.abs(item.top - r.top) < 0.5);
        if (prev) {
          prev.bottom = Math.max(prev.bottom, r.bottom);
          prev.height = prev.bottom - prev.top;
          continue;
        }
        unique.push({
          top: r.top,
          bottom: r.bottom,
          height: r.height,
        });
      }
      return unique;
    };

    const overlapPx = (above, below) => {
      if (!above || !below) return null;
      return above.bottom - below.top;
    };

    const previousLineBox = (prose, citeEl) => {
      if (!prose || !citeEl) return null;
      const citeTop = citeEl.getBoundingClientRect().top;
      const above = lineBoxes(prose)
        .filter((line) => line.top < citeTop - 2)
        .sort((a, b) => b.top - a.top);
      return above[0] || null;
    };

    const wrapCite = document.getElementById("wrap-cite");
    const wrapDescender = document.getElementById("wrap-descender");
    const wrapProse = document.getElementById("wrap-cite-prose");
    const nocite = document.getElementById("wrap-nocite-twin");
    const forcedCite = document.getElementById("forced-cite");
    const forcedDescender = document.getElementById("forced-descender");
    const forcedProse = document.getElementById("forced-break-prose");
    const clusterProse = document.getElementById("archive-cluster-prose");
    const clusterCites = [
      document.getElementById("cluster-cite-1"),
      document.getElementById("cluster-cite-2"),
      document.getElementById("cluster-cite-3"),
    ];
    const clusterDescender = document.getElementById("cluster-descender");
    const footnoteItems = [...document.querySelectorAll("#footnote-list > li")];
    const wrapCiteStyle = wrapCite ? getComputedStyle(wrapCite) : null;
    const wrapLines = wrapProse ? lineBoxes(wrapProse) : [];
    const nociteLines = nocite ? lineBoxes(nocite) : [];
    const maxWrapLine = wrapLines.reduce((m, l) => Math.max(m, l.height), 0);
    const maxNociteLine = nociteLines.reduce((m, l) => Math.max(m, l.height), 0);
    const listFontSize = parseFloat(
      getComputedStyle(document.getElementById("footnote-list")).fontSize,
    );

    const wrapPrev = previousLineBox(wrapProse, wrapCite);
    const forcedPrev = previousLineBox(forcedProse, forcedCite);
    const clusterPrev = previousLineBox(clusterProse, clusterCites[0]);

    return {
      wrap: {
        cite: box(wrapCite),
        descender: box(wrapDescender),
        previousLine: wrapPrev,
        overlapPx: overlapPx(wrapPrev, box(wrapCite)),
        differentLines:
          wrapCite && wrapPrev
            ? wrapCite.getBoundingClientRect().top - wrapPrev.top > 4
            : false,
        computed: wrapCiteStyle
          ? {
              top: wrapCiteStyle.top,
              lineHeight: wrapCiteStyle.lineHeight,
              verticalAlign: wrapCiteStyle.verticalAlign,
              position: wrapCiteStyle.position,
              fontSize: wrapCiteStyle.fontSize,
            }
          : null,
      },
      forced: {
        cite: box(forcedCite),
        descender: box(forcedDescender),
        previousLine: forcedPrev,
        overlapPx: overlapPx(forcedPrev, box(forcedCite)),
      },
      cluster: clusterCites.map((cite) => ({
        cite: box(cite),
        descender: box(clusterDescender),
        previousLine: clusterPrev,
        overlapPx: overlapPx(clusterPrev, box(cite)),
      })),
      leading: {
        wrapParagraphHeight: box(wrapProse)?.height ?? null,
        nociteParagraphHeight: box(nocite)?.height ?? null,
        extraParagraphPx:
          box(wrapProse) && box(nocite)
            ? box(wrapProse).height - box(nocite).height
            : null,
        maxWrapLinePx: maxWrapLine,
        maxNociteLinePx: maxNociteLine,
        extraLineBoxPx: maxWrapLine - maxNociteLine,
      },
      references: {
        itemBoxes: footnoteItems.map((li) => box(li)),
        itemHeights: footnoteItems.map((li) => li.getBoundingClientRect().height),
        stacked: footnoteItems.every((li, i, arr) => {
          if (i === 0) return true;
          return li.getBoundingClientRect().top + 0.5 >= arr[i - 1].getBoundingClientRect().bottom;
        }),
        fontSizePx: listFontSize,
      },
    };
  });
}

function assertClearance(label, overlapPx) {
  assert.ok(
    overlapPx != null,
    `${label}: missing boxes so overlap could not be measured`,
  );
  assert.ok(
    overlapPx <= 0,
    `${label}: citation overlaps previous-line descender by ${overlapPx.toFixed(2)}px`,
  );
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

test("inline citation superscripts stay below previous-line descenders", async (t) => {
  let chromium;
  try {
    chromium = loadChromium();
  } catch (error) {
    const logPath = await recordPlaywrightUnavailable(error);
    assert.fail(`Playwright/Chromium unavailable; wrote ${logPath}: ${error}`);
  }

  const dir = evidenceDir();
  await mkdir(dir, { recursive: true });
  const logLines = [];
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
        const context = await browser.newContext({
          viewport: VIEWPORT,
          deviceScaleFactor: 2,
          isMobile: true,
        });
        const page = await context.newPage();
        const html = await buildDocument({
          assetRoot: spec.assets,
          aesthetics: spec.aesthetics,
          themeClass: theme,
        });
        await page.setContent(html, { waitUntil: "load" });
        const wrapWidth = await page.evaluate(() => {
          const host = document.querySelector(".mw-parser-output");
          const cite = document.getElementById("wrap-cite");
          const desc = document.getElementById("wrap-descender");
          for (let width = 280; width >= 140; width -= 4) {
            host.style.width = `${width}px`;
            const descBox = desc.getBoundingClientRect();
            const citeBox = cite.getBoundingClientRect();
            if (descBox.height <= 22 && citeBox.top - descBox.top > 8) {
              return width;
            }
          }
          return null;
        });
        assert.ok(
          wrapWidth,
          `${platform}-${theme}: could not wrap the citation onto the line after descenders`,
        );
        const metrics = await measure(page);
        const caseId = `${platform}-${theme.replace("theme-osrs-", "")}`;
        logLines.push(JSON.stringify({ caseId, metrics }, null, 2));

        const wrap = await page.locator("#wrap-cite-prose");
        const forced = await page.locator("#forced-break-prose");
        const cluster = await page.locator("#archive-cluster-prose");
        const refs = await page.locator("#footnote-list");
        await wrap.screenshot({ path: path.join(dir, `sup-descender-${caseId}-wrap.png`) });
        await forced.screenshot({
          path: path.join(dir, `sup-descender-${caseId}-forced.png`),
        });
        await cluster.screenshot({
          path: path.join(dir, `sup-descender-${caseId}-cluster.png`),
        });
        await refs.screenshot({ path: path.join(dir, `sup-descender-${caseId}-refs.png`) });
        await page.screenshot({
          path: path.join(dir, `sup-descender-${caseId}.png`),
          fullPage: true,
        });

        assert.equal(
          metrics.wrap.differentLines,
          true,
          `${caseId}: wrap cite must sit on the line after the descender span`,
        );
        assertClearance(`${caseId} wrap`, metrics.wrap.overlapPx);
        assertClearance(`${caseId} forced break`, metrics.forced.overlapPx);
        for (const [index, clusterMetric] of metrics.cluster.entries()) {
          assertClearance(`${caseId} archive cluster [${index + 1}]`, clusterMetric.overlapPx);
        }

        assert.ok(
          metrics.leading.extraLineBoxPx <= MAX_EXTRA_LINE_BOX_PX,
          `${caseId}: extra line-box ${metrics.leading.extraLineBoxPx.toFixed(2)}px exceeds ${MAX_EXTRA_LINE_BOX_PX}px`,
        );
        assert.ok(
          metrics.leading.extraParagraphPx < 22,
          `${caseId}: paragraph grew ${metrics.leading.extraParagraphPx.toFixed(2)}px vs no-cite twin (blank-line sized)`,
        );

        assert.equal(
          metrics.references.stacked,
          true,
          `${caseId}: footnote list items must remain a tight stacked list`,
        );
        const maxItemPx = MAX_REFERENCE_ITEM_EM * metrics.references.fontSizePx;
        for (const [index, height] of metrics.references.itemHeights.entries()) {
          assert.ok(
            height <= maxItemPx,
            `${caseId}: footnote item ${index} height ${height.toFixed(2)}px exceeds ${maxItemPx.toFixed(2)}px`,
          );
        }
        await context.close();
      }
    }
  } finally {
    await browser.close();
    const logPath = path.join(dir, "sup-descender-layout.log");
    await writeFile(logPath, `${logLines.join("\n\n")}\n`);
  }

  t.diagnostic(`wrote evidence to ${dir}`);
});
