package com.mangaviewer.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2：下载记录增加作者 / 分类元数据（筛选用）与来源标记（应用下载 / 扫描导入）。
 *
 * 三个列都允许 NULL，旧记录保持 null（表示「尚未补全」），
 * 扫描或补全后写入 JSON 数组或 'APP' / 'SCAN'。不触碰任何用户文件。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `artistsJson` TEXT")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `categoriesJson` TEXT")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `origin` TEXT")
    }
}

/**
 * v2 -> v3：收藏记录增加作者元数据（收藏页筛选作者用）。
 *
 * 列允许 NULL，旧收藏保持 null（「尚未补全」）；之后从下载记录、详情页标签或账号同步补全。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `favorites` ADD COLUMN `artistsJson` TEXT")
    }
}

/** v3 -> v4：收藏记录再补分类元数据（收藏页筛选器与下载页同款：日期 / 首字母 / 作者 / 分类）。 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `favorites` ADD COLUMN `categoriesJson` TEXT")
    }
}
