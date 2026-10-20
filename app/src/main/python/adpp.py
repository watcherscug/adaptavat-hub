"""Adaptavat plugin API v1. Host binds the API before calling a plugin."""
import json

_host = None

def _bind(host):
    global _host
    _host = host

def call(command, **arguments):
    result = str(_host.invoke(command, json.dumps(arguments)))
    try:
        return json.loads(result)
    except (ValueError, TypeError):
        return result

def log(message):
    _host.log(str(message))

def models():
    return json.loads(str(_host.models()))

def workspace():
    return str(_host.workspace())

def quantize(source, output, quant="Q4_K_M", layers=(), threads=4):
    return call("quantize", input=source, output=output, quant=quant, layers=list(layers), threads=threads)

def prune(source, output, layers, threads=4):
    return call("prune", input=source, output=output, layers=list(layers), threads=threads)
