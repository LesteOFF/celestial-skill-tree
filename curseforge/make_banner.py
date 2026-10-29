import sys
from pathlib import Path

from PIL import Image

IMAGES = Path(__file__).parent / "images"
TITLE = IMAGES / "banner_title.png"
BANNER = IMAGES / "banner.png"
SPACE = (6, 6, 10)
TREE_DIM = 0.45


def compose(tree_shot):
    tree = Image.open(tree_shot).convert("RGBA")
    veil = Image.new("RGBA", tree.size, SPACE + (round(255 * TREE_DIM),))
    banner = Image.alpha_composite(tree, veil)
    banner.alpha_composite(Image.open(TITLE).convert("RGBA"))
    banner.convert("RGB").save(BANNER)


if __name__ == "__main__":
    compose(sys.argv[1])
