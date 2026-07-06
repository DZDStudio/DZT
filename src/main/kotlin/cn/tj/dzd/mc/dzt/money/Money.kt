package cn.tj.dzd.mc.dzt.money

import cn.tj.dzd.mc.dzt.data.DatabaseGuard
import cn.tj.dzd.mc.dzt.data.table.MoneyRecord
import cn.tj.dzd.mc.dzt.data.table.MoneyRecordType
import cn.tj.dzd.mc.dzt.data.table.moneyMapper
import cn.tj.dzd.mc.dzt.data.table.moneyRecordMapper
import org.bukkit.entity.Player
import taboolib.expansion.DataMapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import cn.tj.dzd.mc.dzt.data.table.Money as MoneyTable

object MoneyService {
    const val MAX_MONEY_RECORDS = 64
    private const val MAX_RELATED_LENGTH = 64
    private const val MAX_REMARK_LENGTH = 256

    private val playerLocks = ConcurrentHashMap<UUID, Any>()

    /**
     * 获取玩家当前 DD Coin 余额。
     *
     * @param player 玩家对象。
     * @return 玩家余额；数据库读取失败时返回 0。
     */
    fun getBalance(player: Player): Int {
        return getBalance(player.uniqueId)
    }

    /**
     * 获取玩家当前 DD Coin 余额。
     *
     * @param uuid 玩家 UUID。
     * @return 玩家余额；数据库读取失败时返回 0。
     */
    fun getBalance(uuid: UUID): Int {
        return DatabaseGuard.execute("获取 DD Coin 余额", 0) {
            moneyMapper.findMoney(uuid)?.ddCoin ?: 0
        }
    }

    /**
     * 增加玩家 DD Coin。
     *
     * 该操作会在同一事务内更新余额、写入入账流水并裁剪旧流水。
     *
     * @param player 玩家对象。
     * @param amount 增加金额，必须大于 0。
     * @param related 关联对象标识，可存玩家 UUID、server、system 等文本。
     * @param remark 备注。
     * @return 是否成功；金额会导致余额溢出或数据库失败时返回 false。
     */
    fun increase(player: Player, amount: Int, related: String? = null, remark: String? = null): Boolean {
        return increase(player.uniqueId, amount, related, remark)
    }

    /**
     * 增加玩家 DD Coin。
     *
     * 该操作会在同一事务内更新余额、写入入账流水并裁剪旧流水。
     *
     * @param uuid 玩家 UUID。
     * @param amount 增加金额，必须大于 0。
     * @param related 关联对象标识，可存玩家 UUID、server、system 等文本。
     * @param remark 备注。
     * @return 是否成功；金额会导致余额溢出或数据库失败时返回 false。
     */
    fun increase(uuid: UUID, amount: Int, related: String? = null, remark: String? = null): Boolean {
        requirePositiveAmount(amount)
        val normalizedRelated = normalizeRelated(related)
        val normalizedRemark = normalizeRemark(remark)

        return withPlayerLocks(uuid) {
            runMoneyTransaction("增加 DD Coin", false) {
                val current = findMoney(uuid)
                val currentBalance = current?.ddCoin ?: 0
                if (currentBalance > Int.MAX_VALUE - amount) {
                    return@runMoneyTransaction false
                }

                saveBalance(uuid, current, currentBalance + amount)
                appendRecord(uuid, MoneyRecordType.IN, amount, normalizedRelated, normalizedRemark)
                trimRecords(uuid)
                true
            }
        }
    }

    /**
     * 减少玩家 DD Coin。
     *
     * 该操作会在同一事务内更新余额、写入出账流水并裁剪旧流水。
     *
     * @param player 玩家对象。
     * @param amount 减少金额，必须大于 0。
     * @param related 关联对象标识，可存玩家 UUID、server、system 等文本。
     * @param remark 备注。
     * @return 是否成功；余额不足或数据库失败时返回 false。
     */
    fun decrease(player: Player, amount: Int, related: String? = null, remark: String? = null): Boolean {
        return decrease(player.uniqueId, amount, related, remark)
    }

