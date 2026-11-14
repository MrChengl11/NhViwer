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
