# AGENTS.md
## 项目概述
DZT 是 DZD JE-BE 互通服的核心插件，服务器所有功能都由此插件提供。

## 技术栈
TabooLib - https://taboolib.maplex.top/docs/intro
Folia 26.1.2 - https://jd.papermc.io/folia/26.1.2/
Geyser - https://github.com/GeyserMC/Geyser
Floodgate - https://github.com/GeyserMC/Floodgate
ServiceIO - https://github.com/TheNextLvl-net/service-io

## 开发注意事项
- 所有接口都需附带调用注释，便于他人使用
- 对于常用代码，应封装为接口，便于复用
- 编写 UI 时需单独为 Java 玩家制作箱子 UI，基岩玩家制作表单 UI
- 获取 Java 玩家的文本，选项，滑块等输入时，优先使用 Paper Dialogs API - https://docs.papermc.io/paper/dev/dialogs/
- 所有表单 UI 按钮必须附带图标
- 在与游戏交互时仔细检查 Folia 兼容
- 调用接口时先确认TabooLib是否提供的更简易的替代

## 关于数据存储
数据存储位于 data 目录。分别对接 MySQL、 Redis。
严禁在代码中修改数据库结构，数据库操作统一使用 https://taboolib.maplex.top/docs/expanding-technology/persistent-container/ 
需要更改数据库结构时请告知人类，由人工手动更新数据库。