# adaptavat-hub

Android hub for local GGUF models, llama.cpp chat, and ADPP extensions.

## Plugins

Open **Мастерская → Плагины → Установить .adpp** to install a Kotlin or Python
plugin through Android's file picker. Enable/disable, remove, and run commands
from each plugin's card. The Console tab is an optional developer tool.

See [ADPP API documentation](docs/ADPP.md), [Python example](examples/plugins/python-info),
and [Kotlin example](examples/plugins/kotlin-info).

Build: `./gradlew assembleDebug`. Python 3.12 is required on the build host.
