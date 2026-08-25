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
const VIEWPORT = { width: 390, height: 844 };
const CONTENT_WIDTH_PX = 360;
const CHIP_MAX_PX = 120;

function evidenceDir() {
  return (
    process.env.OSRS_ARTICLE_AUDIO_LAYOUT_EVIDENCE_DIR ||
    process.env.OSRS_SESSION_ARTIFACT_DIR ||
    path.join(path.dirname(scriptPath), "output")
  );
}

async function readAsset(relativePath) {
  return readFile(path.join(ANDROID_ASSETS, relativePath), "utf8");
}

function fixtureBody() {
  return `
<div id="phoneViewport" style="width:${CONTENT_WIDTH_PX}px;max-width:${CONTENT_WIDTH_PX}px;box-sizing:border-box;">
  <div id="bodyContent" class="mw-body-content">
    <div class="mw-parser-output" id="articleColumn">
      <table class="infobox no-parenthesis-style infobox-music" id="seaShanty">
        <tbody>
          <tr><th class="infobox-header" colspan="5">Sea Shanty 2 <span style="font-size:80%">(#107)</span></th></tr>
          <tr>
            <td class="infobox-full-width-content infobox-media-player" colspan="5" id="mediaCell">
              <span class="mw-default-size" typeof="mw:File">
                <span>
                  <audio id="seaShantyAudio" class="mw-file-element" controls="" width="300" style="width:300px"
                    data-durationhint="128" preload="none">
                    <source src="https://example.test/Sea_Shanty_2.ogg" type="audio/ogg; codecs=&quot;vorbis&quot;">
                    <source src="https://example.test/Sea_Shanty_2.ogg.mp3" type="audio/mpeg">
                  </audio>
                </span>
              </span>
            </td>
          </tr>
          <tr><th colspan="2">Duration</th><td colspan="3">02:08</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</div>`;
}

