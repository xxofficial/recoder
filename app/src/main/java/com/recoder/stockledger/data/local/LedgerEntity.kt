package com.recoder.stockledger.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ledgers")
data class LedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,              // 账本名称，如 "默认个人账本", "查理的备用账本", "致富合资账本"
    val type: String,              // 账本类型: "PERSONAL" (个人), "JOINT" (合资)
    val description: String = "",  // 账本备注
    val partners: String = "",     // 合伙人名单，逗号分隔，如 "我,Alice"
    val createdAt: Long = System.currentTimeMillis()
)
