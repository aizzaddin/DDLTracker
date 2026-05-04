package com.wildanaizzaddin.ddltracker.service

import com.intellij.openapi.diagnostic.logger
import com.wildanaizzaddin.ddltracker.model.DDLChange
import com.wildanaizzaddin.ddltracker.settings.DDLTrackerSettings
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class GitCommitService(private val settings: DDLTrackerSettings) {

    private val LOG = logger<GitCommitService>()

    fun commitAndPush(change: DDLChange, file: File): Result<String> = runCatching {
        val repoDir = File(settings.state.repoPath)
        Git.open(repoDir).use { git ->
            val relativePath = file.relativeTo(repoDir).path.replace('\\', '/')
            git.add().addFilepattern(relativePath).call()

            val author = PersonIdent(change.user, "${change.user}@local")
            val message = commitMessage(change)
            git.commit().setAuthor(author).setCommitter(author).setMessage(message).call()
            LOG.debug("Committed: $message")

            if (settings.state.autoPush) {
                pushWithRetry(git)
            }
            message
        }
    }

    private fun pushWithRetry(git: Git) {
        val remoteUrl = git.repository.config.getString("remote", "origin", "url") ?: return
        val cp = resolveCredentials(remoteUrl)

        val backoff = longArrayOf(500, 1000, 2000)
        for (attempt in 0..2) {
            try {
                git.pull().setRebase(true).also { cp?.let(it::setCredentialsProvider) }.call()
                git.push().setRemote("origin").also { cp?.let(it::setCredentialsProvider) }.call()
                return
            } catch (e: TransportException) {
                LOG.warn("Push attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < 2) Thread.sleep(backoff[attempt]) else throw e
            }
        }
    }

    // Delegate to the system git credential helper (osxkeychain, git-credential-manager, etc.)
    // so we don't need to store credentials in plugin settings.
    private fun resolveCredentials(remoteUrl: String): UsernamePasswordCredentialsProvider? {
        if (!remoteUrl.startsWith("http")) return null
        return try {
            val uri = URIish(remoteUrl)
            val proc = ProcessBuilder("git", "credential", "fill")
                .redirectErrorStream(false)
                .start()
            proc.outputStream.bufferedWriter().use { w ->
                w.write("protocol=${uri.scheme}\nhost=${uri.host}\n\n")
            }
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            val user = out.lines().firstOrNull { it.startsWith("username=") }?.removePrefix("username=")
            val pass = out.lines().firstOrNull { it.startsWith("password=") }?.removePrefix("password=")
            if (user != null && pass != null) UsernamePasswordCredentialsProvider(user, pass) else null
        } catch (e: Exception) {
            LOG.warn("Could not resolve credentials via git credential fill: ${e.message}")
            null
        }
    }

    private fun commitMessage(c: DDLChange): String {
        val base = "[${c.actionType.replace('_', ' ')}] ${c.schema}.${c.objectName} — ${c.user} @ ${c.timestamp}"
        return if (c.project.isNotBlank()) "$base | ${c.project}" else base
    }
}
