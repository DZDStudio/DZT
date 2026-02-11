package cn.tj.dzd.mc.dzt.teleport.be

import cn.tj.dzd.mc.dzt.mapping.DZDPlayer
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.deleteTeleportBack
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.getTeleportBackList
import cn.tj.dzd.mc.dzt.teleport.openTeleportBEMenu
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.expansion.submitChain

/**
 * 打开返回死亡点菜单
 * @param pl 玩家
 */
fun openBackBEMenu(dp: DZDPlayer) {
    submitChain {
        val backList = async {
            dp.getTeleportBackList()
        }

        sync {
            val fm = SimpleForm.builder()
            fm.title("§l§6死亡点")
            fm.content("请选择要操作的死亡点")
            for (back in backList) {
                fm.button("${back.time}\n${back.location.world?.name}:${back.location.x.toInt()} ${back.location.y.toInt()} ${back.location.z.toInt()}", FormImage.Type.PATH, "textures/ui/enable_editor.png")
            }
            fm.validResultHandler {
                val back = backList[it.clickedButtonId()]

                dp.sendForm(ModalForm.builder()
                    .title("§l§6死亡点 ${back.time}")
                    .content("您要对死亡点${back.time}进行什么操作？\n§7世界: §e${back.location.world?.name}\n§7坐标: §e${back.location.x.toInt()} ${back.location.y.toInt()} ${back.location.z.toInt()}")
                    .button1("前往")
                    .button2("§c删除")
                    .validResultHandler { response ->
                        when (response.clickedButtonId()) {
                            0 -> {
                                dp.teleport(back.location)
                                dp.sendSuccess("已传送到死亡点[${back.time}]！")
                            }
                            1 -> {
                                dp.sendForm(ModalForm.builder()
                                    .title("§l§6警告")
                                    .content("您真的要删除死亡点[${back.time}]吗？")
                                    .button1("§c确认")
                                    .button2("返回")
                                    .validResultHandler { response ->
                                        when (response.clickedButtonId()) {
                                            0 -> {
                                                submitChain {
                                                    async {
                                                        dp.deleteTeleportBack(back.time)
                                                    }
                                                    sync {
                                                        dp.sendSuccess("已删除死亡点[${back.time}]！")
                                                        openBackBEMenu(dp)
                                                    }
                                                }
                                            }
                                            1 -> openBackBEMenu(dp)
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            }
            fm.closedResultHandler { _ ->
                openTeleportBEMenu(dp)
            }
            dp.sendForm(fm)
        }
    }
}