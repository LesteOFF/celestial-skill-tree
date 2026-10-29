# CurseForge page

Everything needed to fill the Celestial Skill Tree project page.

| File | Where it goes |
|---|---|
| `description.md` | Description (paste it in the Markdown editor) |
| `images/banner.png` | Top of the description |
| `images/gallery/*.png` | Images tab, in this order |

## Summary

Your skills, written in the stars. A constellation skill tree that is different in every world.

## Banner

The repository is private, so the banner has to be hosted by CurseForge: upload `images/banner.png` with the image
button of the description editor and put the link it gives (`https://media.forgecdn.net/...`) in place of
`BANNER_URL`.

To redraw it after a visual change, run `./gradlew runClient -Pvisualqa -Pgallery` (it writes
`run/screenshots/gallery_banner_tree.png`, the tree without the HUD) and then
`python curseforge/make_banner.py run/screenshots/gallery_banner_tree.png`, which dims the shot and lays
`images/banner_title.png` (logo, title and tagline) over it.

## Gallery

| Image | Title | Description |
|---|---|---|
| `01-star-chart.png` | The Star Chart | Every class is a constellation and every skill a star. The whole chart comes from the world seed. |
| `02-keystone-juggernaut.png` | Keystones | Strong effects with a drawback, at the tips of each class. |
| `03-keystone-thousand-cuts.png` | Plan before you spend | Every skill shows its cost and what a click will do. |
| `04-black-hole.png` | Black Holes | Boost the skills around them and seal the rest. |
| `05-astral-gate.png` | Astral Gates | Portals that link opposite sides of the chart. |
| `06-stats-panel.png` | Character Stats | Every bonus from the tree, the birth sign and conditional effects in one panel. |
| `07-birth-chart.png` | Birth Chart | One of twelve signs on your first login, with a bonus that stays forever. |
| `08-eclipse.png` | Eclipse | Monsters walk in daylight, but respecs are free until dusk. |

The screenshots are captured by `./gradlew runClient -Pgallery` (saved in `run/screenshots/gallery_*.png`).
