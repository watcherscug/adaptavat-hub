#include <jni.h>
#include "llama.h"
#include "gguf.h"
#include <algorithm>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

static std::mutex transform_mutex;
static std::string string(JNIEnv * env, jstring value) {
    const char * raw = env->GetStringUTFChars(value, nullptr);
    std::string result(raw);
    env->ReleaseStringUTFChars(value, raw);
    return result;
}
extern "C" JNIEXPORT void JNICALL
Java_dev_watcher_modelhub_data_NativeEngine_transform(JNIEnv * env, jobject,
        jstring input, jstring output, jstring quant, jintArray layers, jint threads) {
    try {
        std::lock_guard<std::mutex> lock(transform_mutex);
        auto source = string(env, input), dest = string(env, output), type = string(env, quant);
        auto params = llama_model_quantize_default_params();
        if (type == "COPY") params.only_copy = true;
        else if (type == "Q4_0") params.ftype = LLAMA_FTYPE_MOSTLY_Q4_0;
        else if (type == "Q4_K_M") params.ftype = LLAMA_FTYPE_MOSTLY_Q4_K_M;
        else if (type == "Q5_K_M") params.ftype = LLAMA_FTYPE_MOSTLY_Q5_K_M;
        else if (type == "Q6_K") params.ftype = LLAMA_FTYPE_MOSTLY_Q6_K;
        else if (type == "Q8_0") params.ftype = LLAMA_FTYPE_MOSTLY_Q8_0;
        else throw std::runtime_error("Unsupported quantization type");
        params.nthread = std::clamp<int>(threads, 1, 8);
        params.max_buf_size = 64 * 1024 * 1024;
        params.allow_requantize = false;
        int count = env->GetArrayLength(layers);
        std::vector<int32_t> prune(count + 1, -1);
        if (count) env->GetIntArrayRegion(layers, 0, count, prune.data());
        if (count) {
            gguf_init_params gp = {true, nullptr};
            auto metadata = gguf_init_from_file(source.c_str(), gp);
            if (!metadata) throw std::runtime_error("Cannot read GGUF metadata");
            auto arch_key = gguf_find_key(metadata, "general.architecture");
            std::string key = arch_key >= 0 ? std::string(gguf_get_val_str(metadata, arch_key)) + ".block_count" : "";
            auto block_key = gguf_find_key(metadata, key.c_str());
            int blocks = block_key >= 0 ? gguf_get_val_u32(metadata, block_key) : 0;
            gguf_free(metadata);
            if (blocks <= 0 || count >= blocks) throw std::runtime_error("Invalid layer count or pruning all layers");
            for (int i = 0; i < count; ++i)
                if (prune[i] < 0 || prune[i] >= blocks) throw std::runtime_error("Layer index outside model block count");
        }
        if (count) params.prune_layers = prune.data();
        if (llama_model_quantize(source.c_str(), dest.c_str(), &params) != 0)
            throw std::runtime_error("GGUF transform failed; check source precision, layer indices, RAM and disk space");
    } catch (const std::exception & ex) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), ex.what());
    }
}
