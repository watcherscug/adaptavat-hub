import contextlib
import importlib.util
import importlib.metadata
import io
import json
import shlex
import sys
import os
import traceback
import adpp

_namespace = {"adpp": adpp, "__name__": "__console__"}
_plugins = {}

class Output(io.TextIOBase):
    def __init__(self, host):
        self.host = host
        self.pending = ""

    def write(self, text):
        self.pending += str(text)
        while "\n" in self.pending:
            line, self.pending = self.pending.split("\n", 1)
            self.host.log(line)
        return len(text)

    def flush(self):
        if self.pending:
            self.host.log(self.pending)
            self.pending = ""

def packages():
    return json.dumps({d.metadata["Name"]: d.version for d in importlib.metadata.distributions()}, indent=2)

def plugin(directory, entry, host, command, arguments):
    adpp._bind(host)
    filename = directory + "/" + entry
    key = (filename, os.stat(filename).st_mtime_ns)
    if key not in _plugins:
        spec = importlib.util.spec_from_file_location("adpp_plugin_" + str(len(_plugins)), filename)
        module = importlib.util.module_from_spec(spec)
        sys.modules[spec.name] = module
        sys.path.insert(0, directory)
        try:
            spec.loader.exec_module(module)
        except BaseException:
            sys.modules.pop(spec.name, None)
            raise
        finally:
            sys.path.remove(directory)
        _plugins[key] = module
    result = _plugins[key].execute(adpp, command, json.loads(arguments))
    return json.dumps(result, ensure_ascii=False)

def execute(source, host):
    adpp._bind(host)
    stream = Output(host)
    with contextlib.redirect_stdout(stream), contextlib.redirect_stderr(stream):
        try:
            if source.startswith("py "):
                code = source[3:]
                try:
                    compiled = compile(code, "<console>", "eval")
                except SyntaxError:
                    exec(compile(code, "<console>", "exec"), _namespace)
                else:
                    result = eval(compiled, _namespace)
                    if result is not None:
                        print(repr(result))
                return
            if source.startswith("run "):
                rest = source[4:].split(None, 1)
                print(host.invoke(rest[0], rest[1] if len(rest) > 1 else "{}"))
                return
            args = shlex.split(source)
            if not args:
                return
            command, rest = args[0], args[1:]
            values = {}
            if command == "inspect":
                values = {"input": rest[0]}
            elif command in ("quantize", "prune"):
                values = {"input": rest[0], "output": rest[1]}
                if command == "quantize":
                    values["quant"] = rest[2]
                layers = rest[2] if command == "prune" else (rest[3] if len(rest) > 3 else "")
                values["layers"] = [int(x) for x in layers.split(",") if x]
            print(host.invoke(command, json.dumps(values)))
        except BaseException:
            traceback.print_exc()
        finally:
            stream.flush()
