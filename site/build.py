#!/usr/bin/env python3
"""Generate build/site from the fork's markdown + real config.

Pages:
  readme.html        README.md
  architecture.html  docs/ARCHITECTURE.md
  network.html       real servers from config/hypergravel.toml (tomllib)

Each markdown page is rendered GitHub-dark; images referenced in the markdown
are copied into build/site so they render offline. A Star button in the header
points at the GitHub repo (set REPO_URL in ``site/config.py`` once you push).

Usage:
    python3 site/build.py && python3 -m http.server 8123 -d build/site
"""
import re
import shutil
import tomllib
from html import escape
from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "build" / "site"
CONFIG = ROOT / "config" / "hypergravel.toml"
REPO_URL = "https://github.com/KraftifyFOSS/Hypergravel-Edge"

MD = markdown.Markdown(extensions=["fenced_code", "tables", "sane_lists", "toc", "codehilite"],
                       extension_configs={"codehilite": {"guess_lang": False, "noclasses": True},
                                          "toc": {"permalink": False}})

CSS = """
:root{--bg:#0d1117;--panel:#161b22;--panel2:#21262d;--ink:#e6edf3;--muted:#8b949e;
--blue:#58a6ff;--line:#30363d;--codebg:#161b22;--shadow:0 8px 24px rgba(1,4,9,.5)}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.7 -apple-system,
"Segoe UI","Helvetica Neue",Arial,sans-serif;text-rendering:optimizeLegibility}
a{color:var(--blue);text-decoration:none}
a:hover{text-decoration:underline}
header{position:sticky;top:0;z-index:10;background:rgba(13,17,23,.85);
backdrop-filter:blur(8px);border-bottom:1px solid var(--line)}
header .wrap{max-width:1012px;margin:0 auto;padding:0 16px;display:flex;align-items:center;
justify-content:space-between;height:64px}
.brand{display:flex;align-items:center;gap:10px;font-weight:600;color:var(--ink)}
.brand .mark{width:30px;height:30px;border-radius:6px;display:grid;place-items:center;
font-weight:800;font-size:15px;background:var(--panel2);color:var(--blue);
outline:1px solid var(--line)}
.brand small{display:block;font-weight:400;font-size:12px;color:var(--muted)}
nav{display:flex;gap:6px}
nav a{padding:7px 14px;border-radius:6px;font-size:14px;font-weight:500;color:var(--ink);
border:1px solid transparent}
nav a:hover{text-decoration:none;background:var(--panel2)}
nav a.active{background:var(--panel2);border-color:var(--line)}
.star{display:flex;align-items:center;gap:8px;padding:6px 12px;border-radius:6px;
background:var(--panel);border:1px solid var(--line);font-size:13px;font-weight:600;
color:var(--ink)}
.star:hover{background:var(--panel2);text-decoration:none}
.star .n{color:var(--muted);font-weight:500}
.star svg{width:14px;height:14px;fill:currentColor}
main{max-width:1012px;margin:0 auto;padding:28px 16px 64px}
h1,h2,h3,h4{color:var(--ink);line-height:1.3;margin:1.4em 0 .6em}
h1{font-size:1.9em;border-bottom:1px solid var(--line);padding-bottom:.3em}
h2{font-size:1.45em;border-bottom:1px solid var(--line);padding-bottom:.25em}
h3{font-size:1.2em}
p,li,td,th{line-height:1.7}
code{padding:.2em .35em;background:rgba(110,118,129,.22);border-radius:4px;
font-size:.88em;font-family:ui-monospace,SFMono-Regular,"SF Mono",Consolas,monospace}
pre{padding:14px 16px;background:var(--codebg);border:1px solid var(--line);
border-radius:8px;overflow:auto;line-height:1.5}
pre code{padding:0;background:none;border-radius:0}
pre .hll{background:#35622d}
blockquote{margin:.2em 0;padding:0 16px;border-left:4px solid var(--line);color:var(--muted)}
table{border-collapse:collapse;width:100%;margin:1em 0;font-size:14.5px}
th,td{border:1px solid var(--line);padding:7px 12px;text-align:left}
th{background:var(--panel);font-weight:600}
img{max-width:100%;border-radius:6px;border:1px solid var(--line)}
hr{border:none;border-top:1px solid var(--line);margin:2em 0}
footer{max-width:1012px;margin:0 auto;padding:24px 16px 48px;color:var(--muted);
font-size:13px;border-top:1px solid var(--line)}
.pill{display:inline-flex;align-items:center;gap:6px;padding:6px 12px;border-radius:6px;
background:var(--panel);border:1px solid var(--line);color:var(--ink);font-size:13px;
font-weight:600}
a.pill:hover{background:var(--panel2);text-decoration:none}
.dot{width:8px;height:8px;border-radius:50%;background:#3fb950;display:inline-block}
.mono{font-family:ui-monospace,SFMono-Regular,"SF Mono",Consolas,monospace}
"""

