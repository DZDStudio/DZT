package cn.tj.dzd.mc.dzt.money

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.data.table.MoneyRecord
import cn.tj.dzd.mc.dzt.data.table.MoneyRecordType
import cn.tj.dzd.mc.dzt.data.table.moneyRecordMapper
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import taboolib.common.LifeCycle
import taboolib.common.platform.Awake
import taboolib.common.platform.function.severe
import taboolib.expansion.DataMapper
import taboolib.platform.compat.depositBalance
import taboolib.platform.compat.getBalance as getVaultBalance
import taboolib.platform.compat.isEconomySupported
import taboolib.platform.compat.withdrawBalance
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object MoneyService {
    const val MAX_MONEY_RECORDS = 64
    private const val MAX_RELATED_LENGTH = 64
    private const val MAX_REMARK_LENGTH = 256

    private val playerLocks = ConcurrentHashMap<UUID, Any>()

    /**
     * 在插件启用时检查 Vault 是否已连接到经济服务。
     *
     * Vault 本身只是桥接层，服务器还必须安装 EssentialsX、CMI 等经济插件。
     */
    @Awake(LifeCycle.ACTIVE)
    fun checkVaultEconomy() {
        if (!isEconomySupported) {
            severe("未检测到可用的 Vault 经济服务，DZT 经济功能将不可用。")
        }
    }

    /**
     * 获取在线玩家的 Vault 余额。
     *
     * @param player 玩家对象。
     * @return Vault 余额；经济服务不可用时返回 0。
     */
    fun getBalance(player: Player): Double {
        return getBalance(player as OfflinePlayer)
    }

    /**
     * 获取玩家的 Vault 余额，支持离线玩家。
     *
     * @param uuid 玩家 UUID。
     * @return Vault 余额；经济服务不可用时返回 0。
     */
    fun getBalance(uuid: UUID): Double {
        return getBalance(Bukkit.getOfflinePlayer(uuid))
    }

    /**
     * 增加玩家的 Vault 余额。
     *
     * Vault 交易成功后会追加一条本地入账流水。
     *
     * @param player 玩家对象。
     * @param amount 增加金额，必须大于 0。
     * @param related 关联对象标识，可存玩家 UUID、server、system 等文本。
     * @param remark 备注。
     * @return Vault 是否成功完成交易。
     */
    fun increase(player: Player, amount: Int, related: String? = null, remark: String? = null): Boolean {
        return increase(player as OfflinePlayer, amount, related, remark)
    }

    /**
     * 增加离线玩家的 Vault 余额。
     *
     * @param uuid 玩家 UUID。
     * @param amount 增加金额，必须大于 0。
     * @param related 关联对象标识，可存玩家 UUID、server、system 等文本。
     * @param remark 备注。
     * @return Vault 是否成功完成交易。
     */
    fun increase(uuid: UUID, amount: Int, related: String? = null, remark: String? = null): Boolean {
        return increase(Bukkit.getOfflinePlayer(uuid), amount, related, remark)
    }

    /**
     * 减少玩家的 Vault 余额。
     *
     * @param player 玩家对象。
     * @param amount 减少金额，必须大于 0。
     * @param related 关联对象标识，可存玩家 UUID、server、system 等文本。
     * @param remark 备注。
     * @return 余额充足且 Vault 成功完成交易时返回 true。
     */
    fun decrease(player: Player, amount: Int, related: String? = null, remark: String? = null): Boolean {
        return decrease(player as OfflinePlayer, amount, related, remark)
    }

    /**
     * 减少离线玩家的 Vault 余额。
     *
     * @param uuid 玩家 UUID。
     * @param amount 减少金额，必须大于 0。
     * @param related 关联对象标识，可存玩家 UUID、server、system 等文本。
     * @param remark 备注。
     * @return 余额充足且 Vault 成功完成交易时返回 true。
     */
    fun decrease(uuid: UUID, amount: Int, related: String? = null, remark: String? = null): Boolean {
        return decrease(Bukkit.getOfflinePlayer(uuid), amount, related, remark)
    }

    /**
     * 在两个玩家的 Vault 账户之间转账。
     *
     * 若接收方入账失败，会立即向转出方补偿退款。Vault 不提供跨账户事务，
     * 因此极端情况下补偿也可能失败，此时会向控制台输出严重错误供人工处理。
     *
     * @param from 转出玩家。
     * @param to 接收玩家。
     * @param amount 转账金额，必须大于 0。
     * @param remark 备注。
     * @return 双方 Vault 交易均成功时返回 true。
     */
    fun transfer(from: Player, to: Player, amount: Int, remark: String? = null): Boolean {
        return transfer(from as OfflinePlayer, to as OfflinePlayer, amount, remark)
    }

    /**
     * 在两个离线玩家的 Vault 账户之间转账。
     *
     * @param from 转出玩家 UUID。
     * @param to 接收玩家 UUID。
     * @param amount 转账金额，必须大于 0。
     * @param remark 备注。
     * @return 双方 Vault 交易均成功时返回 true。
     */
    fun transfer(from: UUID, to: UUID, amount: Int, remark: String? = null): Boolean {
        return transfer(Bukkit.getOfflinePlayer(from), Bukkit.getOfflinePlayer(to), amount, remark)
    }

    /**
     * 获取玩家 DD Coin 流水记录。
     *
     * @param player 玩家对象。
     * @param limit 返回数量上限，必须大于 0。
     * @return 按时间倒序排列的流水记录；数据库读取失败时返回空列表。
     */
    fun getRecords(player: Player, limit: Int = MAX_MONEY_RECORDS): List<MoneyRecord> {
        return getRecords(player.uniqueId, limit)
    }

    /**
     * 获取玩家 DD Coin 流水记录。
     *
     * @param uuid 玩家 UUID。
     * @param limit 返回数量上限，必须大于 0，超过 [MAX_MONEY_RECORDS] 时按上限处理。
     * @return 按时间倒序排列的流水记录；数据库读取失败时返回空列表。
     */
    fun getRecords(uuid: UUID, limit: Int = MAX_MONEY_RECORDS): List<MoneyRecord> {
        require(limit > 0) { "流水记录获取数量必须大于 0" }
        val actualLimit = limit.coerceAtMost(MAX_MONEY_RECORDS)

        return withPlayerLocks(uuid) {
            runRecordTransaction("获取 DD Coin 流水", emptyList()) {
                val records = findSortedRecords(uuid)
                trimRecords(uuid, records)
                records.take(actualLimit)
            }
        }
    }

    /**
     * 清空玩家 DD Coin 流水记录，不会修改 Vault 余额。
     *
     * @param player 玩家对象。
     * @return 是否成功清空；数据库失败时返回 false。
     */
    fun clearRecords(player: Player): Boolean {
        return clearRecords(player.uniqueId)
    }

    /**
     * 清空玩家 DD Coin 流水记录，不会修改 Vault 余额。
     *
     * @param uuid 玩家 UUID。
     * @return 是否成功清空；数据库失败时返回 false。
     */
    fun clearRecords(uuid: UUID): Boolean {
        return withPlayerLocks(uuid) {
            runRecordTransaction("清空 DD Coin 流水", false) {
                deleteWhere {
                    "uuid" eq uuid.toString()
                }
                true
            }
        }
    }

    private fun getBalance(player: OfflinePlayer): Double {
        if (!isEconomySupported) {
            return 0.0
        }
        return runCatching { player.getVaultBalance() }
            .onFailure { logVaultFailure("查询余额", player.uniqueId, it) }
            .getOrDefault(0.0)
    }

    private fun increase(
        player: OfflinePlayer,
        amount: Int,
        related: String?,
        remark: String?,
    ): Boolean {
        requirePositiveAmount(amount)
        val normalizedRelated = normalizeRelated(related)
        val normalizedRemark = normalizeRemark(remark)

        return withPlayerLocks(player.uniqueId) {
            if (!isEconomySupported) {
                return@withPlayerLocks false
            }
            val response = runCatching { player.depositBalance(amount.toDouble()) }
                .onFailure { logVaultFailure("存款", player.uniqueId, it) }
                .getOrNull() ?: return@withPlayerLocks false
            if (!response.transactionSuccess()) {
                logVaultRejection("存款", player.uniqueId, response.errorMessage)
                return@withPlayerLocks false
            }

            recordTransaction(player.uniqueId, MoneyRecordType.IN, amount, normalizedRelated, normalizedRemark)
            true
        }
    }

    private fun decrease(
        player: OfflinePlayer,
        amount: Int,
        related: String?,
        remark: String?,
    ): Boolean {
        requirePositiveAmount(amount)
        val normalizedRelated = normalizeRelated(related)
        val normalizedRemark = normalizeRemark(remark)

        return withPlayerLocks(player.uniqueId) {
            if (!isEconomySupported || getBalance(player) < amount) {
                return@withPlayerLocks false
            }
            val response = runCatching { player.withdrawBalance(amount.toDouble()) }
                .onFailure { logVaultFailure("取款", player.uniqueId, it) }
                .getOrNull() ?: return@withPlayerLocks false
            if (!response.transactionSuccess()) {
                logVaultRejection("取款", player.uniqueId, response.errorMessage)
                return@withPlayerLocks false
            }

            recordTransaction(player.uniqueId, MoneyRecordType.OUT, amount, normalizedRelated, normalizedRemark)
            true
        }
    }

    private fun transfer(from: OfflinePlayer, to: OfflinePlayer, amount: Int, remark: String?): Boolean {
        require(from.uniqueId != to.uniqueId) { "转账双方不能是同一个玩家" }
        requirePositiveAmount(amount)
        val normalizedRemark = normalizeRemark(remark)

        return withPlayerLocks(from.uniqueId, to.uniqueId) {
            if (!isEconomySupported || getBalance(from) < amount) {
                return@withPlayerLocks false
            }

            val withdrawal = runCatching { from.withdrawBalance(amount.toDouble()) }
                .onFailure { logVaultFailure("转账扣款", from.uniqueId, it) }
                .getOrNull() ?: return@withPlayerLocks false
            if (!withdrawal.transactionSuccess()) {
                logVaultRejection("转账扣款", from.uniqueId, withdrawal.errorMessage)
                return@withPlayerLocks false
            }

            val deposit = runCatching { to.depositBalance(amount.toDouble()) }
                .onFailure { logVaultFailure("转账入账", to.uniqueId, it) }
                .getOrNull()
            if (deposit == null || !deposit.transactionSuccess()) {
                if (deposit != null) {
                    logVaultRejection("转账入账", to.uniqueId, deposit.errorMessage)
                }
                compensateTransfer(from, amount)
                return@withPlayerLocks false
            }

            recordTransfer(from.uniqueId, to.uniqueId, amount, normalizedRemark)
            true
        }
    }

    private fun compensateTransfer(from: OfflinePlayer, amount: Int) {
        val compensation = runCatching { from.depositBalance(amount.toDouble()) }
            .onFailure { logVaultFailure("转账补偿退款", from.uniqueId, it) }
            .getOrNull()
        if (compensation == null || !compensation.transactionSuccess()) {
            severe(
                "Vault 转账补偿退款失败，请人工核对账户。",
                "玩家 UUID: ${from.uniqueId}",
                "应退金额: $amount",
                "Vault 错误: ${compensation?.errorMessage ?: "调用异常"}",
            )
        }
    }

    private fun recordTransaction(
        uuid: UUID,
        type: MoneyRecordType,
        amount: Int,
        related: String?,
        remark: String?,
    ) {
        runRecordTransaction("写入 DD Coin 流水", Unit) {
            appendRecord(uuid, type, amount, related, remark)
            trimRecords(uuid)
        }
    }

    private fun recordTransfer(from: UUID, to: UUID, amount: Int, remark: String?) {
        runRecordTransaction("写入 DD Coin 转账流水", Unit) {
            appendRecord(from, MoneyRecordType.OUT, amount, to.toString(), remark)
            appendRecord(to, MoneyRecordType.IN, amount, from.toString(), remark)
            trimRecords(from)
            trimRecords(to)
        }
    }

    private fun <T> runRecordTransaction(
        action: String,
        fallback: T,
        block: DataMapper<MoneyRecord>.() -> T,
    ): T {
        return DatabaseGuard.execute(action, fallback) {
            moneyRecordMapper.transaction {
                this.block()
            }.getOrThrow()
        }
    }

    private fun DataMapper<MoneyRecord>.appendRecord(
        uuid: UUID,
        type: MoneyRecordType,
        amount: Int,
        related: String?,
        remark: String?,
    ) {
        insert(
            MoneyRecord(
                uuid = uuid,
                time = nextRecordTime(uuid),
                type = type,
                amount = amount,
                related = related,
                remark = remark,
            )
        )
    }

    private fun DataMapper<MoneyRecord>.nextRecordTime(uuid: UUID): Long {
        val latestTime = findSortedRecords(uuid).maxOfOrNull { it.time }
        val now = System.currentTimeMillis()
        return if (latestTime != null && now <= latestTime) latestTime + 1 else now
    }

    private fun DataMapper<MoneyRecord>.findSortedRecords(uuid: UUID): List<MoneyRecord> {
        return findAll {
            "uuid" eq uuid.toString()
        }.sortedByDescending { it.time }
    }

    private fun DataMapper<MoneyRecord>.trimRecords(
        uuid: UUID,
        sortedRecords: List<MoneyRecord> = findSortedRecords(uuid),
    ) {
        sortedRecords.drop(MAX_MONEY_RECORDS).forEach { record ->
            deleteWhere {
                "uuid" eq uuid.toString()
                "time" eq record.time
            }
        }
    }

    private fun requirePositiveAmount(amount: Int) {
        require(amount > 0) { "金额必须大于 0" }
    }

    private fun normalizeRelated(related: String?): String? {
        return normalizeOptionalText(related, MAX_RELATED_LENGTH, "关联对象")
    }

    private fun normalizeRemark(remark: String?): String? {
        return normalizeOptionalText(remark, MAX_REMARK_LENGTH, "备注")
    }

    private fun normalizeOptionalText(value: String?, maxLength: Int, fieldName: String): String? {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        require(normalized.length <= maxLength) { "$fieldName 长度不能超过 $maxLength 个字符" }
        return normalized
    }

    private fun logVaultFailure(action: String, uuid: UUID, error: Throwable) {
        severe(
            "Vault $action 调用失败。",
            "玩家 UUID: $uuid",
            error.stackTraceToString(),
        )
    }

    private fun logVaultRejection(action: String, uuid: UUID, errorMessage: String?) {
        severe(
            "Vault $action 被经济插件拒绝。",
            "玩家 UUID: $uuid",
            "Vault 错误: ${errorMessage ?: "未提供错误信息"}",
        )
    }

    private fun <T> withPlayerLocks(vararg uuids: UUID, block: () -> T): T {
        val locks = uuids
            .distinct()
            .sortedBy { it.toString() }
            .map { uuid -> playerLocks.computeIfAbsent(uuid) { Any() } }

        fun acquire(index: Int): T {
            return if (index >= locks.size) {
                block()
            } else {
                synchronized(locks[index]) {
                    acquire(index + 1)
                }
            }
        }

        return acquire(0)
    }
}

