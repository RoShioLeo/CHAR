# License scope / 许可范围

本仓库包含不同许可的部分，不能将整个项目视为只需署名的 MIT 项目。

## 原创新增代码：MIT

Copyright (c) 2026 RoShioLeo。完整条款见 LICENSE-MIT。

MIT 适用于独立编写的 Halo 集成代码与管理界面（halo-plugin 内的 Java、Vue、TypeScript、测试和构建配置），以及新增前台界面 index.html、styles.css、app.js、appearance.js、打包脚本和新增测试程序。另有第三方声明的文件除外。

保留版权和许可声明即可按 MIT 条款使用这些原创部分。MIT 不替代上游材料或依赖自己的许可。

## CHAR 上游及其修改：CC BY-SA 4.0

原项目：https://github.com/yukiyuqichen/CHAR

原许可保留在 LICENSE，适用于继承的根目录 CHAR 转换器 charConverter.js、char_converter、字库 JSON、test/fork-overrides.json 和上游图片等材料及其修改。贡献者与修改说明见 NOTICE.md。

上游 npm/char_converter/package.json 另标 MIT，原样保留该子包声明；该子包引用根目录字库，未据此将根目录字库重新声明为 MIT。

CC BY-SA 4.0 包含署名、标明修改和相同方式共享要求；并非仅需署名。条款：https://creativecommons.org/licenses/by-sa/4.0/

## 第三方和 Halo 运行环境

Gradle Wrapper 保留其 Apache-2.0 声明。Halo 本体、Halo API 和宿主提供的 @halo-dev/components、@halo-dev/ui-shared 等保留各自的 GPL-3.0 许可；Vue、Spring、Jackson 等保留各自许可。详见 THIRD_PARTY_NOTICES.md。

采用 MIT 的原创部分不能免除 GPL 组合分发或其他第三方许可产生的义务。本项目不声称与 Halo 组合后的整体只需遵守 MIT。
