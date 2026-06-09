# happyu

![happyu preview](src/main/resources/img/a7e3bab7-08c0-47ab-bfbd-2a78d3baf33b.png)
![happyu preview](src/main/resources/img/e638acac-0a5e-4f05-af77-1b1aa6707af8.png)

happyu 是一个给 IntelliJ IDEA 用的低调摸鱼浏览器插件。

它会在 IDEA 编辑器 tab 中打开网页，而不是跳到系统浏览器。插件默认屏蔽图片、视频、favicon 等媒体资源，并把网页尽量处理成文字优先的阅读模式。页面颜色会跟随 IDEA 当前主题，适合在休息间隙低调浏览搜索结果、热搜、榜单、文档和其他文字内容。

简单说：想看点网页，但又不想离开 IDEA，也不想让页面花花绿绿太显眼，就用它。

## 功能

- `Tools -> Open happyu` 打开浏览器 tab。
- 默认打开的首页为 `https://www.baidu.com/`。
- 可自由输入网址。
- 顶部提供地址栏、返回、前进、刷新和图片开关。
- 默认屏蔽图片、favicon、视频等媒体资源，并注入 CSS 隐藏图片、背景图、视频、canvas 等元素。
- 快捷键 `Ctrl+Alt+I` 切换图片屏蔽状态。macOS 上如果和系统快捷键冲突，可在 IDEA 的 Keymap 里调整 `Toggle happyu Images`。
- 网页背景色、文字色、链接色会按 IDEA 当前明暗主题重新注入。

## 兼容版本

- IntelliJ IDEA 2024.1 到 2026.1 系列。
- 对应 IntelliJ Platform build：`241` 到 `261.*`。

## 运行

```bash
./gradlew runIde
```

如果本机已经安装 IntelliJ IDEA，也可以使用本地 IDE SDK：

```bash
gradle runIde -PideaLocalPath="/Applications/IntelliJ IDEA.app/Contents"
```

## 打包

```bash
./gradlew buildPlugin
```

生成的插件包在 `build/distributions/`。

## 开源协议

本项目基于 [MIT License](LICENSE) 开源。
