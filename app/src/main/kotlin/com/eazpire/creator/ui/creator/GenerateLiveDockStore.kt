package com.eazpire.creator.ui.creator

import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.usableJwt
import com.eazpire.creator.auth.SecureTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class LiveGenJob(
    val jobId: String,
    val prompt: String,
    val status: String = "generating",
    val previewUrl: String = "",
    val error: String = "",
    val busy: Boolean = false,
)

object GenerateLiveDockStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val streamJobs = mutableMapOf<String, Job>()

    private val _jobs = MutableStateFlow<List<LiveGenJob>>(emptyList())
    val jobs: StateFlow<List<LiveGenJob>> = _jobs

    private val _minimized = MutableStateFlow(false)
    val minimized: StateFlow<Boolean> = _minimized

    private val _activeId = MutableStateFlow<String?>(null)
    val activeId: StateFlow<String?> = _activeId

    fun attach(tokenStore: SecureTokenStore, jobId: String, prompt: String) {
        if (jobId.isBlank()) return
        if (_jobs.value.any { it.jobId == jobId }) {
            _activeId.value = jobId
            _minimized.value = false
            return
        }
        _jobs.update { it + LiveGenJob(jobId = jobId, prompt = prompt) }
        _activeId.value = jobId
        _minimized.value = false
        startStream(tokenStore, jobId)
    }

    fun attachPending(prompt: String): String {
        val id = "pending_${System.currentTimeMillis()}_${(Math.random() * 1_000_000).toInt()}"
        _jobs.update { it + LiveGenJob(jobId = id, prompt = prompt) }
        _activeId.value = id
        _minimized.value = false
        return id
    }

    fun promote(tokenStore: SecureTokenStore, pendingId: String, jobId: String, prompt: String) {
        if (jobId.isBlank()) {
            drop(pendingId)
            return
        }
        val hadPending = _jobs.value.any { it.jobId == pendingId }
        if (!hadPending) {
            attach(tokenStore, jobId, prompt)
            return
        }
        _jobs.update { list ->
            list.map { job ->
                if (job.jobId == pendingId) job.copy(jobId = jobId, prompt = prompt.ifBlank { job.prompt })
                else job
            }
        }
        if (_activeId.value == pendingId) _activeId.value = jobId
        _minimized.value = false
        startStream(tokenStore, jobId)
    }

    fun drop(pendingId: String) {
        if (pendingId.isBlank()) return
        remove(pendingId)
    }

    fun minimize() {
        _minimized.value = true
    }

    fun expand() {
        _minimized.value = false
    }

    fun select(jobId: String) {
        _activeId.value = jobId
        _minimized.value = false
    }

    fun save(tokenStore: SecureTokenStore, jobId: String) {
        val ownerId = tokenStore.getOwnerId().orEmpty()
        patch(jobId) { it.copy(busy = true) }
        scope.launch {
            try {
                val jwt = usableJwt(tokenStore.getJwt())
                val api = CreatorApi(jwt = jwt)
                val body = JSONObject()
                    .put("job_id", jobId)
                    .put("owner_id", ownerId)
                    .put("library_status", "inactive")
                val resp = withContext(Dispatchers.IO) { api.saveDesign(body) }
                if (resp.optBoolean("ok", true) && resp.optString("error").isBlank()) {
                    remove(jobId)
                } else {
                    patch(jobId) {
                        it.copy(
                            busy = false,
                            error = resp.optString("message").ifBlank { resp.optString("error", "save_failed") },
                        )
                    }
                }
            } catch (e: Exception) {
                patch(jobId) { it.copy(busy = false, error = e.message?.take(160).orEmpty()) }
            }
        }
    }

    fun discard(tokenStore: SecureTokenStore, jobId: String) {
        streamJobs.remove(jobId)?.cancel()
        val ownerId = tokenStore.getOwnerId().orEmpty()
        scope.launch {
            try {
                val jwt = usableJwt(tokenStore.getJwt())
                withContext(Dispatchers.IO) {
                    CreatorApi(jwt = jwt).discardGeneratedJob(ownerId, jobId)
                }
            } catch (_: Exception) {
            } finally {
                remove(jobId)
            }
        }
    }

    private fun startStream(tokenStore: SecureTokenStore, jobId: String) {
        streamJobs.remove(jobId)?.cancel()
        streamJobs[jobId] = scope.launch(Dispatchers.IO) {
            try {
                val jwt = usableJwt(tokenStore.getJwt())
                val ownerId = tokenStore.getOwnerId().orEmpty()
                CreatorApi(jwt = jwt).generateLiveStream(ownerId, jobId) { ev ->
                    val type = ev.optString("type")
                    val image = ev.optJSONObject("image")
                    val url = image?.optString("url").orEmpty()
                    when (type) {
                        "partial" -> if (url.isNotBlank()) patch(jobId) { it.copy(status = "partial", previewUrl = url) }
                        "completed" -> patch(jobId) {
                            it.copy(status = "ready", previewUrl = url.ifBlank { it.previewUrl })
                        }
                        "error" -> patch(jobId) { it.copy(status = "error", error = ev.optString("message", "error")) }
                    }
                }
            } catch (e: Exception) {
                patch(jobId) { it.copy(status = "error", error = e.message?.take(160).orEmpty()) }
            }
        }
    }

    private fun patch(jobId: String, fn: (LiveGenJob) -> LiveGenJob) {
        _jobs.update { list -> list.map { if (it.jobId == jobId) fn(it) else it } }
    }

    private fun remove(jobId: String) {
        streamJobs.remove(jobId)?.cancel()
        _jobs.update { list -> list.filterNot { it.jobId == jobId } }
        if (_activeId.value == jobId) {
            _activeId.value = _jobs.value.firstOrNull()?.jobId
        }
        if (_jobs.value.isEmpty()) _minimized.value = false
    }
}
