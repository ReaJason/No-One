# No One

[![Telegram](https://img.shields.io/badge/Chat-Telegram-%2326A5E4?style=flat-square&logo=telegram&logoColor=%2326A5E4)](https://t.me/memshell)
[![MemShellParty](https://img.shields.io/badge/SisterProj-MemShellParty-%23646CFF?style=flat-square&logoColor=%23646CFF)](https://github.com/ReaJason/MemShellParty)

No One（无名）：Next Generation Java WebShell Manager，让我们一起如入无人之境。

> [!WARNING]
> 本工具仅供安全研究人员、网络管理员及相关技术人员进行授权的安全测试、漏洞评估和安全审计工作使用。使用本工具进行任何未经授权的网络攻击或渗透测试等行为均属违法，使用者需自行承担相应的法律责任。

> [!TIP]
> 由于本人仅是 RASP 安全产品研发，无实战经验，此项目仅用于本人更好地了解 Web 攻防场景下常见的攻击行为，从而编写更可靠的
> RASP
> 防御规则。欢迎加入 TG 交流群，一起学习交流。

## Why

### 开源 WebShell 工具现状

1. 大部分仅支持 Servlets HTTP 规范，不支持 Reactor(SpringCloud) 或 Netty(XXL-JOB) 等 Web
   场景下常见的通信协议，无法测试部分漏洞攻击行为，不知道攻击手法导致防御跟不上攻击行为检测。
2. 部分桌面程序依赖 JavaFX 在某些 JDK 版本启动困难，想测个 shell 怎么这么难
3. 蚁剑/冰蝎/哥斯拉客户端年久失修，部分功能也不太好用，急需一款更现代的 WebShell 管理工具
4. 冰蝎/哥斯拉仅提供成品包，有部分反编译源码项目分享，但并不能完整还原项目代码，不利于学习

### 现代化 WebShell 管理工具

1. Web 界面，现代 UI，风格统一，跨平台能力，协同作战
2. 统一认证授权，权限分离（不同角色对于 shell 操作权限不一）
3. 全局审计日志，操作留痕，方便追溯
4. 自动化内网渗透，批量进行后渗透，内网资产采集与敏感文件解析

### 时代在召唤

漏洞扫描器越来越强大，而在单点渗透这块大部分工作仍然需要手动进行，或半自动，效率不佳。依赖于开源社区各种后渗透工具，完全能够编写一个强大的单点渗透扫描器，随着规则越来越多，一个人在本地往往很难维护（数据丢失），且无法团队共享，本地客户端不再适合，平台化就此展开。而且随着
AI 智能的不断提升，慢慢加入到了安全漏洞辅助研判中来，因此渗透工作也会变得越来越自动化，平台化是趋势，自动发现 +
人工确认，我们都能亲自部署，亲自指挥。

### 学习应用开发安全

在学习完各种 Web 漏洞产生的原理，在开发中就应该对自己的接口进行安全审计，确保项目是安全的，学习的最佳方式就是实践。学习如何开发，学习如何测试，学习如何部署。

## What

这是一个旨在提高安全研究人员的开发技能提升的项目，用于学习和练习平台化中需要开发所使用的技能，让我们都能成为会应用安全的研发人员。

### 基础功能模块

1. 用户管理/权限管理：RBAC 认证授权模型
2. 项目管理：用于 shell 分组方便进行管理和归档，统一进行操作
3. 审计日志管理：查询操作日志与筛选导出
4. Shell 连接管理：ShellManager（新增/编辑/分组/删除/导入/导出/流量模板）
5. Web 组件管理：WebComponentManager（新增/删除）（注入其他类型内存马、一键持久化内存马）
6. 命令执行：CommandExecution（命令回显/虚拟终端）
7. 文件管理：FileManager（遍历目录/查看/上传/下载/新增/遍历 jar/遍历 zip/压缩文件/更改最后修改时间）
8. 数据库管理：DataBaseManager（建立连接/执行 SQL 语句/存档/数据库驱动 JAR 注入）
9. HTTP 隧道：HttpTunnel（HTTP 代理/发送 HTTP 请求）
10. SOCKS5 正向代理：Socks5Proxy

### 后渗透工具箱

1. 获取当前 Java 进程基础信息
2. 获取网络连接
3. 获取进程信息
4. 端口扫描（CIDR/IP）（PORTS）（TIMEOUT）
5. DNS LookUp
6. JarLoader
7. ShellCodeLoader
8. HeapDump（提取有用数据）
9. 其他各种辅助工具

### 核心设计

1. 突出一个集大成者（没有一滴是自己的），基于哥斯拉进行二开，流量模板 c2-profile
2. 类缓存：缓存注册的类，之后进行方法调用时直接传方法名和参数即可，首次执行可将加载的类和动作参数一起发送，解决部分元空间不够大的问题。
3. 后台定时任务：部分耗时操作，例如敏感文件扫描/端口扫描等在后台进行执行并将结果缓存在内存中（当数据量过大时使用文件缓存），后续发送获取请求拿到结果并清空内存中对象释放内存。

## How

### 技术栈

前端：React + shadcn/ui + Tailwind CSS V4 + React Router V7(Server-Side Rendering)

后端：JDK17 + Spring Boot 3 + Spring Security + PostgreSQL

部署：Nginx + Docker

未来可期！
