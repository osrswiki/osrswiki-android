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
const MIN_BODY_FRACTION_OF_PARAGRAPH = 0.65;
const MIN_LINE_FRACTION_OF_PARAGRAPH = 0.5;
const MAX_MARK_FRACTION_OF_VIEWPORT = 0.28;
const MAX_OVERFLOW_PX = 2;

const SEA_SHANTY_QUOTE =
  'The 60kB limit was still in place when I wrote "Sea Shanty 2", so it was a simple tune like all those early tracks — again, I was going for catchy. It came very quickly to me once I was in the shanty-writing groove. It has remained in the game for well over a decade, and has always enjoyed a degree of notoriety because it\'s a cheesy earworm. Since Old School RuneScape launched in 2013, we came to realise that the fanbase had re-embraced the track, and it has become a meme. There are plenty of silly videos featuring it, Rick-Rolling, etc. Although it makes me cringe a little, I don\'t mind it being a humorous song as it has charm. One of my ambitions in life is to write a novelty track and retire from the proceeds. "Sea Shanty 2" might be my best effort yet, but sadly I\'m not close to retiring...';

const JAGEX_QUOTE =
  "Sadly the game was not as complete as we wanted and we spent the first few months trying to \"fix\" the game where we could. About a month or so ago we took the decision to stop trying to \"fix it\" as we still wouldn't have the game we wanted and the game certainly did not meet all the objectives and specifications established in the original game design document and therefore it would be better to go back to the founding principles and build the game we always wanted.";

function evidenceDir() {
  return (
    process.env.OSRS_QUOTE_WRAP_EVIDENCE_DIR ||
    process.env.OSRS_SESSION_ARTIFACT_DIR ||
    path.join(path.dirname(scriptPath), "output")
  );
}

async function readAsset(assetRoot, relativePath) {
  return readFile(path.join(assetRoot, relativePath), "utf8");
}

function cquote2Table({ tableId, textId, spacerId, citeId, quote, cite }) {
  return `
      <table id="${tableId}" align="center" style="border-collapse:collapse; border-style:none; background-color:transparent;">
        <tbody><tr>
          <td class="quotation-mark" style="width:20px; vertical-align:top; font-size:40px; font-family:serif; font-weight:bold; text-align:left; padding:10px 10px;">“</td>
          <td id="${textId}" style="vertical-align:center; padding:4px 10px;">${quote}</td>
          <td class="quotation-mark" style="width:20px; vertical-align:bottom; font-size:40px; font-family:serif; font-weight:bold; text-align:right; padding:10px 10px;">”</td>
        </tr>
        <tr>
          <td id="${spacerId}">&nbsp;</td>
          <td id="${citeId}" style="vertical-align:top"><div style="line-height:1em;text-align: right"><cite style="font-style:normal;">${cite}</cite></div></td>
        </tr></tbody>
      </table>`;
}

