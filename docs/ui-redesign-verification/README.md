# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`4d5bcbf191712d85cda5f0185ce89cbad03c5422`
- GitHub Actions run：[40061002349](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/40061002349)
- artifact ID：`12548255187`
- artifact digest：`sha256:34908b91d71e29a55475342cef59cf84f36cc2cbac925feb2b50f0aa7b66f9ec`
- APK：`app-debug.apk`，SHA-256 `6da53145b2ae8ae6c6701649026fc5306103fa3b1b5bbb8533f579a6c4426da9`，22231603 字节
- artifact 内未配对 smoke：`pass`（2026-10-24T04:14:55.982759+00:00）
- artifact 内配对 fixture：`pass`（2026-10-24T04:18:52.404942+00:00）
- 报告生成时间：`2026-10-24T04:19:04Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `17c9153ce48d8edb633c36ac2ee4730bf8045a7b0e10aafa2cef6167f229c9bc` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `b4f0caf5739610fda505c81ff413793e4f3efbc5c38aaeceb2f10153abe2e9b5` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `c6f4f3faab940a47edab9b1a286dcec997460496a4458ccdc3200cb493249b42` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `cf40ed175303429cb3305bfd103e0064d6b77eb3074314e48e0279cb84d4f988` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `1be9bb6a8b88cd7cedd11ee4248afe67fd84fef45fea4d16138f3d947ef15436` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `a246b08c21e8f3d0ab751dd694ca893def7e6bb06958c1f7611fb600418822a6` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `7771429e2f9f16cf6cfc803423b2f8e3db6dd3fbe7323f3d0b7c071bc89f414f` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `19a170b43cdcaea40ec15795494fced8465551bb24a3aeda9a50de28d270c73e` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `2d09de93319339b0f96a567d6613577b0d6cde3e50eef8a00f4559a56232b245` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `9a4caf07bcf418c7c16542ca7d8352db8a3ddec336f7d670956ebf107841281c` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `448e2f82f08288e512b737543efd769a3c4c233a6a7268b490d00c0da9d9be52` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `1c4d47f0c10f87bff3a9d5ee8c72876163e6deddd00ffba94b396b86970aa964` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `ede95eca042d1c0e676247d9a90f6e032baf0b291c482e3d9ff1168c42fb46f0` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `bec943350753ed0f1458271f92fb777a01879ce3eb62938f2f246a75925d6cd5` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `ede95eca042d1c0e676247d9a90f6e032baf0b291c482e3d9ff1168c42fb46f0` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `ede95eca042d1c0e676247d9a90f6e032baf0b291c482e3d9ff1168c42fb46f0` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `db3918eb22c70a989a79c65c436a7bc83a921c642bfe65813fccd2cf671a0fd7` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `ac03b04234bd97ed988f3e0be09bd530af9daef387060a75102874bbbfac7889` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `3eff605a44fc39e7a4790955561d14fe489129353773d7be38f004754f557703` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `7ce97159f8016ebd879958eb0f2b21c168984b1e47a6c868f0645b59148cae4f` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `d13c394271bbd3b309e7074712b379ff42d601986458155922dd5a18492ad4c3` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `d13c394271bbd3b309e7074712b379ff42d601986458155922dd5a18492ad4c3` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `314fdef0e7785844cc86f89a8e9a418ac3848f27bd78176334c5929e6878472d` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `8acbbf1f2718011c3e680433e6dd384aec6851b972e0db588975c662518a56ae` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `51259e320ed6c4d64d8eee0e0fca365aa3947a3ca259c1f4839bc57d298e227d` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `f0389198e0737b5dcda086b69d57bdde7f64f46ba48604d1926ddacd940f6ace` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `c7168bfa76540298e8f5a36b9d535409420e024c94448ee252c50f47336c4111` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `1af9c56729b9c1839c489141de939a73cff25420452bd50ab9581538316568bb` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `c1d617e5612658a245e3501de1e6f059710404a77c15d38e3f1fcf00c839bdb5` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `f413a4182835b13c1971fed752d2c3a52d4f229049b25954ca0ed64529f08f99` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `cf88b47f4a77371443721718f13464930df2c3eee2b6c2f6d2f258f9af347676` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `b7f371b1034d959509a9dcd0089ddb91cf52740664583d27f8ccf3875b0875fb` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `7dad8626667b25895dc1e7859b6df5668d043fb3ace008d835c28c383c1eecd3` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `4a73cc8c99498e0c7773c062391f77e8412f9cb11016b44e492a862f36016ce3` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `c50763b5d4dab1d7657c8f9f48d135e107a03099d36e76a9af1649f8a07da66c` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `83cc2ae079cd2dff5ad0d135deaa62910f97e428edd5130216f869b21368cf9c` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `d3c0b494e14a803ba03b5c69a271fb9da190ab9af27ba4daba24e37dd0a1b800` |

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
| `results.json` | `860b72023488f72d76901de622a1cd4a4531a16e14aa0981def67ba8e5d74537` |
| `screenshot_manifest.json` | `a1ed2d4a3fe01d1a1fda4e93f55f6a0edc604f39e0cf01392791fe5b96e1fb28` |
| `paired-fixture/results.json` | `039a5f525758576d3b512b2554b24af6b24ccb086728f33d8931d681ceb53f8c` |
| `paired-fixture/fixture_manifest.json` | `c3dd54e0ed99ffa2b00f8eef743adebbeb701b76cf9858759d21ef24f390af18` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
