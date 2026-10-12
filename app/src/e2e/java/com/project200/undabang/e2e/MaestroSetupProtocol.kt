package com.project200.undabang.e2e

import android.content.Context
import android.system.Os
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import net.openid.appauth.AuthState
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

internal sealed interface MaestroSetupRequest {
    data object Export : MaestroSetupRequest

    data class FixtureOperation(
        val operation: String,
        val authState: AuthState,
        val memberId: String,
        val fixtureStartDate: LocalDate,
    ) : MaestroSetupRequest
}

internal class MaestroSetupFailure(val reason: String) : Exception(reason)

internal class MaestroSetupProtocol
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun consumeRequest(): MaestroSetupRequest {
            val requestFile = File(context.filesDir, REQUEST_FILE)
            if (!requestFile.isFile) throw MaestroSetupFailure("missing_request")

            // 파싱 또는 인증이 실패해도 원본 자격 증명 파일은 남기지 않습니다
            val requestText =
                try {
                    if (requestFile.length() !in 1..MAX_REQUEST_BYTES) {
                        throw MaestroSetupFailure("invalid_request")
                    }
                    requestFile.readText(Charsets.UTF_8)
                } finally {
                    if (!requestFile.delete()) throw MaestroSetupFailure("private_file_failed")
                }

            return try {
                val json = JSONObject(requestText)
                when (val operation = json.getString("operation")) {
                    "export" -> {
                        requireKeys(json, setOf("operation"))
                        MaestroSetupRequest.Export
                    }
                    "prepare", "cleanup" -> {
                        requireKeys(json, setOf("operation", "authState", "memberId", "fixtureStartDate"))
                        val memberId = json.get("memberId") as? String
                        val startDate = json.get("fixtureStartDate") as? String
                        if (memberId.isNullOrBlank() || startDate == null || !DATE_PATTERN.matches(startDate)) {
                            throw MaestroSetupFailure("invalid_request")
                        }
                        MaestroSetupRequest.FixtureOperation(
                            operation = operation,
                            authState = AuthState.jsonDeserialize(json.getJSONObject("authState")),
                            memberId = memberId,
                            fixtureStartDate = LocalDate.parse(startDate),
                        )
                    }
                    else -> throw MaestroSetupFailure("invalid_request")
                }
            } catch (failure: MaestroSetupFailure) {
                throw failure
            } catch (_: Exception) {
                // JSON/AppAuth 예외의 메시지에는 입력 토큰이 포함될 수 있습니다
                throw MaestroSetupFailure("invalid_request")
            }
        }

        fun clearOutputs() {
            deletePrivateFile(RESULT_FILE)
            deleteSession()
        }

        fun deleteRequest() {
            deletePrivateFile(REQUEST_FILE)
        }

        fun deleteSession() {
            deletePrivateFile(SESSION_FILE)
        }

        fun writeStatus(status: String) {
            writePrivateJson(RESULT_FILE, JSONObject().put("status", status))
        }

        fun writeFailure(reason: String) {
            writePrivateJson(RESULT_FILE, JSONObject().put("status", "failed").put("reason", reason))
        }

        fun writeSession(
            authState: AuthState,
            memberId: String,
        ) {
            writePrivateJson(
                SESSION_FILE,
                JSONObject().put("authState", authState.jsonSerialize()).put("memberId", memberId),
            )
        }

        private fun requireKeys(
            json: JSONObject,
            keys: Set<String>,
        ) {
            if (json.keys().asSequence().toSet() != keys) throw MaestroSetupFailure("invalid_request")
        }

        private fun deletePrivateFile(name: String) {
            val file = File(context.filesDir, name)
            AtomicFile(file).delete()
            // AtomicFile의 임시 파일도 프로세스 중단 뒤 재사용하지 않습니다
            val pending = File(context.filesDir, "$name.new")
            if ((pending.exists() && !pending.delete()) || file.exists() || File(context.filesDir, "$name.bak").exists()) {
                throw MaestroSetupFailure("private_file_failed")
            }
        }

        private fun writePrivateJson(
            name: String,
            json: JSONObject,
        ) {
            val atomicFile = AtomicFile(File(context.filesDir, name))
            val stream = atomicFile.startWrite()
            try {
                Os.fchmod(stream.fd, PRIVATE_FILE_MODE)
                stream.write(json.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
                atomicFile.finishWrite(stream)
            } catch (_: Exception) {
                atomicFile.failWrite(stream)
                throw MaestroSetupFailure("private_file_failed")
            }
        }

        companion object {
            private const val REQUEST_FILE = "maestro-request.json"
            private const val RESULT_FILE = "maestro-result.json"
            private const val SESSION_FILE = "maestro-session.json"
            private const val MAX_REQUEST_BYTES = 1024L * 1024L
            private const val PRIVATE_FILE_MODE = 384 // 0600
            private val DATE_PATTERN = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
        }
    }