ICONS = {
    "star": '<svg viewBox="0 0 16 16" aria-hidden="true"><path fill-rule="evenodd" '
            'd="M8 .25a.75.75 0 0 1 .673.418l1.882 3.815 4.21.612a.75.75 0 0 1 '
            '.416 1.279l-3.046 2.97.719 4.192a.75.75 0 0 1-1.088.791L8 12.347l-3.766 1.98a.75.75 '
            '0 0 1-1.088-.79l.72-4.194L.818 6.374a.75.75 0 0 1 .416-1.28l4.21-.611L7.327.668A.75.75 '
            '0 0 1 8 .25z"/></svg>',
    "eye": '<svg viewBox="0 0 16 16" aria-hidden="true"><path fill-rule="evenodd" '
           'd="M1.679 7.932c.412-.621 1.242-1.75 2.366-2.717C5.175 4.242 6.527 3.5 8 3.5c1.473 0 '
           '2.824.742 3.955 1.715 1.124.967 1.954 2.096 2.366 2.717a.119.119 0 0 1 0 .136c-.412.621-1.242 '
           '1.75-2.366 2.717C10.825 11.758 9.473 12.5 8 12.5c-1.473 0-2.824-.742-3.955-1.715C2.92 '
           '9.818 2.09 8.689 1.679 8.068a.119.119 0 0 1 0-.136zM8 2c-1.981 0-3.686.966-5.011 1.992C1.635 '
           '4.99.638 6.05.11 7.58a1.625 1.625 0 0 0 0 .842C.468 10.192 1.425 10.983 1.06 12a1.845 1.845 '
           '0 0 0 1.135 2.2c.561.211 1.061.158 1.443.784.207.339.225.777.167 1.173-.058.392-.193.754-.34 '
           '1.068l-.004.01a.75.75 0 0 0 1.332.609c.164-.358.278-.712.33-1.052a4.2 4.2 0 0 0-.036-1.672l-.048.014a1.5 1.5 '
           '0 0 1-.273-.003c-.366.661-1.09 1.02-1.706.869-.612-.151-1.031-.777-.858-1.572.28-1.277 2.008 1.402 5.659 '
           '1.402 2.551.186-.84  cataracts 0 5.546-1.402l-.003-.006a.75.75 0 0 0-.362-.434z"/></svg>',
}


def slug(s):
    return s.lower().replace(" ", "-")


def img_copy(m):
    """Rewrite ![alt](path) so the referenced file is copied into the site."""
    alt, target = m.group(1), m.group(2)
    if target.startswith(("http://", "https://", "data:")):
        return m.group(0)
    src = ROOT / target
    if not src.exists():
        src = (ROOT / "docs").resolve() / target
    if src.exists():
        dst = OUT / "assets" / src.name
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
        return f'![{alt}](assets/{src.name})'
    return m.group(0)


def normalize(html: str) -> str:
    """Strip U+2014/U+2013 (em/en dash) from the finished page. U+2500 —
    box-drawing used in the docs' ASCII art — is a different codepoint and is
    left untouched."""
    return html.replace("\u2014", "-").replace("\u2013", "-")


def render_md(path):
    MD.reset()
    src = (ROOT / path).read_text(encoding="utf-8")
    src = re.sub(r"!\[([^\]]*)\]\(([^)]+)\)", img_copy, src)
    return MD.convert(src)


def fmt_toml(value, depth=0):
    pad = "  " * depth
    if isinstance(value, dict):
        out = []
        for k, v in value.items():
            if isinstance(v, dict):
                out.append(f'{pad}[{k}]')
                out.append(fmt_toml(v, depth + 1))
            else:
                out.append(f"{pad}{k} = {fmt_toml(v, 0)}")
        return "\n".join(out)
    if isinstance(value, list):
        return "[\n" + "".join(f"{pad}  {fmt_toml(v)}\n" for v in value) + pad + "]"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, str):
        return f'"{value}"'
    return str(value)


