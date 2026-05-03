package com.wildanaizzaddin.ddltracker.service

import com.intellij.openapi.diagnostic.logger
import com.wildanaizzaddin.ddltracker.model.DDLChange
import com.wildanaizzaddin.ddltracker.settings.DDLTrackerSettings
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.lib.PersonIdent
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
        val hasRemote = git.repository.config.getString("remote", "origin", "url") != null
        if (!hasRemote) return

        val backoff = longArrayOf(500, 1000, 2000)
        for (attempt in 0..2) {
            try {
                git.pull().setRebase(true).call()
                git.push().setRemote("origin").call()
                return
            } catch (e: TransportException) {
                LOG.warn("Push attempt ${attempt + 1} failed: ${e.message}")
                if (attempt < 2) Thread.sleep(backoff[attempt]) else throw e
            }
        }
    }

    private fun commitMessage(c: DDLChange) =
        "[${c.actionType.replace('_', ' ')}] ${c.schema}.${c.objectName} — ${c.user} @ ${c.timestamp}"
}
