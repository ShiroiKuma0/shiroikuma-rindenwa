#!/usr/bin/env python3
"""
shiroikuma-rindenwa — give every upstream layout, shape, selector and text style colour roles
of its own.

Upstream's XML names shared colours: a theme attribute (?attr/color_main2_600), one of its own
colour names (@color/bc_white), or one of the fork's general roles (@color/sk_yellow). Two items
that share such a name cannot be coloured apart. This script rewrites each of those references to
a role that belongs to the item:

    layout      @color/sk_<group>__<base>__<kind>     group = the screen the layout belongs to,
                                                      kind  = t text · i icon · g ground · f frame · x other
    drawable    @color/sk_d__<file>__<base>__<kind>
    selector    @color/sk_c__<file>__<base>__x
    text style  @color/sk_s__<style>__<base>__<kind>

and regenerates the two files that declare them:

    app/src/main/res/values/sk_roles_generated.xml            placeholder values (see sk_colors.xml)
    app/src/main/java/org/linphone/shiroikuma/SkRolesGenerated.kt   the roles, each falling back to its base

A role's default is the base it replaced, so nothing changes on screen until 白い熊 sets one.

THE SOURCES ARE NEVER REWRITTEN. The Gradle task `skItemRoles` (app/build.gradle.kts) runs this
before every build with `--out <dir>`: the whole `res/` tree is copied to `<dir>/res`, the copy
is rewritten, and the build takes its resources from the copy and the generated Kotlin from
`<dir>/kotlin`. So the layouts in the repository keep upstream's own colour names and an upstream
sync conflicts no more than before; a layout upstream adds or changes gets its item roles at the
next build, with nothing to do by hand.

    python3 tools/sk_colour_roles.py --out app/build/generated/sk-item-roles
"""
import glob
import os
import re
import sys

import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE_RES = os.path.join(ROOT, "app/src/main/res")
KT = os.path.join(ROOT, "app/src/main/java/org/linphone/shiroikuma")

if len(sys.argv) != 3 or sys.argv[1] != "--out":
    sys.exit("usage: sk_colour_roles.py --out <directory>   (the sources are never rewritten)")
OUT = os.path.abspath(sys.argv[2])
RES = os.path.join(OUT, "res")
KT_OUT = os.path.join(OUT, "kotlin", "org", "linphone", "shiroikuma")
shutil.rmtree(OUT, ignore_errors=True)
shutil.copytree(SOURCE_RES, RES)
os.makedirs(KT_OUT)

# ---- the bases: the hand-written roles a reference may point at (parsed from SkRoles.kt)
roles_kt = open(os.path.join(KT, "SkRoles.kt")).read()
BASE_LABEL = {}
for key, label in re.findall(r'SkRole\("([\w]+)", R\.color\.\w+, SkArea\.\w+, "([^"]*)"\)', roles_kt):
    BASE_LABEL[key] = label
ITEM_ROLES = {"sk_viewer_bar", "sk_pdf_paper"}  # already one item each: never split further
ATTR = {k[len("sk_a_"):] for k in BASE_LABEL if k.startswith("sk_a_")}
UP = {k[len("up_"):] for k in BASE_LABEL if k.startswith("up_")}
SK = {k for k in BASE_LABEL if k.startswith("sk_") and not k.startswith("sk_a_") and k not in ITEM_ROLES}


def short(base):
    """sk_a_main2_600 -> a_main2_600 · up_bc_white -> u_bc_white · sk_yellow -> s_yellow"""
    if base.startswith("sk_a_"):
        return "a_" + base[5:]
    if base.startswith("up_"):
        return "u_" + base[3:]
    return "s_" + base[3:]


def long(code):
    return {"a": "sk_a_", "u": "up_", "s": "sk_"}[code[0]] + code[2:]