/**
 * 获取玩家当前 Vault 余额。
 *
 * @return Vault 余额；经济服务不可用时返回 0。
 */
fun Player.getMoneyBalance(): Double {
    return MoneyService.getBalance(this)
}

/**
 * 增加玩家的 Vault 余额。
 *
 * @param amount 增加金额，必须大于 0。
 * @param related 关联对象标识，可存玩家 UUID、server、system 等文本。
 * @param remark 备注。
 * @return Vault 是否成功完成交易。
 */
fun Player.increaseMoney(amount: Int, related: String? = null, remark: String? = null): Boolean {
    return MoneyService.increase(this, amount, related, remark)
}

/**
 * 减少玩家的 Vault 余额。
 *
 * @param amount 减少金额，必须大于 0。
 * @param related 关联对象标识，可存玩家 UUID、server、system 等文本。
 * @param remark 备注。
 * @return 余额充足且 Vault 成功完成交易时返回 true。
 */
fun Player.decreaseMoney(amount: Int, related: String? = null, remark: String? = null): Boolean {
    return MoneyService.decrease(this, amount, related, remark)
}

/**
 * 向指定玩家的 Vault 账户转账。
 *
 * @param target 接收玩家。
 * @param amount 转账金额，必须大于 0。
 * @param remark 备注。
 * @return 双方 Vault 交易均成功时返回 true。
 */
