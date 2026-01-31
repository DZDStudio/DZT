package cn.tj.dzd.mc.dzt.teleport.be

import cn.tj.dzd.mc.dzt.Floodgate.getFloodgatePlayer
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.deleteDTPBack
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.getDTPBackList
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.expansion.submitChain

/**
 * 打开返回死亡点菜单
 * @param pl 玩家
 */
fun openBackBEMenu(pl: Player) {
    submitChain {
        val fpl = pl.getFloodgatePlayer() ?: throw Exception("获取Floodgate玩家失败！")
        val backList = async {
            pl.getDTPBackList()
        }

        sync {
            val fm = SimpleForm.builder()
            fm.title("死亡点")
            fm.content("请选择要操作的死亡点")
            for (back in backList) {
                fm.button("${back.time}\n${back.location.world?.name}:${back.location.x.toInt()} ${back.location.y.toInt()} ${back.location.z.toInt()}", FormImage.Type.PATH, "textures/ui/enable_editor.png")
            }
            fm.validResultHandler {
                val back = backList[it.clickedButtonId()]

                fpl.sendForm(ModalForm.builder()
                    .title("死亡点${back.time}")
                    .content("您要对死亡点${back.time}进行什么操作？\n§7世界: §e${back.location.world?.name}\n§7坐标: §e${back.location.x.toInt()} ${back.location.y.toInt()} ${back.location.z.toInt()}")
                    .button1("前往")
                    .button2("§c删除")
                    .validResultHandler { response ->
                        when (response.clickedButtonId()) {
                            0 -> {
                                pl.teleport(back.location)
                                pl.sendMessage("§a已传送到死亡点[${back.time}]！")
                            }
                            1 -> {
                                fpl.sendForm(ModalForm.builder()
                                    .title("警告")
                                    .content("您真的要删除死亡点[${back.time}]吗？")
                                    .button1("§c确认")
                                    .button2("返回")
                                    .validResultHandler { response ->
                                        when (response.clickedButtonId()) {
                                            0 -> {
                                                submitChain {
                                                    async {
                                                        pl.deleteDTPBack(back.time)
                                                    }
                                                    sync {
                                                        pl.sendMessage("§a已删除死亡点[${back.time}]！")
                                                        openBackBEMenu(pl)
                                                    }
                                                }
                                            }
                                            1 -> {
                                                openBackBEMenu(pl)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            }
            fpl.sendForm(fm)
        }
    }
}