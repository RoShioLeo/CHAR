"""Package only the files required by the static website."""
from pathlib import Path
import argparse
import shutil

ROOT = Path(__file__).resolve().parents[1]
FILES = (
    'index.html', 'styles.css', 'charConverter.js', 'app.js', 'appearance.js',
    'img/lemon.svg', 'char_converter/data/v2s.json', 'char_converter/data/v2t.json',
    'LICENSE', 'NOTICE.md',
)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--destination', type=Path, default=ROOT / '_site')
    destination = parser.parse_args().destination.resolve()
    if destination.exists():
        raise SystemExit('Choose an empty destination; existing output is preserved.')
    for name in FILES:
        assert (ROOT / name).is_file(), name
    destination.mkdir(parents=True)
    for name in FILES:
        target = destination / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(ROOT / name, target)
    print(f'Packaged {len(FILES)} files to {destination}')
