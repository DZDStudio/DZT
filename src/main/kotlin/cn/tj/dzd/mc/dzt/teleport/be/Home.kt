package cn.tj.dzd.mc.dzt.teleport.be

import cn.tj.dzd.mc.dzt.mapping.DZDPlayer
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.addTeleportHome
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.deleteTeleportHome
import cn.tj.dzd.mc.dzt.mapping.tables.dtp.getTeleportHomeList
import cn.tj.dzd.mc.dzt.teleport.openTeleportBEMenu
import org.geysermc.cumulus.form.CustomForm
import org.geysermc.cumulus.form.ModalForm
import org.geysermc.cumulus.form.SimpleForm
import org.geysermc.cumulus.util.FormImage
import taboolib.expansion.submitChain

/**
 * 打开传送点管理
 */
fun openHomeBEMenu(dp: DZDPlayer) {
    submitChain {
        val homeList = async {
            dp.getTeleportHomeList()
        }
        val fm = SimpleForm.builder()
        fm.title("§l§6传送点")
        fm.button("返回上一页", FormImage.Type.PATH, "textures/ui/box_ride.png")
        fm.button("添加传送点", FormImage.Type.PATH, "textures/ui/Add-Ons_Side-Nav_Icon_24x24.png")
        for (home in homeList) {
            fm.button(home.name)
        }
        fm.validResultHandler{
            when (it.clickedButtonId()) {
                0 -> { openTeleportBEMenu(dp) }
                1 -> {
                    dp.sendForm(CustomForm.builder()
                        .title("§l§6添加传送点")
                        .input("新传送点名称", "请输入新传送点名称")
                        .validResultHandler { response ->
                            val homeName = response.asInput(0) ?:""

                            try {
                                submitChain {
                                    async {
                                        dp.addTeleportHome(homeName, dp.location)
                                    }
                                    sync {
                                        dp.sendForm(ModalForm.builder()
                                            .title("添加成功")
                                            .content("已添加传送点[$homeName]！")
                                            .button1("返回")
                                            .button2("关闭")
                                            .validResultHandler { response ->
                                                if (response.clickedButtonId() == 0) {
                                                    openHomeBEMenu(dp)
                                                }
                                            }
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                dp.sendForm(ModalForm.builder()
                                    .title("添加失败")
                                    .content("§c${e.message}")
                                    .button1("返回")
                                    .button2("关闭")
                                    .validResultHandler { response ->
                                        if (response.clickedButtonId() == 0) {
                                            openHomeBEMenu(dp)
                                        }
                                    }
                                )
                            }
                        }
                        .closedResultHandler { _ ->
                            openHomeBEMenu(dp)
                        }
                    )
                }
                else -> {
                    val home = homeList[it.clickedButtonId() - 2]
                    dp.sendForm(ModalForm.builder()
                        .title("§l§6" + home.name)
                        .content("您要对传送点[${home.name}]进行什么操作？")
                        .button1("前往")
                        .button2("§c删除")
                        .validResultHandler { response ->
                            when (response.clickedButtonId()) {
                                0 -> {
                                    submitChain {
                                        sync {
                                            dp.teleport(home.location)
                                            dp.sendSuccess("已传送至传送点[${home.name}]！")
                                        }
                                    }
                                }
                                1 -> {
                                    dp.sendForm(ModalForm.builder()
                                        .title("警告")
                                        .content("您真的要删除传送点[${home.name}]吗？\n他会消失很久，很久的！")
                                        .button1("§c确认")
                                        .button2("返回")
                                        .validResultHandler { response ->
                                            when (response.clickedButtonId()) {
                                                0 -> {
                                                    submitChain {
                                                        async {
                                                            dp.deleteTeleportHome(home.name)
                                                        }
                                                        sync {
                                                            dp.sendSuccess("已删除传送点[${home.name}]！")
                                                            openHomeBEMenu(dp)
                                                        }
                                                    }
                                                }
                                                1 -> {
                                                    openHomeBEMenu(dp)
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
        dp.sendForm(fm)
    }
}