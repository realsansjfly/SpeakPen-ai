from base64 import b64encode
from pathlib import Path
import re
from shutil import copyfile
from zipfile import ZIP_DEFLATED, ZipFile


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "server" / "public"
OUTPUT = ROOT / "deliverables" / "product-showcase-v2-2026-10-31"
ASSETS = OUTPUT / "assets"
OUTPUT.mkdir(parents=True, exist_ok=True)
ASSETS.mkdir(parents=True, exist_ok=True)

html = (SOURCE / "product.html").read_text(encoding="utf-8-sig")
license_text = (ROOT / "design" / "Nunito-OFL.txt").read_text(encoding="utf-8-sig")
html = html.replace("</head>", "<!-- Bundled Nunito font license:\n" + license_text + "\n-->\n</head>")
css = (SOURCE / "product.css").read_text(encoding="utf-8-sig")
js = (SOURCE / "product.js").read_text(encoding="utf-8-sig")
fonts = []
for filename, weight in (("nunito_regular.ttf", 400), ("nunito_extra_bold.ttf", 800)):
    font_path = ROOT / "app" / "src" / "main" / "res" / "font" / filename
    fonts.append(
        "@font-face{font-family:Nunito;src:url(data:font/ttf;base64,"
        + b64encode(font_path.read_bytes()).decode("ascii")
        + ") format('truetype');font-weight:"
        + str(weight)
        + ";font-display:swap}"
    )
    copyfile(font_path, ASSETS / filename)
css = re.sub(r"@font-face\s*\{[^}]+\}", "", css)
if "src:url(../../app/" in css or "src: url(../../app/" in css:
    raise SystemExit("A source font reference survived single-file embedding.")
css = "\n".join(fonts) + "\n" + css
html, css_replacements = re.subn(
    r'<link rel="stylesheet" href="\./product\.css">',
    lambda _: "<style>\n" + css + "\n</style>",
    html,
    count=1,
)
html, js_replacements = re.subn(
    r'<script src="\./product\.js" defer></script>',
    lambda _: "<script>\n" + js + "\n</script>",
    html,
    count=1,
)
if css_replacements != 1 or js_replacements != 1:
    raise SystemExit("Product page source markers were not found exactly once.")

single_file = OUTPUT / "SpeakPen-功能介绍.html"
single_file.write_text(html, encoding="utf-8")
(OUTPUT / "index.html").write_text(html, encoding="utf-8")
(OUTPUT / "product.css").write_text(
    (SOURCE / "product.css").read_text(encoding="utf-8-sig").replace("../../app/src/main/res/font/", "./assets/"),
    encoding="utf-8",
)
copyfile(SOURCE / "product.js", OUTPUT / "product.js")
(OUTPUT / "OFL.txt").write_text(license_text, encoding="utf-8")

archive = OUTPUT / "SpeakPen-功能介绍-静态站点.zip"
with ZipFile(archive, "w", ZIP_DEFLATED) as package:
    for path in (OUTPUT / "index.html", OUTPUT / "product.css", OUTPUT / "product.js", OUTPUT / "OFL.txt"):
        package.write(path, path.name)
    for path in sorted(ASSETS.glob("*.ttf")):
        package.write(path, "assets/" + path.name)

print(
    f"single_file={single_file.stat().st_size // 1024} KiB; "
    f"static_site={OUTPUT}; archive={archive.name}; "
    "fonts/css/js are local and the single-file version is self-contained"
)
