# Adaptavat plugins and Workshop API v1

## Runtime and installation

Open **Мастерская → Плагины → Установить .adpp** and select an archive using
the Android file picker. Each installed plugin has an enable switch, delete button,
and buttons for its exported commands. A command opens a JSON argument dialog;
its result appears in the Console tab. Console commands remain optional.

The **Каталог** tab searches public Hugging Face dataset repositories (plugin
archives are not model weights). To publish a discoverable entry, put
`manifest.json` and `plugin.adpp` in the repository root. Add `"type": "adpp-plugin"`
to the manifest. The manifest must target API v1 and language python/kotlin.
Search checks at most 30 repositories; this is a simple community index, not a
curated marketplace. Installation verifies the archive's ID before installing it.
Installing an archive does not execute it: its entry point runs on the first command.
Updates currently require removing the old version first. Module-local state is not
persistent across process restarts; save persistent data in `adpp.workspace()`.

ADPP v1 is a ZIP archive with `manifest.json` at its root. Example:

```json
{
  "apiVersion": 1,
  "id": "example.info",
  "name": "Model inventory",
  "version": "1.0.0",
  "language": "python",
  "entryPoint": "main.py",
  "commands": ["summary"]
}
```

IDs: `[a-z][a-z0-9_.-]{2,79}`. Command names: `[a-z][a-z0-9_-]{0,39}`.
API version must equal 1. Maximum unpacked archive size: 64 MiB, 2048 entries.
The installer rejects path traversal and duplicate files. Exports are namespaced
as `plugin.id:command`; plugins cannot replace built-in command names.

Plugins are trusted application code, **not sandboxed**. Python and DEX execute
with the app's permissions and can access app data and network. The host API's
path checks are not a security boundary against plugin code. No package signing,
permission sandbox, automatic dependency installer or hot unloading is implemented.
Disabling prevents future dispatch; it cannot undo plugin side effects. Restart
the app after replacing plugins which keep module-global state.

## Python

Embedded CPython 3.12 via Chaquopy 17.1.0. Built-in packages:
NumPy 1.26.2, requests 2.32.3, their packaged dependencies and Python standard library.
Run `packages` to list exact installed distributions. PyTorch, transformers, CUDA,
desktop shells and arbitrary native PyPI wheels are **not** preinstalled.
Pure Python helper modules can be bundled in an ADPP. No runtime `pip` command.

```python
def execute(api, command, arguments):
    api.log("Plugin called")
    return {"models": api.models(), "args": arguments}
```

`arguments` is decoded JSON. Return a JSON-serializable value. Available API:

* `api.models()` -> list of `{name, path, bytes}` (downloaded and workshop GGUF).
* `api.workspace()` -> persistent, writable workshop directory.
* `api.log(text)` -> console output.
* `api.call(command, **arguments)` -> host/other plugin command result.
* `api.quantize(source, output, quant="Q4_K_M", layers=(), threads=4)`.
* `api.prune(source, output, layers, threads=4)`.

Plugins export their own API as commands. Example inter-plugin call:
`api.call("example.info:summary")`. Maximum nested host dispatch depth: 8.
The Python console exposes the same module as `adpp`:

```text
py import numpy as np
py np.arange(5).mean()
py adpp.models()
run example.info:numpy {"weights":[1,2,3]}
```

Python console variables persist until process restart. Multiline Python is allowed
after `py `. `stdout`, `stderr` and tracebacks are captured; native C++ logs remain
in Logcat. There is one serial workshop worker. Jobs are in-process: no foreground
service, restart recovery or force-stop button for Python/native transforms yet.

## Kotlin

Kotlin ADPP uses `language: "kotlin"`, an entry-point class name, and `classes.dex`
at the archive root. Source `.kt` is not compiled on the phone.
Implement the public interfaces in `plugins/PluginApi.kt`:

```kotlin
class MyPlugin : AdppPlugin {
    override fun execute(api: PluginApi, command: String, argumentsJson: String): String {
        api.log("Hello")
        return api.invoke("models", "{}")
    }
}
```

Host API: `version(): Int`, `log(String)`, `workspace(): String`, `models(): String`,
`invoke(command: String, argumentsJson: String): String`. Inputs/results use JSON
strings unless the command explicitly returns plain text. Public commands and JSON
keys are the same as for Python. Calls execute on the workshop worker, not UI thread.
Use a public no-argument constructor. Do not bundle API classes or another copy
of Kotlin stdlib; host owns them. Android framework APIs are available, Compose UI
extension points are not part of v1.

Build the interface JAR with `gradle :app:exportPluginApi`:
`app/build/plugin-sdk/adpp-api-v1.jar`. On a desktop with Kotlin compiler and Android
SDK (replace SDK/JAR paths for your installation):

```sh
kotlinc InventoryPlugin.kt -classpath adpp-api-v1.jar -jvm-target 17 -d plugin.jar
mkdir -p dex
"$ANDROID_HOME/build-tools/36.0.0/d8" --min-api 26 \
  --lib "$ANDROID_HOME/platforms/android-36/android.jar" \
  --classpath adpp-api-v1.jar \
  --classpath "$KOTLIN_HOME/lib/kotlin-stdlib.jar" \
  --output dex plugin.jar
```

Put `dex/classes.dex` beside `manifest.json`, then pack. Example source is in
`examples/plugins/kotlin-info`. Compilation on a phone is not implemented.

## Console commands / JSON API

```text
help
models
files
workspace
packages
plugins
inspect "/path/to/model.gguf"
quantize "/path/to/f16.gguf" result-q4.gguf Q4_K_M
prune "/path/to/model.gguf" result-pruned.gguf 4,5
quantize "/path/to/f16.gguf" result-combined.gguf Q5_K_M 4,5
run example.info:summary {}
```

`run` also accepts built-ins and full JSON options:

```text
run transform {"input":"/path/to/f16.gguf","output":"compressed.gguf","quant":"Q4_K_M","layers":[4,5],"threads":4}
```

Supported quant types: Q4_0, Q4_K_M, Q5_K_M, Q6_K, Q8_0; `COPY` preserves
precision (used by `prune`). Layer indices are zero-based. `prune` requires at least
one index; the entire model cannot be removed. Backend verifies indices against
GGUF architecture block count. Supported architectures depend on pinned llama.cpp.
This does not implement arbitrary channel/head pruning, layer scoring, calibration,
imatrix generation, recovery training or quality evaluation.

Quantization calls the bundled `llama_model_quantize`, layer removal uses its
`prune_layers` field. Combined operation removes selected layers and quantizes the
remaining weights. There is no automatic search for a good compression recipe.
Pruning may severely degrade quality; benchmark the result yourself. Requantization
is disabled: use FP16/BF16/F32 source weights for quantization. `COPY` pruning can
preserve already quantized tensors. No claim of universal architecture support.

Use **+ GGUF** to copy an external source into the workspace. `models` prints
absolute paths. Relative input/output paths are resolved under workspace.
Outputs cannot overwrite existing files or sources. Partial files are removed on
reported failure (process death may leave `.partial`). Output is also copied into
the local model library for chat: reserve disk space for source, output and that copy.
Transforms run on CPU with a 64 MiB quantization slab budget; total peak RAM can
be much higher. No percentage progress or interruption is exposed by this wrapper.

## Packaging

```sh
python3 tools/pack_adpp.py examples/plugins/python-info example-info.adpp
```

Example Kotlin folder requires compilation to `classes.dex` before packing.
Build/install confirmation is distinct from on-device validation: manually verify
plugin execution and transforms on the intended phone and model.
