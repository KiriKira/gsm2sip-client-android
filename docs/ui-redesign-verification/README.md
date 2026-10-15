# Android 主机三标签 UI 验证

本报告由通过的 Android 35、7.6 英寸折叠屏 KVM UI smoke artifact 自动生成。截图是 artifact 中原始 PNG 字节的逐字节副本。

## 运行来源

- 仓库：`KiriKira/gsm2sip-client-android`
- 源提交：`a4beece516941920d972ca6cff955e63566adeb7`
- GitHub Actions run：[38715495431](https://github.com/KiriKira/gsm2sip-client-android/actions/runs/38715495431)
- artifact ID：`11962963729`
- artifact digest：`sha256:30cb43193c25a2aaea7b2b8f121e435eb6e2eec54c19f3bd4d4903886485e009`
- APK：`app-debug.apk`，SHA-256 `91f5cabb254e92653e2b88ef4c2b5824f8be7e226d48f27daa4063f95c815f7b`，19681021 字节
- artifact 内未配对 smoke：`pass`（2026-10-15T09:55:28.032843+00:00）
- artifact 内配对 fixture：`pass`（2026-10-15T09:59:37.437649+00:00）
- 报告生成时间：`2026-10-15T09:59:51Z`

## 验证范围

未配对流程覆盖电话记录页、拨号键盘、短信列表、设置与备份入口，以及窗口旋转、折叠、展开和挖孔安全区。配对 fixture 使用离线合成的会话、两张 SIM、三条短信和通话记录；新建短信草稿经历 IME、旋转、折叠和展开。

四张出站准备、通话中、通话键盘和来电截图来自进程内合成 coordinator UI 状态；没有启动 SIP、Telecom、音频或蜂窝通话，未发送或接收短信。配对 fixture 结果要求 `fixture_active_call_ui=pass`。

本报告只验证 Android Emulator 上的界面流程，不代表 Z Fold8 真机、真实 SIM 短信收发、SIP 注册或双向语音已完成设备验收。

## 截图与文件校验

| 页面 | PNG | 尺寸 | 文件 SHA-256 |
|---|---|---:|---|
| 三标签导航 | [`images/host_navigation_initial.png`](images/host_navigation_initial.png) | 1768 × 2208 | `e885093989283e99d5c37271705b17509772cdd30c1ab3301e7067a2b2ac6ce3` |
| 未配对电话记录页 | [`images/host_call_history.png`](images/host_call_history.png) | 1768 × 2208 | `02537aff6647f45809dc6f2da2bbf52e0d7f3aabcbd50350dffa8c3b759aed93` |
| 独立拨号键盘 | [`images/host_dialpad.png`](images/host_dialpad.png) | 1768 × 2208 | `bf71d3c69c89e33a148f6bb5b581ce86169db0e72e75fa6278826d6af50eaec1` |
| 短信会话列表 | [`images/host_sms_thread_list.png`](images/host_sms_thread_list.png) | 1768 × 2208 | `409909d62a564547d0d0ee4ca78e93c22678a408ad27d4deab81746f489fce52` |
| 设置与配对表单 | [`images/host_settings_tab.png`](images/host_settings_tab.png) | 1768 × 2208 | `5516635412d6fe8eedb07515a29e2cd62c216579230c41539a4eface66ef3582` |
| 设置中的短信备份入口 | [`images/host_settings_backup_entry.png`](images/host_settings_backup_entry.png) | 1768 × 2208 | `13b2058e3f087350bf5d2ce3d60ae99e04fc4ffdc7b209a15ab52deb7c2c104a` |
| 主界面合拢窗口 | [`images/folded_ui.png`](images/folded_ui.png) | 1768 × 2208 | `3ee4631f048da1f92aefd63a94c752dea3c417600e5d9b61eec1aaeebde7ee9b` |
| 主界面展开窗口 | [`images/unfolded_ui.png`](images/unfolded_ui.png) | 1768 × 2208 | `9e2c8511e51856ca5c2e99789c836b597b70d9e811972d39372b0b982496d640` |
| 显示挖孔时的主界面 | [`images/hole_cutout_enabled.png`](images/hole_cutout_enabled.png) | 1768 × 2208 | `aec19d1b60a10a1506f1f95d87509580e825f1f01d0036c0b4c6a4e491ecb99c` |
| 滚动后可见的备份入口 | [`images/main-backup-entry-host.png`](images/main-backup-entry-host.png) | 1768 × 2208 | `943c11dc5cfaa6e35a8ee2b0342bf322a13b64339b39d615fad175aab126943e` |
| 短信备份与归档页面 | [`images/backup-overview.png`](images/backup-overview.png) | 1768 × 2208 | `f2087e84559c73ecc146df7bd8b459a396776d6ba6b83061a35906ad3050c2e1` |
| 备份页面横屏 | [`images/backup-rotation-landscape.png`](images/backup-rotation-landscape.png) | 2208 × 1768 | `c84c4ef361d1add86f9c7ccb00135a78887d8c043d837411a15778c9e2a085fa` |
| 备份页面竖屏 | [`images/backup-rotation-portrait.png`](images/backup-rotation-portrait.png) | 1768 × 2208 | `b5bda737c3aa62ce364a44c23eb837ec78c597f99797af5cf45da2e73ca24bf2` |
| 备份页面合拢 | [`images/backup-folded.png`](images/backup-folded.png) | 1768 × 2208 | `a4b23d6cad9b145b2877d37758ada0d199b184b28a82034279304f18637f2666` |
| 备份页面展开 | [`images/backup-unfolded.png`](images/backup-unfolded.png) | 1768 × 2208 | `b5bda737c3aa62ce364a44c23eb837ec78c597f99797af5cf45da2e73ca24bf2` |
| 挖孔安全区中的备份页面 | [`images/backup-cutout.png`](images/backup-cutout.png) | 1768 × 2208 | `ce60edb5d179c541227ee14bf5ca1f9ea957e59c77010eefc6b4a813cf07126b` |
| 合成短信归档导入预览 | [`images/backup-import-preview.png`](images/backup-import-preview.png) | 1768 × 2208 | `2637b5d5b6784d1b1fd47ce764be17e8f12dd2b979ab1feda7bdeab0b649388c` |
| 导入后的归档历史 | [`images/backup-imported-history.png`](images/backup-imported-history.png) | 1768 × 2208 | `3c83ecfb7b541da6c453863b5c68853887e0798af248128a87ed63e070452cae` |
| 配对状态下的三标签 | [`images/paired-fixture/paired_three_tabs.png`](images/paired-fixture/paired_three_tabs.png) | 1768 × 2208 | `c1b1df0c6b430922e00c7bf2ee12fac98d15cd06d731b267dbc02be32c8adee7` |
| 双 SIM 合成通话记录 | [`images/paired-fixture/paired_call_history.png`](images/paired-fixture/paired_call_history.png) | 1768 × 2208 | `84f10a1941a956b846828ef464e0a17bf5679c243064b18deb3b1bd9583453cc` |
| 从通话记录预填号码的拨号页 | [`images/paired-fixture/paired_dialpad_from_history.png`](images/paired-fixture/paired_dialpad_from_history.png) | 1768 × 2208 | `29c2a191d9d0c07e7307932550aa88ae919a8dc6fbd12e8acf844dd2128129c2` |
| 配对状态下的独立拨号键盘 | [`images/paired-fixture/paired_dialpad.png`](images/paired-fixture/paired_dialpad.png) | 1768 × 2208 | `29c2a191d9d0c07e7307932550aa88ae919a8dc6fbd12e8acf844dd2128129c2` |
| 三条合成短信会话 | [`images/paired-fixture/paired_sms_threads.png`](images/paired-fixture/paired_sms_threads.png) | 1768 × 2208 | `b8b9271ca593f3d85df063d4734f139c79b5d987e8fff39add368a8f1820f462` |
| 合拢窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_folded.png`](images/paired-fixture/paired_sms_threads_folded.png) | 1768 × 2208 | `7fef217f177a350dbe1a747a942c6865687d42aed210b8e4ff895e7fab8d20cd` |
| 展开窗口中的短信会话 | [`images/paired-fixture/paired_sms_threads_unfolded.png`](images/paired-fixture/paired_sms_threads_unfolded.png) | 1768 × 2208 | `6e5a07668018402872746aad416fdeed3b8d2c7751b58db25ac146fb6f707e58` |
| 既有短信对话与回复框 | [`images/paired-fixture/paired_sms_conversation.png`](images/paired-fixture/paired_sms_conversation.png) | 1768 × 2208 | `56bc3595c3ad328cb446485ec23eb0616ee2bc538f43005f87605062be365709` |
| 新短信编辑页 | [`images/paired-fixture/paired_new_message.png`](images/paired-fixture/paired_new_message.png) | 1768 × 2208 | `c1783c042417fb62ef8459d8020edfad081cfc652f022ad3cbc5c3ae2081632f` |
| 选择 SIM 2 的新短信页 | [`images/paired-fixture/paired_new_message_sim_b.png`](images/paired-fixture/paired_new_message_sim_b.png) | 1768 × 2208 | `aaa6401e1630baa9edbd7881bf2b0f44cd82c200d704ea84ce01000ab6ead5bb` |
| 横屏且键盘显示时的短信草稿 | [`images/paired-fixture/paired_new_message_landscape.png`](images/paired-fixture/paired_new_message_landscape.png) | 2208 × 1768 | `6471c09efd13bb536106618d0f71dc98b524b506ab2778a26328bc2c35737455` |
| 合拢窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_folded.png`](images/paired-fixture/paired_new_message_folded.png) | 1768 × 2208 | `b8a30e0792bdd49472ddbaef469f00f2342b3690a633e647309bdd7e39a3823e` |
| 展开窗口中的短信草稿 | [`images/paired-fixture/paired_new_message_unfolded.png`](images/paired-fixture/paired_new_message_unfolded.png) | 1768 × 2208 | `432e5dd004a0915c64229ea859427ff77c97c6ef1c932126956f84b7b119eed8` |
| 配对状态下的设置 | [`images/paired-fixture/paired_settings.png`](images/paired-fixture/paired_settings.png) | 1768 × 2208 | `2b00692589f6f423d1548e29253dbfdf350996cd945a707bcf8557bfc0c47b53` |
| 配对设置中的短信备份入口 | [`images/paired-fixture/paired_settings_backup.png`](images/paired-fixture/paired_settings_backup.png) | 1768 × 2208 | `94e545da78d801d35bbba61930e9a571bae4a94392b01468fa539afa5459998d` |
| 合成出站呼叫准备界面 | [`images/paired-fixture/call-synthetic-dialing.png`](images/paired-fixture/call-synthetic-dialing.png) | 1768 × 2208 | `3bae2088f1ddcda0da6bc7edd5ed6943c9265ad1324662a0263fa479dfc0908b` |
| 合成通话中界面 | [`images/paired-fixture/call-synthetic-active.png`](images/paired-fixture/call-synthetic-active.png) | 1768 × 2208 | `309bc0f211f07966a14f4b0e72e7ae66559f69ec570a218e13fa1166dc0c5998` |
| 合成通话键盘界面 | [`images/paired-fixture/call-synthetic-keypad.png`](images/paired-fixture/call-synthetic-keypad.png) | 1768 × 2208 | `5092cbc980e8b5743a3e2cd9b7555637d6fac6c8309b87d6cdb0e722debd8300` |
| 合成来电界面 | [`images/paired-fixture/call-synthetic-incoming.png`](images/paired-fixture/call-synthetic-incoming.png) | 1768 × 2208 | `6b02b1106b520b4b7887f105d6490a671d1c9c6caf34aefe6c11a59cd2df1bfd` |

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
| `results.json` | `7b8737aa7717b4777461f99caa75a74bce2569ba6898c6cb1698f49cea3865c0` |
| `screenshot_manifest.json` | `5168e9e199b9b52391c5fb5a7fd5e3d045c53310d3af2be37cb1337cc39079a9` |
| `paired-fixture/results.json` | `914613f72c7aafb116b23252eabc0f1e37e04b453c01fc802df7bdc968e2bf77` |
| `paired-fixture/fixture_manifest.json` | `e583e5f8279e274e39dcf61249f0ceca3486d7b740b6e3fcf5f27c87c2c4e196` |

初始评审材料保留在 `images-initial/`、`initial-evidence.json`、`initial-ui-results.json` 和 `local-checks.json`；它们与本次通过的 KVM 截图分开保存。机器可读运行信息见 [`verified-run.json`](verified-run.json)。