GROUPS = [  # first match wins
    (r"^(assistant_|welcome_)", "asst", "ASSISTANT"),
    (r"^meeting", "meet", "MEETINGS"),
    (r"(popup_menu|long_press_menu|_menu\.xml$)", "menu", "SCREEN_MENUS"),
    (r"^dialog_", "dlg", "SCREEN_DIALOGS"),
    (r"^(call_|calls_|in_call)", "call", "SCREEN_CALL"),
    (r"^(chat_|conversation)", "chat", "SCREEN_CHAT"),
    (r"^(contact|address)", "cont", "SCREEN_CONTACTS"),
    (r"^history", "hist", "SCREEN_HISTORY"),
    (r"^settings", "sett", "SCREEN_SETTINGS"),
    (r"^account", "acct", "SCREEN_ACCOUNT"),
    (r"^drawer", "drawer", "SCREEN_DRAWER"),
    (r"^recording", "rec", "SCREEN_RECORDINGS"),
    (r"^help", "help", "SCREEN_HELP"),
    (r"^file", "view", "SCREEN_VIEWERS"),
    (r"^(start_|generic_address)", "start", "SCREEN_START"),
    (r"^(main_|bottom_nav|top_bar|sliding_pane|search_bar|toast)", "main", "SCREEN_MAIN"),
]
GROUP_AREA = {g: a for _, g, a in GROUPS}
GROUP_AREA["misc"] = "SCREEN_OTHER"


def group_of(name):
    for pattern, group, _ in GROUPS:
        if re.search(pattern, name):
            return group
    return "misc"


KIND_WORD = {"t": "text", "i": "icons", "g": "ground", "f": "frame", "x": "other"}


def kind_of_attribute(attr):
    a = attr.split(":")[-1].lower()
    if "textcolor" in a:
        return "t"
    if "tint" in a:
        return "i"
    if "stroke" in a or "divider" in a:
        return "f"
    if "background" in a:
        return "g"
    return "x"


found = {}  # role name -> (area, label, base key)

GENERATED = re.compile(r"@color/(sk_[a-z]+__(?:\w+?__)?[aus]_\w+?__[tigfx])\b")


def base_of(token):
    """The base role key a reference names, or None when it is not one this script splits."""
    m = re.fullmatch(r"\?attr/color_(\w+)", token)
    if m:
        return "sk_a_" + m.group(1) if m.group(1) in ATTR else None
    m = re.fullmatch(r"@color/(\w+)", token)
    if not m:
        return None
    name = m.group(1)
    if name in UP:
        return "up_" + name
    if name in SK:
        return name
    return None


TOKEN = re.compile(r"\?attr/color_\w+|@color/\w+")


def rewrite_value(value, prefix, kind, area, what):
    def one(m):
        token = m.group(0)
        g = GENERATED.fullmatch(token)
        if g:  # ours already: register it again
            name = g.group(1)
            code, k = name.split("__")[-2], name.split("__")[-1]
            register(name, area, what, long(code), k)
            return token
        base = base_of(token)
        if base is None:
            return token
        name = f"{prefix}__{short(base)}__{kind}"
        register(name, area, what, base, kind)
        return "@color/" + name

    return TOKEN.sub(one, value)


def register(name, area, what, base, kind):
    label = BASE_LABEL.get(base, base)
    label = label.split(" — ")[0] if what is None else label
    text = f"{label} — {KIND_WORD[kind]}" if what is None else f"{what} — {KIND_WORD[kind]} ({label})"
    found[name] = (area, text.replace('"', "'"), base)


ATTRIBUTE = re.compile(r'([\w:]+)="([^"]*)"')


def process(path, prefix, area, what, kind_from_tag=False):
    source = open(path).read()
    if kind_from_tag:
        out, pos, kind = [], 0, "x"
        for m in re.finditer(r"<(\w+)|" + ATTRIBUTE.pattern, source):
            out.append(source[pos:m.start()])
            pos = m.end()
            if m.group(1):
                tag = m.group(1)
                kind = "g" if tag == "solid" else "f" if tag == "stroke" else "x"
                out.append(m.group(0))
            else:
                out.append(f'{m.group(2)}="{rewrite_value(m.group(3), prefix, kind, area, what)}"')
        out.append(source[pos:])
        result = "".join(out)
    else:
        result = ATTRIBUTE.sub(
            lambda m: f'{m.group(1)}="{rewrite_value(m.group(2), prefix, kind_of_attribute(m.group(1)), area, what)}"',
            source,
        )
    if result != source:
        open(path, "w").write(result)
        return 1
    return 0