async function buildDocument() {
  const aesthetics = await readAsset("styles/android-article-aesthetics.css");
  const fixes = await readAsset("styles/fixes.css");
  const audioJs = await readAsset("web/article_audio_player.js");
  assert.equal(
    /(?:^|\n)\s*module\.exports/.test(audioJs),
    false,
    "shipped article_audio_player.js must not unguarded-export for Node",
  );
  return `<!doctype html>
<html class="theme-osrs-light">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Sea Shanty 2</title>
  <style>
    ${fixes}
    ${aesthetics}
    body { margin: 16px; visibility: visible !important; font-size: 16px; background: #e2dbc8; }
    table.infobox { width: fit-content; max-width: 100%; border-collapse: collapse; }
    td.infobox-media-player { padding: 8px; }
  </style>
</head>
<body class="theme-osrs-light">
  ${fixtureBody()}
  <script>${audioJs}</script>
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

test("Sea Shanty 2 infobox player is full-width shipped chrome, not a stacked extra play chip", async (t) => {
  const dir = evidenceDir();
  await mkdir(dir, { recursive: true });
  const chromium = loadChromium();
  const browser = await chromium.launch({ headless: true });
  try {
    const context = await browser.newContext({
      viewport: VIEWPORT,
      deviceScaleFactor: 2,
      isMobile: true,
    });
    const page = await context.newPage();
    await page.setContent(await buildDocument(), { waitUntil: "load" });

    const metrics = await page.evaluate(() => {
      const rect = (el) => {
        if (!el) return null;
        const r = el.getBoundingClientRect();
        const cs = getComputedStyle(el);
        return {
          w: r.width,
          h: r.height,
          x: r.left,
          y: r.top,
          display: cs.display,
          visibility: cs.visibility,
          opacity: cs.opacity,
        };
      };
      const cell = document.getElementById("mediaCell");
      const infobox = document.getElementById("seaShanty");
      const wrap = document.querySelector(".osrs-article-audio");
      const chrome = document.querySelector(".osrs-article-audio-chrome");
      const playInChrome = chrome && chrome.querySelector(".osrs-article-audio-play");
      const time = chrome && chrome.querySelector(".osrs-article-audio-time");
      const duration = chrome && chrome.querySelector(".osrs-article-audio-duration");
      const seek = chrome && chrome.querySelector(".osrs-article-audio-seek");
      const stackedPlay = [...document.querySelectorAll(".osrs-article-audio-play")].filter(
        (btn) => !chrome || !chrome.contains(btn),
      );
      const stackedVisible = stackedPlay.filter((btn) => {
        const cs = getComputedStyle(btn);
        const r = btn.getBoundingClientRect();
        return cs.display !== "none" && cs.visibility !== "hidden" && Number(cs.opacity) > 0.05 && r.height > 1 && r.width > 1;
      });
      return {
        title: document.title,
        hasEnhance: typeof window.OSRSArticleAudio !== "undefined" && typeof window.OSRSArticleAudio.enhance === "function",
        moduleExports: typeof globalThis.module !== "undefined" && globalThis.module && globalThis.module.exports,
        vw: window.innerWidth,
        cell: rect(cell),
        infobox: rect(infobox),
        wrap: rect(wrap),
        chrome: rect(chrome),
        playInChrome: rect(playInChrome),
        playLabel: playInChrome ? playInChrome.getAttribute("aria-label") : "",
        playVisibleText: playInChrome ? String(playInChrome.innerText || playInChrome.textContent || "").trim() : "",
        playHasSvg: !!(playInChrome && playInChrome.querySelector("svg")),
        time: rect(time),
        timeText: time ? String(time.textContent || "").trim() : "",
        duration: rect(duration),
        durationText: duration ? String(duration.textContent || "").trim() : "",
        seek: rect(seek),
        seekType: seek ? seek.getAttribute("type") || seek.type : "",
        stackedVisibleCount: stackedVisible.length,
        audioControls: document.querySelector("audio") && document.querySelector("audio").hasAttribute("controls"),
      };
    });

    const logPath = path.join(dir, "article-audio-player-layout.json");
    await writeFile(logPath, `${JSON.stringify(metrics, null, 2)}\n`);
    await page.locator("#seaShanty").screenshot({
      path: path.join(dir, "article-audio-player-layout.png"),
    });

    assert.equal(metrics.hasEnhance, true, "shipped script must attach OSRSArticleAudio.enhance on window");
    assert.ok(metrics.chrome, "shipped player chrome must exist");
    assert.ok(metrics.playInChrome, "play/pause must live inside the player chrome");
    assert.equal(metrics.playLabel, "Play audio");
    assert.equal(metrics.playHasSvg, true, "play control must be an icon, not the word Play");
    assert.notEqual(
      metrics.playVisibleText.toLowerCase(),
      "play",
      "visible play control must not be a literal Play label",
    );
    assert.ok(metrics.time, "runtime/time must live inside the player chrome");
    assert.ok(metrics.duration, "duration must live inside the player chrome");
    assert.ok(metrics.seek, "progress/seek must live inside the player chrome");
    assert.equal(metrics.seekType, "range", "seek control must be a range input, not native audio chrome");
    assert.equal(
      metrics.stackedVisibleCount,
      0,
      "extra stacked play above the player must not be displayed when chrome is present",
    );
    assert.equal(
      metrics.audioControls,
      false,
      "native <audio controls> must be dropped so Android compact chrome is not the surface",
    );

    const hostWidth = metrics.infobox?.w || 0;
    assert.ok(hostWidth >= 200, `infobox should be a content column, got ${hostWidth}`);
    assert.equal(metrics.cell?.display, "table-cell", "media cell must stay a table-cell so it can use infobox width");
    assert.ok(
      metrics.chrome.w >= hostWidth * 0.85,
      `chrome width ${metrics.chrome.w} must track infobox width ${hostWidth}, not a shrink-wrapped chip remainder`,
    );
    assert.ok(
      metrics.chrome.w > CHIP_MAX_PX,
      `chrome width ${metrics.chrome.w} is still chip-sized (≤ ${CHIP_MAX_PX}px)`,
    );
    assert.ok(metrics.playInChrome.h >= 20 && metrics.playInChrome.w >= 20, "play control must be visible in chrome");
    assert.ok(metrics.seek.w > 80, `seek width ${metrics.seek.w} must be a progress bar, not a leftover thumb`);
    assert.match(metrics.timeText, /\d+:\d+/);

    t.diagnostic(`wrote ${logPath}`);
    await context.close();
  } finally {
    await browser.close();
  }
});
