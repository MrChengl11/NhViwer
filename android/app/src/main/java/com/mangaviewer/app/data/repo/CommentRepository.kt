package com.mangaviewer.app.data.repo

import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.db.CommentDraftEntity
import com.mangaviewer.app.data.model.CommentUi
import com.mangaviewer.app.data.model.toUi
import com.mangaviewer.app.data.remote.pow.Sha256Pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 评论：列表、发布（自动求解 PoW）、草稿。 */
class CommentRepository(private val container: AppContainer) {

    private val pow = Sha256Pow()

    suspend fun list(galleryId: Long, page: Int = 0, perPage: Int = 25): List<CommentUi> {
        val api = container.api(container.settings.current().effectiveBase)
        return api.comments(galleryId, page, perPage).result.map { it.toUi() }
    }

    /**
     * 发布评论。Turnstile 无法程序化通过，因此 captcha 需要用户手动提供（可留空由服务端判定）；
     * PoW 部分由 [Sha256Pow] 自动求解。
     */
    suspend fun post(galleryId: Long, body: String, captchaResponse: String?): CommentUi {
        val trimmed = body.trim()
        require(trimmed.length in MIN_LENGTH..MAX_LENGTH) {
            "评论长度需在 " + MIN_LENGTH + "-" + MAX_LENGTH + " 字之间"
        }
        val api = container.api(container.settings.current().effectiveBase)
        val powDto = api.pow("comment")
        val nonce = withContext(Dispatchers.Default) { pow.solve(powDto.challenge, powDto.difficulty) }
        val dto = api.postComment(galleryId, trimmed, powDto.challenge, nonce, captchaResponse)
        container.commentDao.delete(galleryId)
        return dto.toUi()
    }

    suspend fun delete(commentId: Long) {
        val api = container.api(container.settings.current().effectiveBase)
        api.deleteComment(commentId)
    }

    suspend fun draft(galleryId: Long): String? = container.commentDao.find(galleryId)?.body

    suspend fun saveDraft(galleryId: Long, body: String) {
        if (body.isBlank()) container.commentDao.delete(galleryId)
        else container.commentDao.upsert(CommentDraftEntity(galleryId, body, System.currentTimeMillis()))
    }

    companion object {
        const val MIN_LENGTH = 10
        const val MAX_LENGTH = 1000
    }
}
