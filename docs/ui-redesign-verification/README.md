# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`e995c33f2e97364b7064b21f598423dab0ce3c7b`
- GitHub Actions run：[38842316009](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38842316009)
- artifact ID：`12022880138`
- artifact digest：`sha256:e6d81c546f82170ec5e7198adc00eef5b133d9a4958e742d92523cf9c7f37d45`
- APK：`app-debug.apk`，SHA-256 `0d3ce8e6b4038f32aa2fd58f88c7b3770dc5c840a71ba92b49b25f3e81a818ce`，19744736 字节
- artifact 内未配对 smoke：`pass`（2026-10-16T02:55:51.039005+00:00）
- artifact 内配对 fixture：`pass`（2026-10-16T02:59:30.104858+00:00）
- 报告生成时间：`2026-10-16T02:59:42Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `988b2f981f78a5ebd2dfefa354b6b1f322a62ea7eb51020486bb15101ade2136` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `8a3683c10cf6a259588c6fae7c02f16b74f6953b41d3eddaac696ecec2fa1e86` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `6a306e387e08d4d635044df7ddd7495b4db1e008d1c6cc3259152d602d96d781` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `7974c7016384b237cbda899c4d17d76abb28f719ce996f61519ee7816d5fd64a` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `2321b648ddb383975c52bddfc84e09473d422c09161a5efeada68fc0d8158b42` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `922d414b6fe643416c03926ab66d64af0d67f533b59daa3590a0c28241c1924c` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `2d91e615dfd68dba0d2282257ea495b02ee52b99da2f7776707988d4af2c0d42` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `46d1b78a379e5ec61e4ed26c1051272a39002b7e24f2127835224b76a531ab0c` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `dae7809897fbebdaaf041b3d0ace45b420452edc05a7c66a9d7def19d308a0a8` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `c07d3c3f06dd7b3d2238e4b3eac8a645c867f582c9b58cd95da574569023bf37` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `cb667e5ae070cee5016f9b06a81de3ff4da75cd8d80f2fc2d4ed0b100303b4a6` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `1f1bb45d835242b0e4e54f07b30b96562ac7cc72e6ef4281bfead6a019a4112a` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `3a7f2ab561aeb81d572d539e71bfc8f623d3889583e27123ea4e21b7fcd1090e` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `c2b8542013e0f6dae4c5baa1b013571f40489037becf584e194d790352df3fdf` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `bf43345522d3b4e3203e61897fd66724bb5d1418319141ae66804fc31c5082ec` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `bf43345522d3b4e3203e61897fd66724bb5d1418319141ae66804fc31c5082ec` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `f647e0d9087b328cd31318055d7ac9f56f3dcdf8acf06ec169e7fa4d3a351b38` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `7a7598aaac4cfc791183d70fe234dee2da7734b2d94efc6a3ff0e117bb2a4439` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `3a78ede4002ee99b19af7912fc94eec22793ad73923207a421e3fdf1601580b8` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `b1968f1bf8e35c85c535a587a079ad72a9cda792004fadd24919edb1ceadac60` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `1d3fa2c21e994d0cc98790b6191a2be780da0442aa7dc60348265d1bf1f723c4` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `1d3fa2c21e994d0cc98790b6191a2be780da0442aa7dc60348265d1bf1f723c4` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `a1be706784cca01f6fc3b7f671e874aa041ad500696f916ab073154de4a8cbcc` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `2bc34457652b31716903384261fb7bf32f895bbe45f36cf34c32cd6ebc7e1c27` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `52e94b8727d260c7e32de12190e12640f745a64802a7d37b3a08ea1ebdb8d1a0` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `712cfd7739b099b0703d47130b997f02314595202654d0b426606e41f9134515` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `983adc4d3469b55862cd2d8dac0a08399b42f3ef66763f2006d885586385b8ba` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `ad71d893fecbc5c385dadf9042ebdbde34ba10f5c40cb2b0036cf29acf16ed40` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `99970b968a21288b7b5328b1bccdbe26f0a7756a8e640c5df11708eb9beceab4` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `d70d9b8f0226ff49a2af5254cf528569ccd3ff24ae4d71df68e9f41863b966ee` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `04dbeab8c261cbe2d54a3d4c9d0e9fa92feac8bd51c0156a3fbe6b3209500bd0` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `087e2ace5227f1943f2e8d9a41f46e0acff018cc24e606a5d11c4ff1b9b9806b` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `ed7e480159ef092594a4d8ec1f9c1bb54a387eb5295e0249d40024230ea0e1e1` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `c081bbec52b595c25573231523ebc6f084c2b2c2fc488824eca5b5a44b09c852` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `b2279517b51dcf3ed93bc97f891ecee02d2a6d405ef6a4ac9cb3d2dafcc7a2e9` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `94bab407b671b4ae01f368f39434f30d8d5bb4194cda6127428820f9b9cfd187` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `426b43e859e4c33a505bdd5f2d83973ca25f4fc5a367404aeb99b530230fb512` |

## 截图

### 未配对界面

#### 三标签导航

![三标签导航](images/host_navigation_initial.png)

### 电话与拨号

#### 未配对电话记录页

![未配对电话记录页](images/host_call_history.png)

#### 独立拨号键盘

![独立拨号键盘](images/host_dialpad.png)

### 短信

#### 短信会话列表

![短信会话列表](images/host_sms_thread_list.png)

### 设置

#### 设置与配对表单

![设置与配对表单](images/host_settings_tab.png)

#### 设置中的短信备份入口

![设置中的短信备份入口](images/host_settings_backup_entry.png)

### 窗口适配

#### 主界面合拢窗口

![主界面合拢窗口](images/folded_ui.png)

#### 主界面展开窗口

![主界面展开窗口](images/unfolded_ui.png)

#### 显示挖孔时的主界面

![显示挖孔时的主界面](images/hole_cutout_enabled.png)

### 短信备份

#### 滚动后可见的备份入口

![滚动后可见的备份入口](images/main-backup-entry-host.png)

#### 短信备份与归档页面

![短信备份与归档页面](images/backup-overview.png)

#### 备份页面横屏

![备份页面横屏](images/backup-rotation-landscape.png)

#### 备份页面竖屏

![备份页面竖屏](images/backup-rotation-portrait.png)

#### 备份页面合拢

![备份页面合拢](images/backup-folded.png)

#### 备份页面展开

![备份页面展开](images/backup-unfolded.png)

#### 挖孔安全区中的备份页面

![挖孔安全区中的备份页面](images/backup-cutout.png)

#### 合成短信归档导入预览

![合成短信归档导入预览](images/backup-import-preview.png)

#### 导入后的归档历史

![导入后的归档历史](images/backup-imported-history.png)

### 配对数据界面

#### 配对状态下的三标签

![配对状态下的三标签](images/paired-fixture/paired_three_tabs.png)

#### 双 SIM 合成通话记录

![双 SIM 合成通话记录](images/paired-fixture/paired_call_history.png)

#### 从通话记录预填号码的拨号页

![从通话记录预填号码的拨号页](images/paired-fixture/paired_dialpad_from_history.png)

#### 配对状态下的独立拨号键盘

![配对状态下的独立拨号键盘](images/paired-fixture/paired_dialpad.png)

#### 三条合成短信会话

![三条合成短信会话](images/paired-fixture/paired_sms_threads.png)

#### 合拢窗口中的短信会话

![合拢窗口中的短信会话](images/paired-fixture/paired_sms_threads_folded.png)

#### 展开窗口中的短信会话

![展开窗口中的短信会话](images/paired-fixture/paired_sms_threads_unfolded.png)

#### 既有短信对话与回复框

![既有短信对话与回复框](images/paired-fixture/paired_sms_conversation.png)

#### 新短信编辑页

![新短信编辑页](images/paired-fixture/paired_new_message.png)

#### 选择 SIM 2 的新短信页

![选择 SIM 2 的新短信页](images/paired-fixture/paired_new_message_sim_b.png)

#### 横屏且键盘显示时的短信草稿

![横屏且键盘显示时的短信草稿](images/paired-fixture/paired_new_message_landscape.png)

#### 合拢窗口中的短信草稿

![合拢窗口中的短信草稿](images/paired-fixture/paired_new_message_folded.png)

#### 展开窗口中的短信草稿

![展开窗口中的短信草稿](images/paired-fixture/paired_new_message_unfolded.png)

#### 配对状态下的设置

![配对状态下的设置](images/paired-fixture/paired_settings.png)

#### 配对设置中的短信备份入口

![配对设置中的短信备份入口](images/paired-fixture/paired_settings_backup.png)

### 合成通话界面

#### 合成出站呼叫准备界面

![合成出站呼叫准备界面](images/paired-fixture/call-synthetic-dialing.png)

#### 合成通话中界面

![合成通话中界面](images/paired-fixture/call-synthetic-active.png)

#### 合成通话键盘界面

![合成通话键盘界面](images/paired-fixture/call-synthetic-keypad.png)

#### 合成来电界面

![合成来电界面](images/paired-fixture/call-synthetic-incoming.png)

## 结果文件校验

| artifact 文件 | SHA-256 |
|---|---|
| `results.json` | `f94135cb36a4128b97299dce53afeadba77ac1e853155ad9e949f93b1930cd5f` |
| `screenshot_manifest.json` | `1c1c9e78b318ad4e9b84f08fe0905882224c45e907c7c16118f2778b3ce7b981` |
| `paired-fixture/results.json` | `b260e1a054d8cffefd5a23a7f8df01276a972afdb836a9ccb6f3c3676dc6f359` |
| `paired-fixture/fixture_manifest.json` | `88b0bcf93d23515baed5e70532fc93ff26cbe08bb68072b177284bb28d207909` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
