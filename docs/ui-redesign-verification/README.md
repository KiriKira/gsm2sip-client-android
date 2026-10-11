# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`245bfab51e1559b0101a4da326750f9d7496db94`
- GitHub Actions run：[38141062274](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38141062274)
- artifact ID：`11703441850`
- artifact digest：`sha256:636764c000dcb40674130304e61c2a1cde0e12ed96430a7bc6a5e9ef634815e9`
- APK：`app-debug.apk`，SHA-256 `866027c7a46599f02c54f5c443e30782b8e23f65e0be8ded0ccaec6b635c6b7b`，19615133 字节
- artifact 内未配对 smoke：`pass`（2026-10-11T13:02:47.942765+00:00）
- artifact 内配对 fixture：`pass`（2026-10-11T13:06:51.743481+00:00）
- 报告生成时间：`2026-10-11T13:07:07Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `3ac1db7d476a0005c833ac6d633d2b239b800b2de89d40db082d7405b282137a` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `061e0fc0a1eb9d770da3077f7cd6890032aa105a927e27c6e7c0abee436e9290` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `e21d438e5a63e647bcf0ce4bc45d155b6daa1f9cc112d329b267e0c18c93879a` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `e1e65e2d7c2220ccc83648033c3e50aefd526c57cf5bc719b75d8afec4be08c2` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `840872b187aefdf1b880d8a34d3d39a0fdb86f6f0e06f607507ba756fab69ff0` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `4946227b600c09d91c00659717e1a4d50a683ca839a90f8bb87c30efae96f41d` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `bba25325823955dfeedca977e69f33098ad7af344f017ab3130c5eaf2b182d63` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `63f7aac8d6b702bf99b73f84bcf417fe1e0a63e1543c7cb3c49b06f9a1bb8663` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `20386103bb4a96d2730e3c134b239468f50fd4e7e3b4d74cc85b947f2e359288` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `b3c9a4d416afcdb3f328932d7e4367e6474ea983764c9d2df78da3ad3eafb5dc` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `4d279f4c925dfb22b65da7abe1e579e5a26936e025732e19283dc2100230f055` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `a6f431ccd1944b77e27f86baed8f2eef100fb51d877b17d775a5f1583864c071` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `2b3e551c37741b45280e257d2d24ce394a515c71f16a193362ccf8dcf9d85dd4` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `2c6b6280a80460d3cd3502926aa18f8434b1bc10e41baae2ed5117e745f4917f` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `090453e94e8a3498c737714dddde6708b7c1070bd765bf5c95c708cb52509357` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `090453e94e8a3498c737714dddde6708b7c1070bd765bf5c95c708cb52509357` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `619d84845878d53a592bec597e68de8b79ef57c7985cac9b05e98abc9d2bded3` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `ef1bedcad67e3a37ef50b3c6ab45756a0fe02e581e36ce2ef13759099414d4e5` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `ddd4833d0ca0e0ad9ba98b66df1ad3e329312cd949a8cd5ecd5845b0d36e0b93` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `c84ca659e76b317fdf557f7ca80a816b3fd5ac387285499eba5d1a4e0bf10b77` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `c1613da0067cc5e3cbf07f0f61ff10430a211048ef4443f0793a249975abb2d5` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `c1613da0067cc5e3cbf07f0f61ff10430a211048ef4443f0793a249975abb2d5` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `63acce9f87a5c831b9b8fac4d7f15859d5c91446a82affc2ccc378211b961aff` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `6a93423ea126d911dd6d25bdc9e594d7d296d36e8edfdf48ad886061f150dfc8` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `63acce9f87a5c831b9b8fac4d7f15859d5c91446a82affc2ccc378211b961aff` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `9d1a1633728eadf80520410f4fef4335b46e7b65a794d908611085c7cd761703` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `870f087be503bf00b35f157cd57bd16b3828fe58f0982c7a273bb6a7f95957c9` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `dd6de569337d6493e829fe94b3faaca2bf0aaf3c4374bca4e3cf2a05bd3a7c20` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `2706a69bc03fc902d81b5221ab6def246da91c2d9df16f45f30b18ca1c8fbffd` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `4aeba0c6df57493cdf3df73a5d73224a758b3365ab0acbabf850643ec4fc6534` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `0ee89cceb3ec0872963d49c45ce9fbb0f67b8920d40ef19ba1dbb062b5ff17db` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `e0cbde200efe93bdb7dbd5a4b256eef93aa71a4dccaab5ac5a52b06cb3c16e4e` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `2f19064dcef770fc8efd3f0c784bd3c0eddd983eee47c643e592a9f453070c64` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `0cea98328a7d1519bbc21790a73ff1b4b21be42c76966fc7f2a20edfc4de83e1` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `23d14ac3191221bbb11f3a26bcc686d0100bb0b67fc31eab1bf9e38ca922c600` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `de912c0e5d06a8ba731616de463521f65bafc991e34019a9c8f29958d2c85ffe` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `24350724808aafe74448c33c5287f514b441542f1bc71c26a98912f4836d64c9` |

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
| `results.json` | `f482b4b88f7f674bc6ef1f72b2583e22cf249b21b9e758e0e3713661fd252cab` |
| `screenshot_manifest.json` | `b8b629e0596a12afb2cb25b2816af56dfeb822fb786b3a125680657449a2b335` |
| `paired-fixture/results.json` | `2d6bd7095a0c68ce3e6c57924897ca28df76881c9d70b2eb4394a45061e23eb4` |
| `paired-fixture/fixture_manifest.json` | `c78268e44b351e713228ee4d077a8821a41adb9cf4988612ee43052cc01b4490` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