    /**
     * 减少玩家 DD Coin。
     *
     * 该操作会在同一事务内更新余额、写入出账流水并裁剪旧流水。
     *
     * @param uuid 玩家 UUID。
     * @param amount 减少金额，必须大于 0。
     * @param related 关联对象标识，可存玩家 UUID、server、system 等文本。
     * @param remark 备注。
     * @return 是否成功；余额不足或数据库失败时返回 false。
     */
    fun decrease(uuid: UUID, amount: Int, related: String? = null, remark: String? = null): Boolean {
        requirePositiveAmount(amount)
        val normalizedRelated = normalizeRelated(related)
        val normalizedRemark = normalizeRemark(remark)

        return withPlayerLocks(uuid) {
            runMoneyTransaction("减少 DD Coin", false) {
                val current = findMoney(uuid)
                val currentBalance = current?.ddCoin ?: 0
                if (currentBalance < amount) {
                    return@runMoneyTransaction false
                }

                saveBalance(uuid, current, currentBalance - amount)
                appendRecord(uuid, MoneyRecordType.OUT, amount, normalizedRelated, normalizedRemark)
                trimRecords(uuid)
                true
            }
        }
    }

    /**
     * 转账 DD Coin。
     *
     * 该操作会在同一事务内扣除转出方余额、增加接收方余额、写入双方流水并裁剪旧流水。
     *
     * @param from 转出玩家。
     * @param to 接收玩家。
     * @param amount 转账金额，必须大于 0。
     * @param remark 备注。
     * @return 是否成功；余额不足、接收方余额溢出或数据库失败时返回 false。
     */
    fun transfer(from: Player, to: Player, amount: Int, remark: String? = null): Boolean {
        return transfer(from.uniqueId, to.uniqueId, amount, remark)
    }

    /**
     * 转账 DD Coin。
     *
     * 该操作会在同一事务内扣除转出方余额、增加接收方余额、写入双方流水并裁剪旧流水。
     *
     * @param from 转出玩家 UUID。
     * @param to 接收玩家 UUID。
     * @param amount 转账金额，必须大于 0。
     * @param remark 备注。
     * @return 是否成功；余额不足、接收方余额溢出或数据库失败时返回 false。
     */
    fun transfer(from: UUID, to: UUID, amount: Int, remark: String? = null): Boolean {
        require(from != to) { "转账双方不能是同一个玩家" }
        requirePositiveAmount(amount)
        val normalizedRemark = normalizeRemark(remark)

        return withPlayerLocks(from, to) {
            runMoneyTransaction("转账 DD Coin", false) {
                val fromMoney = findMoney(from)
                val toMoney = findMoney(to)
                val fromBalance = fromMoney?.ddCoin ?: 0
                val toBalance = toMoney?.ddCoin ?: 0

                if (fromBalance < amount || toBalance > Int.MAX_VALUE - amount) {
                    return@runMoneyTransaction false
                }

                saveBalance(from, fromMoney, fromBalance - amount)
                saveBalance(to, toMoney, toBalance + amount)
                appendRecord(from, MoneyRecordType.OUT, amount, to.toString(), normalizedRemark)
                appendRecord(to, MoneyRecordType.IN, amount, from.toString(), normalizedRemark)
                trimRecords(from)
                trimRecords(to)
                true
            }
        }
    }

    /**
     * 获取玩家 DD Coin 流水记录。
     *
     * 返回结果按时间倒序排列，最多返回 [MAX_MONEY_RECORDS] 条。
     *
     * @param player 玩家对象。
     * @param limit 返回数量上限，必须大于 0，超过 [MAX_MONEY_RECORDS] 时按 [MAX_MONEY_RECORDS] 处理。
     * @return 玩家流水记录；数据库读取失败时返回空列表。
     */
    fun getRecords(player: Player, limit: Int = MAX_MONEY_RECORDS): List<MoneyRecord> {
        return getRecords(player.uniqueId, limit)
    }

    /**
     * 获取玩家 DD Coin 流水记录。
     *
     * 返回结果按时间倒序排列，最多返回 [MAX_MONEY_RECORDS] 条。
     *
     * @param uuid 玩家 UUID。
     * @param limit 返回数量上限，必须大于 0，超过 [MAX_MONEY_RECORDS] 时按 [MAX_MONEY_RECORDS] 处理。
     * @return 玩家流水记录；数据库读取失败时返回空列表。
     */
    fun getRecords(uuid: UUID, limit: Int = MAX_MONEY_RECORDS): List<MoneyRecord> {
        require(limit > 0) { "流水记录获取数量必须大于 0" }
        val actualLimit = limit.coerceAtMost(MAX_MONEY_RECORDS)

        return withPlayerLocks(uuid) {
            runMoneyTransaction("获取 DD Coin 流水", emptyList()) {
                val records = findSortedRecords(uuid)
                trimRecords(uuid, records)
                records.take(actualLimit)
            }
        }
    }