changed = 0
for path in sorted(glob.glob(os.path.join(RES, "layout*", "*.xml"))):
    name = os.path.basename(path)
    group = group_of(name)
    changed += process(path, f"sk_{group}", GROUP_AREA[group], None)

for path in sorted(glob.glob(os.path.join(RES, "drawable", "*.xml"))):
    stem = os.path.basename(path)[:-4]
    if stem.startswith("sk_"):
        continue  # the fork's own shapes are drawn in code (SkShapes)
    changed += process(path, f"sk_d__{stem}", "SHAPES", stem.replace("_", " "), kind_from_tag=True)

for path in sorted(glob.glob(os.path.join(RES, "color", "*.xml"))):
    stem = os.path.basename(path)[:-4]
    if stem == "sk_switch_color":
        continue
    changed += process(path, f"sk_c__{stem}", "STATES", stem.replace("_", " "))

# text styles: one <style> at a time, so each style owns its colours
styles_path = os.path.join(RES, "values", "styles.xml")
styles = open(styles_path).read()


def style_block(m):
    style = m.group(1).replace(".", "_")

    def item(i):
        kind = kind_of_attribute(i.group(1))
        return f'<item name="{i.group(1)}">{rewrite_value(i.group(2), f"sk_s__{style}", kind, "STYLES", style.replace("_", " "))}</item>'

    return m.group(0)[: m.start(2) - m.start(0)] + re.sub(r'<item name="([\w:]+)">([^<]*)</item>', item, m.group(2)) + "</style>"


new_styles = re.sub(r'<style name="([\w.]+)"[^>]*>(.*?)</style>', style_block, styles, flags=re.S)
if new_styles != styles:
    open(styles_path, "w").write(new_styles)
    changed += 1

# ---- the two generated files
names = sorted(found)
with open(os.path.join(RES, "values", "sk_roles_generated.xml"), "w") as out:
    out.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- GENERATED by tools/sk_colour_roles.py — do not edit. One colour role per item of\n     upstream\'s XML. The values are placeholders, as in sk_colors.xml: SkPalette feeds the real\n     ones at run time from the role table. -->\n<resources>\n')
    for name in names:
        kind = name[-1]
        out.write(f'    <color name="{name}">{"#FF140014" if kind == "g" else "#FFFF66FF"}</color>\n')
    out.write("</resources>\n")

CHUNK = 150
with open(os.path.join(KT_OUT, "SkRolesGenerated.kt"), "w") as out:
    out.write("package org.linphone.shiroikuma\n\nimport org.linphone.R\n\n")
    out.write("/**\n * GENERATED by tools/sk_colour_roles.py — do not edit.\n *\n * One colour role for every item of upstream's XML: a layout's text, icons, grounds and frames\n * by screen, every shape, every state selector, every text style. Each falls back to the general\n * role it replaced, so a role that was never set looks exactly as before.\n */\nobject SkRolesGenerated {\n")
    parts = [names[i:i + CHUNK] for i in range(0, len(names), CHUNK)]
    out.write("    val ALL: List<SkRole> by lazy {\n        " + " + ".join(f"part{i}()" for i in range(len(parts))) + "\n    }\n" if parts else "    val ALL: List<SkRole> = emptyList()\n")
    for i, part in enumerate(parts):
        out.write(f"\n    private fun part{i}(): List<SkRole> = listOf(\n")
        for name in part:
            area, label, base = found[name]
            out.write(f'        SkRole("{name}", R.color.{name}, SkArea.{area}, "{label}", "{base}"),\n')
        out.write("    )\n")
    out.write("}\n")

print(f"{changed} files rewritten, {len(names)} item roles")
by_area = {}
for name in names:
    by_area[found[name][0]] = by_area.get(found[name][0], 0) + 1
print(" ".join(f"{a}={n}" for a, n in sorted(by_area.items())))
