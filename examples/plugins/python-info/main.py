def execute(api, command, arguments):
    if command == "summary":
        models = api.models()
        api.log("Local GGUF inventory")
        return {"count": len(models), "bytes": sum(m["bytes"] for m in models)}
    if command == "numpy":
        import numpy as np
        weights = np.array(arguments.get("weights", [1, 2, 3]), dtype=np.float32)
        return {"mean": float(weights.mean()), "shape": list(weights.shape)}
    raise ValueError(command)