async function buildDocument({ assetRoot, aesthetics, themeClass, polish, interceptor }) {
  const sheets = [...CRITICAL_STYLES, ...DEFERRED_SHARED, aesthetics];
  const css = (await Promise.all(sheets.map((rel) => readAsset(assetRoot, rel)))).join(
    "\n\n",
  );
  return `<!doctype html>
<html class="${themeClass}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Quote wrap fixture</title>
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
      <p id="lead-para">Ian Taylor later commented on its fame:</p>
      ${cquote2Table({
        tableId: "sea-shanty-quote",
        textId: "sea-quote-text",
        spacerId: "sea-quote-spacer",
        citeId: "sea-quote-cite",
        quote: SEA_SHANTY_QUOTE,
        cite: "— Ian Taylor",
      })}
      <div id="toc" class="toc"><div class="toctitle"><h2>Contents</h2></div><ul><li>Versions</li></ul></div>
      <p id="jagex-lead">Mark Gerhard later commented:</p>
      ${cquote2Table({
        tableId: "jagex-quote",
        textId: "jagex-quote-text",
        spacerId: "jagex-quote-spacer",
        citeId: "jagex-quote-cite",
        quote: JAGEX_QUOTE,
        cite: "— Mark Gerhard, 25 October 2009",
      })}
      <blockquote id="quote-block">${SEA_SHANTY_QUOTE}</blockquote>
      <pre id="code-sample"><code>UNBROKEN_TOKEN_THAT_SHOULD_STILL_BE_ALLOWED_TO_OVERFLOW_HORIZONTALLY_WHEN_IT_CANNOT_WRAP_BECAUSE_IT_HAS_NO_SPACES_OR_BREAKS_0123456789_ABCDEFGHIJKLMNOPQRSTUVWXYZ</code></pre>
    </div>
  </div>
  <script>${polish}</script>
  <script>${interceptor}</script>
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
    function lineBoxHeight(el) {
      const cs = getComputedStyle(el);
      const parsed = parseFloat(cs.lineHeight);
      if (cs.lineHeight !== "normal" && Number.isFinite(parsed)) return parsed;
      return parseFloat(cs.fontSize) * 1.2;
    }
    function lineBoxes(el) {
      const range = document.createRange();
      range.selectNodeContents(el);
      const unique = [];
      for (const r of range.getClientRects()) {
        const prev = unique.find((item) => Math.abs(item.top - r.top) < 0.5);
        if (prev) {
          prev.width = Math.max(prev.width, r.width);
          continue;
        }
        unique.push({ top: r.top, width: r.width, height: r.height });
      }
      return unique;
    }
    function measureQuote(tableId, textId, spacerId) {
      const table = document.getElementById(tableId);
      const cell = document.getElementById(textId);
      const spacer = document.getElementById(spacerId);
      const marks = [...table.querySelectorAll("td.quotation-mark")];
      const cellCs = getComputedStyle(cell);
      const lines = lineBoxes(cell);
      return {
        tableWidth: table.getBoundingClientRect().width,
        tableOverflow: table.scrollWidth - table.clientWidth,
        tableSurface: !!(
          table.closest(".osrs-local-scroll-surface") ||
          (table.parentElement &&
            /osrs-local-scroll-surface|osrs-article-scroll-region/.test(
              table.parentElement.className || "",
            ))
        ),
        cellWidth: cell.getBoundingClientRect().width,
        cellHeight: cell.getBoundingClientRect().height,
        cellLine: lineBoxHeight(cell),
        cellWhiteSpace: cellCs.whiteSpace,
        cellMaxLineWidth: lines.reduce((m, l) => Math.max(m, l.width), 0),
        cellLineCount: lines.length,
        markWidths: marks.map((el) => el.getBoundingClientRect().width),
        spacerWidth: spacer ? spacer.getBoundingClientRect().width : null,
      };
    }
    if (typeof window.OSRSApplyArticlePolish === "function") {
      window.OSRSApplyArticlePolish();
    }
    const para = document.getElementById("lead-para");
    const block = document.getElementById("quote-block");
    const pre = document.getElementById("code-sample");
    const blockCs = getComputedStyle(block);
    const blockLines = lineBoxes(block);
    return {
      viewport: document.documentElement.clientWidth,
      paraWidth: para.getBoundingClientRect().width,
      rootOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      sea: measureQuote("sea-shanty-quote", "sea-quote-text", "sea-quote-spacer"),
      jagex: measureQuote("jagex-quote", "jagex-quote-text", "jagex-quote-spacer"),
      block: {
        width: block.getBoundingClientRect().width,
        height: block.getBoundingClientRect().height,
        line: lineBoxHeight(block),
        whiteSpace: blockCs.whiteSpace,
        overflow: block.scrollWidth - block.clientWidth,
        maxLineWidth: blockLines.reduce((m, l) => Math.max(m, l.width), 0),
        surface: !!block.closest(".osrs-local-scroll-surface"),
      },
      preOverflowX: getComputedStyle(pre).overflowX,
    };
  });
}

function assertParagraphWidthQuote(label, quote, paraWidth, viewport) {
  assert.ok(paraWidth > 0, `${label}: missing sibling paragraph width`);
  assert.ok(
    quote.cellWidth >= paraWidth * MIN_BODY_FRACTION_OF_PARAGRAPH,
    `${label}: quote body ${quote.cellWidth.toFixed(1)}px is a rail vs paragraph ${paraWidth.toFixed(1)}px`,
  );
  assert.ok(
    quote.cellMaxLineWidth >= paraWidth * MIN_LINE_FRACTION_OF_PARAGRAPH,
    `${label}: longest quote line ${quote.cellMaxLineWidth.toFixed(1)}px is not paragraph-width`,
  );
  const maxMark = Math.max(...quote.markWidths, 0);
  assert.ok(
    maxMark <= viewport * MAX_MARK_FRACTION_OF_VIEWPORT,
    `${label}: quotation-mark column ${maxMark.toFixed(1)}px absorbed leftover width`,
  );
  assert.ok(
    quote.cellWidth > maxMark * 2,
    `${label}: quote body must be wider than decorative quote-mark columns`,
  );
  assert.notEqual(quote.cellWhiteSpace, "nowrap", `${label}: quote body must wrap`);
  assert.ok(
    quote.cellHeight > quote.cellLine * 1.5,
    `${label}: long quote must wrap onto more than one line`,
  );
  assert.ok(
    quote.tableOverflow <= MAX_OVERFLOW_PX,
    `${label}: quote table overflow ${quote.tableOverflow}px`,
  );
  assert.equal(quote.tableSurface, false, `${label}: quote table must not become a scroll surface`);
}

test("wiki quote boxes wrap at paragraph width, not a 1-2 letter rail", async (t) => {
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
        const interceptor = await readAsset(
          spec.assets,
          "web/horizontal_scroll_interceptor.js",
        );
        const html = await buildDocument({
          assetRoot: spec.assets,
          aesthetics: spec.aesthetics,
          themeClass: theme,
          polish,
          interceptor,
        });
        const context = await browser.newContext({
          viewport: VIEWPORT,
          deviceScaleFactor: 2,
          isMobile: true,
        });
        const page = await context.newPage();
        await page.setContent(html, { waitUntil: "load" });
        const metrics = await measure(page);
        const caseId = `${platform}-${theme.replace("theme-osrs-", "")}`;
        allMetrics.push({ caseId, ...metrics });

        await page.locator("#sea-shanty-quote").screenshot({
          path: path.join(dir, `sea-shanty-2-${caseId}.png`),
        });
        await page.locator("#jagex-quote").screenshot({
          path: path.join(dir, `second-quote-${caseId}.png`),
        });
        if (caseId === "android-light") {
          await page.screenshot({
            path: path.join(dir, "quote-wrap-layout.png"),
            fullPage: true,
          });
        }

        assertParagraphWidthQuote(`${caseId} Sea shanty 2`, metrics.sea, metrics.paraWidth, metrics.viewport);
        assertParagraphWidthQuote(`${caseId} Jagex`, metrics.jagex, metrics.paraWidth, metrics.viewport);
        assert.ok(
          metrics.block.width >= metrics.paraWidth * 0.85,
          `${caseId}: blockquote ${metrics.block.width.toFixed(1)}px must stay near paragraph width`,
        );
        assert.notEqual(metrics.block.whiteSpace, "nowrap", `${caseId}: blockquote must wrap`);
        assert.ok(
          metrics.block.height > metrics.block.line * 1.5,
          `${caseId}: blockquote must wrap onto more than one line`,
        );
        assert.ok(
          metrics.block.overflow <= MAX_OVERFLOW_PX,
          `${caseId}: blockquote overflow ${metrics.block.overflow}px`,
        );
        assert.equal(metrics.block.surface, false, `${caseId}: blockquote is not a scroll surface`);
        const preOverflow = metrics.preOverflowX;
        assert.ok(
          preOverflow === "auto" || preOverflow === "scroll",
          `${caseId}: pre/code must keep overflow-x, got ${preOverflow}`,
        );
        assert.ok(metrics.rootOverflow <= 8, `${caseId}: page overflow ${metrics.rootOverflow}px`);
        await context.close();
      }
    }
  } finally {
    await browser.close();
    await writeFile(
      path.join(dir, "quote-wrap-layout.json"),
      `${JSON.stringify(allMetrics, null, 2)}\n`,
    );
  }

  t.diagnostic(`wrote evidence to ${dir}`);
});
