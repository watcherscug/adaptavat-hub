"""Usage: python3 tools/pack_adpp.py PLUGIN_DIRECTORY OUTPUT.adpp"""
import json
import pathlib
import sys
import zipfile

def main():
    source = pathlib.Path(sys.argv[1]).resolve()
    output = pathlib.Path(sys.argv[2]).resolve()
    manifest = json.loads((source / "manifest.json").read_text())
    assert manifest["apiVersion"] == 1
    entry = "classes.dex" if manifest["language"] == "kotlin" else manifest["entryPoint"]
    assert (source / entry).is_file(), f"Missing {entry}; compile Kotlin and run d8 first"
    assert output.suffix == ".adpp" and not output.exists()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(source.rglob("*")):
            if file.is_file() and not file.is_symlink() and file.resolve() != output and "__pycache__" not in file.parts:
                archive.write(file, file.relative_to(source))
    print(output)

if __name__ == "__main__":
    main()