    /**
     * 清空玩家 DD Coin 流水记录。
     *
     * @param player 玩家对象。
     * @return 是否成功清空；数据库失败时返回 false。
     */
    fun clearRecords(player: Player): Boolean {
        return clearRecords(player.uniqueId)
    }

    /**
     * 清空玩家 DD Coin 流水记录。
     *
     * @param uuid 玩家 UUID。
     * @return 是否成功清空；数据库失败时返回 false。
     */
    fun clearRecords(uuid: UUID): Boolean {
        return withPlayerLocks(uuid) {
            runMoneyTransaction("清空 DD Coin 流水", false) {
                moneyRecordMapper.deleteWhere {
                    "uuid" eq uuid.toString()
                }
                true
            }
        }
    }

    private fun <T> runMoneyTransaction(action: String, fallback: T, block: DataMapper<MoneyTable>.() -> T): T {
        return DatabaseGuard.execute(action, fallback) {
            moneyRecordMapper.tableName
            moneyMapper.transaction {
                this.block()
            }.getOrThrow()
        }
    }

    private fun DataMapper<MoneyTable>.findMoney(uuid: UUID): MoneyTable? {
        return findById(uuid)
    }

    private fun DataMapper<MoneyTable>.saveBalance(uuid: UUID, current: MoneyTable?, balance: Int) {
        val money = MoneyTable(uuid, balance)
        if (current == null) {
            insert(money)
        } else {
            update(money)
        }
    }

    private fun appendRecord(
        uuid: UUID,
        type: MoneyRecordType,
        amount: Int,
        related: String?,
        remark: String?,
    ) {
        moneyRecordMapper.insert(
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

    private fun nextRecordTime(uuid: UUID): Long {
        val latestTime = findSortedRecords(uuid).maxOfOrNull { it.time }
        val now = System.currentTimeMillis()
        return if (latestTime != null && now <= latestTime) latestTime + 1 else now
    }

    private fun findSortedRecords(uuid: UUID): List<MoneyRecord> {
        return moneyRecordMapper.findAll {
            "uuid" eq uuid.toString()
        }.sortedByDescending { it.time }
    }

    private fun trimRecords(uuid: UUID, sortedRecords: List<MoneyRecord> = findSortedRecords(uuid)) {
        sortedRecords
            .drop(MAX_MONEY_RECORDS)
            .forEach { record ->
                moneyRecordMapper.deleteWhere {
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
 * 获取玩家当前 DD Coin 余额。
 *
 * @return 玩家余额；数据库读取失败时返回 0。
 */
fun Player.getMoneyBalance(): Int {
    return MoneyService.getBalance(this)
}

/**
 * 增加玩家 DD Coin。
 *
 * @param amount 增加金额，必须大于 0。
 * @param related 关联对象标识，可存玩家 UUID、server、system 等文本。
 * @param remark 备注。
 * @return 是否成功；金额会导致余额溢出或数据库失败时返回 false。
 */
fun Player.increaseMoney(amount: Int, related: String? = null, remark: String? = null): Boolean {
    return MoneyService.increase(this, amount, related, remark)
}

/**
 * 减少玩家 DD Coin。
 *
 * @param amount 减少金额，必须大于 0。
 * @param related 关联对象标识，可存玩家 UUID、server、system 等文本。
 * @param remark 备注。
 * @return 是否成功；余额不足或数据库失败时返回 false。
 */
fun Player.decreaseMoney(amount: Int, related: String? = null, remark: String? = null): Boolean {
    return MoneyService.decrease(this, amount, related, remark)
}

/**
 * 向指定玩家转账 DD Coin。
 *
 * @param target 接收玩家。
 * @param amount 转账金额，必须大于 0。
 * @param remark 备注。
 * @return 是否成功；余额不足、接收方余额溢出或数据库失败时返回 false。
 */
fun Player.transferMoney(target: Player, amount: Int, remark: String? = null): Boolean {
    return MoneyService.transfer(this, target, amount, remark)
}

/**
 * 向指定玩家转账 DD Coin。
 *
 * @param target 接收玩家 UUID。
 * @param amount 转账金额，必须大于 0。
 * @param remark 备注。
 * @return 是否成功；余额不足、接收方余额溢出或数据库失败时返回 false。
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
 * 清空玩家 DD Coin 流水记录。
 *
 * @return 是否成功清空；数据库失败时返回 false。
 */
fun Player.clearMoneyRecords(): Boolean {
    return MoneyService.clearRecords(this)
}