def network_page():
    cfg = tomllib.loads((CONFIG).read_text(encoding="utf-8"))
    servers = cfg.get("servers", {})
    cards = []
    for name in sorted(servers):
        s = servers[name]
        state = "limbo" if s.get("limbo") else "open"
        dot = "var(--line)" if state == "limbo" else "#3fb950"
        rest = " · <span class=mono>restricted</span>" if s.get("restricted") else ""
        perm = f'<br><span class="mono">@{escape(s.get("permission",""))}</span>' \
               if s.get("permission") else ""
        falls = f' → <a href="#">{escape(s["on-down"])}</a>' if s.get("on-down") else ""
        cards.append(
            f'<div style="background:var(--panel);border:1px solid var(--line);border-radius:8px;'
            f'padding:14px 16px;display:flex;justify-content:space-between;align-items:center;gap:12px">'
            f'<div><strong style="font-size:15px">{escape(name)}</strong>{rest}'
            f'<div style="color:var(--muted);font-size:13.5px">'
            f'<span class="mono">{escape(s.get("address",""))}</span>{falls}</div></div>'
            f'<div style="display:flex;align-items:center;gap:6px;color:var(--muted);font-size:13px">'
            f'<span style="width:8px;height:8px;border-radius:50%;background:{dot};display:inline-block"></span>'
            f'{state}{perm}</div></div>'
        )
    ops = cfg.get("ops", {})
    opsrows = "".join(
        '<tr><th scope="row">health</th><td class="mono">GET /health</td>'
        '<td><code>200</code> while the process is alive</td></tr>'
        '<tr><th scope="row">ready</th><td class="mono">GET /ready</td>'
        '<td><code>200</code> if at least one backend is up, else <code>503</code></td></tr>'
        '<tr><th scope="row">metrics</th><td class="mono">GET /metrics</td>'
        '<td>Prometheus text exposition</td></tr>'
    )

    body = f"""
<h1>Network — real config</h1>
<p>Rendered live from <code>config/hypergravel.toml</code>. This is the network
the edge actually fronts — every backend, its state, and the failover policy.</p>
<p><span class="pill"><span class="dot"></span>&nbsp;simulated for a public repo
— points at <span class="mono">127.0.0.1</span> and is not the live network</span></p>

<h2>Backend servers</h2>
{''.join(cards)}

<h2>Ops endpoints</h2>
<table><thead><tr><th>Probe</th><th>Route</th><th>Checks</th></tr></thead><tbody>{opsrows}</tbody></table>
<p>Default bind <span class="mono">127.0.0.1:{ops.get("metrics-port","9100")}</span>.
Keep it off public interfaces — it has no auth.</p>

<h2>Why it's on 127.0.0.1</h2>
<p>The proxy binds to <span class="mono">0.0.0.0</span> on the Minecraft port
(<span class="mono">{cfg.get("bind",{}).get("port","25565")}</span>) and fronts
every backend behind one address; playit.gg or socat forward <em>that</em>
address. Ops stays loopback-only so nobody else can read topology.</p>
"""
    return body


PAGES = [("readme", "README", "README.md"), ("architecture", "Architecture", "docs/ARCHITECTURE.md")]


def main():
    shutil.rmtree(OUT, ignore_errors=True)
    OUT.mkdir(parents=True, exist_ok=True)
    nav = "".join(f'<a href="{slug}#"\":" >0</a>' for _ in [])
    nav = '<a href="readme.html" class="active">README</a>' \
          '<a href="architecture.html">Architecture</a>' \
          '<a href="network.html">Network</a>'
    for name, label, path in PAGES:
        body = render_md(path)
        page = PAGE.format(title=label, css=CSS, nav=nav, star=ICONS["star"], body=body, repo=REPO_URL)
        (OUT / f"{name}.html").write_text(normalize(page), encoding="utf-8")
        print(f"wrote {OUT / (name + '.html')}  ({len(body)} chars)")
    body = network_page()
    page = PAGE.format(title="Network", css=CSS, nav=nav, star=ICONS["star"], body=body, repo=REPO_URL)
    (OUT / "network.html").write_text(normalize(page), encoding="utf-8")
    print(f"wrote {OUT / 'network.html'}  ({len(body)} chars)")


PAGE = """<!doctype html>
<html lang="en">
<head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title} · HyperGravel Edge</title><style>{css}</style></head>
<body>
<header><div class="wrap">
  <a class="brand" href="readme.html">
    <span class="mark">HG</span>
    <span>HyperGravel Edge<small>{title} · reverse proxy</small></span>
  </a>
  <nav>{nav}</nav>
  <a class="star" href="{repo}" target="_blank" rel="noopener">{star}<span>Star</span><span class="n">0</span></a>
</div></header>
<main>{body}</main>
<footer>HyperGravel Edge — a public, self-contained fork. Source at
<a href="{repo}" target="_blank" rel="noopener">{repo}</a>.</footer>
</body></html>
"""

if __name__ == "__main__":
    main()
