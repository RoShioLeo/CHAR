# CHAR 自维护版

正式网页：https://www.lemonslice.cloud/tools/char/

Halo 插件标识：`lemonslice-char`，模块位于 `halo-plugin/`，初始版本 1.0.0。插件直接提供前台路由，后台「工具 → 字库管理」提供检索、新增、编辑、停用、恢复、导出和修改记录。管理 API 额外校验当前用户实际具有 `super-role`。

## 2026-11-26 同步

合入上游 `yukiyuqichen/CHAR` 的 `64b27076638255e8293827e04f81c070855edfad`。共同基线为 `4da29b1b512295f591e8acd3b17e58400edce53d`，同步前本分支为 `0d97a375bd2783d96e3c80b1882c5992df0502ce`。

已确认的冲突处理：两份 JSON 字库各保留 27 个自定义修正，包括删除「壹、拾、柒」映射；Pages 继续使用本分支的 checkout@v5、upload-pages-artifact@v4。其余上游修改按字形逐条合入。字库条目数为 v2s 6564、v2t 6766。

界面使用中文，选项为「默认转换」「多字候选」。脚本、字库和图标均从网页所在服务器加载。顶部与页脚按私厨页面的形式，明暗模式与博客共享 `lemonslice-color-mode` 偏好。

## 后续同步

1. 获取上游更新并在工作分支合并。字库冲突按 JSON 键逐项比较共同基线、本分支和上游，保留 `test/fork-overrides.json` 中记录的自定义修正；如上游与这些修正冲突，先由维护者确认。
2. 运行 `python -m unittest discover -s test -p 'test_*.py'` 和 `node --experimental-strip-types --test test/*.test.cjs`。测试覆盖所有自定义修正、两种转换方式、兼容汉字、补充平面汉字和 Unicode 码输入。
3. 在 `halo-plugin` 内运行 `./gradlew build` 构建插件。运行 `./gradlew clean build` 生成正式安装包，安装包包含前台资源和基础字库。
4. 在 Halo 安装或升级插件，验证匿名读取、超级管理员管理、普通用户拒绝访问，以及转换、文件下载、复制、移动端和明暗模式。

`checks.yml` 在推送及 PR 时运行回归检查和插件构建，手动执行时可填写 `releaseVersion` 发布安装包；`static.yml` 仅保留手动 GitHub Pages 备用发布，不再随推送自动发布。`scripts/package_site.py` 为独立静态预览打包，只包含前台必要文件。

## 字库保存及权限

发布包内的 JSON 是基础快照；`test/fork-overrides.json` 记录已确认的修正，并在首次使用时作为初始修正。后台修改独立保存到 Halo 的 `lemonslice-char-v2s`、`lemonslice-char-v2t` ConfigMap 中，包含候选、停用状态、备注、操作人和修改记录；新版本不会重写这些对象。保存使用资源版本校验，版本变化时返回冲突，要求刷新确认。

前台 JSON 路由只输出基础快照与修正合并后的映射，不输出管理员、备注或修改记录。匿名角色仅获得 `/tools/char` 及其子路径的 GET 权限，所有管理接口均在 Console API 下，并强制检查 `super-role`。升级前可在后台导出字库和修正记录备份。

前台的外观按钮与主站、私厨共用三态轮换：浅色 → 深色 → 跟随系统；图标分别为太阳、月亮和日月组合。右上角「字库管理」由服务端检查超级管理员角色后显示。编辑候选和新增原字支持字符、`U+4E00`、`0x4E00` 或 `4E00` 形式的十六进制码，并显示字形及 Unicode 编码。

独立编写的 Halo 集成和新增前台界面采用 MIT；CHAR 字库、原转换器及其修改保留 CC BY-SA 4.0。安装包包含两份许可、许可范围与第三方声明。Halo 运行环境和宿主 UI 依赖保留自身许可，不将整个组合宣称为只需署名；详见 `LICENSES.md`。