fun Player.transferMoney(target: Player, amount: Int, remark: String? = null): Boolean {
    return MoneyService.transfer(this, target, amount, remark)
}

/**
 * 向指定 UUID 对应的 Vault 账户转账。
 *
 * @param target 接收玩家 UUID。
 * @param amount 转账金额，必须大于 0。
 * @param remark 备注。
 * @return 双方 Vault 交易均成功时返回 true。
 */
fun Player.transferMoney(target: UUID, amount: Int, remark: String? = null): Boolean {
    return MoneyService.transfer(uniqueId, target, amount, remark)
}

/**
 * 获取玩家 DD Coin 流水记录。
 *
 * @param limit 返回数量上限，必须大于 0，超过 64 时按 64 处理。
 * @return 按时间倒序排列的流水记录；数据库读取失败时返回空列表。
 */
fun Player.getMoneyRecords(limit: Int = MoneyService.MAX_MONEY_RECORDS): List<MoneyRecord> {
    return MoneyService.getRecords(this, limit)
}

/**
 * 清空玩家 DD Coin 流水记录，不会修改 Vault 余额。
 *
 * @return 是否成功清空；数据库失败时返回 false。
 */
fun Player.clearMoneyRecords(): Boolean {
    return MoneyService.clearRecords(this)
}
