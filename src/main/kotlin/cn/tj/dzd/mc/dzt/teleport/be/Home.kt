package cn.tj.dzd.mc.dzt.teleport.be

import cn.tj.dzd.mc.dzt.mapping.tables.dtp.addDTPHome
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.deleteDTPHome
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.getDTPHomeList
import org.bukkit.entity.Player
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import org.geysermc.floodgate.api.player.FloodgatePlayer
import taboolib.expansion.submitChain

/**
 * 打开传送点管理
 * @param pl 玩家
 */
fun openHomeBEMenu(pl: Player, fpl: FloodgatePlayer) {
    submitChain {
        val homeList = async {
            pl.getDTPHomeList()
        }
        val fm = SimpleForm.builder()
        fm.title("§l§6传送点")
        fm.button("添加传送点", FormImage.Type.PATH, "textures/ui/Add-Ons_Side-Nav_Icon_24x24.png")
        for (home in homeList) {
            fm.button(home.name)
        }
        fm.validResultHandler{
            when (it.clickedButtonId()) {
                0 -> {
                    fpl.sendForm(CustomForm.builder()
                        .title("§l§6添加传送点")
                        .input("新传送点名称", "请输入新传送点名称")
                        .validResultHandler { response ->
                            val homeName = response.asInput(0) ?:""

                            try {
                                submitChain {
                                    async {
                                        pl.addDTPHome(homeName, pl.location)
                                    }
                                    sync {
                                        fpl.sendForm(ModalForm.builder()
                                            .title("添加成功")
                                            .content("已添加传送点[$homeName]！")
                                            .button1("返回")
                                            .button2("关闭")
                                            .validResultHandler { response ->
                                                if (response.clickedButtonId() == 0) {
                                                    openHomeBEMenu(pl, fpl)
                                                }
                                            }
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                fpl.sendForm(ModalForm.builder()
                                    .title("添加失败")
                                    .content("§c${e.message}")
                                    .button1("返回")
                                    .button2("关闭")
                                    .validResultHandler { response ->
                                        if (response.clickedButtonId() == 0) {
                                            openHomeBEMenu(pl, fpl)
                                        }
                                    }
                                )
                            }
                        })
                }
                else -> {
                    val home = homeList[it.clickedButtonId() - 1]
                    fpl.sendForm(ModalForm.builder()
                        .title("§l§6" + home.name)
                        .content("您要对传送点[${home.name}]进行什么操作？")
                        .button1("前往")
                        .button2("§c删除")
                        .validResultHandler { response ->
                            when (response.clickedButtonId()) {
                                0 -> {
                                    submitChain {
                                        sync {
                                            pl.teleport(home.location)
                                            pl.sendMessage("§a已传送至传送点[${home.name}]！")
                                        }
                                    }
                                }
                                1 -> {
                                    fpl.sendForm(ModalForm.builder()
                                        .title("警告")
                                        .content("您真的要删除传送点[${home.name}]吗？\n他会消失很久，很久的！")
                                        .button1("§c确认")
                                        .button2("返回")
                                        .validResultHandler { response ->
                                            when (response.clickedButtonId()) {
                                                0 -> {
                                                    submitChain {
                                                        async {
                                                            pl.deleteDTPHome(home.name)
                                                        }
                                                        sync {
                                                            pl.sendMessage("§a已删除传送点[${home.name}]！")
                                                            openHomeBEMenu(pl, fpl)
                                                        }
                                                    }
                                                }
                                                1 -> {
                                                    openHomeBEMenu(pl, fpl)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
        fpl.sendForm(fm)
    }
}