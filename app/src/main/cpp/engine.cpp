#include <jni.h>
#include "llama.h"
#include <algorithm>
#include <atomic>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
std::atomic<bool> stopped{false};
std::once_flag initialized;
struct Engine {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    llama_sampler * sampler = nullptr;
    llama_batch_ext * batch = nullptr;
    int position = 0;
    void reset() {
        if (batch) llama_batch_ext_free(batch);
        if (sampler) llama_sampler_free(sampler);
        if (context) llama_free(context);
        batch = nullptr; sampler = nullptr; context = nullptr;
    }
    ~Engine() { reset(); if (model) llama_model_free(model); }
};
void fail(JNIEnv * env, const char * message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
Engine * engine(jlong handle) {
    if (!handle) throw std::runtime_error("Model is not loaded");
    return reinterpret_cast<Engine *>(handle);
}
std::string bytes(JNIEnv * env, jbyteArray value) {
    std::string result(env->GetArrayLength(value), '\0');
    env->GetByteArrayRegion(value, 0, result.size(), reinterpret_cast<jbyte *>(result.data()));
    return result;
}
void decode(Engine * e, const llama_token * tokens, int count) {
    llama_batch_ext_clear(e->batch);
    for (int i = 0; i < count; ++i) {
        int index = llama_batch_ext_add_token(e->batch, 0, tokens[i]);
        llama_pos pos = e->position + i;
        llama_batch_ext_set_pos(e->batch, index, &pos);
    }
    llama_batch_ext_set_output_logits(e->batch, count - 1, true);
    if (llama_process(e->context, LLAMA_PROCESS_TYPE_DECODE, e->batch) != 0)
        throw std::runtime_error("llama.cpp decode failed (RAM or unsupported model)");
    e->position += count;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_watcher_modelhub_data_NativeEngine_load(JNIEnv * env, jobject, jstring path) {
    try {
        std::call_once(initialized, [] { llama_backend_init(); });
        const char * filename = env->GetStringUTFChars(path, nullptr);
        auto e = std::make_unique<Engine>();
        auto params = llama_model_default_params();
        params.n_gpu_layers = 0;
        e->model = llama_model_load_from_file(filename, params);
        env->ReleaseStringUTFChars(path, filename);
        if (!e->model) throw std::runtime_error("Cannot load GGUF: check file integrity, architecture and available RAM");
        if (!llama_model_has_decoder(e->model) || llama_model_has_encoder(e->model))
            throw std::runtime_error("This file is not a supported text-generation model");
        return reinterpret_cast<jlong>(e.release());
    } catch (const std::exception & ex) { fail(env, ex.what()); return 0; }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_watcher_modelhub_data_NativeEngine_release(JNIEnv *, jobject, jlong handle) {
    delete reinterpret_cast<Engine *>(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_watcher_modelhub_data_NativeEngine_prepare(JNIEnv * env, jobject, jlong handle,
        jobjectArray roles, jobjectArray contents, jfloat temperature, jint threads) {
    try {
        auto e = engine(handle);
        stopped = false;
        e->reset(); e->position = 0;
        int count = env->GetArrayLength(contents);
        std::vector<std::string> role_strings, texts;
        role_strings.reserve(count); texts.reserve(count);
        for (int i = 0; i < count; ++i) {
            auto role = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
            const char * r = env->GetStringUTFChars(role, nullptr);
            role_strings.emplace_back(r);
            env->ReleaseStringUTFChars(role, r); env->DeleteLocalRef(role);
            auto text = static_cast<jbyteArray>(env->GetObjectArrayElement(contents, i));
            texts.push_back(bytes(env, text)); env->DeleteLocalRef(text);
        }
        std::vector<llama_chat_message> messages;
        for (int i = 0; i < count; ++i) messages.push_back({role_strings[i].c_str(), texts[i].c_str()});
        const char * tmpl = llama_model_chat_template(e->model, nullptr);
        if (!tmpl) throw std::runtime_error("GGUF has no chat template; choose an Instruct/Chat model");
        int length = llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, nullptr, 0);
        if (length < 0) throw std::runtime_error("Model chat template is not supported by this backend");
        std::vector<char> prompt(length + 1);
        llama_chat_apply_template(tmpl, messages.data(), messages.size(), true, prompt.data(), prompt.size());
        auto vocab = llama_model_get_vocab(e->model);
        int n = -llama_tokenize(vocab, prompt.data(), length, nullptr, 0, true, true);
        if (n <= 0 || n > 1920) throw std::runtime_error("Context is full (2048 tokens). Start a new conversation or shorten your message");
        std::vector<llama_token> tokens(n);
        if (llama_tokenize(vocab, prompt.data(), length, tokens.data(), n, true, true) < 0)
            throw std::runtime_error("Tokenization failed");
        auto cp = llama_context_default_params();
        cp.n_ctx = 2048; cp.n_batch = 128; cp.n_ubatch = 128;
        cp.n_threads = std::clamp<int>(threads, 1, 8); cp.n_threads_batch = cp.n_threads;
        e->context = llama_init_from_model(e->model, cp);
        if (!e->context) throw std::runtime_error("Cannot allocate model context; not enough RAM");
        e->batch = llama_batch_ext_init(e->context);
        e->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(e->sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(e->sampler, llama_sampler_init_top_p(0.95f, 1));
        llama_sampler_chain_add(e->sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(e->sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        for (int offset = 0; offset < n && !stopped; offset += 128)
            decode(e, tokens.data() + offset, std::min(128, n - offset));
    } catch (const std::exception & ex) { fail(env, ex.what()); }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_watcher_modelhub_data_NativeEngine_next(JNIEnv * env, jobject, jlong handle) {
    try {
        auto e = engine(handle);
        if (stopped || !e->context || e->position >= static_cast<int>(llama_n_ctx(e->context)) - 1) return nullptr;
        auto vocab = llama_model_get_vocab(e->model);
        llama_token token = llama_sampler_sample(e->sampler, e->context, -1);
        if (llama_vocab_is_eog(vocab, token)) return nullptr;
        std::vector<char> piece(256);
        int n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (n < 0) {
            piece.resize(-n);
            n = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        }
        if (n < 0) throw std::runtime_error("Cannot decode token");
        decode(e, &token, 1);
        auto result = env->NewByteArray(n);
        env->SetByteArrayRegion(result, 0, n, reinterpret_cast<const jbyte *>(piece.data()));
        return result;
    } catch (const std::exception & ex) { fail(env, ex.what()); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_watcher_modelhub_data_NativeEngine_stop(JNIEnv *, jobject) { stopped = true; }
