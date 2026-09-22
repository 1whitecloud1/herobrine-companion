"""Build the small white/cyan ribbon texture used by the standalone renderer."""
from pathlib import Path
import math
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
DESTINATION = ROOT / 'src/main/resources/assets/herobrine_companion/textures/trail/poem_standalone_trail.png'


def main():
    image = Image.new('RGBA', (256, 64))
    for y in range(image.height):
        across = abs((y + .5) / image.height * 2 - 1)
        core = math.exp(-across * across / .11)
        edge = max(0, 1 - across ** 6) ** 2
        for x in range(image.width):
            along = (x + .5) / image.width
            tail = math.sin(min(1, along * 1.7) * math.pi / 2) ** .55
            alpha = int(255 * edge * tail * (.8 + .2 * core))
            image.putpixel((x, y), (int(45 + 210 * core), int(150 + 105 * core), 255, alpha))
    image.save(DESTINATION, optimize=True)
    print(DESTINATION)


if __name__ == '__main__':
    main()
