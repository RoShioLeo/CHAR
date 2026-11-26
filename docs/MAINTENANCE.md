# CHAR 自维护版

正式网页：https://www.lemonslice.cloud/tools/char/

## 2026-11-26 同步

合入上游 `yukiyuqichen/CHAR` 的 `64b27076638255e8293827e04f81c070855edfad`。共同基线为 `4da29b1b512295f591e8acd3b17e58400edce53d`，同步前本分支为 `0d97a375bd2783d96e3c80b1882c5992df0502ce`。

已确认的冲突处理：两份 JSON 字库各保留 27 个自定义修正，包括删除「壹、拾、柒」映射；Pages 继续使用本分支的 checkout@v5、upload-pages-artifact@v4。其余上游修改按字形逐条合入。字库条目数为 v2s 6564、v2t 6766。

界面使用中文，选项为「默认转换」「多字候选」。脚本、字库和图标均从网页所在服务器加载。顶部与页脚按私厨页面的形式，明暗模式与博客共享 `lemonslice-color-mode` 偏好。

## 后续同步

1. 获取上游更新并在工作分支合并。字库冲突按 JSON 键逐项比较共同基线、本分支和上游，保留 `test/fork-overrides.json` 中记录的自定义修正；如上游与这些修正冲突，先由维护者确认。
2. 运行 `python -m unittest discover -s test -p 'test_*.py'` 和 `node --test test/browser.test.cjs`。测试覆盖所有自定义修正、两种转换方式、兼容汉字和补充平面汉字。
3. 运行 `python scripts/package_site.py` 生成 `_site`。仅将该目录部署到 `/var/www/lemonslice-tools/char/releases/<版本>/`，通过 `current` 符号链接切换版本。
4. 验证页面、两份字库、文本转换、文件下载、复制、移动端和明暗模式。

`checks.yml` 在推送及 PR 时运行回归检查；`static.yml` 仅保留手动 GitHub Pages 备用发布，不再随推送自动发布。服务器发布使用已验证的静态包，服务器密钥保存在站外。

服务器通过 Nginx 的 `/tools/char/` 路由提供静态文件。路由片段 `/etc/nginx/snippets/lemonslice-tools.conf` 同时用于博客两个域名；更新先执行 `nginx -t` 再 reload，上一发布目录保留以便回退。
