package cn.tj.dzd.mc.dzt

import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info

object DZT : Plugin() {
    override fun onEnable() {
        info("DZD 基础插件已启用。")
//
//        val mail = Mail(1, "测试邮件")
//            .addContent(MailContentType.TEXT, "测试    文本\nawa")
//            .addContent(MailContentType.SUCCESS, "测试成功")
//            .addContent(MailContentType.WARNING, "测试警告")
//            .addContent(MailContentType.ERROR, "测试错误")
//
//        info(mail.toHtml())
//        info(mail.toContentJsonListStr())
//        sendMail(mail)
    }
    
    override fun onDisable() {
        info("DZD 基础插件正在关闭。")
    }
}