// Runs in the extension's content-script world: the page can't see or call it.
(function () {
  "use strict";
  if (window.top !== window) return;
  const port = browser.runtime.connectNative("airlock");

  const ALLOWED = new Set(["P", "BR", "HR", "H1", "H2", "H3", "H4", "H5", "H6", "UL", "OL", "LI", "BLOCKQUOTE", "PRE",
    "CODE", "EM", "I", "STRONG", "B", "A", "IMG", "FIGURE", "FIGCAPTION", "TABLE", "THEAD", "TBODY", "TR", "TH", "TD",
    "SUP", "SUB", "DL", "DT", "DD", "DEL", "S", "MARK", "SMALL", "DIV", "SPAN", "SECTION"]);
  const DROP = new Set(["SCRIPT", "STYLE", "NOSCRIPT", "IFRAME", "OBJECT", "EMBED", "FORM", "BUTTON", "INPUT", "SELECT",
    "TEXTAREA", "SVG", "MATH", "TEMPLATE", "VIDEO", "AUDIO", "CANVAS", "SOURCE"]);

  function webUrl(v) {
    try { const u = new URL(v, location.href); return (u.protocol === "https:" || u.protocol === "http:") ? u.href : null; }
    catch (e) { return null; }
  }

  // Only a fixed set of tags and attributes survive; everything else is unwrapped or dropped.
  function clean(node, doc) {
    const out = doc.createDocumentFragment();
    for (const child of Array.from(node.childNodes)) {
      if (child.nodeType === 3) { out.appendChild(doc.createTextNode(child.nodeValue)); continue; }
      if (child.nodeType !== 1 || DROP.has(child.tagName)) continue;
      if (!ALLOWED.has(child.tagName)) { out.appendChild(clean(child, doc)); continue; }
      const el = doc.createElement(child.tagName.toLowerCase());
      if (child.tagName === "A") { const h = webUrl(child.getAttribute("href")); if (h) el.setAttribute("href", h); }
      if (child.tagName === "IMG") {
        const s = webUrl(child.getAttribute("src") || child.getAttribute("data-src"));
        if (!s || !s.startsWith("https:")) continue;
        el.setAttribute("src", s); el.setAttribute("alt", child.getAttribute("alt") || "");
      }
      el.appendChild(clean(child, doc));
      out.appendChild(el);
    }
    return out;
  }

  function markdown(node) {
    let out = "";
    const walk = (n, ctx) => {
      if (n.nodeType === 3) { out += ctx.pre ? n.nodeValue : n.nodeValue.replace(/\s+/g, " ").replace(/([\\`*_\[\]<>])/g, "\\$1"); return; }
      if (n.nodeType !== 1) return;
      const t = n.tagName;
      const kids = (c) => Array.from(n.childNodes).forEach((k) => walk(k, c || ctx));
      const block = (pre, post) => { out = out.replace(/[ ]+$/, ""); out += pre; kids(); out += post; };
      if (/^H[1-6]$/.test(t)) return block("\n\n" + "#".repeat(+t[1]) + " ", "\n\n");
      switch (t) {
        case "P": case "DIV": case "SECTION": case "FIGURE": case "FIGCAPTION": case "TABLE": return block("\n\n", "\n\n");
        case "BR": out += "  \n"; return;
        case "HR": out += "\n\n---\n\n"; return;
        case "STRONG": case "B": return block("**", "**");
        case "EM": case "I": return block("*", "*");
        case "CODE": if (ctx.pre) return kids(); return block("`", "`");
        case "PRE": out += "\n\n```\n"; kids({ ...ctx, pre: true }); out += "\n```\n\n"; return;
        case "BLOCKQUOTE": {
          const start = out.length; out += "\n\n"; kids();
          const inner = out.slice(start).trim(); out = out.slice(0, start);
          out += "\n\n" + inner.split("\n").map((l) => ("> " + l).trimEnd()).join("\n") + "\n\n"; return;
        }
        case "UL": case "OL": {
          out += "\n"; let i = 0; const depth = ctx.depth || 0;
          Array.from(n.children).forEach((li) => {
            i++; out += "\n" + "  ".repeat(depth) + (t === "OL" ? i + ". " : "- ");
            Array.from(li.childNodes).forEach((k) => walk(k, { ...ctx, depth: depth + 1 }));
          });
          out += "\n\n"; return;
        }
        case "A": { const h = n.getAttribute("href"); if (!h) return kids(); out += "["; kids(); out += "](" + h + ")"; return; }
        case "IMG": out += "![" + (n.getAttribute("alt") || "").replace(/]/g, "") + "](" + n.getAttribute("src") + ")"; return;
        default: kids();
      }
    };
    walk(node, {});
    return out.replace(/\n{3,}/g, "\n\n").trim() + "\n";
  }

  port.onMessage.addListener((msg) => {
    if (!msg || msg.action !== "extract") return;
    try {
      const a = new Readability(document.cloneNode(true), { charThreshold: 300 }).parse();
      if (!a) { port.postMessage({ type: "article", ok: false, request: msg.request }); return; }
      const parsed = new DOMParser().parseFromString("<body>" + (a.content || "") + "</body>", "text/html");
      const safeDoc = document.implementation.createHTMLDocument("");
      const holder = safeDoc.createElement("div");
      holder.appendChild(clean(parsed.body, safeDoc));
      port.postMessage({
        type: "article", ok: true, request: msg.request, url: location.href,
        title: a.title || document.title, byline: a.byline || "", site: a.siteName || "", published: a.publishedTime || "",
        html: holder.innerHTML, markdown: markdown(holder),
      });
    } catch (e) {
      port.postMessage({ type: "article", ok: false, request: msg.request, error: String(e) });
    }
  });
})();
