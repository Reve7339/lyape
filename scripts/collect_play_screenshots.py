"""Recoge las cuatro capturas ficticias que AGP extrajo antes de desinstalar la app."""

import argparse
from pathlib import Path
import shutil

NAMES = ("01-inicio.png", "02-movimientos.png", "03-cuentas.png", "04-reportes.png")


def collect(source: Path, destination: Path) -> None:
    files = []
    for name in NAMES:
        candidates = list(source.glob(f"**/play-screenshots/{name}"))
        if len(candidates) != 1:
            raise ValueError(f"Se esperaba una captura {name}; encontradas: {len(candidates)} en {source}")
        candidate = candidates[0]
        with candidate.open("rb") as stream:
            if stream.read(8) != b"\x89PNG\r\n\x1a\n":
                raise ValueError(f"La captura no es un PNG: {candidate}")
        files.append(candidate)
    destination.mkdir(parents=True, exist_ok=True)
    for file in files:
        shutil.copyfile(file, destination / file.name)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path,
                        default=Path("app/build/outputs/connected_android_test_additional_output"))
    parser.add_argument("--destination", type=Path, default=Path("delivery/play-screenshots"))
    args = parser.parse_args()
    try:
        collect(args.source, args.destination)
    except (ValueError, OSError) as error:
        parser.exit(1, f"Capturas: {error}\n")
    print(f"Capturas: {len(NAMES)} PNG recogidos en {args.destination}")


if __name__ == "__main__":
    main()
