# 证据：Reception v2（R1）补测，2026-10-31

本目录只放脱敏后的用例摘要，来自测试机的私有目录 `private/retest-20261031/`。原始
`logcat`、`uiautomator` dump、截图与节点数据库快照都只在测试机上，没有入库；入库前已替换掉
模拟器地址与端口、隧道地址、号码、token 与测试机路径。

| 文件 | 用例 | 内容 |
| --- | --- | --- |
| `d3-1-summary.txt` | D3 第 1 次 | 注入前后状态、`device_reception`、注入→响铃时延、网关 offer、主机推送与响铃日志 |
| `d3-2-summary.txt` | D3 第 2 次 | 同上 |
| `c1-a-summary.txt` | C1-a 亮屏后台 | 焦点、通知栏控件、拒接后网关结束时间、参与者终态 |
| `c1-b-summary.txt` | C1-b 熄屏锁屏 | 熄屏状态、全屏来电界面出现时间、拒接后结束时间与文案 |
| `c1-c-summary.txt` | C1-c 前台 | 前台时直接打开通话界面、拒接时间与文案顺序 |
| `c1-perm-summary.txt` | C1 权限项 | `appops` 撤销前后的值、设置页显示的「未允许」、跳转到的系统页面 |
| `b1-summary.txt` | B1 | H1 关闭后只有 H2 响铃、两台节点的 `incoming` 与 revision |
| `b4-summary.txt` | B4 | 划掉应用与重启后本地偏好与节点侧状态 |
| `c3-summary.txt` | C3 | 两台同时响铃、关闭后 H1 停止（`reception_disabled`）、H2 继续响铃并可接听 |
| `a1-summary.txt` | A1 | force-idle 时长、响铃时延、接听后的参与者状态（`answered_elsewhere`） |

每条记录的 `*_utc` 是测试机时钟；主机侧的 `HostPush`/`HostCall` 行是模拟器时钟，两者不混用，
跨设备时延用测试机时钟的轮询值（粒度约 0.5 s）。
