package de.taz.app.android.persistence.dao

import androidx.room.Dao
import androidx.room.Query
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Image
import de.taz.app.android.persistence.join.ArticleImageJoin


@Dao
abstract class ArticleImageJoinDao : BaseDao<ArticleImageJoin>() {

    @Query(
        """
        SELECT name, storageType, moTime, sha256, size, folder, downloadedStatus, type, alpha, resolution
        FROM FileEntry INNER JOIN ArticleImageJoin
        ON FileEntry.name = ArticleImageJoin.imageFileName
        INNER JOIN Image ON Image.fileEntryName == ArticleImageJoin.imageFileName
        WHERE ArticleImageJoin.articleFileName == :articleFileName
        ORDER BY ArticleImageJoin.`index` ASC
    """
    )
    abstract fun getImagesForArticle(articleFileName: String): List<Image>

}
