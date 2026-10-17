# 验证记录

验证日期：2026-10-17。实机：Redmi Note 12T Pro，Android 15（API 35）。全部功能验证使用普通应用权限，未使用 Root、Xposed、无障碍或录屏。

## 已通过

| 项目 | 结果 |
| --- | --- |
| Debug / Release 编译 | 均成功；未提供私钥，Release 为未签名 APK |
| Debug / Release Lint | 无错误；存在目标 SDK、旧版兼容资源和电池优化相关提示 |
| 主机核心测试 | 18 项通过，覆盖任意目录、外置卷路径、图片扩展名、筛选规则和提交顺序 |
| 第三方应用写入非标准目录 | 独立测试应用保存至 `Pictures/UnusualApp`，自动复制 480×640 图片 |
| 延迟保存 | 测试应用先置 `IS_PENDING=1`，2.5 秒后完成，复制最终 480×640 图片 |
| 系统截图 | 通过系统截图按键生成，复制 1080×2460 图片 |
| 授权文件夹 | 用系统目录选择器授权测试目录；含 `.nomedia`、未进入相册的 512×512 图片变更后被扫描复制 |
| 私有图片分享 | 独立测试应用通过临时 URI 授权分享内部图片，成功复制 480×640 图片 |
| 跨应用剪贴板 | 不同 UID 的测试应用读取剪贴板 URI 并成功解码，验证实际图片及 URI 授权 |
| 缩略图过滤 | 64×224 图片被最小边长规则忽略 |
| 升级安装 | 覆盖安装 Debug 包后，设置和目录授权保留，重新打开应用后恢复前台监听 |
| 暂停 | 暂停后前台服务停止；期间保存的测试图片没有复制记录 |
| 固定高度历史列表 | 实机在列表内上滑，记录变化、卡片及页面位置不变；列表高度固定 248dp |

实机日志、数据库和个人图片不提交到仓库。`probe` 是可复现部分验证的独立开发工具，不会进入主 APK。

## 复现关键步骤

```sh
./gradlew :app:assembleDebug :probe:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r probe/build/outputs/apk/debug/probe-debug.apk
```

先打开主应用，允许所有照片与通知并开启监听，再生成测试图：

```sh
adb shell am start -S -W -n com.zfj.imageclipboard.probe/.ProbeActivity \
  --es case emit --es path Pictures/UnusualApp/ --es name capture-test.png --el delay 2500
```

等待保存与复制完成后，验证另一应用读取：

```sh
adb shell am start -S -W -n com.zfj.imageclipboard.probe/.ProbeActivity --es case read
adb logcat -d -s ImageClipboardQA:I
```

注意每次测试操作之间要等异步回调完成。`-S` 确保新的测试 Intent 被执行；不要在延迟写入完成前停止测试应用。分享测试使用 `--es case share`。

## 尚未全面验证

- Android 10–14、其他厂商系统、三指／长截图的全部组合。
- 实体 SD 卡、多用户／工作资料、云文档提供器、超大图库。
- 所有 HEIF/AVIF 机型编解码组合，以及所有目标聊天软件的图片粘贴界面。
- 重启自启动、厂商清理后台、整夜待机／Doze 下的持续性与功耗。

上述场景依赖平台和提供器行为，不能从单台手机的通过结果推断为全部兼容。厂商后台限制可能造成延迟或服务停止；文件夹轮询依赖提供器报告时间／大小变化。剪贴板 URI 已验证可读取，但目标应用是否显示图片粘贴入口由其自身决定。
